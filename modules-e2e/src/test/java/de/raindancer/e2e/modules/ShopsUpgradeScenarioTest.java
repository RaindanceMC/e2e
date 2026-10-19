package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * From the 0.1 chest shops to 0.2.0 on one server: a chest shop and a statue made with 0.1.2 (what Lilly runs), the
 * plugin swapped, the server started again — the old shop is closed and its owner told when they come back, the
 * statue is gone, the chest is a chest again with everything still in it, and the owner sets up a new shop.
 */
@Tag("e2e")
class ShopsUpgradeScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("0.1.2 chest shop and statue → 0.2.0: closed, owner told, statue gone, stock kept, new shop works")
    void upgrade() throws Exception {
        Path rainscore = Path.of(System.getProperty("e2e.rainscore"));
        // …/repository/de/raindancer/RainsCore/<version>/RainsCore-<version>.jar → …/repository
        Path repository = rainscore.getParent().getParent().getParent().getParent().getParent();
        Path old = repository.resolve("de/raindancer/modules/shops-standalone/0.1.2/shops-standalone-0.1.2.jar");
        assertThat(old).as("0.1.2 in the local repository").exists();
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path fresh = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        Path folder = E2e.serverFolder("shops-upgrade");
        PaperServer server = PaperServer.forRun().seed(1).in(folder).plugin(rainscore).plugin(economy).plugin(old)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(40);
            ShopsScenarioTest.platform(server);
            server.console("item replace block " + X + " " + Y + " " + Z + " container.0 with minecraft:diamond 20");

            // ── with 0.1.2: a chest shop and a statue ───────────────────────────────────────────────────────────
            Bot ada = server.bot("Ada").join();
            server.console("gamemode survival Ada");
            server.console("give Ada minecraft:diamond 1");
            server.console("tp Ada " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.hold(0);
            ada.useOn(X, Y, Z);
            Await.ticks(10);
            ada.closeWindow();
            ada.run("bazaar create 10 0 1");
            ada.expectChat("Open for business");
            server.console("tp Ada " + (X + 3) + " " + Y + " " + (Z + 3));
            Await.ticks(20);
            ada.run("bazaar statue");
            ada.expectChat("A statue of you now stands here");
            assertThat(server.console("execute if entity @e[type=minecraft:mannequin]")).contains("passed");

            // ── the plugin swapped, the server started again ────────────────────────────────────────────────────
            server.stop();
            try (Stream<Path> plugins = Files.list(folder.resolve("plugins"))) {
                for (Path jar : plugins.filter(path -> path.getFileName().toString().startsWith("shops-standalone")
                        || path.getFileName().toString().startsWith("RainsShops")).toList()) {
                    Files.delete(jar);
                }
            }
            Files.copy(fresh, folder.resolve("plugins").resolve(fresh.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.awaitLog("Closed 1 shop\\(s\\) from before the rework", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(60);

            // ── Ada hears about it; the statue is gone; the chest is hers to open, stock and all ────────────────
            Bot back = server.bot("Ada").join();
            back.expectChat("Shops work differently now");
            Await.until("the old statue took itself away", WAIT, () ->
                    !server.console("execute if entity @e[type=minecraft:mannequin]").contains("passed"));
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Bo");
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            Await.until("Bo opened the chest — just a chest now", WAIT, () -> bo.window().isPresent());
            bo.closeWindow();
            assertThat(ShopsScenarioTest.inChest(server, X, "diamond")).isEqualTo(20);

            // ── and a new shop is set up the new way ────────────────────────────────────────────────────────────
            server.console("gamemode survival Ada");
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            back.run("bazaar create Gem Corner");
            back.expectChat("is open");
            assertThat(server.console("execute if entity @e[type=minecraft:mannequin]")).contains("passed");

            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops-upgrade"));
        }
    }
}
