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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Players' shops on a real server, with RainsEconomy paying: a chest becomes a shop by command, a customer buys and
 * sells at its counter, breaking it and a hopper under it take nothing, a statue of the owner opens the store, and
 * the bazaar finds the shop by what it sells.
 */
@Tag("e2e")
class ShopsScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);

    /** The chest, on a platform out of everybody's way. */
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("open a shop, buy, sell, survive a pickaxe and a hopper, open the store through a statue, find it")
    void shops() throws Exception {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        PaperServer server = PaperServer.forRun().seed(1).in(E2e.serverFolder("shops"))
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(economy).plugin(shops)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.awaitLog("The economy is up", WAIT);
            server.console("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 4) + " " + (Y - 1) + " "
                    + (Z + 4) + " minecraft:stone");
            server.console("setblock " + X + " " + Y + " " + Z + " minecraft:chest");
            server.console("item replace block " + X + " " + Y + " " + Z
                    + " container.0 with minecraft:diamond 20");

            Bot ada = server.bot("Ada").join();
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Ada");
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");
            server.console("give Ada minecraft:diamond 1");

            // ── Ada opens a shop: 2 diamonds for 10, buying 2 back for 4 ─────────────────────────────────────────
            standInFront(server, ada, "Ada");
            ada.hold(0);
            // Clicked first, then the command: it takes the container clicked a moment ago.
            ada.useOn(X, Y, Z);
            Await.ticks(10);
            ada.closeWindow();
            ada.run("bazaar create 10 4 2");
            ada.expectChat("Open for business");

            // ── Bo buys a bundle at the counter ─────────────────────────────────────────────────────────────────
            standInFront(server, bo, "Bo");
            bo.useOn(X, Y, Z);
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 2× Diamond");
            Await.until("two diamonds reached Bo", WAIT, () -> carried(server, "Bo", "diamond") == 2);
            ada.expectChat("Bo bought");
            assertThat(inChest(server, "diamond")).isEqualTo(18);

            // ── and sells one back ──────────────────────────────────────────────────────────────────────────────
            bo.clickSlot(slot(bo, "Sell one bundle"));
            bo.expectChat("Sold 2× Diamond");
            Await.until("Bo's diamonds went into the shop", WAIT, () -> carried(server, "Bo", "diamond") == 0);
            assertThat(inChest(server, "diamond")).isEqualTo(20);
            bo.closeWindow();

            // ── nothing but a trade takes stock ─────────────────────────────────────────────────────────────────
            bo.dig(X, Y, Z);
            Await.ticks(60);
            assertThat(server.console("execute if block " + X + " " + Y + " " + Z + " minecraft:chest"))
                    .as("Bo broke Ada's shop").contains("passed");
            server.console("setblock " + X + " " + (Y - 1) + " " + Z + " minecraft:hopper");
            Await.ticks(100);
            assertThat(inChest(server, "diamond")).as("a hopper under the shop emptied it").isEqualTo(20);
            assertThat(server.console("data get block " + X + " " + (Y - 1) + " " + Z + " Items"))
                    .doesNotContain("diamond");

            // ── a statue of Ada opens her store ─────────────────────────────────────────────────────────────────
            server.console("tp Ada " + (X + 3) + " " + Y + " " + (Z + 3));
            Await.ticks(20);
            ada.run("bazaar statue");
            ada.expectChat("A statue of you now stands here");
            UUID statue = mannequin(server);
            server.console("tp Bo " + (X + 3) + " " + Y + " " + (Z + 1));
            Await.until("Bo sees the statue", WAIT, () -> bo.sees(statue));
            bo.interact(statue);
            bo.clickSlot(slot(bo, "Diamond"));
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 2× Diamond");
            Await.until("bought through the statue", WAIT, () -> carried(server, "Bo", "diamond") == 2);
            bo.closeWindow();

            // ── the bazaar finds it by what it sells ────────────────────────────────────────────────────────────
            bo.run("bazaar diamond");
            assertThat(slot(bo, "Diamond")).isNotNegative();
            bo.closeWindow();
            bo.run("bazaar netherite");
            assertThat(slot(bo, "Nothing found")).isNotNegative();
            bo.closeWindow();

            assertThat(errors(server)).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops"));
        }
    }

    /** Two blocks south of the chest, looking at it. */
    private static void standInFront(PaperServer server, Bot bot, String name) {
        server.console("tp " + name + " " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
        Await.ticks(20);
        bot.look(180, 30);
        Await.ticks(5);
    }

    /** What a player carries, as the server counts it; {@code clear} with a count of 0 takes nothing. */
    private static int carried(PaperServer server, String player, String material) {
        Matcher found = Pattern.compile("Found (\\d+)")
                .matcher(server.console("clear " + player + " minecraft:" + material + " 0"));
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** How many of an item are in the chest, summed over its slots. */
    private static int inChest(PaperServer server, String material) {
        String items = server.console("data get block " + X + " " + Y + " " + Z + " Items");
        Matcher stack = Pattern.compile("count: (\\d+)[^}]*id: \"minecraft:" + material + "\"|id: \"minecraft:"
                + material + "\"[^}]*count: (\\d+)").matcher(items);
        int total = 0;
        while (stack.find()) {
            total += Integer.parseInt(stack.group(1) != null ? stack.group(1) : stack.group(2));
        }
        return total;
    }

    /** The one Mannequin on the server, by the four ints {@code data get} prints its UUID as. */
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

    private static List<String> errors(PaperServer server) {
        return server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                .filter(line -> !line.contains("resource pack server could not start")).toList();
    }
}
