package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * /guild, /skills, /hub and /preferences as the game's own windows, with a tooltip each so the mouse shows — pictures for
 * Tom, taken by the real client: {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=NativeWindowsScreenshotsTest}.
 */
@Tag("screenshots")
class NativeWindowsScreenshotsTest {

    @Test
    void windows() throws Exception {
        try (Server server = Server.start("native-windows", List.of("guilds-standalone:RainsGuilds-.*",
                "skills-standalone:RainsSkills-.*", "enchants-standalone:RainsEnchants-.*"), List.of())) {
            server.console("settings set founding-price 0");
            server.console("settings set founding-hours 0");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("op Viewer");
                server.console("e2e as Viewer guild create Elves ELF");
                Await.ticks(60);
                server.console("skills admin level Viewer 14");
                for (String window : List.of("guild", "skills", "hub", "preferences")) {
                    server.console("e2e as Viewer " + window);
                    Await.ticks(40);
                    viewer.screenshot(shots.resolve("native-" + window + ".png"));
                    viewer.screenshotPointingAt(shots.resolve("native-" + window + "-hover.png"),
                            Integer.getInteger("hover.x", 380), Integer.getInteger("hover.y", 330));
                }
            }
        }
    }
}
