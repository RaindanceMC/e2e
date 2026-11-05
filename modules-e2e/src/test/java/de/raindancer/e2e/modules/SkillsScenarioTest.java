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
 * RainsSkills on a real server: {@code /skills} lists every skill (fishing's too, when RainsFishing runs); a skill
 * point every adventurer level, spent in any skill's tree — Toughness's heart, a choice that shuts its other side,
 * a tree wider and taller than the window scrolled with the arrows, a whole branch taken to its mastery badge, one
 * skill unlearnt — experience from mining ore the world made, a blueprint saved and pasted from the inventory,
 * Dual Wielder's off hand striking for half, and the magic items: a potion, a scroll, a wand, an amulet, loot.
 */
@Tag("e2e")
class SkillsScenarioTest {

    private static final String PLUGIN = "skills-standalone:RainsSkills-.*";
    private static final String UP = "Skills are up";
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

    private static boolean blockIs(Server server, int x, int y, int z, String block) {
        return server.console("execute if block " + x + " " + y + " " + z + " minecraft:" + block).contains("passed");
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

    private static boolean shows(Bot bot, String text) {
        return bot.window().flatMap(window -> window.slotNamed(text)).isPresent();
    }

    private static int count(Bot bot, String material) {
        return bot.items().stream().filter(Objects::nonNull).filter(item -> item.is(material))
                .mapToInt(Bot.Item::amount).sum();
    }

    /** Takes the next rank of a node on the page shown, and waits for the tree to say so. */
    private static void learn(Bot bot, String node, int rank) {
        bot.click(node);
        bot.expectChat(node + " rank " + rank + " learnt");
        Await.until(node + " at rank " + rank + " on the page", WAIT, () -> named(bot, node).contains(rank + "/")
                || (named(bot, node).contains(node) && !named(bot, node).contains("/")));
        // The page is drawn again after a rank; a click before it lands would hit the old one.
        Await.ticks(10);
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
    @DisplayName("a point every level; Toughness's heart, a choice shuts its other side, Defense unlearnt")
    void tree() {
        try (Server server = Server.start("skills", List.of(PLUGIN), List.of(UP))) {
            arena(server);
            Bot ada = server.player("Ada");
            server.console("gamemode survival Ada");
            server.console("tp Ada 0.5 " + Y + " 0.5 180 0");
            server.console("clear Ada");

            // --- /skills lists every skill; Defense's tree, at level 1: one point ---------------------------------------
            ada.runAndOpen("skills", "Skills");
            for (String skill : List.of("Defense", "Melee", "Mining", "Farming", "Building", "Exploration")) {
                assertThat(shows(ada, skill)).as(skill + " in /skills").isTrue();
            }
            assertThat(lore(ada, "Defense")).contains("Level 1").contains("1 point to spend").contains("Mastery 0 of 3");
            ada.click("Defense");
            ada.awaitWindow("Defense");
            assertThat(lore(ada, "Defense")).contains("Adventurer level 1").contains("Skill points to spend: 1 of 1");
            assertThat(lore(ada, "Hearty Meals")).contains("Needs a node next to it first.");
            assertThat(maxHealth(server, "Ada")).isEqualTo(20.0);
            learn(ada, "Toughness", 1);
            Await.until("a heart more", WAIT, () -> maxHealth(server, "Ada") == 22.0);
            ada.click("Hearty Meals");
            ada.expectChat("Not enough skill points");
            ada.closeWindow();

            // --- 300 experience: level 2, and a second point -----------------------------------------------------
            server.console("skills admin xp Ada 300");
            ada.expectTitle("Level 2");
            ada.expectChat("skill point(s) to spend");

            // --- the new point: Toughness's next rank waits for level 3, Hearty Meals does not ----------------------
            ada.runAndOpen("skills defense", "Defense");
            assertThat(lore(ada, "Defense")).contains("Skill points to spend: 1 of 2");
            ada.click("Toughness");
            ada.expectChat("Needs adventurer level 3");
            learn(ada, "Hearty Meals", 1);
            Await.until("nothing left to spend", WAIT,
                    () -> lore(ada, "Defense").contains("Skill points to spend: 0 of 2"));

            // --- a choice: Second Wind shuts Stoneskin ---------------------------------------------------------------
            server.console("skills admin level Ada 10");
            ada.expectTitle("Level 10");
            Await.until("level 10 on the page", WAIT, () -> {
                ada.runAndOpen("skills defense", "Defense");
                return lore(ada, "Defense").contains("Adventurer level 10");
            });
            learn(ada, "Mending", 1);
            learn(ada, "Second Wind", 1);
            Await.until("Stoneskin shut", WAIT, () -> lore(ada, "Stoneskin").contains("Shut: you took the other side"));
            ada.click("Stoneskin");
            ada.expectChat("Shut");

            // --- shift-click the middle button: Defense unlearnt, every point back -----------------------------------
            ada.shiftClickSlot(ada.window().flatMap(window -> window.slotNamed("Defense")).orElseThrow());
            ada.awaitWindow("Unlearn");
            ada.click("Yes, do it");
            ada.expectChat("Defense is unlearnt");
            Await.until("Toughness's heart is gone again", WAIT, () -> maxHealth(server, "Ada") == 20.0);
            Await.until("every point back", WAIT, () -> {
                ada.runAndOpen("skills defense", "Defense");
                return lore(ada, "Defense").contains("Skill points to spend: 10 of 10")
                        && named(ada, "Toughness").contains("0/3");
            });
            learn(ada, "Toughness", 1);
            Await.until("unlearning again waits", WAIT, () -> lore(ada, "Defense").contains("Unlearn again in"));

            // --- staff: experience, and a reset back to level 1 --------------------------------------------------
            ada.closeWindow();
            server.console("skills admin reset Ada");
            ada.expectChat("Your adventurer was reset by staff");
            server.console("skills admin xp Ada 900");
            ada.expectTitle("Level 3");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }

    @Test
    @DisplayName("the tree scrolls past the window both ways; a whole branch taken opens its mastery badge")
    void mastery() {
        try (Server server = Server.start("skills-mastery", List.of(PLUGIN), List.of(UP))) {
            arena(server);
            Bot ida = server.player("Ida");
            server.console("gamemode survival Ida");
            server.console("tp Ida 0.5 " + Y + " 0.5 180 0");
            server.console("skills admin level Ida 40");
            ida.expectTitle("Level 40");

            // --- the badge is a column past the window, the other branches rows below it ---------------------------
            ida.runAndOpen("skills defense", "Defense");
            assertThat(shows(ida, "Defender's Mastery")).as("off the window to the right").isFalse();
            assertThat(lore(ida, "Right ▶")).contains("1 more column");
            assertThat(lore(ida, "▼ Down")).contains("more row");
            ida.click("▼ Down");
            Await.until("one row down: the top row gone", WAIT, () -> !shows(ida, "Hearty Meals") && shows(ida, "▲ Up"));
            ida.click("▲ Up");
            Await.until("back at the top", WAIT, () -> shows(ida, "Hearty Meals"));
            Await.ticks(10);

            // --- everything of Vitality but its last node, and Stoneskin, which Second Wind shuts --------------------
            String[][] take = {{"Toughness", "3"}, {"Hearty Meals", "3"}, {"Thick Skin", "2"}, {"Mending", "3"},
                    {"Stout", "2"}, {"Hardened", "3"}, {"Big Heart", "3"}, {"Second Wind", "1"}, {"Fast Healer", "2"},
                    {"Giant's Blood", "3"}, {"Iron Hide", "2"}, {"Bottomless Stomach", "1"}, {"Undying", "1"}};
            for (String[] node : take) {
                for (int rank = 1; rank <= Integer.parseInt(node[1]); rank++) {
                    learn(ida, node[0], rank);
                }
            }
            ida.click("Right ▶");
            Await.until("the badge in view", WAIT, () -> shows(ida, "Defender's Mastery"));
            Await.ticks(10);
            assertThat(lore(ida, "Defender's Mastery")).contains("Finish every other node of the branch first.");
            ida.click("Defender's Mastery");
            ida.expectChat("Finish every other node");

            // --- the last node, then the badge: the branch is mastered -------------------------------------------
            ida.click("◀ Left");
            Await.until("back at the left edge", WAIT, () -> !shows(ida, "Defender's Mastery"));
            Await.ticks(10);
            learn(ida, "Bulwark", 1);
            ida.click("Right ▶");
            Await.until("the badge in view again", WAIT, () -> shows(ida, "Defender's Mastery"));
            Await.ticks(10);
            assertThat(lore(ida, "Defender's Mastery")).contains("free").contains("Click to learn");
            ida.click("Defender's Mastery");
            ida.expectChat("you have mastered a branch of Defense");
            Await.until("one branch mastered", WAIT, () -> lore(ida, "Defense").contains("Mastered: 1 of 3"));
            ida.closeWindow();
            ida.runAndOpen("skills", "Skills");
            Await.until("/skills says so too", WAIT, () -> lore(ida, "Defense").contains("Mastery 1 of 3"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }

    @Test
    @DisplayName("with RainsFishing, fishing is one of the skills in /skills, and its tree opens from there")
    void fishingIsASkill() {
        try (Server server = Server.start("skills-fishing", List.of(PLUGIN, "fishing-standalone:RainsFishing-.*"),
                List.of(UP))) {
            arena(server);
            Bot fin = server.player("Fin");
            fin.runAndOpen("skills", "Skills");
            Await.until("Fishing listed", WAIT, () -> shows(fin, "Fishing"));
            assertThat(lore(fin, "Fishing")).contains("Mastery 0 of 5");
            fin.click("Fishing");
            fin.awaitWindow("Tidecaller");
            assertThat(lore(fin, "Tidecaller")).contains("Branches mastered: 0 of 5");
            fin.click("Back");
            fin.awaitWindow("Skills");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills", "RainsFishing")).isEmpty();
        }
    }

    @Test
    @DisplayName("ore the world made, mined: experience enough for a level — no monster needed")
    void miningEarns() {
        try (Server server = Server.start("skills-mining", List.of(PLUGIN), List.of(UP))) {
            arena(server);
            Bot mo = server.player("Mo");
            server.console("gamemode survival Mo");
            server.console("tp Mo 0.5 " + Y + " 0.5 180 0");
            server.console("clear Mo");
            server.console("item replace entity Mo weapon.mainhand with netherite_pickaxe[enchantments={efficiency:5}]");
            // Haste far past a beacon's, so ore breaks at the first touch and the bot's one dig packet is the whole break.
            server.console("effect give Mo haste infinite 20 true");
            Await.until("Level 2 from ore alone", Duration.ofSeconds(90), () -> {
                for (int x = -4; x <= 4; x += 2) {
                    server.console("setblock " + x + " " + (Y - 1) + " -3 minecraft:diamond_ore");
                }
                Await.ticks(10);
                for (int x = -4; x <= 4; x += 2) {
                    server.console("tp Mo " + (x + 0.5) + " " + Y + " -1.5 180 60");
                    Await.ticks(5);
                    mo.dig(x, Y - 1, -3);
                    Await.ticks(5);
                }
                return mo.chatText().stream().anyMatch(line -> line.contains("Level 2"));
            });
            assertThat(blockIs(server, -4, Y - 1, -3, "air")).as("the ore was really mined").isTrue();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }

    @Test
    @DisplayName("a blueprint: marked, saved, pasted elsewhere from the inventory, and taken down again")
    void blueprint() {
        try (Server server = Server.start("skills-blueprint", List.of(PLUGIN), List.of(UP))) {
            arena(server);
            Bot bea = server.player("Bea");
            server.console("gamemode survival Bea");
            server.console("clear Bea");
            server.console("skills admin level Bea 10");
            bea.expectTitle("Level 10");

            // --- Draftsman, on Building's Architect branch (the middle button turns to it) --------------------------
            bea.run("skills blueprint mark");
            bea.expectChat("Blueprints come with the Architect's Draftsman node");
            bea.runAndOpen("skills building", "Building");
            bea.click("Building");
            Await.until("the Architect branch in view", WAIT, () -> shows(bea, "Draftsman"));
            bea.click("Draftsman");
            bea.expectChat("Draftsman rank 1 learnt");
            bea.closeWindow();

            // --- a little hut of three by two by three, marked corner to corner and saved ---------------------------
            server.console("fill 4 " + Y + " 4 6 " + (Y + 1) + " 6 minecraft:oak_planks");
            server.console("fill 5 " + Y + " 5 5 " + (Y + 1) + " 5 minecraft:air");
            server.console("tp Bea 5.5 " + Y + " 1.5 0 0");
            bea.run("skills blueprint mark");
            bea.expectChat("Mark a box");
            bea.hold(bea.hotbarSlotOf(item -> item.name().contains("Blueprint marker")));
            bea.useOn(4, Y, 4);
            bea.expectChat("First corner set");
            bea.useOn(6, Y + 1, 6);
            bea.expectChat("Box marked: 3 × 2 × 3");
            bea.run("skills blueprint save hut");
            bea.expectChat("Blueprint hut saved");

            // --- pasted twelve blocks along, every plank out of the inventory ---------------------------------------
            server.console("give Bea oak_planks 64");
            Await.until("planks in hand", WAIT, () -> count(bea, "oak_planks") == 64);
            server.console("tp Bea 5.5 " + Y + " 13.5 0 0");
            Await.ticks(20);
            bea.run("skills blueprint paste hut");
            bea.expectChat("hut is built");
            Await.until("the hut stands again, twelve blocks on", WAIT,
                    () -> blockIs(server, 4, Y, 16, "oak_planks") && blockIs(server, 6, Y + 1, 18, "oak_planks"));
            assertThat(blockIs(server, 5, Y, 17, "air")).as("its hollow middle kept").isTrue();
            assertThat(count(bea, "oak_planks")).as("16 planks paid").isEqualTo(64 - 16);

            // --- undo: down again, and every plank back -----------------------------------------------------------
            bea.run("skills blueprint undo");
            bea.expectChat("hut taken down");
            Await.until("gone again", WAIT, () -> blockIs(server, 4, Y, 16, "air"));
            Await.until("every plank back", WAIT, () -> count(bea, "oak_planks") == 64);

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }

    @Test
    @DisplayName("Dual Wielder: a sword in the off hand strikes too, for half; Great Weapon Master is shut")
    void dualWielder() {
        try (Server server = Server.start("skills-dual", List.of(PLUGIN), List.of(UP))) {
            arena(server);
            server.console("gamerule natural_health_regeneration false");
            Bot bo = server.player("Bo");
            server.console("gamemode survival Bo");
            server.console("clear Bo");
            server.console("skills admin level Bo 12");
            bo.expectTitle("Level 12");

            // --- without the feat, the off hand does nothing ------------------------------------------------------
            server.console("item replace entity Bo weapon.mainhand with diamond_sword");
            server.console("item replace entity Bo weapon.offhand with diamond_sword");
            double untrained = hit(server, bo, mob(server, "zombie", 0.5, 0.5, STURDY), 0.5, 0.5);
            assertThat(untrained).as("a diamond sword, no feat").isCloseTo(7.0, within(0.2));

            // --- Brawler, Crusher, then Dual Wielder in Melee's tree ------------------------------------------------
            bo.runAndOpen("skills melee", "Melee");
            assertThat(lore(bo, "Dual Wielder")).contains("Needs a node next to it first.");
            learn(bo, "Brawler", 1);
            learn(bo, "Crusher", 1);
            learn(bo, "Dual Wielder", 1);
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

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }

    @Test
    @DisplayName("a potion mends, a scroll and a wand strike, an amulet adds hearts, a kill drops loot")
    void items() {
        try (Server server = Server.start("skills-items", List.of(PLUGIN),
                List.of(), java.util.Map.of("plugins/RainsSkills/config.yml", "loot-percent: 1000\n"),
                List.of(UP))) {
            arena(server);
            server.console("gamerule natural_health_regeneration false");
            Bot dee = server.player("Dee");
            server.console("gamemode survival Dee");
            server.console("tp Dee 0.5 " + Y + " 0.5 0 0");
            server.console("clear Dee");

            // --- a healing potion, drunk ----------------------------------------------------------------------------
            server.console("skills admin give Dee potion_of_supreme_healing");
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
            server.console("skills admin give Dee spell_scroll magic_missile 3");
            dee.expectItem("a scroll", item -> item.name().contains("Magic Missile"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Magic Missile"))).useHeld();
            dee.expectChat("crumbles to dust");
            Await.until("the darts hurt it", WAIT, () -> health(server, target) < 200);
            double afterScroll = health(server, target);
            Await.ticks(30);
            server.console("skills admin give Dee wand_of_magic_missiles");
            dee.expectItem("a wand", item -> item.name().contains("Wand of Magic Missiles"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Wand"))).useHeld();
            Await.until("the wand spent a charge", WAIT, () -> dee.items().stream().filter(Objects::nonNull)
                    .anyMatch(item -> item.name().contains("Wand") && item.lore().stream()
                            .anyMatch(line -> line.contains("Charges: 6 of 7"))));
            Await.until("the wand's darts hurt it too", WAIT, () -> health(server, target) < afterScroll);
            server.console("kill " + target);

            // --- an Amulet of Health in the off hand: two hearts ----------------------------------------------------
            server.console("skills admin give Dee amulet_of_health");
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

            assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
        }
    }
}
