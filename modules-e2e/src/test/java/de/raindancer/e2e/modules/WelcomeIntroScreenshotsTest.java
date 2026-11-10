package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The welcome intro as the real client sees it: a newcomer accepts the pack, the camera glides through three shots
 * above the spawn with their captions, then the welcome window. A frame about every second of the flight:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=WelcomeIntroScreenshotsTest}.
 */
@Tag("screenshots")
class WelcomeIntroScreenshotsTest {

    @Test
    @DisplayName("the intro's flight, frame by frame, then the welcome")
    void intro() {
        String shots = """
                shots:
                - {world: world, x: 140.5, y: 105.0, z: 140.5, yaw: -45.0, pitch: 25.0, caption: 'Welcome to Hyades | A survival world of its own'}
                - {world: world, x: 175.5, y: 95.0, z: 135.5, yaw: 20.0, pitch: 20.0, caption: 'Guilds, jobs and skills'}
                - {world: world, x: 195.5, y: 110.0, z: 175.5, yaw: 120.0, pitch: 30.0, caption: 'Your adventure starts here'}
                """;
        try (Server server = Server.start("welcome-intro", List.of("welcome-standalone:RainsWelcome-.*"), List.of(),
                Map.of("plugins/RainsWelcome/intro.yml", shots), List.of("Intro: 3 shot"))) {
            server.console("settings set welcome:seconds-per-shot 9");
            Await.ticks(40);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer", 6)) {
                Path frames = E2e.out().resolve("screenshots");
                Await.ticks(100);
                viewer.click(484, 408);
                Await.ticks(160);
                viewer.click(484, 408);
                Await.until("the intro started", java.time.Duration.ofSeconds(60),
                        () -> server.console("data get entity Viewer playerGameType").contains(": 3"));
                // A screenshot takes about a second, so the frames are about that far apart.
                for (int frame = 0; frame < 16; frame++) {
                    viewer.screenshot(frames.resolve("welcome-intro-%02d.png".formatted(frame)));
                }
                Await.until("the intro is over", java.time.Duration.ofSeconds(30),
                        () -> !server.console("data get entity Viewer playerGameType").contains(": 3"));
                Await.ticks(60);
                viewer.screenshot(frames.resolve("welcome-intro-zz-welcome.png"));
                assertThat(frames.resolve("welcome-intro-zz-welcome.png")).exists();
            }
            assertThat(server.paper.errorsFrom("RainsWelcome", "RainsCore").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        }
    }
}
