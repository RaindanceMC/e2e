package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autopilot on a real server, in first person: switched on in mid-glide it pushes the player along where they look,
 * burning rockets from deep in the inventory; the scroll wheel sets the cruise speed without changing the held slot,
 * F switches economy mode, sneaking switches it off. And Boost leaves a right click with food in hand to the food.
 */
@Tag("e2e")
class AutopilotScenarioTest {

    private static final int Y = 160;

    private static int rockets(Server server, String player) {
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("Found (\\d+)")
                .matcher(server.console("clear " + player + " firework_rocket 0"));
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    private static boolean gliding(Server server, String player) {
        return server.console("execute if entity @a[name=" + player + ",nbt={FallFlying:1b}]").contains("passed");
    }

    private static void glide(Server server, Bot bot, String name, double x) {
        server.console("tp " + name + " " + x + " " + Y + " 0.5 -90 0");
        Await.ticks(20);
        bot.look(-90, 0);
        // Falling a little each try, as the server wants before an elytra opens.
        for (int fall = 1; fall <= 5 && !gliding(server, name); fall++) {
            bot.moveTo(x, Y - 0.5 * fall, 0.5, false);
            bot.startGliding();
            Await.ticks(5);
        }
        assertThat(gliding(server, name)).as(name + " glides").isTrue();
    }

    @Test
    @DisplayName("on in mid-glide: pushed along on rockets from the inventory; scroll, F and sneak; food is eaten, not boosted on")
    void autopilot() {
        try (Server server = Server.start("autopilot", List.of("enchants-standalone:RainsEnchants-.*"),
                List.of("Enchants are up"))) {
            server.console("gamerule spawn_mobs false");
            server.console("forceload add -32 -32 32 32");
            Bot pilot = server.player("Pilot");
            server.console("gamemode survival Pilot");
            server.console("item replace entity Pilot armor.chest with elytra[enchantments={\"rainsenchants:autopilot\":3}]");
            server.console("item replace entity Pilot inventory.20 with firework_rocket[fireworks={flight_duration:1}] 16");
            server.console("item replace entity Pilot hotbar.0 with stone");
            glide(server, pilot, "Pilot", 0.5);

            pilot.run("autopilot");
            Await.until("switched on", Duration.ofSeconds(5),
                    () -> pilot.chatText().stream().anyMatch(line -> line.contains("Autopilot on") && line.contains("60")));
            Await.until("a rocket burnt from deep in the inventory", Duration.ofSeconds(5),
                    () -> rockets(server, "Pilot") == 15);
            Await.until("pushed east, along the look, faster than walking", Duration.ofSeconds(5),
                    () -> pilot.pushes().stream().anyMatch(push -> push[0] > 1));
            assertThat(pilot.soundsHeard()).contains("entity.firework_rocket.launch");
            assertThat(server.console("advancement test Pilot rainsenchants:autopilot")).doesNotContain("not");

            // Three notches down the wheel: 6 blocks a second slower; the slot stays the stone's.
            pilot.hold(3);
            Await.until("the cruise speed went down", Duration.ofSeconds(3),
                    () -> pilot.actionBars().stream().anyMatch(line -> line.contains("/54 b/s")));
            assertThat(server.console("data get entity Pilot SelectedItemSlot")).as("the hotbar did not move").contains(": 0");

            pilot.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket(
                    org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction.SWAP_HANDS,
                    org.cloudburstmc.math.vector.Vector3i.ZERO,
                    org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.DOWN, 0));
            Await.until("F: economy mode", Duration.ofSeconds(3),
                    () -> pilot.chatText().stream().anyMatch(line -> line.contains("Economy mode")));
            assertThat(server.console("data get entity Pilot Inventory[{Slot:-106b}]")).as("nothing swapped into the off hand")
                    .doesNotContain("stone");

            pilot.sneak(true);
            Await.until("sneak: off", Duration.ofSeconds(3),
                    () -> pilot.chatText().stream().anyMatch(line -> line.contains("Autopilot off")));
            pilot.sneak(false);
            int left = rockets(server, "Pilot");
            Await.ticks(60);
            assertThat(rockets(server, "Pilot")).as("no rocket burnt once off").isEqualTo(left);

            // --- Boost: a right click with food in hand eats, a right click with a block boosts -------------------------
            Bot eater = server.player("Eater");
            server.console("gamemode survival Eater");
            server.console("item replace entity Eater armor.chest with elytra[enchantments={\"rainsenchants:boost\":3}]");
            server.console("item replace entity Eater hotbar.0 with bread 8");
            server.console("item replace entity Eater hotbar.1 with stone");
            server.console("effect give Eater hunger 5 255 true");
            Await.ticks(60);
            glide(server, eater, "Eater", 10.5);
            eater.hold(0).useHeld();
            Await.ticks(10);
            assertThat(eater.actionBars()).as("no boost while eating").noneMatch(line -> line.contains("Boost!"));
            eater.releaseUse();
            eater.hold(1).useHeld();
            Await.until("a block in hand still boosts", Duration.ofSeconds(3),
                    () -> eater.actionBars().stream().anyMatch(line -> line.contains("Boost!")));
        }
    }
}
