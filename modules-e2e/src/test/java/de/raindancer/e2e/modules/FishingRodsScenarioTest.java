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
 * RainsFishing's rods with RainsEnchants beside it: a rod not unlocked will not cast, unlocking one spends fishing
 * experience after a confirmation, and a fishing enchantment put on by RainsEnchants is read by RainsFishing.
 */
@Tag("e2e")
class FishingRodsScenarioTest {

    @Test
    @DisplayName("rods are unlocked before they cast, and the fishing enchantments are read off them")
    void rods() throws Exception {
        try (Server server = Server.start("fishing-rods",
                List.of("fishing-standalone:RainsFishing-.*", "enchants-standalone:RainsEnchants-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            server.console("clear Ada");
            ada.run("fishing give Ada rod:reed");
            Bot.Item reed = ada.expectItem("a Reed Rod", item -> item.name().contains("Reed Rod"));
            assertThat(reed.lore()).anyMatch(line -> line.contains("unlocked it"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Reed Rod")));

            ada.forgetChat();
            ada.useHeld();
            Await.until("a locked rod refuses to cast", Duration.ofSeconds(10), () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("Reed Rod") && line.contains("not unlocked")));

            ada.run("fishing xp Ada 600");
            Await.ticks(20);
            ada.runAndOpen("fishing rods", "Rods");
            ada.forgetChat();
            ada.click("Reed Rod");
            ada.awaitWindow("Unlock the Reed");
            ada.click("Yes, do it");
            Await.until("the rod is unlocked", Duration.ofSeconds(10), () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("Reed Rod") && line.contains("is yours")));
            Bot.Window rods = ada.window().orElseThrow();
            assertThat(rods.slotNamed("Reed Rod").map(slot -> rods.top().get(slot).lore()).orElseThrow())
                    .anyMatch(line -> line.contains("Unlocked"));
            ada.closeWindow();

            ada.forgetChat();
            ada.useHeld();
            Await.ticks(20);
            assertThat(ada.chatText()).noneMatch(line -> line.contains("not unlocked"));
            ada.useHeld();
            Await.ticks(5);

            server.console("enchant Ada rainsenchants:deep_bite 3");
            Await.ticks(5);
            Bot.Window rod = ada.runAndOpen("fishing rod", "Rod upgrades").window().orElseThrow();
            List<String> about = rod.top().get(rod.slotNamed("The rod in your hand").orElseThrow()).lore();
            assertThat(about).anyMatch(line -> line.contains("Reed Rod"));
            assertThat(about).anyMatch(line -> line.contains("Deep Bite III") && line.contains("21% sooner"));
            ada.closeWindow();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing", "RainsEnchants")).isEmpty();
        }
    }
}
