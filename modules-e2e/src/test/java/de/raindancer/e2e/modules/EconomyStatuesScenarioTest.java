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
 * Statues of the top three: a whole podium of three on pedestals under a label, or each statue placed, moved and
 * removed on its own; each wearing the skin of whoever holds its place, the next one up when somebody overtakes
 * them. A podium placed before 0.61.0 comes back as three statues, its old pieces cleared away.
 */
@Tag("e2e")
class EconomyStatuesScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static int count(Server server, String selector) {
        String out = server.console("execute if entity " + selector);
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("(\\d+)").matcher(out);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** Statues wearing this holder: a player's id, or "-" for a place nobody holds. */
    private static String wearing(String holder) {
        return "@e[type=minecraft:mannequin,nbt={BukkitValues:{\"rainseconomy:statue_holder\":\"" + holder + "\"}}]";
    }

    private static String one(String holder) {
        return wearing(holder).replace("[type=", "[limit=1,type=");
    }

    @Test
    @DisplayName("a podium of the richest and one of the casino's winners: statues in skins, pedestals, a heading, removed whole")
    void podiums() {
        try (Server server = Server.start("economy-statues", List.of("economy-standalone:RainsEconomy-.*"),
                List.of("The economy is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("eco give Bo 500000");
            server.console("eco give Ada 100000");
            Await.ticks(20);

            // ---- the richest: Bo first, Ada second, nobody third yet
            server.console("execute as Ada at @s run tp @s ~ ~ ~ 100 20");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place richest");
            Await.until("Ada is told the podium stands", WAIT, () -> said(ada, "three richest players"));
            Await.until("Bo stands on it", WAIT, () -> count(server, wearing(bo.id().toString())) == 1);
            Await.until("so does Ada", WAIT, () -> count(server, wearing(ada.id().toString())) == 1);
            Await.until("and the third place is empty", WAIT, () -> count(server, wearing("-")) == 1);
            assertThat(count(server, "@e[type=minecraft:block_display]")).as("a pedestal per place").isEqualTo(3);
            assertThat(count(server, "@e[type=minecraft:text_display]")).as("one label, over the winner").isEqualTo(1);

            String first = server.console("data get entity " + one(bo.id().toString()) + " CustomName");
            assertThat(first).contains("#1").contains("Bo");
            assertThat(server.console("data get entity " + one(bo.id().toString()) + " Rotation"))
                    .as("turned to the nearest quarter, level").contains("90.0f, 0.0f");
            assertThat(server.console("data get entity " + one(bo.id().toString()) + " NoGravity"))
                    .as("the statue stays on its pedestal").contains("1b");

            // ---- Ada overtakes Bo: the statues change hands by themselves
            server.console("eco give Ada 1000000");
            Await.until("Ada is first now", WAIT, () -> server.console("data get entity "
                    + one(ada.id().toString()) + " CustomName").contains("#1"));
            assertThat(count(server, "@e[type=minecraft:mannequin]")).as("still three statues").isEqualTo(3);

            // ---- the casino board: nobody has won anything, so all three places are empty
            server.console("execute as Ada at @s run tp @s ~10 ~ ~");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place");
            Await.until("a casino podium", WAIT, () -> said(ada, "biggest casino winners"));
            Await.until("three empty places on it", WAIT, () -> count(server, wearing("-")) == 4);
            assertThat(count(server, "@e[type=minecraft:text_display]")).as("a label per podium").isEqualTo(2);
            ada.forgetChat();
            ada.run("eco statue place jackpot");
            Await.until("an unknown board is refused", WAIT, () -> said(ada, "is not a board"));

            // ---- removing the podium takes every statue on it, and leaves the other standing
            ada.forgetChat();
            ada.run("eco statue remove podium");
            Await.until("Ada is told it is gone", WAIT, () -> said(ada, "podium is gone"));
            Await.until("the casino podium is gone", WAIT, () -> count(server, "@e[type=minecraft:mannequin]") == 3);
            assertThat(count(server, "@e[type=minecraft:block_display]")).isEqualTo(3);
            assertThat(count(server, "@e[type=minecraft:text_display]")).isEqualTo(1);

            // ---- and it stays gone: the next refresh does not put it back
            Await.ticks(140);
            assertThat(count(server, "@e[type=minecraft:mannequin]")).isEqualTo(3);

            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy")).isEmpty();
        }
    }

    /** Pieces of a kind within {@code reach} blocks of Ada. */
    private static int byAda(Server server, String type, int reach) {
        return count(server, "@e[type=minecraft:" + type + ",distance=.." + reach + "]", "execute at Ada if entity ");
    }

    private static int count(Server server, String selector, String prefix) {
        String out = server.console(prefix + selector);
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("(\\d+)").matcher(out);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** Pieces of a kind within a few blocks of a point. */
    private static String near(String type, int x, int y, int z) {
        return "@e[type=minecraft:" + type + ",x=" + x + ",y=" + y + ",z=" + z + ",distance=..4]";
    }

    @Test
    @DisplayName("statues one by one: each placed where staff stand, moved, removed on its own; an old podium comes back as three")
    void oneByOne() {
        String old = "world 200.50 100.00 200.50 0.0 richest";
        try (Server server = Server.start("economy-statues-single", List.of("economy-standalone:RainsEconomy-.*"),
                List.of(), java.util.Map.of("plugins/RainsEconomy/config.yml", "statue:\n  podiums: '" + old + "'\n"),
                List.of("The economy is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("eco give Bo 500000");
            server.console("eco give Ada 100000");
            Await.ticks(20);

            // ---- a podium from 0.60: the pieces it left behind, tagged the old way
            server.console("forceload add 200 200");
            server.console("fill 196 99 196 204 99 204 minecraft:stone");
            server.console("summon minecraft:text_display 200.5 103.4 200.5 {BukkitValues:{\"rainseconomy:statue\":\""
                    + old + "#heading\"}}");
            server.console("summon minecraft:block_display 200 100 200 {BukkitValues:{\"rainseconomy:statue\":\""
                    + old + "#1\"}}");
            Await.until("it comes back as three statues", WAIT,
                    () -> count(server, near("mannequin", 200, 100, 200)) == 3);
            Await.until("the old heading and pedestal are cleared away", WAIT,
                    () -> count(server, near("block_display", 200, 100, 200)) == 3
                            && count(server, near("text_display", 200, 103, 200)) == 1);
            Await.until("Bo stands first on it", WAIT, () -> server.console("data get entity "
                    + one(bo.id().toString()) + " CustomName").contains("#1"));

            // ---- one statue: the richest player alone, on its own pedestal under its own label
            server.console("execute as Ada at @s run tp @s ~ ~ ~ 30 20");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place richest 1");
            Await.until("Ada is told it stands", WAIT, () -> said(ada, "#1 of the richest players stands here"));
            Await.until("Bo stands there too", WAIT, () -> count(server, wearing(bo.id().toString())) == 2);
            Await.until("next to Ada", WAIT, () -> byAda(server, "mannequin", 2) == 1);
            assertThat(server.console("execute at Ada run data get entity @e[limit=1,type=minecraft:mannequin,"
                    + "distance=..2] Rotation")).as("facing the nearest eighth of Ada's look").contains("45.0f, 0.0f");
            assertThat(byAda(server, "block_display", 2)).as("its own pedestal").isEqualTo(1);
            assertThat(byAda(server, "text_display", 5)).as("its own label").isEqualTo(1);

            // ---- the third of the casino somewhere else, nobody's yet
            server.console("execute as Ada at @s run tp @s ~8 ~ ~ 0 20");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place casino 3");
            Await.until("Ada is told", WAIT, () -> said(ada, "#3 of the biggest casino winners"));
            Await.until("an empty #3", WAIT, () -> count(server, wearing("-")) == 2);
            ada.forgetChat();
            ada.run("eco statue place casino 4");
            Await.until("a fourth place is refused", WAIT, () -> said(ada, "is not a place"));

            // ---- moving: the richest #1 nearest to Ada walks over, leaving nothing behind
            server.console("execute as Ada at @s run tp @s ~ ~ ~8 180 20");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue move richest 1");
            Await.until("Ada is told it moved", WAIT, () -> said(ada, "#1 of the richest players stands here now"));
            Await.until("it stands by Ada now", WAIT, () -> byAda(server, "mannequin", 2) == 1);
            assertThat(byAda(server, "text_display", 5)).as("its label came along").isEqualTo(1);
            Await.ticks(140);
            assertThat(count(server, wearing(bo.id().toString()))).as("moved, not copied").isEqualTo(2);
            assertThat(count(server, "@e[type=minecraft:mannequin]")).isEqualTo(5);
            assertThat(count(server, "@e[type=minecraft:block_display]")).isEqualTo(5);

            // ---- a #2 of the same board side by side shares the #1's label instead of stacking its own
            server.console("execute as Ada at @s run tp @s ~2 ~ ~ 180 20");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place richest 2");
            Await.until("Ada is told", WAIT, () -> said(ada, "#2 of the richest players stands here"));
            Await.until("the #2 stands", WAIT, () -> count(server, "@e[type=minecraft:mannequin]") == 6);
            Await.ticks(140);
            assertThat(byAda(server, "text_display", 5)).as("one label over the two").isEqualTo(1);
            ada.forgetChat();
            ada.run("eco statue remove");
            Await.until("the #2 is gone", WAIT, () -> count(server, "@e[type=minecraft:mannequin]") == 5);
            server.console("execute as Ada at @s run tp @s ~-2 ~ ~ 180 20");
            Await.ticks(10);

            ada.forgetChat();
            ada.run("eco statue move playtime 2");
            Await.until("nothing to move is said", WAIT, () -> said(ada, "There is no #2"));

            // ---- the ranking changes: the single statue changes hands by itself
            server.console("eco give Ada 1000000");
            Await.until("Ada is the richest now, on both", WAIT,
                    () -> count(server, wearing(ada.id().toString())) == 2);

            // ---- removing a single statue takes only that one
            ada.forgetChat();
            ada.run("eco statue remove");
            Await.until("Ada is told", WAIT, () -> said(ada, "statue is gone"));
            Await.until("only that one is gone", WAIT, () -> count(server, "@e[type=minecraft:mannequin]") == 4);
            Await.ticks(140);
            assertThat(count(server, "@e[type=minecraft:mannequin]")).as("and stays gone").isEqualTo(4);
            assertThat(count(server, "@e[type=minecraft:block_display]")).isEqualTo(4);

            // ---- the migrated podium is a podium: removed whole
            server.console("tp Ada 200.5 100 203.5");
            Await.ticks(20);
            ada.forgetChat();
            ada.run("eco statue remove podium");
            Await.until("Ada is told", WAIT, () -> said(ada, "podium is gone"));
            Await.until("its three are gone", WAIT, () -> count(server, near("mannequin", 200, 100, 200)) == 0);
            assertThat(count(server, "@e[type=minecraft:mannequin]")).isEqualTo(1);

            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy")).isEmpty();
        }
    }
}
