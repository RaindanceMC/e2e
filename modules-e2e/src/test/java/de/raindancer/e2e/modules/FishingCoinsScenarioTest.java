package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A sea chest's money comes out of it as the economy's own coins — and a cheque for more than a few stacks. */
@Tag("e2e")
class FishingCoinsScenarioTest {

    @Test
    @DisplayName("with the economy installed, a Void chest's money comes out as coins and a cheque, not as a chat line")
    void coins() throws Exception {
        try (Server server = Server.start("fishing-coins",
                List.of("fishing-standalone:RainsFishing-.*", "economy-standalone:RainsEconomy-.*"),
                List.of("Fishing is up", "The economy is up"))) {
            Bot ada = server.admin("Ada");
            ada.run("clear");
            ada.run("fishing give Ada chest:void");
            ada.expectItem("a Void Chest", item -> item.name().contains("Void Chest"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Void Chest")));

            var at = ada.position();
            int x = (int) Math.floor(at.getX()) + 2;
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("setblock " + x + " " + (y - 1) + " " + z + " stone");
            server.console("setblock " + x + " " + y + " " + z + " air");
            ada.useOn(x, y - 1, z);
            Await.until("the chest is down", Duration.ofSeconds(10),
                    () -> server.console("execute if block " + x + " " + y + " " + z + " barrier").contains("passed"));

            ada.forgetChat();
            ada.useOn(x, y, z);
            Await.until("the chest has opened", Duration.ofSeconds(40),
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("Void Chest held")));

            int coins = ada.items().stream().filter(item -> item.name().contains("Coin"))
                    .mapToInt(Bot.Item::amount).sum();
            assertThat(coins).as("a few stacks of coins, the most a chest gives as coins").isEqualTo(192);
            assertThat(ada.items()).as("the rest on one cheque from the chest")
                    .anyMatch(item -> item.name().contains("Cheque")
                            && item.lore().stream().anyMatch(line -> line.contains("Void Chest")));
            assertThat(ada.chatText()).as("no chat line about the money")
                    .noneMatch(line -> line.contains("straight to your balance"));
        }
    }
}
