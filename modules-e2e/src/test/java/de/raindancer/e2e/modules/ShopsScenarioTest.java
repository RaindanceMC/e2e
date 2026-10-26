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
 * Chest shops on a real server, with RainsEconomy paying: an item frame showing a diamond hangs on a chest, its owner
 * writes a {@code [market]} sign on it, and the chest is a shop — the sign rewritten and waxed, the frame fixed, the
 * item and price floating over it. A customer buys and sells at the frame and at the chest, cannot open, break or
 * drain the chest or break the sign, and buys from afar through /market, which finds the shop by what it sells. The
 * owner switches a floating line off in the back room, and breaking the sign ends the shop.
 */
@Tag("e2e")
class ShopsScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);

    /** The shop's chest, on a platform out of everybody's way; a plain chest stands two blocks east. */
    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    @Test
    @DisplayName("a chest, a frame, a [market] sign: trade at it and from afar; it stays the shop's; the sign ends it")
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

            // ── Ada, sneaking with an item frame, puts it on her chest — the game places it ─────────────────────
            server.console("clear Ada");
            server.console("give Ada minecraft:item_frame 1");
            server.console("tp Ada " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            ada.hold(ada.hotbarSlotOf(item -> item.material().toLowerCase().contains("item_frame")));
            ada.sneak(true);
            ada.useOn(X, Y, Z);
            Await.ticks(20);
            ada.sneak(false);
            assertThat(server.console("execute if entity @e[type=minecraft:item_frame]")).as("the frame is on the chest")
                    .contains("passed");
            server.console("kill @e[type=minecraft:item_frame]");
            Await.ticks(5);

            // ── Ada sets up her shop: a diamond for 10, buying one back for 4 ───────────────────────────────────
            setUp(server, ada, "Ada");
            assertThat(server.console("data get block " + (X + 1) + " " + Y + " " + Z + " front_text.messages[0]"))
                    .as("the sign says what the shop is").contains("Market");
            assertThat(server.console("data get entity @e[type=minecraft:item_frame,limit=1] Fixed"))
                    .as("the frame is fixed").contains("1b");
            Await.until("the item and price float over the chest", WAIT, () -> server.console(
                    "execute if entity @e[type=minecraft:text_display]").contains("passed")
                    && server.console("execute if entity @e[type=minecraft:item_display]").contains("passed"));
            spins(server);

            // ── Bo buys one at the frame ────────────────────────────────────────────────────────────────────────
            UUID frame = frame(server);
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 3) + ".5 180 0");
            Await.until("Bo sees the frame", WAIT, () -> bo.sees(frame));
            bo.interact(frame);
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("a diamond reached Bo", WAIT, () -> carried(server, "Bo", "diamond") == 1);
            ada.expectChat("Bo bought");
            assertThat(inChest(server, X, "diamond")).isEqualTo(19);

            // ── and sells it back ───────────────────────────────────────────────────────────────────────────────
            bo.clickSlot(slot(bo, "Sell one bundle"));
            bo.expectChat("Sold 1× Diamond");
            Await.until("Bo's diamond went into the chest", WAIT, () -> carried(server, "Bo", "diamond") == 0);
            assertThat(inChest(server, X, "diamond")).isEqualTo(20);
            bo.closeWindow();

            // ── the chest is the shop's: clicking it is the counter; not broken, not drained; the sign stays ───
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            assertThat(slot(bo, "Buy one bundle")).as("the chest is the counter for a customer").isNotNegative();
            bo.closeWindow();
            bo.dig(X, Y, Z);
            Await.ticks(60);
            assertThat(server.console("execute if block " + X + " " + Y + " " + Z + " minecraft:chest"))
                    .as("Bo broke the shop's chest").contains("passed");
            bo.dig(X + 1, Y, Z);
            Await.ticks(60);
            assertThat(server.console("execute if block " + (X + 1) + " " + Y + " " + Z + " minecraft:oak_wall_sign"))
                    .as("Bo broke the shop's sign").contains("passed");
            server.console("setblock " + X + " " + (Y - 1) + " " + Z + " minecraft:hopper");
            Await.ticks(100);
            assertThat(server.console("data get block " + X + " " + (Y - 1) + " " + Z + " Items"))
                    .as("a hopper drained the shop's chest").doesNotContain("diamond");
            server.console("setblock " + X + " " + (Y - 1) + " " + Z + " minecraft:stone");

            // ── found through /market: a click takes Bo to the shop, in front of its chest, and trades nothing ──
            server.console("tp Bo " + (X + 40) + " " + Y + " " + Z);
            Await.ticks(40);
            bo.run("market diamond");
            bo.clickSlot(slot(bo, "Diamond"));
            Await.until("Bo travelled to the front of the shop's chest", WAIT,
                    () -> Math.abs(bo.position().getX() - (X + 0.5)) < 1.5
                            && Math.abs(bo.position().getZ() - (Z + 1.5)) < 1.5);
            assertThat(carried(server, "Bo", "diamond")).as("going there bought nothing").isZero();
            assertThat(inChest(server, X, "diamond")).isEqualTo(20);

            // ── from afar, through /market, only when switched on (off by default): shift click ─────────────────
            assertThat(server.console("settings set remote-buying true")).contains("is now");
            assertThat(server.console("settings set remote-selling true")).contains("is now");
            server.console("tp Bo " + (X + 40) + " " + Y + " " + Z);
            Await.ticks(40);
            bo.run("market diamond");
            bo.shiftClickSlot(slot(bo, "Diamond"));
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("bought from afar", WAIT, () -> carried(server, "Bo", "diamond") == 1);
            assertThat(inChest(server, X, "diamond")).isEqualTo(19);
            bo.closeWindow();
            // ── buying and selling from afar switch off each on its own ─────────────────────────────────────────
            assertThat(server.console("settings set remote-buying false")).contains("is now");
            bo.run("market sell diamond");
            bo.shiftClickSlot(slot(bo, "Diamond"));
            assertThat(lore(bo, "Buy one bundle")).as("buying from afar is off, and the button says so")
                    .contains("Buying from afar");
            bo.forgetChat();
            bo.clickSlot(slot(bo, "Sell one bundle"));
            bo.expectChat("Sold 1× Diamond");
            Await.until("Bo sold his diamond from afar", WAIT, () -> carried(server, "Bo", "diamond") == 0);
            bo.closeWindow();
            assertThat(server.console("settings set remote-buying true")).contains("is now");
            assertThat(server.console("settings set remote-selling false")).contains("is now");
            bo.run("market diamond");
            bo.shiftClickSlot(slot(bo, "Diamond"));
            bo.forgetChat();
            bo.clickSlot(slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            Await.until("Bo bought one from afar", WAIT, () -> carried(server, "Bo", "diamond") == 1);
            assertThat(lore(bo, "Sell one bundle")).as("selling from afar is off, and the button says so")
                    .contains("Selling from afar");
            bo.closeWindow();
            assertThat(server.console("settings set remote-selling true")).contains("is now");
            assertThat(inChest(server, X, "diamond")).isEqualTo(19);
            Await.ticks(20);
            bo.run("market netherite");
            assertThat(slot(bo, "Nothing found")).isNotNegative();
            bo.closeWindow();

            // ── Ada, sneaking with an empty hand at her chest, gets the back room and stops the price floating ──
            server.console("tp Ada " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            server.console("clear Ada");
            Await.ticks(20);
            ada.sneak(true);
            Await.ticks(5);
            ada.useOn(X, Y, Z);
            ada.clickSlot(slot(ada, "What floats over the chest"));
            ada.clickSlot(slot(ada, "Item"));
            ada.clickSlot(slot(ada, "Price"));
            ada.clickSlot(slot(ada, "Name"));
            ada.sneak(false);
            ada.closeWindow();
            Await.until("nothing floats any more", WAIT, () -> !server.console(
                    "execute if entity @e[type=minecraft:text_display]").contains("passed")
                    && !server.console("execute if entity @e[type=minecraft:item_display]").contains("passed"));

            // ── and breaking the sign ends the shop; the frame is let go, the chest keeps its stock ─────────────
            // In creative: a bot's dig is instant, and a survival player cannot break a sign in no time.
            server.console("gamemode creative Ada");
            Await.ticks(10);
            ada.dig(X + 1, Y, Z);
            ada.expectChat("is closed");
            Await.ticks(20);
            assertThat(server.console("data get entity @e[type=minecraft:item_frame,limit=1] Fixed"))
                    .as("the frame is let go").contains("0b");
            assertThat(inChest(server, X, "diamond")).isEqualTo(19);
            server.console("tp Bo " + X + ".5 " + Y + " " + (Z + 2) + ".5 180 30");
            Await.ticks(20);
            bo.useOn(X, Y, Z);
            Await.until("a chest again: Bo opens it", WAIT, () -> bo.window().map(window -> !window.title()
                    .contains("Diamond")).orElse(false));
            bo.closeWindow();

            assertThat(errors(server)).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("shops"));
        }
    }

    /** Air above stone, the shop's chest and a plain one two blocks east. */
    static void platform(PaperServer server) {
        server.console("fill " + (X - 4) + " " + Y + " " + (Z - 4) + " " + (X + 6) + " " + (Y + 4) + " "
                + (Z + 6) + " minecraft:air");
        server.console("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + 6) + " " + (Y - 1) + " "
                + (Z + 6) + " minecraft:stone");
        server.console("setblock " + X + " " + Y + " " + Z + " minecraft:chest[facing=south]");
        server.console("setblock " + (X + 2) + " " + Y + " " + Z + " minecraft:chest[facing=south]");
    }

    /**
     * The owner's chest shop at the chest at X: an item frame showing a diamond on its south side and a sign on its
     * east side, written {@code [market] / B 10 S 4} by the owner as a player writes one.
     */
    static void setUp(PaperServer server, Bot owner, String name) {
        server.console("summon minecraft:item_frame " + X + ".5 " + Y + ".5 " + (Z + 1) + ".03 {Facing:3b,block_pos:[I;"
                + X + "," + Y + "," + (Z + 1) + "],Item:{id:\"minecraft:diamond\",count:1}}");
        server.console("setblock " + (X + 1) + " " + Y + " " + Z + " minecraft:oak_wall_sign[facing=east]");
        server.console("clear " + name);
        server.console("tp " + name + " " + (X + 2) + ".5 " + Y + " " + (Z + 2) + ".5 135 20");
        Await.ticks(20);
        writeSign(owner, X + 1, Y, Z, "[market]", "B 10 S 4", "", "");
        owner.expectChat("is open for business");
    }

    /** Opens the sign at x y z for editing and sends its lines, as a player's client does. */
    static void writeSign(Bot writer, int x, int y, int z, String... lines) {
        writer.useOn(x, y, z);
        Await.ticks(10);
        writer.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket(
                org.cloudburstmc.math.vector.Vector3i.from(x, y, z), List.of(lines), true));
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

    /** The lore of the button with this label on the page the bot has open, as one line. */
    static String lore(Bot bot, String label) {
        int at = slot(bot, label);
        return String.join(" ", bot.window().orElseThrow().items().get(at).lore());
    }

    /** The one item frame on the server, by the four ints {@code data get} prints its UUID as. */
    /** The floating item turns round and bobs; each switched off in the settings stops at once, no restart. */
    static void spins(PaperServer server) {
        String item = "data get entity @e[type=minecraft:item_display,limit=1] ";
        Await.until("the floating item turns about its own axis", WAIT,
                () -> server.console(item + "billboard").contains("fixed"));
        String before = server.console(item + "transformation.left_rotation");
        Await.ticks(45);
        assertThat(server.console(item + "transformation.left_rotation")).as("the item turned").isNotEqualTo(before);

        assertThat(server.console("settings set item-spin-seconds 0")).contains("is now");
        Await.until("the item faces the viewer again", WAIT,
                () -> server.console(item + "billboard").contains("vertical"));
        String still = server.console(item + "transformation.left_rotation");
        Await.ticks(45);
        assertThat(server.console(item + "transformation.left_rotation")).as("the item stops turning").isEqualTo(still);
        String height = server.console(item + "transformation.translation");
        Await.ticks(25);
        assertThat(server.console(item + "transformation.translation")).as("it still bobs").isNotEqualTo(height);

        assertThat(server.console("settings set item-bobs false")).contains("is now");
        Await.ticks(10);
        String resting = server.console(item + "transformation.translation");
        Await.ticks(25);
        assertThat(server.console(item + "transformation.translation")).as("it rests").isEqualTo(resting);
        assertThat(server.console("settings set item-bobs true")).contains("is now");
        assertThat(server.console("settings set item-spin-seconds 6")).contains("is now");
        Await.until("it turns again", WAIT, () -> server.console(item + "billboard").contains("fixed"));
    }

    static UUID frame(PaperServer server) {
        String said = server.console("data get entity @e[type=minecraft:item_frame,limit=1] UUID");
        Matcher ints = Pattern.compile("\\[I;\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)]").matcher(said);
        assertThat(ints.find()).as("no item frame: " + said).isTrue();
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
