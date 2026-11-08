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
 * Ranks drawn as Core's badges on a server whose pack is required — in chat and over a head, beside the name and
 * above it: {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=RankBadgesScreenshotsTest}.
 */
@Tag("screenshots")
class RankBadgesScreenshotsTest {

    @Test
    @DisplayName("a rank is a badge in chat and over the head, beside the name and, switched, above it")
    void screenshots() {
        try (Server server = Server.start("rank-badges-screenshots", List.of("ranks-standalone:RainsRanks-.*",
                "chat-standalone:RainsChat-.*", "cosmetics-standalone:RainsCosmetics-.*"), List.of())) {
            server.console("settings set core:packs-required true");
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:smooth_stone");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("time set 6000");
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                Bot rain = server.player("Rain");
                server.console("rank give Rain owner");
                server.console("rank give Viewer mod");
                server.console("tp Rain 0.5 64 -3.5 0 0");
                server.console("tp Viewer 0.5 64 0.5 180 0");
                Await.ticks(60);
                rain.say("hey, look at my badge");
                server.console("e2e as Viewer say and mine");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("rank-badges-1-beside.png"));

                server.console("settings set core:nametag-badge-above true");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("rank-badges-2-above.png"));
                assertThat(shots.resolve("rank-badges-2-above.png")).exists();
            }
        }
    }
}
