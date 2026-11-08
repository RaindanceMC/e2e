package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /profile as the game's own window, photographed with the real client by a player who is no staff: somebody else's
 * profile (their face big, when they were here, what the installed modules offer) and their own.
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ProfileScreenshotsTest -Drainscore.version=2.69.0}.
 */
@Tag("screenshots")
class ProfileScreenshotsTest {

    @Test
    @DisplayName("/profile Notch and /profile, seen by an ordinary player")
    void screenshots() {
        try (Server server = Server.start("profile-screenshots", List.of("essentials-standalone:RainsEssentials-.*",
                "economy-standalone:RainsEconomy-.*"), List.of())) {
            server.player("Notch");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                // Core's resource pack carries the font the big face is drawn in.
                viewer.click(484, 408);
                Await.ticks(300);
                Path shots = E2e.out().resolve("screenshots");
                List<Path> taken = new ArrayList<>();
                server.console("e2e as Viewer profile Notch");
                Await.ticks(80);
                taken.add(viewer.screenshot(shots.resolve("profile-1-somebody.png")));
                server.console("e2e as Viewer profile");
                Await.ticks(80);
                taken.add(viewer.screenshot(shots.resolve("profile-2-own.png")));
                assertThat(taken).allSatisfy(path -> assertThat(path).exists());
                assertThat(server.paper.errorsFrom("RainsCore", "RainsEssentials"))
                        .filteredOn(line -> !line.contains("ran on the thread running the world")).isEmpty();
            }
        }
    }
}
