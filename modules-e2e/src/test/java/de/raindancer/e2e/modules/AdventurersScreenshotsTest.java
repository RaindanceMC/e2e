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
 * RainsAdventurers photographed with the real client:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AdventurersScreenshotsTest}. A bot walks the same pages
 * first, so the slots the client clicks and hovers over are read rather than guessed.
 */
@Tag("screenshots")
class AdventurersScreenshotsTest {

    private static int slot(Bot scout, String named) {
        return scout.window().flatMap(window -> window.slotNamed(named))
                .orElseThrow(() -> new AssertionError("no '" + named + "' in " + scout.window()));
    }

    /** Scout and Viewer both take the node; the scout's reopened page is waited for. */
    private static void learn(Server server, Bot scout, String node) {
        int at = slot(scout, node);
        server.console("e2e click Viewer " + at);
        scout.clickSlot(at);
        scout.expectChat(node + " rank");
        Await.ticks(30);
    }

    @Test
    @DisplayName("every branch of the skill tree, Dual Wielder, a fireball scroll and the magic items")
    void screenshots() throws java.io.IOException {
        try (Server server = Server.start("adventurers-screenshots", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 90 24 minecraft:air");
            server.console("gamerule spawn_mobs false");
            server.console("gamerule advance_time false");
            server.console("time set 6000");
            server.console("weather clear");
            server.console("kill @e[type=!minecraft:player]");
            Bot scout = server.player("Scout");
            server.console("gamemode spectator Scout");
            server.console("tp Scout 20 80 20");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode survival Viewer");
                server.console("tp Viewer 0.5 64 0.5 0 5");
                server.console("adventurer admin level Viewer 30");
                server.console("adventurer admin level Scout 30");
                Await.ticks(60);

                // --- Vitality: a heart, mending, and Second Wind shutting Stoneskin ----------------------------------
                scout.runAndOpen("adventurer", "Vitality");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("tree-1-vitality-fresh.png"), slot(scout, "Toughness"));
                for (String node : List.of("Toughness", "Toughness", "Mending", "Hardened", "Big Heart", "Second Wind")) {
                    learn(server, scout, node);
                }
                viewer.screenshot(shots.resolve("tree-2-vitality.png"));
                viewer.screenshotHovering(shots.resolve("tree-3-stoneskin-shut.png"), slot(scout, "Stoneskin"));

                // --- Might: Dual Wielder ---------------------------------------------------------------------------
                int next = slot(scout, "Next page");
                server.console("e2e click Viewer " + next);
                scout.clickSlot(next);
                scout.awaitWindow("Might");
                Await.ticks(30);
                for (String node : List.of("Brawler", "Crusher", "Dual Wielder")) {
                    learn(server, scout, node);
                }
                viewer.screenshotHovering(shots.resolve("tree-4-dual-wielder.png"), slot(scout, "Dual Wielder"));

                // --- the other three branches -----------------------------------------------------------------------
                int page = 5;
                for (String branch : List.of("Marksman", "Shadow", "Arcana")) {
                    server.console("e2e click Viewer " + next);
                    scout.clickSlot(next);
                    scout.awaitWindow(branch);
                    Await.ticks(40);
                    viewer.screenshot(shots.resolve("tree-" + page++ + "-" + branch.toLowerCase() + ".png"));
                }
                viewer.screenshotHovering(shots.resolve("tree-8-summary.png"), slot(scout, "Arcana"));
                server.console("e2e click Viewer " + slot(scout, "Close"));
                scout.closeWindow();

                // --- a fireball scroll into a row of husks --------------------------------------------------------------
                for (int husk = -2; husk <= 2; husk++) {
                    server.console("summon minecraft:husk " + (0.5 + husk * 1.5) + " 64 12.5 "
                            + "{NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f]}");
                }
                server.console("tp Viewer 0.5 64 0.5 0 3");
                server.console("clear Viewer");
                server.console("adventurer admin give Viewer spell_scroll fireball 5");
                Await.ticks(40);
                server.console("e2e use Viewer item");
                viewer.screenshot(shots.resolve("dnd-1-fireball.png"));
                Await.ticks(60);
                server.console("kill @e[type=minecraft:husk]");
                server.console("kill @e[type=minecraft:item]");

                // --- magic items, in the ender chest the Bag of Holding opens ----------------------------------------
                server.console("clear Viewer");
                for (String item : List.of("weapon_plus_three", "wand_of_magic_missiles", "ring_of_protection",
                        "amulet_of_health", "boots_of_striding", "potion_of_supreme_healing", "potion_of_heroism",
                        "potion_of_speed", "spell_scroll lightning 5")) {
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
                viewer.screenshot(shots.resolve("dnd-2-items.png"));

                assertThat(shots.resolve("dnd-2-items.png")).exists();
                assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every painting and model").doesNotContain("rainsadventurers:")
                        .doesNotContain("rainscore:");
            }
        }
    }
}
