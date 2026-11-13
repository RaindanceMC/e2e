package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
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
 * Once by /afkcam preview, once by going /afk with the essentials module saying who is away — tips above the hotbar
 * after the first seconds.
 */
@Tag("e2e")
class AfkCamScenarioTest {

    /** Entity data index of an entity's "silent" flag. */
    private static final int SILENT_FLAG = 4;

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
                    .record(ClientboundSoundEntityPacket.class)
                    .record(ClientboundSetEntityDataPacket.class)
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
            assertThat(watcher.received(ClientboundSoundEntityPacket.class))
                    .as("music from the camera itself, where the player hears from, on the jukebox slider (the game's "
                            + "own background music on the music slider is kept quiet) — a client without the server's "
                            + "pack gets the game's menu music")
                    .anyMatch(sound -> sound.getEntityId() == display && sound.getCategory() == SoundCategory.RECORD
                            && sound.getSound().getName().contains("music.menu"));
            assertThat(watcher.received(ClientboundSetEntityDataPacket.class).stream()
                    .filter(data -> data.getEntityId() == display)
                    .flatMap(data -> java.util.Arrays.stream(data.getMetadata()))
                    .filter(entry -> entry.getId() == SILENT_FLAG))
                    .as("the camera is not silent — a client drops every sound bound to a silent entity, the music too")
                    .noneMatch(entry -> Boolean.TRUE.equals(entry.getValue()));
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
            // first the way back, then, a few seconds on, a tip after the minutes
            watcher.expectActionBar("any key brings you back");
            Await.until(() -> "a tip above the hotbar (" + watcher.actionBars() + ")", Duration.ofSeconds(25),
                    () -> watcher.actionBars().stream().anyMatch(bar -> bar.startsWith("Away 0 min · 💡 ")));
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
