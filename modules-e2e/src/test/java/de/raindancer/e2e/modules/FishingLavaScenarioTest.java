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
 * A real catch, end to end, in lava: a plain rod burns through; with the Ember Line (bought with fishing experience)
 * the hook floats, a fish bites — the lava flares — and reeling in then lands it straight in the bag, weighed.
 */
@Tag("e2e")
class FishingLavaScenarioTest {

    @Test
    @DisplayName("an ordinary line burns in lava; the Ember Line holds, a bite flares, the catch comes up and lands weighed; the boards show it")
    void lava() throws Exception {
        try (Server server = Server.start("fishing-lava", List.of("fishing-standalone:RainsFishing-.*"),
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
            Await.ticks(10);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));
            ada.look(-90f, 25f);
            Await.ticks(5);

            ada.forgetChat();
            ada.useHeld();
            Await.until("a silk line burns through", Duration.ofSeconds(10), () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("burnt through")));

            ada.run("fishing xp Ada 4000");
            Await.ticks(20);
            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.forgetChat();
            ada.click("Line");
            Await.until("the Ember Line is fitted", Duration.ofSeconds(10), () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("Ember Line")));
            ada.closeWindow();

            int before = ada.items().size();
            ada.forgetEffects();
            ada.forgetChat();
            ada.useHeld();
            Await.until("a fish bites in the lava", Duration.ofSeconds(40),
                    () -> ada.particlesSeen().contains("FLAME"));
            ada.useHeld();
            Await.until("the catch lands in the bag", Duration.ofSeconds(10), () -> ada.items().size() > before
                    || ada.items().stream().anyMatch(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                    .startsWith("fishing:")));
            assertThat(ada.items()).anyMatch(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                    .matches("fishing:(fish|chest)-.*"));
            Await.until("its weight shows", Duration.ofSeconds(5), () -> ada.actionBars().stream()
                    .anyMatch(line -> line.endsWith(" kg") || line.endsWith(" g"))
                    || ada.items().stream().anyMatch(item -> item.name().contains("Chest")));

            // the leaderboard and the fisher's own record, as screens
            ada.run("screen place fishing-top");
            ada.run("screen place fishing-you");
            Await.until("the screens show the fisher", Duration.ofSeconds(15), () -> ada.entityTexts().stream()
                    .anyMatch(line -> line.contains("Fishing leaderboard") && line.contains("Ada"))
                    && ada.entityTexts().stream().anyMatch(line -> line.contains("Your fishing")));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
        }
    }
}
