package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The AFK camera's hidden arm and HUD on the real client, with GrimAC watching as on Hyades: only the client is told
 * it spectates, so Grim must not flag the player, W must bring them back into survival, and walking afterwards must
 * be clean. Needs Grim's jar in {@code ~/.cache/rains-e2e/grim/}:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AfkCamHudScreenshotsTest}.
 */
@Tag("screenshots")
class AfkCamHudScreenshotsTest {

    @Test
    @DisplayName("no arm or HUD during the camera, survival on the server, W back, no Grim flag")
    void hudHidden() throws IOException {
        Path grim = grim();
        try (Server server = Server.start("afk-cam-hud", List.of("afkcam-standalone:RainsAfkCam-.*"), List.of(grim),
                List.of("AFK cam is up"))) {
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(100);
                server.console("spreadplayers 40 40 0 1 false Viewer");
                server.console("item replace entity Viewer weapon.mainhand with minecraft:diamond_sword");
                Await.ticks(100);
                viewer.screenshot(shots.resolve("afkcam-hud-0-before.png"));

                server.console("e2e as Viewer afkcam preview");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("afkcam-hud-1-hidden.png"));
                assertThat(server.console("data get entity Viewer playerGameType")).as("still survival on the server")
                        .contains(": 0");
                Await.ticks(400);
                viewer.screenshot(shots.resolve("afkcam-hud-2-hidden-later.png"));

                String standing = server.console("data get entity Viewer Pos");
                assertThat(server.console("execute if entity @e[type=mannequin]")).as("the camera still runs")
                        .doesNotContainIgnoringCase("failed");
                viewer.press(KeyEvent.VK_W, 400);
                Await.ticks(40);
                assertThat(server.console("execute if entity @e[type=mannequin]")).as("W ended the camera")
                        .containsIgnoringCase("failed");
                viewer.press(KeyEvent.VK_W, 1500);
                Await.ticks(40);
                viewer.screenshot(shots.resolve("afkcam-hud-3-back.png"));
                assertThat(server.console("data get entity Viewer playerGameType")).contains(": 0");
                assertThat(server.console("data get entity Viewer Pos")).as("walked on after the camera")
                        .isNotEqualTo(standing);
                assertThat(viewer.isRunning()).as("not kicked").isTrue();
            }
            assertThat(server.log()).as("no Grim alert").doesNotContainPattern("Viewer.{0,40}failed");
            assertThat(server.log()).doesNotContain("Viewer lost connection: ").doesNotContain("Viewer drowned")
                    .doesNotContain("Viewer died").doesNotContain("Viewer fell").doesNotContain("Flying is not enabled");
            assertThat(server.paper.errorsFrom("RainsAfkCam", "RainsCore")).isEmpty();
        }
    }

    private static Path grim() throws IOException {
        Path folder = Path.of(System.getProperty("user.home"), ".cache", "rains-e2e", "grim");
        assumeTrue(Files.isDirectory(folder), "Grim's jar is not in " + folder);
        try (Stream<Path> jars = Files.list(folder)) {
            return jars.filter(p -> p.getFileName().toString().startsWith("grimac")).findFirst()
                    .orElseThrow(() -> new AssertionError("no grimac jar in " + folder));
        }
    }
}
