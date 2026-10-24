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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A chest shop inside somebody's claim: a stranger, trusted with nothing there, still gets the counter from its item
 * frame and from its chest and trades there — without Core's land protection telling them first that they may not.
 */
@Tag("e2e")
class ShopsInClaimScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("a stranger trades at a chest shop in somebody else's claim, and is never refused out loud")
    void inAClaim() throws Exception {
        Path claims = OneBlockNetworkScenarioTest.jar("claims-standalone", "RainsExtendedClaims-.*");
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        PaperServer server = PaperServer.forRun().seed(1).in(E2e.serverFolder("shops-claim"))
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(claims).plugin(economy).plugin(shops)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.awaitLog("Claims are up", WAIT);
            Await.ticks(60);
            assertThat(server.console("settings set limits.min-area-blocks 1")).contains("is now");
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(40);
            ShopsScenarioTest.platform(server);
            server.console("item replace block " + X + " " + Y + " " + Z
                    + " container.0 with minecraft:diamond 20");

            Bot ada = server.bot("Ada").join();
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Ada");
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");

            // ── Ada claims the ground around both chests ────────────────────────────────────────────────────────
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.run("claim new");
            ada.expectChat("Marking out");
            ada.hold(ada.hotbarSlotOf(item -> item.material().toLowerCase().contains("stick")
                    || item.material().toLowerCase().contains("hoe")));
            ada.useOn(X - 1, Y - 1, Z); // corners within reach: the claim runs X-1..X+3, Z..Z+4
            ada.expectChat("Corner");
            ada.useOn(X + 3, Y - 1, Z + 4);
            ada.expectChat("What should it be called?");
            ada.say("market");
            ada.expectChat("Claimed");

            // ── a chest shop in it ──────────────────────────────────────────────────────────────────────────────
            ShopsScenarioTest.setUp(server, ada, "Ada");

            // ── Bo, trusted with nothing here, buys at the item frame ───────────────────────────────────────────
            UUID frame = ShopsScenarioTest.frame(server);
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 3) + ".5 180 0");
            Await.until("Bo sees the frame", WAIT, () -> bo.sees(frame));
            bo.forgetChat();
            bo.interact(frame);
            bo.clickSlot(ShopsScenarioTest.slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("a diamond reached Bo", WAIT, () -> ShopsScenarioTest.carried(server, "Bo", "diamond") == 1);
            bo.closeWindow();

            // ── and the chest is the counter too ────────────────────────────────────────────────────────────────
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            assertThat(ShopsScenarioTest.slot(bo, "Buy one bundle")).isNotNegative();
            bo.closeWindow();

            Await.ticks(20);
            assertThat(bo.chatText()).as("Bo was refused out loud on the way")
                    .noneMatch(line -> line.toLowerCase().contains("may not") || line.toLowerCase().contains("not allowed")
                            || line.toLowerCase().contains("permission"));
            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy", "RainsExtendedClaims").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops-claim"));
        }
    }
}
