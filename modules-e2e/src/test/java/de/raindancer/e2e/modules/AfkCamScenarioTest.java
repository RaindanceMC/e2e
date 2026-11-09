package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AFK camera on a real server, through the game's own packets: the client is told to look through an item
 * display it was sent, with a mannequin for its own body, and back through its own eyes the moment it presses a key —
 * while it stays in survival.
 * Once by /afkcam preview, once by going /afk with the essentials module saying who is away.
 */
@Tag("e2e")
class AfkCamScenarioTest {

    @Test
    @DisplayName("preview and going away both point the camera at a display; a key brings it back; survival throughout")
    void camera() {
        try (Server server = Server.start("afk-cam",
                List.of("afkcam-standalone:RainsAfkCam-.*", "essentials-standalone:RainsEssentials-.*"),
                List.of("AFK cam is up"))) {
            assertThat(server.log()).as("Core's camera fits this server").doesNotContain("cannot move a player's camera");
            Bot watcher = server.player("Watcher")
                    .record(ClientboundSetCameraPacket.class)
                    .record(ClientboundAddEntityPacket.class)
                    .record(ClientboundMoveEntityPosPacket.class)
                    .record(ClientboundMoveEntityPosRotPacket.class)
                    .record(ClientboundMoveEntityRotPacket.class)
                    .record(ClientboundTeleportEntityPacket.class)
                    .record(ClientboundEntityPositionSyncPacket.class);
            Await.ticks(40);
            server.console("fill -4 98 -4 4 98 4 minecraft:stone");
            server.console("tp Watcher 0 99 0");
            Await.ticks(40);

            watcher.run("afkcam preview");
            int display = lookingThroughSomethingElse(watcher);
            assertThat(watcher.titles()).anyMatch(title -> title.contains("Away"));
            Await.ticks(20);
            long moved = movesOf(watcher, display);
            Await.ticks(100);
            assertThat(movesOf(watcher, display)).as("between cuts the camera stands still — nothing to shake")
                    .isEqualTo(moved);
            pressForward(watcher);
            backInOwnEyes(watcher);
            assertThat(server.console("data get entity Watcher playerGameType")).contains(": 0");
            Await.ticks(10);
            assertThat(server.console("execute if entity @e[type=item_display]")).as("the display " + display + " is gone")
                    .containsIgnoringCase("failed");
            assertThat(server.console("execute if entity @e[type=mannequin]")).as("the stand-in is gone")
                    .containsIgnoringCase("failed");

            int before = watcher.received(ClientboundSetCameraPacket.class).size();
            watcher.run("afk");
            Await.until("a camera after going away", Duration.ofSeconds(20),
                    () -> watcher.received(ClientboundSetCameraPacket.class).size() > before);
            lookingThroughSomethingElse(watcher);
            pressForward(watcher);
            backInOwnEyes(watcher);
            assertThat(server.paper.errorsFrom("RainsAfkCam")).isEmpty();
        }
    }

    private static int lookingThroughSomethingElse(Bot bot) {
        int camera = Await.value("a camera packet for something other than the player", Duration.ofSeconds(10), () -> {
            List<ClientboundSetCameraPacket> cameras = bot.received(ClientboundSetCameraPacket.class);
            return cameras.isEmpty() || cameras.getLast().getCameraEntityId() == bot.entityId()
                    ? null : cameras.getLast().getCameraEntityId();
        });
        assertThat(bot.received(ClientboundAddEntityPacket.class))
                .as("the client was sent the display it looks through")
                .anyMatch(added -> added.getEntityId() == camera && added.getType() == EntityType.ITEM_DISPLAY);
        assertThat(bot.received(ClientboundAddEntityPacket.class))
                .as("and a mannequin to stand in for its own body, which the game does not draw to itself")
                .anyMatch(added -> added.getType() == EntityType.MANNEQUIN);
        return camera;
    }

    /** Packets that move or turn {@code entity}; the server's "still here" with no movement does not count. */
    private static long movesOf(Bot bot, int entity) {
        return bot.received(ClientboundMoveEntityPosPacket.class).stream().filter(p -> p.getEntityId() == entity)
                .filter(p -> p.getMoveX() != 0 || p.getMoveY() != 0 || p.getMoveZ() != 0).count()
                + bot.received(ClientboundMoveEntityPosRotPacket.class).stream().filter(p -> p.getEntityId() == entity).count()
                + bot.received(ClientboundMoveEntityRotPacket.class).stream().filter(p -> p.getEntityId() == entity).count()
                + bot.received(ClientboundTeleportEntityPacket.class).stream().filter(p -> p.getId() == entity).count()
                + bot.received(ClientboundEntityPositionSyncPacket.class).stream().filter(p -> p.getId() == entity).count();
    }

    private static void pressForward(Bot bot) {
        bot.keys(true, false, false, false, false, false, false);
        Await.ticks(5);
        bot.keys(false, false, false, false, false, false, false);
    }

    private static void backInOwnEyes(Bot bot) {
        Await.until("the camera back on the player", Duration.ofSeconds(5), () -> {
            List<ClientboundSetCameraPacket> cameras = bot.received(ClientboundSetCameraPacket.class);
            return !cameras.isEmpty() && cameras.getLast().getCameraEntityId() == bot.entityId();
        });
    }
}
