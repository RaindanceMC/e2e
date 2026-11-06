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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core's model engine (BetterModel, installed by Core) in the real client: its pack arrives inside Core's one pack
 * and a Blockbench model stands and walks — {@code mvn -o -Pscreenshots test -pl modules-e2e
 * -Dtest=ModelEngineScreenshotsTest}. Needs jars/bettermodel/ with the engine jar and a test model.
 */
@Tag("screenshots")
class ModelEngineScreenshotsTest {

    private static final Path HERE = Path.of(System.getProperty("e2e.reactor")).resolve("bettermodel");

    @Test
    @DisplayName("a Blockbench model shows in the real client, from Core's one pack")
    void modelShows() throws Exception {
        String model = Files.readString(HERE.resolve("drake.bbmodel"));
        try (Server server = Server.start("model-engine", List.of(),
                List.of(HERE.resolve("bettermodel-3.6.0-paper.jar")),
                Map.of("plugins/BetterModel/models/drake.bbmodel", model), List.of())) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 90 24 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("time set 6000");
            server.console("weather clear");
            Await.ticks(100);
            assertThat(server.paper.log()).contains("BetterModel");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode creative Viewer");
                server.console("tp Viewer 0.5 64 -9.5 0 8");
                Await.ticks(40);
                server.console("e2e as Viewer bm spawn drake husk 1 0.5 64 0.5");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("model-1-drake.png"));
                server.console("tp Viewer 9.5 66 -6.5 45 15");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("model-2-drake-side.png"));
                server.console("e2e as Viewer bm test drake walk Viewer 0.5 64 6.5");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("model-3-walk.png"));
                assertThat(server.paper.errorsFrom("RainsCore", "BetterModel")).isEmpty();
            }
        }
    }
}
