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
 * The welcome server's tour (RainsWelcome 0.3.0): staff generate the tutorial island with its stations; a player on
 * the tour is led to the first station by their guide (a mannequin only they see), hears what it is about, tries
 * it — /balance at "Money &amp; the shop" — and is told it is done; /tutorial next skips a stop, /tutorial stop ends it
 * and takes the guide away. Falling off the island puts you back at the start. /trailer with no intro says so.
 */
@Tag("e2e")
class WelcomeTutorialScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Test
    @DisplayName("island generated; the guide leads to a station, explains, the task is done; next, stop")
    void tour() {
        try (Server server = Server.start("welcome-tutorial", List.of("welcome-standalone:RainsWelcome-.*"),
                List.of("Tutorial: 0 station"))) {
            Bot ada = server.admin("Ada");
            // From the console: a fresh welcome server is set up before anybody has joined.
            server.console("tutorial island generate");
            Await.until("the island is built", WAIT,
                    () -> server.console("execute in tutorial if block 0 63 0 minecraft:air").toLowerCase().contains("failed"));
            ada.run("tutorial station list");
            Await.until("all nine stations placed", WAIT, () -> text(ada).contains("rules"));
            assertThat(text(ada)).doesNotContain("not placed");

            int dialogs = ada.dialogsShown();
            ada.run("tutorial");
            Await.until("Ada is on the island", WAIT,
                    () -> server.console("execute in tutorial if entity @a[name=Ada,distance=..5,x=0,y=64,z=0]")
                            .toLowerCase().contains("passed"));
            var hello = ada.awaitDialog(dialogs, WAIT);
            assertThat(hello.title()).as("her guide greets her in the game's own dialog").contains("Hi, I'm Iris");
            assertThat(server.console("execute in tutorial if entity @e[type=mannequin]")).contains("passed");
            int beforeStation = ada.dialogsShown();
            ada.pressDialogButton("Let's go!", java.util.Map.of());

            // She follows: kept a few blocks behind the guide until the guide stands at the first station.
            Await.until("the guide has led her to the money station", Duration.ofSeconds(90), () -> {
                server.console("execute in tutorial as @e[type=mannequin,limit=1] at @s run tp Ada ^ ^ ^-2");
                return ada.dialogsShown() > beforeStation;
            });
            var station = ada.dialog().orElseThrow();
            assertThat(station.title()).contains("Money & the shop");
            assertThat(String.join(" ", station.texts())).contains("Hyades pays in Drachmas").contains("/balance");
            ada.pressDialogButton("Let me try", java.util.Map.of());
            Await.ticks(20);
            ada.run("balance");
            Await.until("the station is done", WAIT, () -> text(ada).contains("Money & the shop — well done"));

            ada.run("tutorial next");
            // Stop 2 (cosmetics) was on its way after /balance; /tutorial next skips it: stop 3, claims.
            Await.until("on to the next stop", WAIT, () -> ada.actionBars().stream()
                    .anyMatch(bar -> bar.contains("3/9") && bar.contains("Your land")));
            ada.run("tutorial stop");
            Await.until("the tour ended", WAIT, () -> text(ada).contains("Tour ended"));
            Await.until("the guide is gone", WAIT,
                    () -> server.console("execute in tutorial if entity @e[type=mannequin]").toLowerCase().contains("failed"));

            // Off the island into the void: back at the start, not falling for ever.
            server.console("execute in tutorial run tp Ada 0 30 120");
            Await.until("Ada was put back at the start", WAIT, () -> text(ada).contains("not that way"));
            Await.until("standing on the island again", WAIT,
                    () -> server.console("execute in tutorial if entity @a[name=Ada,x=0,y=64,z=0,distance=..6]")
                            .toLowerCase().contains("passed"));

            ada.run("trailer");
            Await.until("no trailer recorded yet", WAIT, () -> text(ada).contains("no trailer yet"));
            assertThat(server.paper.errorsFrom("RainsWelcome")).isEmpty();
        }
    }

    private static String text(Bot bot) {
        return String.join("\n", bot.chatText());
    }
}
