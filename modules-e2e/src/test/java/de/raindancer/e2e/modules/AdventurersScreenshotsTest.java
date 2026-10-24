package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsAdventurers photographed with the real client:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AdventurersScreenshotsTest}. A bot walks the same menus
 * first, so the slots the client hovers over are read rather than guessed.
 */
@Tag("screenshots")
class AdventurersScreenshotsTest {

    private static int slot(Bot scout, String named) {
        return scout.window().flatMap(window -> window.slotNamed(named))
                .orElseThrow(() -> new AssertionError("no '" + named + "' in " + scout.window()));
    }

    @Test
    @DisplayName("classes, sheet, spellbook, stance, a fireball, wild magic and magic items")
    void screenshots() {
        try (Server server = Server.start("adventurers-screenshots", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 90 24 minecraft:air");
            server.console("gamerule doMobSpawning false");
            server.console("gamerule doDaylightCycle false");
            server.console("time set 6000");
            server.console("weather clear");
            server.console("kill @e[type=!minecraft:player]");
            Bot scout = server.player("Scout");
            server.console("gamemode spectator Scout");
            server.console("tp Scout 20 80 20");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                server.console("gamemode survival Viewer");
                server.console("tp Viewer 0.5 64 0.5 0 5");
                Await.ticks(60);

                // --- choosing a class ---------------------------------------------------------------------------
                scout.runAndOpen("adventurer", "Choose");
                int wizard = slot(scout, "Wizard");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("dnd-1-classes.png"), wizard);

                // --- the character sheet of a level 12 wizard --------------------------------------------------------
                scout.click("Wizard");
                scout.awaitWindow("Character");
                server.console("e2e click Viewer " + wizard);
                Await.ticks(40);
                server.console("adventurer admin level Viewer 12");
                server.console("adventurer admin level Scout 12");
                server.console("e2e as Viewer adventurer");
                scout.runAndOpen("adventurer", "Character");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("dnd-2-sheet.png"));
                viewer.screenshotHovering(shots.resolve("dnd-3-sheet-ability.png"), slot(scout, "Intelligence"));

                // --- the spellbook, and Fireball in the picker -------------------------------------------------------
                server.console("e2e as Viewer adventurer spells");
                scout.runAndOpen("adventurer spells", "Spellbook");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("dnd-4-spellbook.png"));
                int empty = slot(scout, "Magic Missile");
                scout.clickSlot(empty);
                scout.awaitWindow("Stance slot");
                server.console("e2e click Viewer " + empty);
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("dnd-5-fireball-tooltip.png"), slot(scout, "Fireball"));
                int fireball = slot(scout, "Fireball");
                server.console("e2e click Viewer " + fireball);
                scout.clickSlot(fireball);
                Await.ticks(20);

                // --- the casting stance in the world -------------------------------------------------------------------
                for (int zombie = -2; zombie <= 2; zombie++) {
                    server.console("summon minecraft:husk " + (0.5 + zombie * 1.5) + " 64 12.5 "
                            + "{NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}");
                }
                server.console("e2e as Viewer adventurer stance");
                server.console("tp Viewer 0.5 64 0.5 0 3");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("dnd-6-stance.png"));

                // --- a fireball into the zombies -----------------------------------------------------------------------
                server.console("e2e as Viewer adventurer stance");
                Await.ticks(10);
                server.console("clear Viewer");
                server.console("adventurer admin give Viewer spell_scroll fireball");
                Await.ticks(20);
                server.console("e2e use Viewer item");
                viewer.screenshot(shots.resolve("dnd-7-fireball.png"));
                Await.ticks(60);
                server.console("kill @e[type=minecraft:husk]");
                server.console("kill @e[type=minecraft:item]");

                // --- a sorcerer chooses Wild Magic ------------------------------------------------------------------
                server.console("adventurer admin reset Viewer");
                server.console("adventurer admin reset Scout");
                Await.ticks(40);
                server.console("e2e as Viewer adventurer class");
                scout.runAndOpen("adventurer class", "Choose");
                int sorcerer = slot(scout, "Sorcerer");
                server.console("e2e click Viewer " + sorcerer);
                scout.click("Sorcerer");
                scout.awaitWindow("Character");
                Await.ticks(40);
                int origin = slot(scout, "Choose your origin");
                server.console("e2e click Viewer " + origin);
                scout.clickSlot(origin);
                scout.awaitWindow("Choose");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("dnd-8-origins.png"), slot(scout, "Wild Magic"));
                int wild = slot(scout, "Wild Magic");
                server.console("e2e click Viewer " + wild);
                scout.clickSlot(wild);
                scout.awaitWindow("Become");
                Await.ticks(30);
                server.console("e2e click Viewer " + slot(scout, "Yes, do it"));
                Await.ticks(40);
                server.console("adventurer admin level Viewer 20");
                server.console("e2e as Viewer adventurer stance");
                server.console("e2e as Viewer adventurer stance");
                Await.ticks(100);

                // --- wild magic surges ------------------------------------------------------------------------------
                server.console("tp Viewer 0.5 64 0.5 0 25");
                server.console("adventurer admin surge Viewer 13");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("dnd-9-surge-poppies.png"));
                server.console("tp Viewer 0.5 64 -6.5 0 -25");
                server.console("adventurer admin surge Viewer 3");
                Await.ticks(1);
                viewer.screenshot(shots.resolve("dnd-10-surge-sheep.png"));
                Await.ticks(80);
                server.console("tp Viewer 0.5 64 0.5 0 -10");
                server.console("adventurer admin surge Viewer 25");
                Await.ticks(1);
                viewer.screenshot(shots.resolve("dnd-11-surge-lightning.png"));
                server.console("tp Viewer 0.5 64 0.5 0 15");
                server.console("adventurer admin surge Viewer 43");
                Await.ticks(30);
                server.console("tp Viewer 0.5 64 0.5 180 30");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("dnd-12-surge-wolves.png"));

                // --- magic items, in the ender chest the Bag of Holding opens ----------------------------------------
                server.console("clear Viewer");
                for (String item : List.of("weapon_plus_three", "wand_of_magic_missiles", "ring_of_protection",
                        "amulet_of_health", "boots_of_striding", "potion_of_supreme_healing", "potion_of_heroism",
                        "potion_of_speed", "spell_scroll meteor_swarm")) {
                    server.console("adventurer admin give Viewer " + item);
                }
                Await.ticks(20);
                for (int hotbar = 0; hotbar < 9; hotbar++) {
                    server.console("item replace entity Viewer enderchest." + hotbar + " from entity Viewer hotbar."
                            + hotbar);
                }
                server.console("clear Viewer");
                server.console("adventurer admin give Viewer bag_of_holding");
                Await.ticks(20);
                server.console("e2e use Viewer item");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("dnd-13-items.png"));

                assertThat(shots.resolve("dnd-13-items.png")).exists();
                assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
            }
        }
    }
}
