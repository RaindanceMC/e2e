package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * The AFK camera's two lines above the hotbar on the real client with the server's pack: the minutes away, and a tip
 * under them in Core's lowered font (RainsCore 2.97.0, TwoLines):
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AfkCamTipsScreenshotsTest}.
 */
@Tag("screenshots")
class AfkCamTipsScreenshotsTest {
    @Test
    @org.junit.jupiter.api.DisplayName("the minutes away over a tip, two tips apart")
    void tip() {
        try (Server server = Server.start("afk-tip", List.of("afkcam-standalone:RainsAfkCam-.*",
                "essentials-standalone:RainsEssentials-.*"), List.of("AFK cam is up"))) {
            server.console("settings set core:packs-required true");
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                Await.ticks(200);
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("spreadplayers 40 40 0 1 false Viewer");
                server.console("gamemode survival Viewer");
                Await.ticks(100);
                server.console("e2e as Viewer afk");
                Await.ticks(320);
                viewer.screenshot(shots.resolve("afk-tip-1.png"));
                Await.ticks(240);
                viewer.screenshot(shots.resolve("afk-tip-2.png"));
            }
        }
    }
}
