package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /bugreport against the real GitHub App and repository, photographed with the real client: the reminder, the
 * kinds, the areas, the open reports, a +1 and the title dialog. Needs the app's key in
 * {@code ~/.config/raindancemc/github-app.pem}; skipped without it.
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=BugReportScreenshotsTest}.
 */
@Tag("screenshots")
class BugReportScreenshotsTest {

    private static final Path KEY = Path.of(System.getProperty("user.home"), ".config/raindancemc/github-app.pem");

    @Test
    @DisplayName("from the reminder in chat to the title dialog, with a real +1 on GitHub")
    void screenshots() throws Exception {
        Assumptions.assumeTrue(Files.exists(KEY), "no GitHub App key here");
        try (Server server = Server.start("bugreport-screenshots", List.of("essentials-standalone:RainsEssentials-.*",
                "economy-standalone:RainsEconomy-.*"), List.of())) {
            Path into = server.paper.folder().resolve("plugins/RainsEssentials/github-app.pem");
            Files.createDirectories(into.getParent());
            Files.copy(KEY, into);
            server.console("settings set bugreports:reminder-minutes 1");
            server.console("settings set bugreports:app-id 5668579");
            Await.ticks(100);
            assertThat(into).as("taken into the secrets and removed").doesNotExist();
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode survival Viewer");
                Await.ticks(Integer.getInteger("bug.wait", 1100));
                viewer.screenshot(shots.resolve("bugreport-0-reminder.png"));

                server.console("e2e as Viewer bugreport");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("bugreport-1-kinds.png"));
                String[] clicks = System.getProperty("bug.clicks", "").split(";");
                int step = 2;
                for (String click : clicks) {
                    if (click.isBlank()) {
                        continue;
                    }
                    String[] xy = click.split(",");
                    viewer.click(Integer.parseInt(xy[0].strip()), Integer.parseInt(xy[1].strip()));
                    Await.ticks(xy.length > 2 ? Integer.parseInt(xy[2].strip()) : 60);
                    viewer.screenshot(shots.resolve("bugreport-" + step++ + ".png"));
                }
                assertThat(shots.resolve("bugreport-1-kinds.png")).exists();
            }
        }
    }
}
