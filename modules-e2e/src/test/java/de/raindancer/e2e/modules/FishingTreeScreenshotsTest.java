package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fishing skill tree in the real client, wearing the pack — the five branches' paintings, portholes and lines:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=FishingTreeScreenshotsTest}.
 */
@Tag("screenshots")
class FishingTreeScreenshotsTest {

    @Test
    @DisplayName("every branch of the skill tree, as a player wearing the pack sees it")
    void tree() throws java.io.IOException {
        try (Server server = Server.start("fishing-tree-looks", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:polished_deepslate");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("time set 6000");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("op Viewer");
                server.console("tp Viewer 0.5 64 0.5 0 10");
                server.console("fishing xp Viewer 20000");
                Await.ticks(20);
                server.console("e2e as Viewer fishing skills");
                Await.ticks(40);
                // quick bite twice, swift reel, spot reader, patience
                for (int slot : new int[]{18, 18, 2, 38, 4}) {
                    server.console("e2e click Viewer " + slot);
                    Await.ticks(30);
                }
                viewer.screenshot(shots.resolve("tree-1-tidecaller.png"));
                viewer.screenshotHovering(shots.resolve("tree-1b-frenzy.png"), 40);
                for (int page = 2; page <= 5; page++) {
                    server.console("e2e click Viewer 50");
                    Await.ticks(30);
                    if (page == 3) {
                        server.console("e2e click Viewer 18");
                        Await.ticks(30);
                    }
                    viewer.screenshot(shots.resolve("tree-" + page + ".png"));
                }
                assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every painting and model")
                        .doesNotContain("rainsfishing:").doesNotContain("rainscore:");
            }
        }
    }
}
