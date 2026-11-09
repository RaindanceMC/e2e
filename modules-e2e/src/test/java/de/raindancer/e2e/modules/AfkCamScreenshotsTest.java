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
 * The AFK camera as the real client renders it, one picture per shot of the reel (shortened to 8 s each):
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AfkCamScreenshotsTest}.
 */
@Tag("screenshots")
class AfkCamScreenshotsTest {

    @Test
    @DisplayName("first person, then the close, wide, low and overhead shots")
    void screenshots() {
        try (Server server = Server.start("afk-cam-screenshots", List.of("afkcam-standalone:RainsAfkCam-.*"),
                List.of("AFK cam is up"))) {
            server.console("settings set afkcam:shot-seconds 8");
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(100);
                server.console("spreadplayers 40 40 0 1 false Viewer");
                Await.ticks(100);
                viewer.screenshot(shots.resolve("afkcam-0-first-person.png"));
                server.console("e2e as Viewer afkcam preview");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("afkcam-1-close.png"));
                Await.ticks(160);
                viewer.screenshot(shots.resolve("afkcam-2-wide.png"));
                Await.ticks(160);
                viewer.screenshot(shots.resolve("afkcam-3-low.png"));
                Await.ticks(160);
                viewer.screenshot(shots.resolve("afkcam-4-overhead.png"));
                assertThat(shots.resolve("afkcam-4-overhead.png")).exists();
            }
        }
    }
}
