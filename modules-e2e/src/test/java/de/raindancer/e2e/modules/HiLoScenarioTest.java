package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Hi-Lo on a real server: no free redraw of the first card by closing the window, and the largest multiplier. */
@Tag("e2e")
class HiLoScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static boolean cardShowing(Bot bot) {
        return bot.window().flatMap(window -> window.slotNamed("Higher").or(() -> window.slotNamed("Lower"))).isPresent();
    }

    private static void guess(Bot bot) {
        bot.click(bot.window().orElseThrow().slotNamed("Higher").isPresent() ? "Higher" : "Lower");
    }

    @Test
    @DisplayName("closing on the first card keeps the bet on the table; a run reaching the largest multiplier is paid out")
    void hiLo() {
        try (Server server = Server.start("hilo", List.of("economy-standalone:RainsEconomy-.*"), List.of("The economy is up"))) {
            Bot ada = server.admin("Ada");

            ada.run("hilo");
            ada.awaitWindow("Hi-Lo");
            ada.click("Start");
            Await.until("the first card shows", WAIT, () -> cardShowing(ada));
            ada.forgetChat();
            ada.click("Cash out");
            Await.ticks(20);
            assertThat(said(ada, "comes back")).as("nothing to cash out before a guess").isFalse();
            assertThat(cardShowing(ada)).as("the card is still on the table").isTrue();
            ada.closeWindow();
            Await.until("the card stays on the table", WAIT, () -> said(ada, "still on the table"));
            assertThat(said(ada, "comes back")).as("no free redraw of the first card").isFalse();

            ada.forgetChat();
            ada.run("hilo");
            ada.awaitWindow("Hi-Lo");
            Await.until("the same run picks up again", WAIT, () -> cardShowing(ada));
            assertThat(ada.window().orElseThrow().slotNamed("Start")).as("no new bet, the old card").isEmpty();

            // any right guess reaches a cap of 1.01×: the run is paid out by itself, without closing anything
            server.console("settings set economy:hilo.max-multiplier 1.01");
            Await.ticks(10);
            boolean paid = false;
            for (int round = 0; round < 12 && !paid; round++) {
                ada.forgetChat();
                if (!cardShowing(ada)) {
                    ada.click("Start");
                    Await.until("a card shows", WAIT, () -> cardShowing(ada));
                }
                guess(ada);
                Await.until("the guess is settled", WAIT, () -> said(ada, "Cashed out on the") || said(ada, "Wrong guess"));
                paid = said(ada, "Cashed out on the");
                Await.ticks(30);
            }
            assertThat(paid).as("a right guess at the cap pays out at once").isTrue();
            assertThat(ada.window()).as("paid out while the window was still open").isPresent();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy")).isEmpty();
        }
    }
}
