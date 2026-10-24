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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsAdventurers on a real server: a wizard chosen from the menu, the casting stance taken up with F, a cantrip and
 * a 1st-level spell cast at a zombie, the real hotbar back on F, after leaving mid-stance and on dying mid-stance —
 * and a short rest broken by a hit.
 */
@Tag("e2e")
class AdventurersScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static void swapHands(Bot bot) {
        bot.send(new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0));
    }

    private static boolean hotbarHas(Bot bot, int slot, String name) {
        Bot.Item item = bot.hotbar().get(slot);
        return item != null && item.name().contains(name);
    }

    private static boolean noIcons(Bot bot) {
        return bot.items().stream().filter(Objects::nonNull).noneMatch(item -> item.tag("icon").isPresent());
    }

    private static boolean zombieHurt(Server server) {
        if (!server.console("execute if entity @e[type=minecraft:zombie,tag=dummy]").contains("passed")) {
            return true;
        }
        return server.console("execute if entity @e[type=minecraft:zombie,tag=dummy,nbt={Health:20.0f}]").contains("failed");
    }

    @Test
    @DisplayName("a wizard chosen, a stance taken, spells cast, the hotbar kept through leaving and dying, a rest broken")
    void wizard() {
        try (Server server = Server.start("adventurers", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            Bot ada = server.player("Ada");
            server.console("forceload add -16 -16 16 16");
            server.console("fill -8 63 -8 16 63 16 minecraft:stone");
            server.console("fill -8 64 -8 16 70 16 minecraft:air");
            server.console("gamerule doImmediateRespawn true");
            server.console("gamerule doMobSpawning false");
            server.console("kill @e[type=!minecraft:player]");
            server.console("time set day");
            server.console("gamemode survival Ada");
            server.console("tp Ada 0.5 64 0.5 0 0");
            server.console("clear Ada");
            server.console("give Ada minecraft:diamond_sword");
            server.console("give Ada minecraft:dirt 5");
            ada.expectItem("a sword", item -> item.is("diamond_sword"));

            // --- a class, from the menu -------------------------------------------------------------------------
            ada.runAndOpen("adventurer", "Choose");
            ada.click("Wizard");
            ada.expectChat("You are a Wizard now");
            ada.awaitWindow("Character");
            ada.closeWindow();

            // --- F: the hotbar becomes spells, and the sword is set aside ----------------------------------------
            ada.hold(0);
            swapHands(ada);
            Await.until("Fire Bolt sits in slot 1", WAIT, () -> hotbarHas(ada, 0, "Fire Bolt"));
            assertThat(hotbarHas(ada, 3, "Magic Missile")).isTrue();
            assertThat(ada.items().stream().filter(Objects::nonNull).noneMatch(item -> item.is("diamond_sword")))
                    .as("the sword is set aside while in the stance").isTrue();

            // --- a cantrip at a zombie: attack rolls until one lands ---------------------------------------------
            server.console("summon minecraft:zombie 0.5 64 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"dummy\"]}");
            Await.ticks(10);
            ada.look(0, 12);
            Await.until(() -> "a Fire Bolt lands on the zombie (chat " + ada.chatText() + ", bars " + ada.actionBars()
                    + ")", Duration.ofSeconds(60), () -> {
                ada.hold(0).useHeld();
                Await.ticks(30);
                return zombieHurt(server);
            });
            ada.expectActionBar("Fire Bolt");

            // --- a 1st-level spell never misses, and spends a slot ------------------------------------------------
            server.console("kill @e[type=minecraft:zombie,tag=dummy]");
            server.console("summon minecraft:zombie 0.5 64 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"dummy\"]}");
            Await.ticks(10);
            assertThat(ada.hotbar().get(3).amount()).as("two 1st-level slots at level 1").isEqualTo(2);
            ada.hold(3).useHeld();
            ada.expectActionBar("Magic Missile");
            Await.until("a slot is spent", WAIT, () -> ada.hotbar().get(3).amount() == 1);
            Await.until("the darts hurt it", WAIT, () -> zombieHurt(server));

            // --- F again: the sword is back where it was --------------------------------------------------------
            swapHands(ada);
            Await.until("the sword is back in slot 1", WAIT,
                    () -> ada.hotbar().get(0) != null && ada.hotbar().get(0).is("diamond_sword"));
            assertThat(ada.hotbar().get(1).is("dirt")).isTrue();
            assertThat(noIcons(ada)).isTrue();

            // --- staff levels her up -----------------------------------------------------------------------------
            server.console("adventurer admin level Ada 5");
            ada.expectTitle("Level 5");

            // --- leaving mid-stance: the hotbar is back on the next join, and so is the class -----------------------
            swapHands(ada);
            Await.until("in the stance again", WAIT, () -> hotbarHas(ada, 0, "Fire Bolt"));
            ada.leave();
            Await.ticks(40);
            Bot back = ada.rejoin();
            Await.until("the sword survived leaving", WAIT,
                    () -> back.hotbar().get(0) != null && back.hotbar().get(0).is("diamond_sword"));
            assertThat(noIcons(back)).isTrue();
            back.runAndOpen("adventurer", "Character");
            back.closeWindow();

            // --- dying mid-stance drops the real items, never an icon ------------------------------------------
            server.console("kill @e[type=minecraft:item]");
            swapHands(back);
            Await.until("in the stance before dying", WAIT, () -> hotbarHas(back, 0, "Fire Bolt"));
            server.console("kill Ada");
            Await.until("the sword lies on the ground", WAIT, () -> server.console(
                    "execute if entity @e[type=minecraft:item,nbt={Item:{id:\"minecraft:diamond_sword\"}}]")
                    .contains("passed"));
            assertThat(server.console(
                    "execute if entity @e[type=minecraft:item,nbt={Item:{id:\"minecraft:blaze_powder\"}}]"))
                    .as("no Fire Bolt icon was dropped").contains("failed");
            back.respawn();
            Await.until("alive again", WAIT, () -> !back.isDead());
            Await.ticks(20);
            assertThat(noIcons(back)).as("nothing of the stance survives a death").isTrue();

            // --- a short rest, broken by a hit ------------------------------------------------------------------
            server.console("tp Ada 0.5 64 0.5");
            back.run("adventurer rest");
            back.expectChat("short rest");
            Await.ticks(10);
            server.console("damage Ada 1 minecraft:generic");
            back.expectChat("Something hurt you");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }

    @Test
    @DisplayName("a cleric heals herself with Cure Wounds, a barbarian rages once and is told so the second time")
    void clericAndBarbarian() {
        try (Server server = Server.start("adventurers-features", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            Bot bo = server.player("Bo");
            Bot cid = server.player("Cid");
            server.console("forceload add -16 -16 16 16");
            server.console("fill -8 63 -8 16 63 16 minecraft:stone");
            server.console("fill -8 64 -8 16 70 16 minecraft:air");
            server.console("gamerule doMobSpawning false");
            server.console("gamemode survival @a");
            server.console("tp Bo 0.5 64 0.5 0 -90");
            server.console("tp Cid 8.5 64 8.5");

            bo.runAndOpen("adventurer", "Choose");
            bo.click("Cleric");
            bo.expectChat("You are a Cleric now");
            bo.closeWindow();
            swapHands(bo);
            Await.until("Cure Wounds sits in slot 2", WAIT, () -> hotbarHas(bo, 1, "Cure Wounds"));
            server.console("damage Bo 10 minecraft:generic");
            Await.until("Bo is hurt", WAIT, () -> bo.health() <= 10.5f);
            float hurt = bo.health();
            bo.look(0, -90);
            bo.hold(1).useHeld();
            bo.expectActionBar("Cure Wounds");
            Await.until("Bo healed herself", WAIT, () -> bo.health() > hurt + 2);

            cid.runAndOpen("adventurer", "Choose");
            cid.click("Barbarian");
            cid.expectChat("You are a Barbarian now");
            cid.closeWindow();
            swapHands(cid);
            Await.until("Rage sits in slot 1", WAIT, () -> hotbarHas(cid, 0, "Rage"));
            cid.hold(0).useHeld();
            cid.expectChat("You rage!");
            Await.ticks(30);
            cid.hold(0).useHeld();
            cid.expectChat("already raging");

            // --- staff hands out experience, and a new class starts again at level 1 --------------------------------
            server.console("adventurer admin xp Bo 6500");
            bo.expectTitle("Level 5");
            swapHands(bo);
            Await.until("Bo's hotbar is back", WAIT, () -> noIcons(bo));
            bo.runAndOpen("adventurer class", "Choose");
            bo.click("Fighter");
            bo.awaitWindow("Become");
            bo.click("Yes, do it");
            bo.expectChat("You are a Fighter now");
            bo.awaitWindow("Character");
            Await.until("the sheet says level 1", WAIT, () -> bo.window().map(window -> window.slotNamed(
                    "Level 1 Fighter").isPresent()).orElse(false));
            bo.closeWindow();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }

    @Test
    @DisplayName("a sorcerer chooses Wild Magic; all hundred surges run clean; potion, scroll and wand work; a kill drops loot")
    void wildMagicAndItems() {
        try (Server server = Server.start("adventurers-wild", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of(), java.util.Map.of("plugins/RainsAdventurers/config.yml", "loot-percent: 1000\n"),
                List.of("Adventurers are up"))) {
            Bot dee = server.player("Dee");
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:stone");
            server.console("fill -24 64 -24 24 80 24 minecraft:air");
            server.console("gamerule doMobSpawning false");
            server.console("gamerule doImmediateRespawn true");
            server.console("kill @e[type=!minecraft:player]");
            server.console("time set day");
            server.console("gamemode survival Dee");
            server.console("tp Dee 0.5 64 0.5 0 0");
            server.console("clear Dee");

            // --- a sorcerer, and the origin chosen from the sheet ------------------------------------------------
            dee.runAndOpen("adventurer", "Choose");
            dee.click("Sorcerer");
            dee.expectChat("You are a Sorcerer now");
            dee.awaitWindow("Character");
            dee.expectChat("origin to choose");
            dee.click("Choose your origin");
            dee.awaitWindow("Choose");
            dee.click("Wild Magic");
            dee.awaitWindow("Become");
            dee.click("Yes, do it");
            dee.expectChat("Your origin: Wild Magic");
            dee.closeWindow();

            // --- every entry of the surge table, on a real server --------------------------------------------------
            server.console("adventurer admin level Dee 20");
            dee.expectTitle("Level 20");
            for (int roll = 1; roll <= 100; roll++) {
                server.console("effect give Dee minecraft:resistance 60 4 true");
                server.console("effect give Dee minecraft:instant_health 1 5 true");
                server.console("tp Dee 0.5 64 0.5 0 0");
                server.console("adventurer admin surge Dee " + roll);
                Await.ticks(4);
            }
            Await.until("the last surge was announced", WAIT, () -> dee.chatText().stream()
                    .anyMatch(line -> line.contains("Wild Magic Surge!") && line.contains("(100)")));
            assertThat(dee.chatText().stream().filter(line -> line.contains("Wild Magic Surge!")).count())
                    .as("all hundred surges were announced").isGreaterThanOrEqualTo(100);
            server.console("effect clear Dee");
            Await.ticks(40);
            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers"))
                    .as("no surge throws on a real server").isEmpty();
            Await.until("everything the surges summoned is gone again", Duration.ofSeconds(80), () -> server
                    .console("execute if entity @e[nbt={PublicBukkitValues:{\"rains_dnd:summoned\":1b}}]")
                    .contains("failed"));

            // --- a healing potion -------------------------------------------------------------------------------
            if (!dee.isDead()) {
                server.console("kill Dee");
            }
            dee.respawn();
            Await.until("alive", WAIT, () -> !dee.isDead());
            server.console("tp Dee 0.5 64 0.5 0 0");
            server.console("clear Dee");
            server.console("adventurer admin give Dee potion_of_supreme_healing");
            dee.expectItem("a potion", item -> item.name().contains("Supreme Healing"));
            Await.ticks(80);
            float full = dee.health();
            server.console("damage Dee 20 minecraft:generic");
            Await.until("hurt", WAIT, () -> dee.health() < full - 10);
            float hurt = dee.health();
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Supreme Healing"))).useHeld();
            Await.until("the potion healed Dee", WAIT, () -> dee.health() > hurt + 5);
            dee.expectChat("The potion mends you");

            // --- a scroll and a wand at a zombie -----------------------------------------------------------------
            server.console("summon minecraft:zombie 0.5 64 6.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,"
                    + "Health:200f,attributes:[{id:\"minecraft:max_health\",base:200}],Tags:[\"dummy\"]}");
            Await.ticks(10);
            dee.look(0, 10);
            server.console("adventurer admin give Dee spell_scroll magic_missile");
            dee.expectItem("a scroll", item -> item.name().contains("Spell Scroll of Magic Missile"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Spell Scroll"))).useHeld();
            dee.expectChat("crumbles to dust");
            Await.ticks(30);
            server.console("adventurer admin give Dee wand_of_magic_missiles");
            dee.expectItem("a wand", item -> item.name().contains("Wand of Magic Missiles"));
            dee.hold(dee.hotbarSlotOf(item -> item.name().contains("Wand"))).useHeld();
            Await.until("the wand spent a charge", WAIT, () -> dee.items().stream().filter(Objects::nonNull)
                    .anyMatch(item -> item.name().contains("Wand") && item.lore().stream()
                            .anyMatch(line -> line.contains("Charges: 6 of 7"))));
            server.console("kill @e[type=minecraft:zombie,tag=dummy]");

            // --- a kill by an adventurer drops a magic item --------------------------------------------------------
            server.console("summon minecraft:iron_golem 0.5 64 5.5 {NoAI:1b,PersistenceRequired:1b,Silent:1b,"
                    + "Health:1f,PlayerCreated:0b}");
            Await.ticks(10);
            server.console("clear Dee");
            server.console("adventurer admin level Dee 1");
            dee.hold(0);
            swapHands(dee);
            Await.until("in the stance", WAIT, () -> hotbarHas(dee, 0, "Fire Bolt"));
            Await.until("the golem falls and drops loot", Duration.ofSeconds(60), () -> {
                dee.hold(0).useHeld();
                Await.ticks(30);
                return dee.chatText().stream().anyMatch(line -> line.contains("Loot!"));
            });

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
        }
    }
}
