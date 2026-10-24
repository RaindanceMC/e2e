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
 * The painted menus in the real client, wearing the pack Core builds — Core's house style, the shop's stall, the
 * owner's workshop and the adventurers' parchment:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=MenuLooksScreenshotsTest}.
 */
@Tag("screenshots")
class MenuLooksScreenshotsTest {

    @Test
    @DisplayName("every style of painted menu, as a player wearing the pack sees it")
    void menus() throws java.io.IOException {
        try (Server server = Server.start("menu-looks", List.of("economy-standalone:RainsEconomy-.*",
                "dnd-standalone:RainsAdventurers-.*"), List.of("The economy is up", "Adventurers are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:polished_deepslate");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("gamerule doMobSpawning false");
            server.console("time set 6000");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("op Viewer");
                server.console("eco give Viewer 12345");
                server.console("tp Viewer 0.5 64 0.5 0 10");
                server.console("give Viewer minecraft:diamond_pickaxe");
                server.console("give Viewer minecraft:bread 12");
                Await.ticks(20);

                server.console("e2e as Viewer shop");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-1-shop.png"));
                viewer.screenshotHovering(shots.resolve("menus-2-shop-purse.png"), 4);
                server.console("e2e as Viewer shop search sword");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-3-shop-search.png"));
                server.console("e2e as Viewer eco menu");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-4-admin.png"));
                server.console("e2e as Viewer bank");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-5-bank.png"));
                Bot scout = server.player("Scout");
                server.console("gamemode spectator Scout");
                server.console("tp Scout 20 80 20");
                scout.runAndOpen("adventurer class", "Choose");
                int wizard = scout.window().flatMap(window -> window.slotNamed("Wizard")).orElseThrow();
                server.console("e2e as Viewer adventurer class");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("menus-6-classes.png"), wizard);
                server.console("e2e click Viewer " + wizard);
                Await.ticks(30);
                server.console("adventurer admin level Viewer 9");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-7-sheet.png"));
                server.console("e2e as Viewer adventurer spells");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("menus-8-spellbook.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy", "RainsAdventurers")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every painting and model")
                        .doesNotContain("rainscore:").doesNotContain("rainseconomy:").doesNotContain("rainsadventurers:");
            }
        }
    }
}
