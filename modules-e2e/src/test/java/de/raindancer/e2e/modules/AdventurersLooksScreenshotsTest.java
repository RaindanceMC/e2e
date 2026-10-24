package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsAdventurers' own looks in the real client, wearing the pack Core builds:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AdventurersLooksScreenshotsTest}.
 */
@Tag("screenshots")
class AdventurersLooksScreenshotsTest {

    private static final List<String> WALL = List.of("potion_of_healing", "potion_of_greater_healing",
            "potion_of_superior_healing", "potion_of_supreme_healing", "potion_of_heroism", "potion_of_speed",
            "wand_of_magic_missiles", "spell_scroll meteor_swarm", "bag_of_holding");
    private static final List<String> WALL_TWO = List.of("ring_of_protection", "amulet_of_health",
            "boots_of_striding", "weapon_plus_one", "weapon_plus_two", "weapon_plus_three", "spell_scroll fireball",
            "spell_scroll magic_missile", "spell_scroll disintegrate");

    private static int slot(Bot scout, String named) {
        return scout.window().flatMap(window -> window.slotNamed(named))
                .orElseThrow(() -> new AssertionError("no '" + named + "' in " + scout.window()));
    }

    private static void wall(Server server, List<String> items) {
        server.console("kill @e[type=minecraft:item_frame]");
        server.console("clear Viewer");
        for (String item : items) {
            server.console("adventurer admin give Viewer " + item);
        }
        Await.ticks(20);
        for (int i = 0; i < items.size(); i++) {
            int x = -4 + i;
            int y = i < 5 ? 66 : 65;
            int column = i < 5 ? x : -8 + i;
            server.console("summon minecraft:item_frame " + column + " " + (i < 5 ? 66 : 65) + " 6 "
                    + "{Facing:2b,Invisible:1b,Fixed:1b,Tags:[\"f" + i + "\"]}");
            server.console("item replace entity @e[type=minecraft:item_frame,tag=f" + i + ",limit=1] contents "
                    + "from entity Viewer hotbar." + i);
        }
    }

    @Test
    @DisplayName("magic items on a wall, in the hotbar and in hand; the stance and the menus with their icons")
    void looks() throws java.io.IOException {
        try (Server server = Server.start("adventurers-looks", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:polished_deepslate");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("fill -6 63 7 6 69 7 minecraft:dark_oak_planks");
            server.console("gamerule doMobSpawning false");
            server.console("gamerule doDaylightCycle false");
            server.console("time set 6000");
            server.console("kill @e[type=!minecraft:player]");
            Bot scout = server.player("Scout");
            server.console("gamemode spectator Scout");
            server.console("tp Scout 20 80 20");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.screenshot(shots.resolve("looks-0-offer.png"));
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode survival Viewer");

                // --- the gallery: two walls of item frames ------------------------------------------------------
                wall(server, WALL);
                server.console("tp Viewer 0.5 64 2.2 0 12");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("looks-1-wall.png"));
                server.console("tp Viewer -1.5 64.6 4.4 0 18");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("looks-2-wall-close.png"));
                wall(server, WALL_TWO);
                server.console("tp Viewer 0.5 64 2.2 0 12");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("looks-3-wall-two.png"));

                // --- the hotbar and in hand ---------------------------------------------------------------------
                server.console("kill @e[type=minecraft:item_frame]");
                server.console("clear Viewer");
                for (String item : WALL) {
                    server.console("adventurer admin give Viewer " + item);
                }
                server.console("tp Viewer 0.5 64 0.5 180 10");
                server.console("item replace entity Viewer weapon.mainhand from entity Viewer hotbar.6");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("looks-4-hotbar-wand.png"));

                // --- the stance's icons, the class menu, the picker ------------------------------------------------
                scout.runAndOpen("adventurer", "Choose");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("looks-5-classes.png"), slot(scout, "Sorcerer"));
                server.console("e2e click Viewer " + slot(scout, "Wizard"));
                scout.click("Wizard");
                scout.awaitWindow("Character");
                Await.ticks(30);
                server.console("adventurer admin level Viewer 17");
                server.console("adventurer admin level Scout 17");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("looks-8-sheet.png"));
                viewer.screenshotHovering(shots.resolve("looks-9-sheet-ability.png"), 14);
                server.console("e2e as Viewer adventurer spells");
                scout.runAndOpen("adventurer spells", "Spellbook");
                Await.ticks(30);
                int slotFour = slot(scout, "Magic Missile");
                server.console("e2e click Viewer " + slotFour);
                scout.clickSlot(slotFour);
                scout.awaitWindow("Stance slot");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("looks-6-spells.png"), slot(scout, "Meteor Swarm"));
                server.console("e2e click Viewer " + slot(scout, "Fireball"));
                Await.ticks(20);
                server.console("clear Viewer");
                server.console("e2e as Viewer adventurer stance");
                server.console("tp Viewer 0.5 64 0.5 180 -5");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("looks-7-stance.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every model and texture").doesNotContain("rainsadventurers:");
            }
        }
    }
}
