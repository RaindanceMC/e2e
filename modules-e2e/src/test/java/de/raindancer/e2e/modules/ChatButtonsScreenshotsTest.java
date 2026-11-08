package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A [/command] in chat drawn as a button, and the sidebar page with a section's size, photographed with the real
 * client: {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ChatButtonsScreenshotsTest}.
 */
@Tag("screenshots")
class ChatButtonsScreenshotsTest {

    @Test
    @DisplayName("command buttons in chat, and how many of the richest the sidebar lists")
    void screenshots() {
        try (Server server = Server.start("chat-buttons-screenshots", List.of("chat-standalone:RainsChat-.*",
                "economy-standalone:RainsEconomy-.*"), List.of())) {
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(200);
                for (String name : List.of("Ada", "Bo", "Cy", "Di")) {
                    server.player(name);
                }
                Bot rain = server.player("Rain");
                Await.ticks(40);
                rain.say("found a bug? use [/bugreport] please");
                rain.say("meet me at [/warp spawn] or check [/bank]");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("chat-buttons-1.png"));
                server.console("e2e as Viewer bank sidebar");
                Await.ticks(60);
                viewer.screenshotHovering(shots.resolve("chat-buttons-2-sidebar-page.png"), 14);
                assertThat(shots.resolve("chat-buttons-2-sidebar-page.png")).exists();
            }
        }
    }
}
