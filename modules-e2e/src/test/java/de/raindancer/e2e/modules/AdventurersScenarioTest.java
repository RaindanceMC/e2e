package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RainsAdventurers on a real server: a skill point every adventurer level, spent in the skill tree
 * {@code /adventurer} opens — Toughness's heart, a choice that shuts its other side, the whole tree unlearnt —
 * Dual Wielder's off hand striking for half, and the magic items: a potion, a scroll, a wand, an amulet, loot.
 */
@Tag("e2e")
class AdventurersScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final int Y = 64;
    private static int next = 1;

    private static void arena(Server server) {
        server.console("forceload add -32 -32 32 32");
        server.console("fill -24 " + (Y - 1) + " -24 24 " + (Y - 1) + " 24 minecraft:stone");
        server.console("fill -24 " + Y + " -24 24 " + (Y + 8) + " 24 minecraft:air");
        server.console("gamerule spawn_mobs false");
        server.console("gamerule advance_time false");
        server.console("kill @e[type=!minecraft:player]");
        // Night: a zombie in the sun burns, and the fire would be counted as a blow.
        server.console("time set midnight");
    }

    /** A mob that stands still, with a UUID chosen here so the bot can hit it. */
    private static UUID mob(Server server, String type, double x, double z, String extra) {
        int n = next++;
        server.console("summon minecraft:" + type + " " + x + " " + Y + " " + z + " {NoAI:1b,PersistenceRequired:1b,"
                + "Silent:1b,UUID:[I;9,9,9," + n + "]" + extra + "}");
        return new UUID((9L << 32) | 9L, (9L << 32) | n);
    }

    /** Lots of health and no armour of its own, so what a blow deals is read off exactly. */
    private static final String STURDY = ",Health:200f,attributes:[{id:\"minecraft:max_health\",base:200},"
            + "{id:\"minecraft:armor\",base:0}]";

    private static double number(String out) {
        Matcher found = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)f?\\s*$").matcher(out.strip());
        return found.find() ? Double.parseDouble(found.group(1)) : Double.NaN;
    }

    private static double health(Server server, UUID who) {
        Matcher found = Pattern.compile("data: ([0-9.]+)f").matcher(server.console("data get entity " + who + " Health"));
        return found.find() ? Double.parseDouble(found.group(1)) : -1;
    }

    private static double maxHealth(Server server, String player) {
        return number(server.console("attribute " + player + " minecraft:max_health get"));
    }

    private static void swapHands(Bot bot) {
        bot.send(new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0));
    }

    private static String named(Bot bot, String text) {
        return bot.window().flatMap(window -> window.slotNamed(text).map(slot -> window.top().get(slot).name()))
                .orElse("");
    }

    private static String lore(Bot bot, String text) {
        return bot.window().flatMap(window -> window.slotNamed(text)
                .map(slot -> String.join(" ", window.top().get(slot).lore()))).orElse("");
    }

    /** {@code who} steps beside the mob and strikes it once; what the blow (and anything right after) dealt. */
    private static double hit(Server server, Bot who, UUID target, double x, double z) {
        server.console("tp " + who.name() + " " + x + " " + Y + " " + (z + 1.6) + " 180 0");
        // A swing the server did not take (the bot had only just been moved) is swung again; the first that lands counts.
        for (int tries = 0; tries < 3; tries++) {
            Await.ticks(30);
            double before = health(server, target);
            who.attack(target);
            Await.ticks(15);
            double dealt = before - health(server, target);
            if (dealt > 0) {
                return dealt;
            }
        }
        return 0;
    }

    @Test
    @DisplayName("a point every level; Toughness's heart, a choice shuts its other side, all unlearnt")
    void tree() {
        try (Server server = Server.start("adventurers", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            arena(server);
            Bot ada = server.player("Ada");
            server.console("gamemode survival Ada");
            server.console("tp Ada 0.5 " + Y + " 0.5 180 0");
            server.console("clear Ada");

            // --- the tree, at level 1: one point -----------------------------------------------------------------
            ada.runAndOpen("adventurer", "Vitality");
            assertThat(lore(ada, "Vitality")).contains("Adventurer level 1").contains("Skill points to spend: 1 of 1");
            assertThat(lore(ada, "Hearty Meals")).contains("Needs a node next to it first.");
            assertThat(maxHealth(server, "Ada")).isEqualTo(20.0);
            ada.click("Toughness");
            ada.expectChat("Toughness rank 1 learnt");
            Await.until("Toughness at rank 1", WAIT, () -> named(ada, "Toughness").contains("1/3"));
            Await.until("a heart more", WAIT, () -> maxHealth(server, "Ada") == 22.0);
            ada.click("Hearty Meals");
            ada.expectChat("Not enough skill points");
            ada.closeWindow();

            // --- 300 experience: level 2, and a second point -----------------------------------------------------
            server.console("adventurer admin xp Ada 300");
            ada.expectTitle("Level 2");
            ada.expectChat("skill point(s) to spend");

            // --- the new point: Toughness's next rank waits for level 3, Hearty Meals does not ----------------------
            ada.runAndOpen("adventurer", "Vitality");
            assertThat(lore(ada, "Vitality")).contains("Skill points to spend: 1 of 2");
            ada.click("Toughness");
            ada.expectChat("Needs adventurer level 3");
            ada.click("Hearty Meals");
            ada.expectChat("Hearty Meals rank 1 learnt");
            Await.until("nothing left to spend", WAIT,
                    () -> lore(ada, "Vitality").contains("Skill points to spend: 0 of 2"));

            // --- a choice: Second Wind shuts Stoneskin ---------------------------------------------------------------
            server.console("adventurer admin level Ada 10");
            ada.expectTitle("Level 10");
            Await.until("level 10 on the page", WAIT, () -> {
                ada.runAndOpen("adventurer", "Vitality");
                return lore(ada, "Vitality").contains("Adventurer level 10");
            });
            ada.click("Mending");
            ada.expectChat("Mending rank 1 learnt");
            ada.click("Second Wind");
            ada.expectChat("Second Wind rank 1 learnt");
            Await.until("Stoneskin shut", WAIT,
                    () -> lore(ada, "Stoneskin").contains("Shut: you took the other side"));
            ada.click("Stoneskin");
            ada.expectChat("Shut");

            // --- the other branches, a page each --------------------------------------------------------------------
            for (String branch : List.of("Might", "Marksman", "Shadow", "Arcana")) {
                ada.click("Next page");
                ada.awaitWindow(branch);
            }

            // --- shift-click the middle button: everything unlearnt, every point back --------------------------------
            ada.runAndOpen("adventurer", "Vitality");
            ada.shiftClickSlot(ada.window().flatMap(window -> window.slotNamed("Vitality")).orElseThrow());
            ada.awaitWindow("Unlearn");
            ada.click("Yes, do it");
            ada.expectChat("Your skill tree is unlearnt");
            Await.until("Toughness's heart is gone again", WAIT, () -> maxHealth(server, "Ada") == 20.0);
            Await.until("every point back", WAIT, () -> {
                ada.runAndOpen("adventurer", "Vitality");
                return lore(ada, "Vitality").contains("Skill points to spend: 10 of 10")
                        && named(ada, "Toughness").contains("0/3");
            });
            ada.click("Toughness");
            ada.expectChat("Toughness rank 1 learnt");
            Await.until("unlearning again waits a day", WAIT,
                    () -> lore(ada, "Vitality").contains("Unlearn again in"));

            // --- staff: experience, and a reset back to level 1 --------------------------------------------------
            ada.closeWindow();
            server.console("adventurer admin reset Ada");
            ada.expectChat("Your adventurer was reset by staff");
            server.console("adventurer admin xp Ada 900");
            ada.expectTitle("Level 3");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }

    @Test
    @DisplayName("Dual Wielder: a sword in the off hand strikes too, for half; Great Weapon Master is shut")
    void dualWielder() {
        try (Server server = Server.start("adventurers-dual", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            arena(server);
            server.console("gamerule natural_health_regeneration false");
            Bot bo = server.player("Bo");
            server.console("gamemode survival Bo");
            server.console("clear Bo");
            server.console("adventurer admin level Bo 12");
            bo.expectTitle("Level 12");

            // --- without the feat, the off hand does nothing ------------------------------------------------------
            server.console("item replace entity Bo weapon.mainhand with diamond_sword");
            server.console("item replace entity Bo weapon.offhand with diamond_sword");
            double untrained = hit(server, bo, mob(server, "zombie", 0.5, 0.5, STURDY), 0.5, 0.5);
            assertThat(untrained).as("a diamond sword, no feat").isCloseTo(7.0, within(0.2));

            // --- Brawler, Crusher, then Dual Wielder on the Might page ---------------------------------------------
            bo.runAndOpen("adventurer", "Vitality");
            bo.click("Next page");
            bo.awaitWindow("Might");
            assertThat(lore(bo, "Dual Wielder")).contains("Needs a node next to it first.");
            bo.click("Brawler");
            bo.expectChat("Brawler rank 1 learnt");
            bo.click("Crusher");
            bo.expectChat("Crusher rank 1 learnt");
            bo.click("Dual Wielder");
            bo.expectChat("Dual Wielder rank 1 learnt");
            Await.until("Great Weapon Master shut", WAIT,
                    () -> lore(bo, "Great Weapon Master").contains("Shut: you took the other side"));
            bo.closeWindow();

            // --- one sword: Brawler's 4 %; two: the off hand follows for half its 7 ------------------------------------
            server.console("item replace entity Bo weapon.offhand with air");
            double one = hit(server, bo, mob(server, "zombie", 6.5, 0.5, STURDY), 6.5, 0.5);
            assertThat(one).as("Brawler I on a diamond sword").isCloseTo(7.0 * 1.04, within(0.2));
            server.console("item replace entity Bo weapon.offhand with diamond_sword");
            UUID both = mob(server, "zombie", 12.5, 0.5, STURDY);
            hit(server, bo, both, 12.5, 0.5);
            Await.ticks(20);
            double total = 200 - health(server, both);
            assertThat(total - one).as("the off hand's strike, " + total + " in all").isBetween(3.3, 4.0);

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }

    @Test
    @DisplayName("a potion mends, a scroll and a wand strike, an amulet adds hearts, a kill drops loot")
    void items() {
        try (Server server = Server.start("adventurers-items", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of(), java.util.Map.of("plugins/RainsAdventurers/config.yml", "loot-percent: 1000\n"),
                List.of("Adventurers are up"))) {
            arena(server);
            server.console("gamerule natural_health_regeneration false");
            Bot dee = server.player("Dee");
            server.console("gamemode survival Dee");
            server.console("tp Dee 0.5 " + Y + " 0.5 0 0");
            server.console("clear Dee");

            // --- a healing potion, drunk ----------------------------------------------------------------------------
            server.console("adventurer admin give Dee potion_of_supreme_healing");
            dee.expectItem("a potion", item -> item.name().contains("Supreme Healing"));
            server.console("damage Dee 15 minecraft:generic");
            Await.until("hurt", WAIT, () -> dee.health() <= 5.5f);
            float hurt = dee.health();
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Supreme Healing"))).useHeld();
            dee.expectChat("The potion mends you");
            Await.until("the potion healed Dee", WAIT, () -> dee.health() > hurt + 5);

            // --- a scroll, and a wand, at a zombie --------------------------------------------------------------------
            UUID target = mob(server, "zombie", 0.5, 6.5, STURDY);
            Await.ticks(10);
            server.console("tp Dee 0.5 " + Y + " 0.5 0 10");
            dee.look(0, 10);
            server.console("adventurer admin give Dee spell_scroll magic_missile 3");
            dee.expectItem("a scroll", item -> item.name().contains("Magic Missile"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Magic Missile"))).useHeld();
            dee.expectChat("crumbles to dust");
            Await.until("the darts hurt it", WAIT, () -> health(server, target) < 200);
            double afterScroll = health(server, target);
            Await.ticks(30);
            server.console("adventurer admin give Dee wand_of_magic_missiles");
            dee.expectItem("a wand", item -> item.name().contains("Wand of Magic Missiles"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Wand"))).useHeld();
            Await.until("the wand spent a charge", WAIT, () -> dee.items().stream().filter(Objects::nonNull)
                    .anyMatch(item -> item.name().contains("Wand") && item.lore().stream()
                            .anyMatch(line -> line.contains("Charges: 6 of 7"))));
            Await.until("the wand's darts hurt it too", WAIT, () -> health(server, target) < afterScroll);
            server.console("kill " + target);

            // --- an Amulet of Health in the off hand: two hearts ----------------------------------------------------
            server.console("adventurer admin give Dee amulet_of_health");
            dee.expectItem("an amulet", item -> item.name().contains("Amulet of Health"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Amulet")));
            swapHands(dee);
            Await.until("two hearts more", WAIT, () -> maxHealth(server, "Dee") == 24.0);
            swapHands(dee);
            Await.until("and gone again in the main hand", WAIT, () -> maxHealth(server, "Dee") == 20.0);

            // --- a monster struck down by an adventurer drops a magic item ------------------------------------------
            server.console("clear Dee");
            server.console("item replace entity Dee weapon.mainhand with diamond_sword");
            Await.until("the zombie falls and drops loot", Duration.ofSeconds(60), () -> {
                UUID weak = mob(server, "zombie", 6.5, 6.5, ",Health:1f");
                hit(server, dee, weak, 6.5, 6.5);
                Await.ticks(10);
                return dee.chatText().stream().anyMatch(line -> line.contains("Loot!"));
            });

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }
}
