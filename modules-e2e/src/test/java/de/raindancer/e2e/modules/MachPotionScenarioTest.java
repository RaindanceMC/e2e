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
 * The Potion of Mach Fuck on a real server: a dragon head brews it from a water bottle in a brewing stand; drunk
 * mid-glide it pushes the player 30 blocks a tick along their look, and a client that follows the pushes is not
 * pulled back as moving too quickly — the world's elytra speed check is off while it works, and on again after.
 */
@Tag("e2e")
class MachPotionScenarioTest {

    private static final int Y = 160;

    private static boolean gliding(Server server, String player) {
        return server.console("execute if entity @a[name=" + player + ",nbt={FallFlying:1b}]").contains("passed");
    }

    private static double serverX(Server server, String player) {
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("\\[(-?[0-9.]+)d")
                .matcher(server.console("data get entity " + player + " Pos"));
        return found.find() ? Double.parseDouble(found.group(1)) : Double.NaN;
    }

    @Test
    @DisplayName("brewed from a dragon head, drunk mid-glide: 600 blocks a second where one looks, and the server keeps up")
    void mach() {
        try (Server server = Server.start("mach", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            server.console("gamerule spawn_mobs false");
            assertThat(server.console("settings set mach.seconds 5")).contains("is now");
            server.console("forceload add -16 -16 16 16");
            server.console("fill -4 63 -4 4 63 4 minecraft:stone");
            server.console("setblock 0 64 0 minecraft:brewing_stand");
            Bot flyer = server.player("Flyer");
            server.console("gamemode survival Flyer");
            server.console("tp Flyer 2.5 64 0.5");

            // --- brewed: water bottle + dragon head + blaze powder ---------------------------------------------------
            server.console("item replace block 0 64 0 container.0 with potion[potion_contents={potion:\"minecraft:water\"}]");
            server.console("item replace block 0 64 0 container.3 with dragon_head");
            server.console("item replace block 0 64 0 container.4 with blaze_powder");
            Await.until("brewed into the Potion of Mach Fuck", Duration.ofSeconds(40), () -> server.console(
                    "data get block 0 64 0 Items[{Slot:0b}].components.\"minecraft:item_model\"").contains("potion_of_mach_fuck"));
            assertThat(server.console("data get block 0 64 0 Items[{Slot:3b}]")).as("the head is used up")
                    .doesNotContain("dragon_head");
            assertThat(server.console("advancement test Flyer rainsadventurers:hold_my_elytra")).doesNotContain("not");

            // --- drunk mid-glide ----------------------------------------------------------------------------------------
            server.console("item replace entity Flyer armor.chest with elytra");
            server.console("item replace entity Flyer hotbar.0 from block 0 64 0 container.0");
            server.console("tp Flyer 0.5 " + Y + " 0.5 -90 0");
            Await.ticks(20);
            flyer.look(-90, 0);
            for (int fall = 1; fall <= 5 && !gliding(server, "Flyer"); fall++) {
                flyer.moveTo(0.5, Y - 0.5 * fall, 0.5, false);
                flyer.startGliding();
                Await.ticks(5);
            }
            assertThat(gliding(server, "Flyer")).as("Flyer glides").isTrue();
            double y = Y - 2.5;
            flyer.moveTo(0.5, y, 0.5, false);
            int before = flyer.pushes().size();
            flyer.hold(0).useHeld();
            Await.until("drunk: MACH FUCK", Duration.ofSeconds(5),
                    () -> flyer.chatText().stream().anyMatch(line -> line.contains("MACH FUCK")));
            Await.until("pushed along the look at 30 blocks a tick", Duration.ofSeconds(3), () -> flyer.pushes()
                    .stream().skip(before).anyMatch(push -> push[0] > 29 && Math.abs(push[2]) < 1));

            // A client follows the pushes for a second: the server goes along, nobody is pulled back.
            // Like a real client: follows each push, and takes the server's word when it is put back.
            double x = 0.5;
            for (int tick = 0; tick < 20; tick++) {
                double told = flyer.position().getX();
                if (Math.abs(told - x) > 1e-3) {
                    x = told;
                }
                List<double[]> pushes = flyer.pushes();
                x += pushes.get(pushes.size() - 1)[0];
                flyer.moveTo(x, y, 0.5, false);
                Await.ticks(1);
            }
            Await.ticks(5);
            long pulledBack = server.log().lines().filter(line -> line.contains("Flyer moved too quickly")).count();
            assertThat(serverX(server, "Flyer")).as("the server has the flyer where the client flew").isGreaterThan(400);
            assertThat(pulledBack).as("never put back as moving too quickly").isZero();
            assertThat(server.console("gamerule elytra_movement_check")).as("the speed check is off while it works")
                    .contains("false");
            Await.until("the advancement", Duration.ofSeconds(3), () -> !server.console(
                    "advancement test Flyer rainsadventurers:mach_fuck").contains("not"));
            Await.until("the speed check back on once it wore off", Duration.ofSeconds(10),
                    () -> server.console("gamerule elytra_movement_check").contains("true"));
            assertThat(flyer.chatText()).as("told it wore off").anyMatch(line -> line.contains("wears off"));
        }
    }
}
