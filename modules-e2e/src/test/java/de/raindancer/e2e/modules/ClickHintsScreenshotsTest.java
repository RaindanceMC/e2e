package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Click hints drawn as a mouse — in a menu's tooltip, on an item the player carries, in chat — photographed
 * with the real client wearing the pack: {@code mvn -o -Pe2e test -pl modules-e2e -De2e.groups=screenshots
 * -Dtest=ClickHintsScreenshotsTest}.
 */
@Tag("screenshots")
class ClickHintsScreenshotsTest {

    @Test
    @DisplayName("the icons in a menu, on a carried item and in chat, and the rest of each line in the game's font")
    void screenshots() {
        try (Server server = Server.start("click-hints-screenshots",
                List.of("economy-standalone:RainsEconomy-.*"), List.of())) {
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode survival Viewer");
                server.console("item replace entity Viewer hotbar.0 with minecraft:paper"
                        + "[minecraft:lore=[\"Right click to use it\",\"Shift click to put it away\"]]");
                Await.ticks(20);

                // Chat first: an open window hides it. A button like /tpa's, as the same system chat packet.
                server.console("tellraw Viewer [\"Rain wants to visit you. \",{\"text\":\"[Click to accept]\","
                        + "\"color\":\"green\",\"click_event\":{\"action\":\"run_command\",\"command\":\"/help\"},"
                        + "\"hover_event\":{\"action\":\"show_text\",\"value\":\"Click to accept\"}}]");
                server.console("tellraw Viewer {\"text\":\"Right click the sign to buy, shift click to sell\",\"color\":\"gray\"}");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("click-hints-0-chat-button.png"));

                server.console("e2e as Viewer bank");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("click-hints-1-bank.png"));
                viewer.screenshotHovering(shots.resolve("click-hints-2-bank-tooltip.png"), 3 * 9 + 1);
                // Row ten of a six-row window is the hotbar's first slot.
                viewer.screenshotHovering(shots.resolve("click-hints-3-carried-item.png"), 10 * 9);
                server.console("e2e as Viewer shop");
                Await.ticks(60);
                viewer.screenshotHovering(shots.resolve("click-hints-4-shop-tooltip.png"), 1);

                assertThat(shots.resolve("click-hints-4-shop-tooltip.png")).exists();
            }
        }
    }
}
