package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Auto Sell Chest and the Sell Wand on a real server: a chest and the book made into one at an anvil (never a
 * stack of them), placed and dressed, things dropped in sold and gone, a hopper's sold as they come, a plugin's own
 * item handed back, no double chest, no explosion, broken back into itself; and the wand selling a chest — worn,
 * enchanted and full things included — after asking.
 */
@Tag("e2e")
class AutoSellChestScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final int Y = 64;

    private static boolean has(Server server, String selector) {
        return server.console("execute if entity " + selector).contains("passed");
    }

    private static boolean block(Server server, int x, int y, int z, String what) {
        return server.console("execute if block " + x + " " + y + " " + z + " " + what).contains("passed");
    }

    private static int count(Server server, String item) {
        String said = server.console("clear Tester " + item + " 0");
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("Found (\\d+)").matcher(said);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    private static long balance(Bot bot) {
        bot.forgetChat();
        bot.run("balance");
        String line = Await.value(() -> "the balance line", WAIT, () -> bot.chatText().stream()
                .filter(text -> text.contains("You have")).findFirst().orElse(null));
        return Long.parseLong(line.substring(line.indexOf('⛃') + 1).split("[^0-9,]")[0].replace(",", ""));
    }

    private static boolean emptied(Server server, int x, int y, int z) {
        String items = server.console("data get block " + x + " " + y + " " + z + " Items");
        return items.contains("[]") || items.contains("Found no elements");
    }

    private static boolean anvilOpen(Bot bot) {
        return bot.window().map(window -> window.type().toUpperCase(java.util.Locale.ROOT).contains("ANVIL")).orElse(false);
    }

    @Test
    @DisplayName("made at an anvil, placed, fed by hand and by hopper, kept single, spared by TNT, broken back; the wand sells a chest")
    void chestAndWand() {
        try (Server server = Server.start("auto-sell-chest",
                List.of("enchants-standalone:RainsEnchants-.*", "economy-standalone:RainsEconomy-.*"),
                List.of("Enchants are up", "The economy is up"))) {
            server.console("forceload add -16 -16 16 16");
            server.console("fill -8 " + (Y - 4) + " -8 12 " + (Y + 8) + " 12 minecraft:air");
            server.console("fill -8 " + (Y - 4) + " -8 12 " + (Y - 1) + " 12 minecraft:stone");

            Bot tester = server.player("Tester");
            server.console("gamemode survival Tester");
            server.console("clear Tester");
            server.console("tp Tester 0.5 " + Y + " 0.5 0 0");
            server.console("effect give Tester minecraft:resistance 600 4 true");
            server.console("xp add Tester 50 levels");
            server.console("setblock 0 " + Y + " 2 minecraft:anvil");
            Await.ticks(20);
            String book = "minecraft:enchanted_book[minecraft:stored_enchantments={\"rainsenchants:auto_sell\":1}]";

            // --- one book does not make a stack of chests into Auto Sell Chests -------------------------------
            server.console("give Tester minecraft:chest 2");
            server.console("give Tester " + book + " 1");
            Await.ticks(10);
            tester.useOn(0, Y, 2);
            Await.until("the anvil opens", WAIT, () -> anvilOpen(tester));
            // Anvil window: 0 and 1 in, 2 out, the hotbar from 30.
            tester.clickSlot(30);
            tester.clickSlot(0);
            tester.clickSlot(31);
            tester.clickSlot(1);
            Await.ticks(20);
            assertThat(tester.window().orElseThrow().items().get(2)).as("no result for a stack").isNull();
            tester.clickSlot(0);
            tester.clickSlot(30);
            tester.clickSlot(1);
            tester.clickSlot(31);
            tester.closeWindow();
            Await.ticks(10);
            server.console("clear Tester minecraft:chest");

            // --- one chest and the book make an Auto Sell Chest ---------------------------------------------
            server.console("give Tester minecraft:chest 1");
            Await.ticks(10);
            tester.useOn(0, Y, 2);
            Await.until("the anvil opens again", WAIT, () -> anvilOpen(tester));
            tester.clickSlot(30);
            tester.clickSlot(0);
            tester.clickSlot(31);
            tester.clickSlot(1);
            Await.until("an Auto Sell Chest in the result", WAIT, () -> tester.window()
                    .map(window -> window.items().get(2)).map(item -> item.name().contains("Auto Sell Chest")).orElse(false));
            tester.clickSlot(2);
            tester.clickSlot(30);
            tester.closeWindow();
            try {
                Await.until("the Auto Sell Chest is carried", WAIT, () -> server.console(
                        "data get entity Tester Inventory[{Slot:0b}].components").contains("rainseconomy:auto_sell_chest"));
            } catch (AssertionError missing) {
                throw new AssertionError(missing.getMessage() + " — carries " + server.console("data get entity Tester Inventory")
                        + " / " + tester.items(), missing);
            }
            assertThat(server.console("data get entity Tester Inventory[{Slot:0b}].components.\"minecraft:enchantments\""))
                    .contains("rainsenchants:auto_sell");

            // --- placed: remembered on the block, dressed by a display ---------------------------------------
            tester.hold(0);
            tester.useOn(2, Y - 1, 0);
            Await.until("the chest stands", WAIT, () -> block(server, 2, Y, 0, "minecraft:chest"));
            assertThat(server.console("data get block 2 " + Y + " 0 PublicBukkitValues")).contains("rainseconomy:auto-sell-chest");
            Await.until("its display", WAIT, () -> has(server, "@e[type=item_display,x=2.5,y=" + Y + ".5,z=0.5,distance=..0.6]"));

            // --- dropped in, sold and gone ------------------------------------------------------------------
            long before = balance(tester);
            server.console("give Tester minecraft:cobblestone 64");
            Await.until("the cobblestone is carried", WAIT, () -> count(server, "minecraft:cobblestone") == 64);
            tester.useOn(2, Y, 0);
            var window = tester.awaitWindow("Auto Sell Chest");
            tester.shiftClickSlot(window.size() + 27);
            Await.until("the cobblestone is gone", WAIT, () -> count(server, "minecraft:cobblestone") == 0);
            Await.until("and paid", WAIT, () -> balance(tester) > before);

            // --- a plugin's own item is not sold: it comes back --------------------------------------------
            server.console("eco wand Tester");
            Await.until("the wand is carried", WAIT, () -> tester.carrying(item -> item.name().contains("Sell Wand")).isPresent());
            int wandSlot = tester.hotbarSlotOf(item -> item.name().contains("Sell Wand"));
            tester.shiftClickSlot(window.size() + 27 + wandSlot);
            Await.ticks(20);
            assertThat(count(server, "minecraft:blaze_rod")).as("handed back: " + server.console("data get entity Tester Inventory")).isEqualTo(1);
            tester.closeWindow();

            // --- a hopper's iron is sold as it comes in ------------------------------------------------------
            long beforeHopper = balance(tester);
            server.console("setblock 2 " + (Y + 1) + " 0 minecraft:hopper[facing=down]");
            server.console("item replace block 2 " + (Y + 1) + " 0 container.0 with minecraft:iron_ingot 8");
            Await.until("the hopper is empty", Duration.ofSeconds(30), () -> emptied(server, 2, Y + 1, 0));
            Await.until("the chest is empty", WAIT, () -> emptied(server, 2, Y, 0));
            Await.until("the iron is paid", WAIT, () -> balance(tester) > beforeHopper);
            server.console("setblock 2 " + (Y + 1) + " 0 minecraft:air");

            // --- a chest placed beside it does not pair with it ----------------------------------------------
            server.console("give Tester minecraft:chest 1");
            Await.ticks(10);
            tester.hold(tester.hotbarSlotOf(item -> item.is("chest") && item.name().isEmpty()));
            tester.useOn(3, Y - 1, 0);
            Await.until("the plain chest stands", WAIT, () -> block(server, 3, Y, 0, "minecraft:chest"));
            assertThat(block(server, 2, Y, 0, "minecraft:chest[type=single]")).isTrue();
            assertThat(block(server, 3, Y, 0, "minecraft:chest[type=single]")).isTrue();

            // --- TNT next to it: the plain chest goes, the Auto Sell Chest stays -----------------------------
            server.console("summon minecraft:tnt 2.5 " + Y + " 1.5 {fuse:1}");
            Await.until("the plain chest is blown away", WAIT, () -> !block(server, 3, Y, 0, "minecraft:chest"));
            assertThat(block(server, 2, Y, 0, "minecraft:chest")).isTrue();

            // --- broken: the Auto Sell Chest comes back, the display goes -----------------------------------
            server.console("kill @e[type=item]");
            server.console("tp Tester 0.5 " + Y + " 0.5 0 0");
            // The bot only starts digging, so the chest has to break at once: Efficiency X does that.
            tester.hold(8);
            Await.ticks(5);
            server.console("item replace entity Tester hotbar.8 with minecraft:netherite_axe"
                    + "[minecraft:enchantments={\"minecraft:efficiency\":10}]");
            Await.ticks(10);
            tester.dig(2, Y, 0);
            Await.until("the chest is broken", WAIT, () -> block(server, 2, Y, 0, "minecraft:air"));
            Await.until("it drops as an Auto Sell Chest", WAIT, () -> has(server,
                    "@e[type=item,nbt={Item:{components:{\"minecraft:item_model\":\"rainseconomy:auto_sell_chest\"}}}]"));
            Await.until("its display is gone", WAIT, () -> !has(server, "@e[type=item_display]"));

            // --- the wand: everything sellable in a chest — worn, enchanted, full — after asking --------------
            server.console("setblock 4 " + Y + " 0 minecraft:chest");
            server.console("item replace block 4 " + Y + " 0 container.0 with minecraft:diamond 3");
            server.console("item replace block 4 " + Y + " 0 container.1 with minecraft:diamond_sword"
                    + "[minecraft:damage=500,minecraft:enchantments={\"minecraft:sharpness\":3}]");
            server.console("item replace block 4 " + Y + " 0 container.2 with minecraft:shulker_box"
                    + "[minecraft:container=[{slot:0,item:{id:\"minecraft:cobblestone\",count:10}}]]");
            long beforeWand = balance(tester);
            tester.hold(tester.hotbarSlotOf(item -> item.name().contains("Sell Wand")));
            tester.useOn(4, Y, 0);
            tester.awaitWindow("Sell it all for");
            assertThat(emptied(server, 4, Y, 0)).as("nothing sold before yes").isFalse();
            tester.click("Yes");
            Await.until("the chest is empty", WAIT, () -> emptied(server, 4, Y, 0));
            Await.until("and paid", WAIT, () -> balance(tester) > beforeWand);
        }
    }
}
