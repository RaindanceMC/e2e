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
 * Players' shops on a real server, with RainsEconomy paying: a shop is set up where its owner stands, its shopkeeper
 * gets an offer, the offer is priced and two stock chests are linked to it; a customer buys and sells at the
 * shopkeeper, cannot open, break or drain the stock chests, buys from the second chest once the first is empty, and
 * buys from afar through the bazaar, which finds the offer by what it sells.
 */
@Tag("e2e")
class ShopsScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);

    /** The first stock chest, on a platform out of everybody's way; the second stands two blocks east. */
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("set up a shop, link two chests, trade at the shopkeeper and from afar; stock stays the shop's")
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
            // Loaded first: a block set in a chunk nobody has loaded is silently not set at all.
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 16) + " " + (Z + 16));
            Await.ticks(40);
            platform(server);
            server.console("item replace block " + X + " " + Y + " " + Z
                    + " container.0 with minecraft:diamond 20");
            assertThat(inChest(server, X, "diamond")).as("the chest was not set up").isEqualTo(20);

            Bot ada = server.bot("Ada").join();
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Ada");
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");

            // ── Ada sets up a shop: a diamond for 10, buying one back for 4, from both chests ───────────────────
            setUp(server, ada, "Ada");

            // ── Ada, sneaking with an item frame, puts it on her stock chest — no menu in the way ───────────────
            server.console("give Ada minecraft:item_frame 1");
            server.console("tp Ada " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.hold(ada.hotbarSlotOf(item -> item.material().toLowerCase().contains("item_frame")));
            ada.sneak(true);
            ada.useOn(X, Y, Z);
            Await.ticks(20);
            ada.sneak(false);
            assertThat(ada.window()).as("a menu opened instead of placing the frame").isEmpty();
            assertThat(server.console("execute if entity @e[type=minecraft:item_frame]")).as("the frame is on the chest")
                    .contains("passed");

            // ── Bo buys one at the shopkeeper ───────────────────────────────────────────────────────────────────
            UUID keeper = mannequin(server);
            server.console("tp Bo " + (X + 1) + ".5 " + Y + " " + (Z + 4) + ".5 180 0");
            Await.until("Bo sees the shopkeeper", WAIT, () -> bo.sees(keeper));
            bo.interact(keeper);
            bo.clickSlot(slot(bo, "Diamond"));
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("a diamond reached Bo", WAIT, () -> carried(server, "Bo", "diamond") == 1);
            ada.expectChat("Bo bought");
            assertThat(inChest(server, X, "diamond")).isEqualTo(19);

            // ── and sells it back ───────────────────────────────────────────────────────────────────────────────
            bo.clickSlot(slot(bo, "Sell one bundle"));
            bo.expectChat("Sold 1× Diamond");
            Await.until("Bo's diamond went into the stock", WAIT, () -> carried(server, "Bo", "diamond") == 0);
            assertThat(inChest(server, X, "diamond") + inChest(server, X + 2, "diamond")).isEqualTo(20);
            bo.closeWindow();

            // ── the stock chests are the shop's: not opened, not broken, not drained ────────────────────────────
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            bo.expectChat("stock");
            assertThat(bo.window()).as("Bo opened a stock chest").isEmpty();
            bo.dig(X, Y, Z);
            Await.ticks(60);
            assertThat(server.console("execute if block " + X + " " + Y + " " + Z + " minecraft:chest"))
                    .as("Bo broke a stock chest").contains("passed");
            server.console("setblock " + X + " " + (Y - 1) + " " + Z + " minecraft:hopper");
            Await.ticks(100);
            assertThat(server.console("data get block " + X + " " + (Y - 1) + " " + Z + " Items"))
                    .as("a hopper drained a stock chest").doesNotContain("diamond");

            // ── the first chest empty: the second sells; and from afar, through the bazaar ──────────────────────
            server.console("data merge block " + X + " " + Y + " " + Z + " {Items:[]}");
            server.console("data merge block " + (X + 2) + " " + Y + " " + Z + " {Items:[]}");
            server.console("item replace block " + (X + 2) + " " + Y + " " + Z
                    + " container.0 with minecraft:diamond 3");
            server.console("tp Bo " + (X + 40) + " " + Y + " " + Z);
            Await.ticks(40);
            bo.run("bazaar diamond");
            bo.clickSlot(slot(bo, "Diamond"));
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("bought from afar", WAIT, () -> carried(server, "Bo", "diamond") == 1);
            assertThat(inChest(server, X + 2, "diamond")).isEqualTo(2);
            bo.closeWindow();
            bo.run("bazaar netherite");
            assertThat(slot(bo, "Nothing found")).isNotNegative();
            bo.closeWindow();

            // ── Ada, sneaking, gets her shop's own page from the shopkeeper ─────────────────────────────────────
            server.console("tp Ada " + (X + 1) + ".5 " + Y + " " + (Z + 4) + ".5 180 0");
            Await.until("Ada sees the shopkeeper", WAIT, () -> ada.sees(keeper));
            ada.sneak(true);
            Await.ticks(5);
            ada.interact(keeper);
            assertThat(slot(ada, "Offer the item in your hand")).isNotNegative();
            ada.sneak(false);

            // ── and paints its name with Core's style editor ────────────────────────────────────────────────────
            ada.rightClickSlot(slot(ada, "Name:"));
            Await.until("the style editor opened", WAIT, () -> ada.window()
                    .map(window -> window.title().contains("Paint")).orElse(false));
            ada.closeWindow();

            assertThat(errors(server)).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops"));
        }
    }

    /** Air above stone, and the two chests. */
    static void platform(PaperServer server) {
        server.console("fill " + (X - 4) + " " + Y + " " + (Z - 4) + " " + (X + 6) + " " + (Y + 4) + " "
                + (Z + 6) + " minecraft:air");
        server.console("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 6) + " " + (Y - 1) + " "
                + (Z + 6) + " minecraft:stone");
        server.console("setblock " + X + " " + Y + " " + Z + " minecraft:chest");
        server.console("setblock " + (X + 2) + " " + Y + " " + Z + " minecraft:chest");
    }

    /**
     * The owner sets up "Gem Corner" standing between the chests, offers the diamond in their hand at 10, buying at 4,
     * links both chests and opens the offer.
     */
    static void setUp(PaperServer server, Bot owner, String name) {
        server.console("clear " + name);
        server.console("give " + name + " minecraft:diamond 1");
        server.console("tp " + name + " " + (X + 1) + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
        Await.ticks(20);
        owner.hold(0);
        owner.run("bazaar create Gem Corner");
        owner.expectChat("is open");
        owner.clickSlot(slot(owner, "Offer the item in your hand"));
        owner.expectChat("New offer");
        owner.clickSlot(slot(owner, "Selling price"));
        owner.typeInAnvil("10");
        owner.expectChat("Done");
        owner.clickSlot(slot(owner, "Buying price"));
        owner.typeInAnvil("4");
        owner.expectChat("Done");
        owner.clickSlot(slot(owner, "Link stock chests"));
        owner.expectChat("Linking chests");
        owner.look(180, 30);
        owner.useOn(X, Y, Z);
        owner.expectChat("(1/");
        owner.useOn(X + 2, Y, Z);
        owner.expectChat("(2/");
        owner.run("bazaar done");
        owner.expectChat("sells from 2 chest");
        owner.clickSlot(slot(owner, "Paused"));
        owner.expectChat("Done");
        assertThat(slot(owner, "Open")).isNotNegative();
        owner.closeWindow();
    }

    /** What a player carries, as the server counts it; {@code clear} with a count of 0 takes nothing. */
    static int carried(PaperServer server, String player, String material) {
        Matcher found = Pattern.compile("Found (\\d+)")
                .matcher(server.console("clear " + player + " minecraft:" + material + " 0"));
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** How many of an item are in the chest at this x, summed over its slots. */
    static int inChest(PaperServer server, int x, String material) {
        String items = server.console("data get block " + x + " " + Y + " " + Z + " Items");
        Matcher stack = Pattern.compile("count: (\\d+)[^}]*id: \"minecraft:" + material + "\"|id: \"minecraft:"
                + material + "\"[^}]*count: (\\d+)").matcher(items);
        int total = 0;
        while (stack.find()) {
            total += Integer.parseInt(stack.group(1) != null ? stack.group(1) : stack.group(2));
        }
        return total;
    }

    /** The one Mannequin on the server, by the four ints {@code data get} prints its UUID as. */
    static UUID mannequin(PaperServer server) {
        String said = server.console("data get entity @e[type=minecraft:mannequin,limit=1] UUID");
        Matcher ints = Pattern.compile("\\[I;\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)]").matcher(said);
        assertThat(ints.find()).as("no shopkeeper: " + said).isTrue();
        long most = ((long) Integer.parseInt(ints.group(1)) << 32) | (Integer.parseInt(ints.group(2)) & 0xffffffffL);
        long least = ((long) Integer.parseInt(ints.group(3)) << 32) | (Integer.parseInt(ints.group(4)) & 0xffffffffL);
        return new UUID(most, least);
    }

    static int slot(Bot bot, String label) {
        Supplier<Integer> found = () -> bot.window().flatMap(window -> window.slotNamed(label)).orElse(null);
        Await.until("a page with '" + label + "' is open for " + bot.name(), WAIT, () -> found.get() != null);
        return found.get();
    }

    private static List<String> errors(PaperServer server) {
        return server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                .filter(line -> !line.contains("resource pack server could not start")).toList();
    }
}
