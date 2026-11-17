package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import de.raindancer.e2e.ShownDialog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /settings as the game's own window: a bot walks it (a door, a section, a switch flipped, a number typed), the real
 * client takes the pictures — {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=SettingsDialogScreenshotsTest}.
 */
@Tag("screenshots")
class SettingsDialogScreenshotsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    @Test
    void settings() throws Exception {
        try (Server server = Server.start("settings-dialog", List.of("guilds-standalone:RainsGuilds-.*"), List.of())) {
            Bot ada = server.admin("Ada");
            Await.ticks(40);
            int before = ada.dialogsShown();
            ada.run("settings");
            ShownDialog front = ada.awaitDialog(before, WAIT);
            assertThat(front.title()).contains("Settings");
            assertThat(front.button("Guilds")).isPresent();

            before = ada.dialogsShown();
            ada.pressDialogButton("Guilds", Map.of());
            ShownDialog guilds = ada.awaitDialog(before, WAIT);
            assertThat(guilds.buttons()).isNotEmpty();

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("op Viewer");
                server.console("e2e as Viewer settings");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("settings-1-front.png"));
                server.console("e2e as Viewer settings guilds");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("settings-2-guilds.png"));
                viewer.click(486, 144);   // the plugin's own "Guilds" section
                Await.ticks(40);
                viewer.screenshot(shots.resolve("settings-3-section.png"));
                viewer.screenshotPointingAt(shots.resolve("settings-4-hover.png"), 640, 276);
                viewer.click(640, 276);   // the first setting under the three doors: the founding price
                Await.ticks(40);
                viewer.screenshot(shots.resolve("settings-5-edit.png"));
            }
            assertThat(server.paper.errorsFrom("RainsCore", "RainsGuilds")).isEmpty();
        }
    }
}
