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
 * The tutorial island as the real client sees it: the generated island from above, the guide greeting with its speech
 * dialog, walking ahead to a station, the station's dialog, the task, and the welcome window offering the tour:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=WelcomeTutorialScreenshotsTest}.
 */
@Tag("screenshots")
class WelcomeTutorialScreenshotsTest {

    @Test
    @DisplayName("island, guide, bubble, a station, the window with the tour")
    void tutorial() {
        try (Server server = Server.start("welcome-tutorial-shots", List.of("welcome-standalone:RainsWelcome-.*"),
                List.of("Tutorial: 0 station"))) {
            Await.ticks(40);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer", 6)) {
                Path shots = E2e.out().resolve("screenshots");
                Await.ticks(100);
                viewer.click(484, 408);
                Await.ticks(160);
                viewer.click(484, 408);
                Await.ticks(60);
                server.console("op Viewer");
                server.console("gamerule sendCommandFeedback false");
                server.console("e2e as Viewer tutorial island generate");
                Await.ticks(200);
                server.console("execute in tutorial run gamerule sendCommandFeedback false");

                server.console("e2e as Viewer tutorial");
                Await.ticks(70);
                viewer.screenshot(shots.resolve("tutorial-1-hello.png"));
                // Closed without a choice, the tour goes on by itself after a while.
                viewer.press(java.awt.event.KeyEvent.VK_ESCAPE, 60);
                for (int frame = 2; frame <= 5; frame++) {
                    for (int i = 0; i < 12; i++) {
                        server.console("execute in tutorial as @e[type=mannequin,limit=1] at @s run tp Viewer ^ ^ ^-3 facing entity @s eyes");
                        Await.ticks(10);
                    }
                    viewer.screenshot(shots.resolve("tutorial-%d-walk.png".formatted(frame)));
                }
                for (int i = 0; i < 24; i++) {
                    server.console("execute in tutorial as @e[type=mannequin,limit=1] at @s run tp Viewer ^ ^ ^-3 facing entity @s eyes");
                    Await.ticks(10);
                }
                viewer.screenshot(shots.resolve("tutorial-6-station-dialog.png"));
                viewer.press(java.awt.event.KeyEvent.VK_ESCAPE, 60);
                Await.ticks(560);
                viewer.screenshot(shots.resolve("tutorial-7-try.png"));

                server.console("e2e as Viewer tutorial stop");
                server.console("execute in tutorial run tp Viewer 0 108 62 facing 0 63 0");
                Await.ticks(160);
                viewer.screenshot(shots.resolve("tutorial-0-island.png"));
                server.console("e2e as Viewer welcome preview");
                Await.ticks(100);
                viewer.screenshot(shots.resolve("tutorial-8-window.png"));
                assertThat(shots.resolve("tutorial-8-window.png")).exists();
            }
            assertThat(server.paper.errorsFrom("RainsWelcome").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        }
    }
}
