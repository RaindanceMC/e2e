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
 * Screens (/screen, Core 2.10.0): a personal one shows every nearby player their own text and nobody else's, a
 * shared one shows one text to all; staff nudge them up and down, and remove them whole.
 */
@Tag("e2e")
class ScreensScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static boolean shown(Bot bot, String text) {
        return bot.entityTexts().stream().anyMatch(line -> line.contains(text));
    }

    private static int count(Server server, String selector) {
        String out = server.console("execute if entity " + selector);
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("(\\d+)").matcher(out);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    @Test
    @DisplayName("personal screens show everybody their own, shared ones one text; nudged up and down, removed whole")
    void screens() {
        try (Server server = Server.start("screens", List.of("economy-standalone:RainsEconomy-.*"),
                List.of("The economy is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("execute as Bo at Ada run tp @s ~2 ~ ~");
            Await.ticks(20);

            // ---- the kinds there are: Core's and the economy's
            ada.forgetChat();
            ada.run("screen kinds");
            Await.until("Ada sees the kinds", WAIT, () -> said(ada, "Welcome") && said(ada, "Your money"));

            // ---- a personal screen: each sees their own greeting, never the other's
            ada.forgetChat();
            ada.run("screen place welcome");
            Await.until("Ada is told it is up", WAIT, () -> said(ada, "is up"));
            Await.until("a display for each of them", WAIT,
                    () -> count(server, "@e[type=minecraft:text_display]") == 2);
            Await.until("Ada is greeted", WAIT, () -> shown(ada, "Welcome, Ada!"));
            Await.until("Bo is greeted", WAIT, () -> shown(bo, "Welcome, Bo!"));
            Await.ticks(60);
            assertThat(shown(ada, "Welcome, Bo!")).as("Ada never sees Bo's greeting").isFalse();
            assertThat(shown(bo, "Welcome, Ada!")).as("Bo never sees Ada's greeting").isFalse();

            // ---- Bo walks away: his display goes, Ada's stays
            server.console("execute as Bo at @s run tp @s ~200 ~ ~");
            Await.until("Bo's display is gone", WAIT, () -> count(server, "@e[type=minecraft:text_display]") == 1);

            // ---- a shared screen: one display, the same for everybody
            server.console("execute as Ada at @s run tp @s ~10 ~ ~");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("screen place online");
            Await.until("the online screen is up", WAIT, () -> said(ada, "is up"));
            Await.until("Ada sees who is online", WAIT, () -> shown(ada, "Online"));

            // ---- nudged up and down: the nearest one, or one by its number
            ada.forgetChat();
            ada.run("screen list");
            Await.until("the list", WAIT, () -> said(ada, "#2"));
            String before = ada.chatText().stream().filter(line -> line.contains("#2")).findFirst().orElseThrow();
            ada.forgetChat();
            ada.run("screen up 1");
            Await.until("moved up", WAIT, () -> said(ada, "moved up 1.00"));
            ada.forgetChat();
            ada.run("screen down 0.5 #2");
            Await.until("moved down", WAIT, () -> said(ada, "moved down 0.50"));
            ada.forgetChat();
            ada.run("screen list");
            Await.until("the list again", WAIT, () -> said(ada, "#2"));
            String after = ada.chatText().stream().filter(line -> line.contains("#2")).findFirst().orElseThrow();
            assertThat(y(after) - y(before)).as("up 1, down 0.5").isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.05));
            ada.forgetChat();
            ada.run("screen up lots");
            Await.until("nonsense refused", WAIT, () -> said(ada, "is no number of blocks"));

            // ---- removed: the nearest, and it is not put back
            ada.forgetChat();
            ada.run("screen remove");
            Await.until("removed", WAIT, () -> said(ada, "#2 is gone"));
            ada.forgetChat();
            ada.run("screen remove 1");
            Await.until("the first removed too", WAIT, () -> said(ada, "#1 is gone"));
            Await.until("no displays left", WAIT, () -> count(server, "@e[type=minecraft:text_display]") == 0);
            Await.ticks(100);
            assertThat(count(server, "@e[type=minecraft:text_display]")).as("not put back").isZero();

            // ---- charts: the server's status, its ticks and its player record
            ada.forgetChat();
            ada.run("screen place server-status");
            Await.until("the status screen is up", WAIT, () -> said(ada, "is up"));
            server.console("execute as Ada at @s run tp @s ~4 ~ ~");
            Await.ticks(5);
            ada.forgetChat();
            ada.run("screen place chart-tps");
            Await.until("the TPS chart is up", WAIT, () -> said(ada, "is up"));
            server.console("execute as Ada at @s run tp @s ~4 ~ ~");
            Await.ticks(5);
            ada.forgetChat();
            ada.run("screen place player-record");
            Await.until("the record screen is up", WAIT, () -> said(ada, "is up"));
            Await.until("Ada sees the status", WAIT, () -> shown(ada, "RAM") && shown(ada, "Players"));
            Await.until("Ada sees the TPS chart", WAIT, () -> shown(ada, "last 6 hours"));
            Await.until("Ada sees the record", WAIT, () -> shown(ada, "Most at once"));
            ada.forgetChat();
            ada.run("screen list");
            Await.until("Ada's list", WAIT, () -> said(ada, "Player record"));
            int adas = Integer.parseInt(ada.chatText().stream().filter(line -> line.contains("Player record"))
                    .findFirst().orElseThrow().trim().split("\\s+")[0].substring(1));

            // ---- a player puts up screens of their own, up to their limit
            bo.forgetChat();
            bo.run("screen place balance");
            Await.until("Bo's first is up", WAIT, () -> said(bo, "is up"));
            for (String kind : List.of("chart-players", "welcome")) {
                server.console("execute as Bo at @s run tp @s ~3 ~ ~");
                Await.ticks(5);
                bo.forgetChat();
                bo.run("screen place " + kind);
                Await.until("Bo's " + kind + " is up", WAIT, () -> said(bo, "is up"));
            }
            bo.forgetChat();
            bo.run("screen place clock");
            Await.until("a fourth is refused", WAIT, () -> said(bo, "screen(s) up already"));
            Await.until("Bo sees his own money", WAIT, () -> shown(bo, "Your money"));
            bo.forgetChat();
            bo.run("screen list");
            Await.until("Bo's list shows only his", WAIT, () -> said(bo, "Your screens (3/3)"));
            assertThat(said(bo, "Player record")).as("not Ada's").isFalse();

            // ---- his own he nudges; Ada's he cannot touch
            bo.forgetChat();
            bo.run("screen up 0.5");
            Await.until("Bo nudges his own", WAIT, () -> said(bo, "moved up 0.50"));
            bo.forgetChat();
            bo.run("screen remove #" + adas);
            Await.until("Ada's is not his", WAIT, () -> said(bo, "no screen of yours #" + adas));
            bo.forgetChat();
            bo.run("screen remove");
            Await.until("Bo takes his nearest down", WAIT, () -> said(bo, "is gone"));
            ada.forgetChat();
            ada.run("screen list");
            Await.until("staff see every screen, his too", WAIT, () -> said(ada, "— Bo"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy")).isEmpty();
        }
    }

    /** The height in a /screen list line: "#2 Who is online world 1.5 66.2 3.5". */
    private static double y(String line) {
        String[] words = line.trim().split("\\s+");
        return Double.parseDouble(words[words.length - 2]);
    }
}
