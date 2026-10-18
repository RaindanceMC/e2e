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
 * Supply and demand at its end: a market flooded with cobblestone stops buying it, and gives it away until it is
 * bought back up — with the shipped defaults (Economy 0.47).
 */
@Tag("e2e")
class EconomyMarketScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static long balance(Bot bot) {
        bot.forgetChat();
        bot.run("balance");
        String line = Await.value(() -> "the balance line", WAIT, () -> bot.chatText().stream()
                .filter(text -> text.contains("You have")).findFirst().orElse(null));
        return Long.parseLong(line.substring(line.indexOf('⛃') + 1).split("[^0-9,]")[0].replace(",", ""));
    }

    @Test
    @DisplayName("flooded with cobblestone: nobody buys it any more, and the shop gives it away")
    void flooded() {
        try (Server server = Server.start("economy-market",
                List.of("economy-standalone:RainsEconomy-.*"), List.of("The economy is up"))) {
            Bot ada = server.player("Ada");
            server.console("gamemode survival Ada");
            server.console("clear Ada");
            server.console("give Ada minecraft:cobblestone 1280");
            Await.ticks(20);

            long before = balance(ada);
            ada.forgetChat();
            ada.run("sell all");
            Await.until("the stone is sold", WAIT, () -> said(ada, "You sold"));
            long afterFlood = balance(ada);
            assertThat(afterFlood).as("twenty stacks still paid").isGreaterThan(before);

            server.console("give Ada minecraft:cobblestone 64");
            Await.ticks(10);
            server.console("item replace entity Ada weapon.mainhand with minecraft:cobblestone 64");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("sell hand");
            Await.until("nobody is buying it", WAIT, () -> said(ada, "Nobody is buying Cobblestone right now"));
            assertThat(balance(ada)).as("not a coin for it").isEqualTo(afterFlood);

            server.console("clear Ada");
            ada.run("shop cobblestone");
            ada.awaitWindow("Cobblestone");
            ada.forgetChat();
            ada.click("Buy 8");
            Await.until("eight arrive", WAIT,
                    () -> ada.carrying(item -> item.is("cobblestone") && item.amount() == 8).isPresent());
            Await.until("Ada is told it was free", WAIT, () -> said(ada, "for nothing"));
            ada.closeWindow();
            assertThat(balance(ada)).as("free").isEqualTo(afterFlood);
            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy")).isEmpty();
        }
    }
}
