package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsAnimals on a real server: horse and dog armour take trims at the smithing table; horse armour takes hits as
 * wolf armour does and wears; the game's own Protection, Feather Falling and Thorns work on animal armour, Respiration
 * from the plugin; the whistle brings the horse its owner last rode. Every number read back from the server.
 */
@Tag("e2e")
class AnimalsScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final int Y = 100;
    private static final int RESULT = 3;

    private static String armour(String id, String enchants) {
        String comps = enchants.isEmpty() ? "" : ",components:{\"minecraft:enchantments\":{" + enchants + "}}";
        return "equipment:{body:{id:\"minecraft:" + id + "\",count:1" + comps + "}}";
    }

    private static String owner(UUID id) {
        long most = id.getMostSignificantBits();
        long least = id.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }

    private static int wear(Server server, String tag) {
        String said = server.console("data get entity @e[tag=" + tag + ",limit=1] equipment.body.components.\"minecraft:damage\"");
        return Integer.parseInt(said.replaceAll(".*: ", "").trim());
    }

    private static void smith(Bot ada, String template, String base, String material) {
        ada.useOn(2, Y, 0);
        ada.awaitWindow("container.upgrade");
        Await.ticks(10);
        for (String item : List.of(template, base, material)) {
            int slot = Await.value("Ada's " + item + " below the smithing table", WAIT, () -> ada.window()
                    .flatMap(window -> window.items().entrySet().stream()
                            .filter(entry -> entry.getKey() > RESULT && entry.getValue().is(item))
                            .map(Map.Entry::getKey).findFirst())
                    .orElse(null));
            ada.shiftClickSlot(slot);
            Await.ticks(5);
        }
        Await.until("the table offers trimmed " + base, WAIT, () -> ada.window()
                .map(window -> window.items().get(RESULT)).map(result -> result.is(base)).orElse(false));
        ada.shiftClickSlot(RESULT);
        Await.ticks(10);
        ada.closeWindow();
    }

    @Test
    @DisplayName("animal armour trims, wears, is enchanted to some effect, and the whistle brings the horse")
    void animals() {
        try (Server server = Server.start("animals", List.of("animals-standalone:RainsAnimals-.*"),
                List.of("Horses are up"))) {
            assertThat(server.log()).contains("Horses are up: 18 trim recipes take horse armour, 18 dog armour");
            server.console("forceload add -64 -16 64 16");
            server.console("fill -60 " + (Y - 3) + " -10 60 " + (Y - 1) + " 10 minecraft:stone");
            server.console("fill -60 " + Y + " -10 60 " + (Y + 4) + " 10 minecraft:air");
            server.console("setblock 2 " + Y + " 0 minecraft:smithing_table");
            Bot ada = server.admin("Ada");
            server.console("tp Ada 0.5 " + Y + " 0.5");
            server.console("gamemode survival Ada");

            // ---- horse and dog armour take a trim at the smithing table
            server.console("clear Ada");
            server.console("give Ada minecraft:sentry_armor_trim_smithing_template");
            server.console("give Ada minecraft:iron_horse_armor");
            server.console("give Ada minecraft:gold_ingot");
            Await.ticks(20);
            smith(ada, "sentry_armor_trim_smithing_template", "iron_horse_armor", "gold_ingot");
            Await.until("Ada has iron horse armour with a sentry trim in gold", WAIT, () -> server.console(
                    "execute if items entity Ada container.* minecraft:iron_horse_armor[minecraft:trim="
                            + "{pattern:'minecraft:sentry',material:'minecraft:gold'}]").contains("passed"));
            server.console("clear Ada");
            server.console("give Ada minecraft:bolt_armor_trim_smithing_template");
            server.console("give Ada minecraft:wolf_armor");
            server.console("give Ada minecraft:diamond");
            Await.ticks(20);
            smith(ada, "bolt_armor_trim_smithing_template", "wolf_armor", "diamond");
            Await.until("Ada has dog armour with a bolt trim in diamond", WAIT, () -> server.console(
                    "execute if items entity Ada container.* minecraft:wolf_armor[minecraft:trim="
                            + "{pattern:'minecraft:bolt',material:'minecraft:diamond'}]").contains("passed"));

            // ---- enchantable: animal armour is in the armour, boots and helmet tags
            server.console("summon minecraft:horse -5 " + Y + " 6 {Tags:[\"plain\"],Tame:1b,NoAI:1b," + armour("iron_horse_armor", "") + "}");
            for (String tag : List.of("enchantable/armor", "enchantable/foot_armor", "enchantable/head_armor", "enchantable/durability")) {
                assertThat(server.console("execute if items entity @e[tag=plain,limit=1] armor.body #minecraft:" + tag))
                        .as(tag).contains("passed");
            }

            // ---- wear: horse armour takes the hit as wolf armour does; the enchantments do their part
            String[][] horses = {{"prot", "\"minecraft:protection\":4"}, {"feather", "\"minecraft:feather_falling\":4"},
                    {"plainfall", ""}, {"thorns", "\"minecraft:thorns\":3"}, {"breath", "\"minecraft:respiration\":3"}};
            for (int i = 0; i < horses.length; i++) {
                server.console("summon minecraft:horse " + (i * 2 - 3) + " " + Y + " 6 {Tags:[\"" + horses[i][0] + "\"],Tame:1b,NoAI:1b,"
                        + armour("iron_horse_armor", horses[i][1]) + "}");
            }
            server.console("summon minecraft:wolf -3 " + Y + " -6 {Tags:[\"dogplain\"],NoAI:1b," + armour("wolf_armor", "") + "}");
            server.console("summon minecraft:wolf 3 " + Y + " -6 {Tags:[\"dogprot\"],NoAI:1b," + armour("wolf_armor", "\"minecraft:protection\":4") + "}");
            server.console("summon minecraft:zombie 0 " + Y + " 9 {Tags:[\"z\"],NoAI:1b,PersistenceRequired:1b}");
            Await.ticks(60);
            String health = server.console("data get entity @e[tag=plain,limit=1] Health");
            for (String tag : List.of("plain", "prot", "thorns")) {
                server.console("damage @e[tag=" + tag + ",limit=1] 10 minecraft:mob_attack" + (tag.equals("thorns") ? " by @e[tag=z,limit=1]" : ""));
            }
            server.console("damage @e[tag=feather,limit=1] 10 minecraft:fall");
            server.console("damage @e[tag=plainfall,limit=1] 10 minecraft:fall");
            server.console("damage @e[tag=dogplain,limit=1] 10 minecraft:mob_attack");
            server.console("damage @e[tag=dogprot,limit=1] 10 minecraft:mob_attack");
            Await.ticks(20);
            assertThat(server.console("data get entity @e[tag=plain,limit=1] Health")).as("the horse took nothing").isEqualTo(health);
            assertThat(wear(server, "plain")).as("10 damage, iron horse armour").isEqualTo(10);
            assertThat(wear(server, "prot")).as("Protection IV: 16 % less").isEqualTo(9);
            assertThat(wear(server, "plainfall")).isEqualTo(10);
            assertThat(wear(server, "feather")).as("Feather Falling IV: 48 % less of a fall").isEqualTo(6);
            assertThat(wear(server, "dogprot")).as("dog armour with Protection IV").isLessThan(wear(server, "dogplain"));
            assertThat(server.console("data get entity @e[tag=z,limit=1] Health")).as("Thorns hit back").doesNotContain("20.0");
            assertThat(server.console("attribute @e[tag=breath,limit=1] minecraft:oxygen_bonus get")).contains("3.0");

            // ---- the whistle: ride the horse once, walk off, whistle — it comes
            server.console("summon minecraft:horse 5 " + Y + " 0 {Tags:[\"steed\"],Tame:1b,Owner:" + owner(ada.id()) + "}");
            Await.ticks(10);
            server.console("ride Ada mount @e[tag=steed,limit=1]");
            Await.ticks(10);
            server.console("ride Ada dismount");
            Await.ticks(10);
            server.console("tp Ada 40.5 " + Y + " 0.5");
            server.console("clear Ada");
            server.console("give Ada animals:whistle");
            Await.ticks(10);
            ada.hold(0);
            ada.useHeld();
            Await.until("the horse came to Ada", WAIT, () -> server.console(
                    "execute if entity @e[tag=steed,x=40,y=" + Y + ",z=0,distance=..4]").contains("passed"));

            // ---- stats: each player switches them for themselves
            ada.runAndExpect("horses stats", "off");
            ada.runAndExpect("horses stats", "on");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsAnimals"))
                    .filteredOn(line -> !line.contains("resource pack server could not start")).isEmpty();
        }
    }
}
