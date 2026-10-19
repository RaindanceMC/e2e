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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A shop and a statue inside somebody's claim: a stranger, trusted with nothing there, still gets the counter and the
 * store's page — without Core's land protection telling them first that they may not open anything here — and still
 * cannot open the chest itself.
 */
@Tag("e2e")
class ShopsInClaimScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("a stranger trades at a shop and a statue in somebody else's claim, and is never refused out loud")
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
            server.console("fill " + (X - 6) + " " + Y + " " + (Z - 6) + " " + (X + 6) + " " + (Y + 4) + " "
                    + (Z + 6) + " minecraft:air");
            server.console("fill " + (X - 6) + " " + (Y - 1) + " " + (Z - 6) + " " + (X + 6) + " " + (Y - 1) + " "
                    + (Z + 6) + " minecraft:stone");
            server.console("setblock " + X + " " + Y + " " + Z + " minecraft:chest");
            server.console("item replace block " + X + " " + Y + " " + Z
                    + " container.0 with minecraft:diamond 20");

            Bot ada = server.bot("Ada").join();
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Ada");
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");

            // ── Ada claims the ground around the chest ──────────────────────────────────────────────────────────
            server.console("tp Ada " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.run("claim new");
            ada.expectChat("Marking out");
            ada.hold(ada.hotbarSlotOf(item -> item.material().toLowerCase().contains("stick")
                    || item.material().toLowerCase().contains("hoe")));
            ada.useOn(X - 2, Y - 1, Z); // corners within reach: the claim runs X-2..X+2, Z..Z+4
            ada.expectChat("Corner");
            ada.useOn(X + 2, Y - 1, Z + 4);
            ada.expectChat("What should it be called?");
            ada.say("market");
            ada.expectChat("Claimed");

            // ── a shop and a statue in it ───────────────────────────────────────────────────────────────────────
            server.console("clear Ada");
            server.console("give Ada minecraft:diamond 1");
            Await.ticks(10);
            ada.hold(0);
            ada.useOn(X, Y, Z);
            Await.ticks(10);
            ada.closeWindow();
            ada.run("bazaar create 10 0 2");
            ada.expectChat("Open for business");
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 3) + ".5");
            Await.ticks(20);
            ada.run("bazaar statue");
            ada.expectChat("A statue of you now stands here");

            // ── Bo, trusted with nothing here, buys at the counter ──────────────────────────────────────────────
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.forgetChat();
            bo.useOn(X, Y, Z);
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 2× Diamond");
            Await.until("two diamonds reached Bo", WAIT, () -> carried(server, "Bo", "diamond") == 2);
            bo.closeWindow();

            // ── and at the statue ───────────────────────────────────────────────────────────────────────────────
            UUID statue = mannequin(server);
            server.console("tp Bo " + (X + 1) + ".5 " + Y + " " + (Z + 1) + ".5");
            Await.until("Bo sees the statue", WAIT, () -> bo.sees(statue));
            bo.interact(statue);
            bo.clickSlot(slot(bo, "Diamond"));
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 2× Diamond");
            bo.closeWindow();

            Await.ticks(20);
            assertThat(bo.chatText()).as("Bo was refused out loud on the way")
                    .noneMatch(line -> line.contains("Shops »") && line.contains("not"))
                    .noneMatch(line -> line.toLowerCase().contains("may not") || line.toLowerCase().contains("not allowed")
                            || line.toLowerCase().contains("permission"));
            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy", "RainsExtendedClaims").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops-claim"));
        }
    }

    private static int carried(PaperServer server, String player, String material) {
        Matcher found = Pattern.compile("Found (\\d+)")
                .matcher(server.console("clear " + player + " minecraft:" + material + " 0"));
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    private static UUID mannequin(PaperServer server) {
        String said = server.console("data get entity @e[type=minecraft:mannequin,limit=1] UUID");
        Matcher ints = Pattern.compile("\\[I;\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)]").matcher(said);
        assertThat(ints.find()).as("no statue: " + said).isTrue();
        long most = ((long) Integer.parseInt(ints.group(1)) << 32) | (Integer.parseInt(ints.group(2)) & 0xffffffffL);
        long least = ((long) Integer.parseInt(ints.group(3)) << 32) | (Integer.parseInt(ints.group(4)) & 0xffffffffL);
        return new UUID(most, least);
    }

    private static int slot(Bot bot, String label) {
        Supplier<Integer> found = () -> bot.window().flatMap(window -> window.slotNamed(label)).orElse(null);
        Await.until("a page with '" + label + "' is open for " + bot.name(), WAIT, () -> found.get() != null);
        return found.get();
    }
}
