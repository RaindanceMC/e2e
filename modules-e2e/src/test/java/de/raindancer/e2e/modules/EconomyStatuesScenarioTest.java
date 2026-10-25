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
 * Podiums of the top three: three statues on pedestals under a heading, each wearing the skin of whoever holds
 * its place, the next one up when somebody overtakes them, and everything gone again with the podium.
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
            assertThat(count(server, "@e[type=minecraft:text_display]")).as("a heading").isEqualTo(1);

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
            ada.forgetChat();
            ada.run("eco statue place jackpot");
            Await.until("an unknown board is refused", WAIT, () -> said(ada, "is not a board"));

            // ---- removing takes the nearest podium, everything on it, and leaves the other standing
            ada.forgetChat();
            ada.run("eco statue remove");
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
}
