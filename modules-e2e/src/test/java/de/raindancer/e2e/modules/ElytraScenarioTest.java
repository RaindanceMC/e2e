package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsElytra on a real server: a smithing table trims an elytra like armour, and /elytra puts a chestplate look on
 * it and takes it off — each read back from the item the server holds. Looks are Prime's by default: Ada, an operator,
 * has them through the staff nodes; Bo, a plain player, is told they are a Prime perk.
 */
@Tag("e2e")
class ElytraScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final int Y = 100;
    /** The smithing table's own slots; the player's inventory follows from 4. */
    private static final int RESULT = 3;
    private static final String TRIMMED =
            "minecraft:elytra[minecraft:trim={pattern:'minecraft:coast',material:'minecraft:diamond'}]";

    @Test
    @DisplayName("an elytra takes a trim at the smithing table, and a chestplate look goes on and off")
    void trimsAndLooks() {
        try (Server server = Server.start("elytra", List.of("elytra-standalone:RainsElytra-.*"),
                List.of("Elytra is up"))) {
            assertThat(server.log()).contains("Elytra is up: 18 trim recipes take an elytra, 7 chestplate looks (Prime only)");

            server.console("forceload add -16 -16 16 16");
            server.console("fill -6 " + (Y - 3) + " -6 6 " + (Y - 1) + " 6 minecraft:stone");
            server.console("fill -6 " + Y + " -6 6 " + (Y + 3) + " 6 minecraft:air");
            server.console("setblock 2 " + Y + " 0 minecraft:smithing_table");
            Bot ada = server.admin("Ada");
            server.console("tp Ada 0.5 " + Y + " 0.5");
            server.console("gamemode survival Ada");
            server.console("clear Ada");
            server.console("give Ada minecraft:coast_armor_trim_smithing_template");
            server.console("give Ada minecraft:elytra");
            server.console("give Ada minecraft:diamond");
            Await.ticks(20);

            // ---- the smithing table: template, elytra, diamond in, a trimmed elytra out
            ada.useOn(2, Y, 0);
            ada.awaitWindow("container.upgrade");
            Await.ticks(10);
            for (String item : List.of("coast_armor_trim_smithing_template", "elytra", "diamond")) {
                int slot = Await.value("Ada's " + item + " below the smithing table", WAIT, () -> ada.window()
                        .flatMap(window -> window.items().entrySet().stream()
                                .filter(entry -> entry.getKey() > RESULT && entry.getValue().is(item))
                                .map(Map.Entry::getKey).findFirst())
                        .orElse(null));
                ada.shiftClickSlot(slot);
                Await.ticks(5);
            }
            Await.until("the table offers a trimmed elytra (" + ada.window().map(Bot.Window::items).orElse(null) + ")",
                    WAIT, () -> ada.window().map(window -> window.items().get(RESULT))
                            .map(result -> result.is("elytra")).orElse(false));
            ada.shiftClickSlot(RESULT);
            Await.ticks(10);
            ada.closeWindow();
            Await.until("Ada has the elytra with a coast trim in diamond", WAIT, () -> server.console(
                    "execute if items entity Ada container.* " + TRIMMED).contains("passed"));

            // ---- a look: on by command, off again, on through the menu — the trim stays through all of it
            // The only thing left after smithing; into the first hotbar slot, which the bot holds.
            server.console("clear Ada");
            server.console("give Ada " + TRIMMED);
            ada.hold(0);
            Await.ticks(10);
            ada.runAndExpect("elytra look netherite", "Netherite");
            Await.until(() -> "the elytra points at the netherite look: " + held(server), WAIT,
                    () -> held(server).contains("rainselytra:netherite_chestplate"));
            assertThat(server.console("execute if items entity Ada weapon.mainhand " + TRIMMED))
                    .as("the look leaves the trim on").contains("passed");

            ada.runAndExpect("elytra look none", "plain wings");
            Await.until("the elytra is plain again", WAIT, () -> !held(server).contains("rainselytra:"));

            ada.runAndOpen("elytra", "Elytra looks");
            ada.click("Diamond chestplate");
            Await.until("the menu put the diamond look on", WAIT,
                    () -> held(server).contains("rainselytra:diamond_chestplate"));
            ada.closeWindow();

            // ---- Prime only: a player without Prime (and not staff) may look, but not put one on
            Bot bo = server.player("Bo");
            server.console("tp Bo 0.5 " + Y + " 1.5");
            server.console("give Bo minecraft:elytra");
            Await.ticks(10);
            bo.hold(0);
            bo.runAndExpect("elytra look gold", "Prime perk");
            assertThat(server.console("data get entity Bo Inventory[{Slot:0b}].components"))
                    .as("Bo's elytra stays plain").doesNotContain("rainselytra:");

            // ---- a look Bo was handed by somebody else (or wore while they still had Prime) comes off by itself
            server.console("give Bo minecraft:elytra[minecraft:equippable={slot:'chest',asset_id:'rainselytra:gold_chestplate'}]");
            Await.until(() -> "the look on Bo's elytra comes off: " + server.console("data get entity Bo Inventory"), WAIT,
                    () -> !server.console("data get entity Bo Inventory").contains("rainselytra:"));
            bo.expectChat("look");

            // Core's pack web server on 8123 is the one thing a second local e2e server running beside this one
            // takes away; it says so in one line, and has nothing to do with the elytra.
            assertThat(server.paper.errorsFrom("RainsCore", "RainsElytra"))
                    .filteredOn(line -> !line.contains("resource pack server could not start")).isEmpty();
        }
    }

    private static String held(Server server) {
        // The asset by path: the console shortens nested components to "...".
        return server.console("data get entity Ada Inventory[{Slot:0b}].components.\"minecraft:equippable\".asset_id");
    }
}
