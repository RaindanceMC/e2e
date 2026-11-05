package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fishing's rod skins are put on in /cosmetics, with every other cosmetic, once the cosmetics plugin is there. */
@Tag("e2e")
class FishingCosmeticsScenarioTest {

    @Test
    @DisplayName("rod skins are a door on the cosmetics page, it opens the skins, and /fishing rod no longer has them")
    void rodSkinsInCosmetics() throws Exception {
        try (Server server = Server.start("fishing-cosmetics", List.of("fishing-standalone:RainsFishing-.*",
                "cosmetics-standalone:RainsCosmetics-.*"), List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            server.console("give Ada fishing_rod");
            Await.ticks(20);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));

            Bot.Window cosmetics = ada.runAndOpen("cosmetics", "Cosmetics").window().orElseThrow();
            assertThat(cosmetics.slotNamed("Rod skins")).as("the door on the cosmetics page").isPresent();
            assertThat(cosmetics.slotNamed("Weapon skins")).isPresent();
            ada.click("Rod skins");
            Await.until("the rod skins open", Duration.ofSeconds(10), () -> ada.window()
                    .map(window -> window.title().contains("Rod skins")).orElse(false));
            ada.closeWindow();

            Bot.Window rod = ada.runAndOpen("fishing rod", "Rod upgrades").window().orElseThrow();
            assertThat(rod.slotNamed("Rod skins")).as("not in /fishing rod any more").isEmpty();
            ada.closeWindow();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing", "RainsCosmetics")).isEmpty();
        }
    }
}
