package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enchants 0.31 with Essentials 1.29 on a real server: each enchantment only on its own gear, XP Siphon, Reinforced on
 * top of Unbreaking, an enchantment switched off and on again from the settings, and /disenchant with what its tab
 * completion offers.
 */
@Tag("e2e")
class EnchantSwitchesScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final int Y = 64;

    private static boolean passes(Server server, String test) {
        return server.console("execute if " + test).contains("passed");
    }

    private static boolean holds(Server server, String enchantment) {
        return passes(server, "items entity Tester weapon.mainhand *[minecraft:enchantments~[{enchantments:\""
                + enchantment + "\"}]]");
    }

    private static int number(String said, String pattern) {
        Matcher found = Pattern.compile(pattern).matcher(said);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** The wear on a worn piece; none written is none taken. */
    private static int wear(Server server, String slot) {
        return number(server.console("data get entity Tester equipment." + slot + ".components.\"minecraft:damage\""),
                "entity data: (\\d+)");
    }

    private static void hand(Server server, String item) {
        server.console("item replace entity Tester weapon.mainhand with " + item);
        Await.ticks(5);
    }

    @Test
    @DisplayName("gear limits, XP Siphon, Reinforced, the switches and /disenchant")
    void switches() {
        try (Server server = Server.start("enchant-switches",
                List.of("enchants-standalone:RainsEnchants-.*", "essentials-standalone:RainsEssentials-.*"),
                List.of("Enchants are up"))) {
            server.console("forceload add -16 -16 16 16");
            server.console("fill -8 " + (Y - 4) + " -8 8 " + (Y + 6) + " 8 minecraft:air");
            server.console("fill -8 " + (Y - 4) + " -8 8 " + (Y - 1) + " 8 minecraft:stone");
            Bot tester = server.player("Tester");
            server.console("gamemode survival Tester");
            server.console("clear Tester");
            server.console("tp Tester 0.5 " + Y + " 0.5 0 0");
            Await.ticks(20);

            // --- each goes only on its own gear: the game's own /enchant asks the same registry the anvil does ---
            hand(server, "minecraft:diamond_pickaxe");
            assertThat(server.console("minecraft:enchant Tester rainsenchants:lifesteal")).doesNotContain("Applied");
            assertThat(holds(server, "rainsenchants:lifesteal")).as("Lifesteal is for weapons").isFalse();
            assertThat(server.console("minecraft:enchant Tester rainsenchants:xp_siphon")).contains("Applied");
            hand(server, "minecraft:diamond_chestplate");
            assertThat(server.console("minecraft:enchant Tester rainsenchants:xp_siphon")).doesNotContain("Applied");
            assertThat(server.console("minecraft:enchant Tester rainsenchants:flight")).contains("Applied");
            hand(server, "minecraft:elytra");
            assertThat(server.console("minecraft:enchant Tester rainsenchants:reinforced 3")).contains("Applied");
            assertThat(server.console("minecraft:enchant Tester minecraft:unbreaking 3"))
                    .as("Reinforced and Unbreaking share an item").contains("Applied");

            // --- XP Siphon: the ore's experience goes into the player, no orb is left lying -----------------------
            server.console("xp set Tester 0 points");
            server.console("xp set Tester 0 levels");
            server.console("setblock 0 " + (Y - 1) + " 2 minecraft:diamond_ore");
            hand(server, "minecraft:netherite_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":10,"
                    + "\"rainsenchants:xp_siphon\":1}]");
            tester.dig(0, Y - 1, 2);
            Await.until("the ore breaks", WAIT, () -> !passes(server, "block 0 " + (Y - 1) + " 2 minecraft:diamond_ore"));
            Await.until("the experience arrives", WAIT,
                    // XpTotal, not "xp query points": that is only the progress within the level, 0 at a full one.
                    () -> number(server.console("data get entity Tester XpTotal"), "entity data: (\\d+)") > 0);
            assertThat(server.console("execute if entity @e[type=experience_orb]")).doesNotContain("passed");
            Await.until("its advancement", WAIT,
                    () -> passes(server, "entity @a[name=Tester,advancements={rainsenchants:xp_siphon=true}]"));

            // --- Reinforced III wears far slower than the same piece without it, hit for hit ----------------------
            server.console("item replace entity Tester armor.chest with minecraft:leather_chestplate"
                    + "[minecraft:enchantments={\"rainsenchants:reinforced\":3}]");
            server.console("item replace entity Tester armor.legs with minecraft:leather_leggings");
            server.console("effect give Tester minecraft:regeneration infinite 255 true");
            server.console("effect give Tester minecraft:saturation infinite 255 true");
            for (int hit = 0; hit < 30; hit++) {
                server.console("damage Tester 1 minecraft:mob_attack");
                Await.ticks(11);
            }
            int plain = wear(server, "legs");
            int reinforced = wear(server, "chest");
            assertThat(plain).as("the plain leggings took the wear").isGreaterThanOrEqualTo(25);
            assertThat(reinforced).as("Reinforced III saves about three in four").isLessThan(plain / 2);
            server.console("effect clear Tester");

            // --- switched off: does nothing until switched on again; Launch is off from the start ----------------
            assertThat(server.console("settings get switched-off")).contains("rainsenchants:launch");
            server.console("item replace entity Tester armor.head with minecraft:iron_helmet"
                    + "[minecraft:enchantments={\"rainsenchants:night_vision\":1}]");
            Await.until("Night Vision works", WAIT,
                    () -> passes(server, "data entity Tester active_effects[{id:\"minecraft:night_vision\"}]"));
            assertThat(server.console("settings set switched-off rainsenchants:night_vision")).contains("is now");
            Await.until("Night Vision stops once it is switched off", WAIT,
                    () -> !passes(server, "data entity Tester active_effects[{id:\"minecraft:night_vision\"}]"));
            assertThat(holds(server, "rainsenchants:night_vision") || passes(server,
                    "items entity Tester armor.head *[minecraft:enchantments~[{enchantments:\"rainsenchants:night_vision\"}]]"))
                    .as("the helmet keeps it").isTrue();
            assertThat(server.console("settings set switched-off rainsenchants:launch")).contains("is now");
            Await.until("Night Vision works again once switched back on", WAIT,
                    () -> passes(server, "data entity Tester active_effects[{id:\"minecraft:night_vision\"}]"));

            // --- /disenchant: tab shows what is on the item, curses stay for a player -----------------------------
            hand(server, "minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":3,"
                    + "\"rainsenchants:lifesteal\":2,\"minecraft:vanishing_curse\":1}]");
            assertThat(tester.complete("disenchant ")).containsExactlyInAnyOrder("all", "sharpness",
                    "rainsenchants:lifesteal");
            assertThat(tester.complete("disenchant life")).containsExactly("rainsenchants:lifesteal");

            tester.run("disenchant lifesteal");
            Await.until("Lifesteal comes off", WAIT, () -> !holds(server, "rainsenchants:lifesteal"));
            assertThat(holds(server, "minecraft:sharpness")).as("only that one").isTrue();

            tester.forgetChat();
            tester.run("disenchant vanishing_curse");
            Await.until("the refusal", WAIT, () -> tester.chatText().stream().anyMatch(line -> line.contains("Curses stick")));
            assertThat(holds(server, "minecraft:vanishing_curse")).isTrue();

            tester.run("disenchant all");
            Await.until("everything but the curse comes off", WAIT, () -> !holds(server, "minecraft:sharpness"));
            assertThat(holds(server, "minecraft:vanishing_curse")).isTrue();
            assertThat(tester.complete("disenchant ")).as("nothing left a player may take off").isEmpty();
        }
    }
}
