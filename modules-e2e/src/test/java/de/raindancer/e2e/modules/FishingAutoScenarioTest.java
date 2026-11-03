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
 * Auto-Reel and Auto-Rethrow from the skill tree, on a real server: one cast by hand into lava, and the bites reel
 * themselves in and the line goes back out until several catches are in the bag.
 */
@Tag("e2e")
class FishingAutoScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private static int caught(Bot bot) {
        return bot.items().stream().filter(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                .matches("fishing:(fish|chest)-.*")).mapToInt(Bot.Item::amount).sum();
    }

    @Test
    @DisplayName("one cast by hand, and Auto-Reel and Auto-Rethrow land catch after catch in lava")
    void autoFishing() throws Exception {
        try (Server server = Server.start("fishing-auto", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            var at = ada.position();
            int x = (int) Math.floor(at.getX());
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("fill " + (x + 2) + " " + (y - 2) + " " + (z - 3) + " " + (x + 9) + " " + (y - 1) + " "
                    + (z + 3) + " lava");
            server.console("fill " + (x + 2) + " " + y + " " + (z - 3) + " " + (x + 9) + " " + (y + 3) + " "
                    + (z + 3) + " air");
            server.console("clear Ada");
            server.console("give Ada fishing_rod");
            server.console("effect give Ada fire_resistance infinite 0 true");
            ada.run("fishing xp Ada 20000");
            Await.ticks(20);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));

            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.forgetChat();
            ada.click("Line");
            Await.until("the Ember Line is fitted", WAIT, () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("Ember Line")));
            ada.closeWindow();

            ada.runAndOpen("fishing skills", "Tidecaller");
            Await.ticks(10);
            for (int rank = 1; rank <= 5; rank++) {
                ada.click("Quick Bite");
                int now = rank;
                Await.until("Quick Bite rank " + now, WAIT, () -> lore(ada, "Quick Bite").contains(now + "/5"));
            }
            ada.click("Auto-Reel");
            Await.until("Auto-Reel learnt", WAIT, () -> lore(ada, "Auto-Reel").contains("1/1"));
            ada.click("Auto-Rethrow");
            Await.until("Auto-Rethrow learnt", WAIT, () -> lore(ada, "Auto-Rethrow").contains("1/1"));
            ada.closeWindow();

            ada.look(-90f, 25f);
            Await.ticks(5);
            int before = caught(ada);
            ada.useHeld();
            Await.until("three catches come in without another click", Duration.ofSeconds(150),
                    () -> caught(ada) >= before + 3);
            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
        }
    }

    private static String lore(Bot bot, String name) {
        return bot.window().flatMap(window -> window.slotNamed(name).map(slot -> window.top().get(slot)))
                .map(item -> item.name() + " " + String.join(" ", item.lore())).orElse("");
    }
}
