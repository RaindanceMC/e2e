package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An enchant shard offered in a player's shop is called what it is — "Thor Shard", never "Echo Shard": Core reads an
 * item's own name.
 */
@Tag("e2e")
class ShopsShardOfferScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("a Thor shard on offer is the Thor Shard")
    void shardOffer() throws Exception {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        Path enchants = OneBlockNetworkScenarioTest.jar("enchants-standalone", "RainsEnchants-.*");
        PaperServer server = PaperServer.forRun().seed(1).in(E2e.serverFolder("shops-shard"))
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(economy).plugin(enchants).plugin(shops)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.awaitLog("Enchants are up", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(40);
            ShopsScenarioTest.platform(server);

            Bot ada = server.bot("Ada").join();
            server.console("gamemode survival Ada");
            server.console("clear Ada");
            server.console("give Ada thor_shard 1");
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.hold(0);
            ada.run("bazaar create Shard Corner");
            ada.expectChat("is open");
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Offer the item in your hand"));
            ada.expectChat("New offer: Thor Shard");
            ada.closeWindow();

            ada.run("bazaar mine");
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Shard Corner"));
            ada.clickSlot(ShopsScenarioTest.slot(ada, "Offers"));
            assertThat(ShopsScenarioTest.slot(ada, "Thor Shard")).isNotNegative();
            ada.closeWindow();

            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEnchants", "RainsEconomy").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops-shard"));
        }
    }
}
