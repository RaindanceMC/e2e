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
 * From the shopkeepers of 0.3.2 to the chest shops of 0.4.0 on one server: a shop with a shopkeeper and an offer
 * selling from a linked chest, the plugin swapped, the server started again — the offer is retired and its owner told
 * how shops work now, the shopkeeper is gone, the chest is a chest again with its stock, and the owner's chest shop
 * joins the shop they already had.
 */
@Tag("e2e")
class ShopsKeeperRetiringScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("0.3.2 shopkeeper → 0.4.0: offer retired, owner told, shopkeeper gone, stock kept, chest shop works")
    void retiring() throws Exception {
        Path rainscore = Path.of(System.getProperty("e2e.rainscore"));
        Path repository = rainscore.getParent().getParent().getParent().getParent().getParent();
        Path old = repository.resolve("de/raindancer/modules/shops-standalone/0.3.2/shops-standalone-0.3.2.jar");
        assertThat(old).as("0.3.2 in the local repository").exists();
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path fresh = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        Path folder = E2e.serverFolder("shops-keeper-retiring");
        PaperServer server = PaperServer.forRun().seed(1).in(folder).plugin(rainscore).plugin(economy).plugin(old)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(40);
            ShopsScenarioTest.platform(server);
            server.console("item replace block " + X + " " + Y + " " + Z + " container.0 with minecraft:diamond 20");

            // ── with 0.3.2: a shopkeeper, an offer, a linked chest ──────────────────────────────────────────────
            Bot ada = server.bot("Ada").join();
            server.console("gamemode survival Ada");
            server.console("clear Ada");
            server.console("give Ada minecraft:diamond 1");
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.hold(0);
            ada.run("bazaar create Gem Corner");
            ada.expectChat("is open");
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Offer the item in your hand"));
            ada.expectChat("New offer");
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Selling price"));
            ada.typeInAnvil("10");
            ada.expectChat("Done");
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Link stock chests"));
            ada.expectChat("Linking chests");
            ada.look(180, 30);
            ada.useOn(X, Y, Z);
            ada.expectChat("(1/");
            ada.run("bazaar done");
            ada.closeWindow();
            assertThat(server.console("execute if entity @e[type=minecraft:mannequin]")).contains("passed");
            ada.leave();

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
            server.awaitLog("Retired 1 shopkeeper offer\\(s\\) for chest shops", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(60);

            // ── Ada hears about it; the shopkeeper is gone; the chest is just a chest, stock and all ─────────────
            Bot back = server.bot("Ada").join();
            back.expectChat("Shopkeepers are retired");
            Await.until("the shopkeeper took itself away", WAIT, () ->
                    !server.console("execute if entity @e[type=minecraft:mannequin]").contains("passed"));
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Bo");
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            Await.until("Bo opened the chest — just a chest now", WAIT, () -> bo.window().isPresent());
            bo.closeWindow();
            assertThat(ShopsScenarioTest.inChest(server, X, "diamond")).isEqualTo(20);

            // ── a chest shop the new way, in the shop Ada already had ───────────────────────────────────────────
            server.console("gamemode survival Ada");
            ShopsScenarioTest.setUp(server, back, "Ada");
            back.run("market mine");
            back.clickSlot(ShopsScenarioTest.slot(back, "Your shop's page"));
            assertThat(ShopsScenarioTest.slot(back, "Gem Corner")).as("the shop from 0.3.2 lives on").isNotNegative();
            back.closeWindow();

            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops-keeper-retiring"));
        }
    }
}
