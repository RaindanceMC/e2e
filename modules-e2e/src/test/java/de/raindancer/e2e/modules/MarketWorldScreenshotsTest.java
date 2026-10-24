package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The market world photographed with the real client, one design element per picture:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=MarketWorldScreenshotsTest}.
 *
 * <p>Coordinates follow the market's layout: blocks of 74 × 34, the square at the origin block, a market street along
 * z 25–33 between the square's block row and the next, a cross street along x 65–73.
 */
@Tag("screenshots")
class MarketWorldScreenshotsTest {

    private static final Duration WAIT = Duration.ofSeconds(120);

    @Test
    @DisplayName("square, fountain, market street, stall, house row, night, from above — photographed")
    void screenshots() {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        PaperServer server = PaperServer.forRun().seed(1).in(E2e.serverFolder("market-world-screenshots"))
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(economy).plugin(shops)
                .build();
        Path shots = E2e.out().resolve("screenshots");
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            assertThat(server.console("settings set market-world true")).contains("is now");
            server.awaitLog("The market world 'market' is open", WAIT);
            server.console("execute in minecraft:market run forceload add -40 -40 160 110");
            server.console("execute in minecraft:market run time set 1000");
            server.console("execute in minecraft:market run gamerule doDaylightCycle false");
            Await.ticks(100);

            try (RealClient viewer = RealClient.join(server, "Viewer", 10)) {
                Await.ticks(100);
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode spectator Viewer");
                shot(server, viewer, shots, "market-1-square", 32.5, 74, 34, 180, 28);
                shot(server, viewer, shots, "market-2-fountain", 32.5, 66.4, 20.5, 180, 12);
                shot(server, viewer, shots, "market-3-market-street", 76, 67.2, 29.5, -90, 6);
                shot(server, viewer, shots, "market-4-stall", 87.5, 66.8, 28.5, 180, 10);
                shot(server, viewer, shots, "market-5-house-row", 69.5, 68, 30, 0, 4);
                shot(server, viewer, shots, "market-6-houses-front", 68, 67.5, 46, -90, -4);
                shot(server, viewer, shots, "market-7-above", 40, 112, 70, 200, 55);
                shot(server, viewer, shots, "market-12-facades-oblique", 66.5, 66.8, 37, -40, -6);
                shot(server, viewer, shots, "market-13-jetty-close", 71.2, 66.2, 40, -55, -22);
                shot(server, viewer, shots, "market-14-end-wall", 72, 67, 27.5, -20, -8);
                shot(server, viewer, shots, "market-15-facades-other-side", 72.5, 66.8, 37, 40, -6);
                server.console("execute in minecraft:market run time set 18000");
                shot(server, viewer, shots, "market-8-night-street", 76, 67.2, 29.5, -90, 6);
                shot(server, viewer, shots, "market-9-night-houses", 69.5, 68, 30, 0, 4);
                // Standing on a free plot: the action bar says so, and /market plot offers it.
                server.console("execute in minecraft:market run time set 1000");
                server.console("gamemode survival Viewer");
                server.console("execute in minecraft:market run tp Viewer 86.5 65 21.5 0 20");
                Await.ticks(60);
                server.console("execute in minecraft:market run tp Viewer 86.5 65 19.5 0 20");
                Await.ticks(10);
                viewer.screenshot(shots.resolve("market-10-free-plot.png"));
                server.runAs("Viewer", "market plot");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("market-11-plot-menu.png"));
                assertThat(shots.resolve("market-11-plot-menu.png")).exists();
            }
            assertThat(server.errorsFrom("RainsShops")).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("market-world-screenshots"));
        }
    }

    private static void shot(PaperServer server, RealClient viewer, Path shots, String name, double x, double y,
                             double z, float yaw, float pitch) {
        server.console("execute in minecraft:market run tp Viewer " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        Await.ticks(160);
        viewer.screenshot(shots.resolve(name + ".png"));
    }
}
