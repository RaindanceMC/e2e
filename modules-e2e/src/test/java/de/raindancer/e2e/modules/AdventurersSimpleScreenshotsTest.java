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
 * The character sheet with its d20, then the same hero with ability scores switched off — no scores, no points to
 * spend, a sheet without their row:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AdventurersSimpleScreenshotsTest}.
 */
@Tag("screenshots")
class AdventurersSimpleScreenshotsTest {

    @Test
    @DisplayName("the sheet with ability scores, and without")
    void simple() {
        try (Server server = Server.start("adventurers-simple", List.of("dnd-standalone:RainsAdventurers-.*"),
                List.of("Adventurers are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:polished_deepslate");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("time set 6000");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("tp Viewer 0.5 64 0.5 0 10");
                Bot scout = server.player("Scout");
                server.console("gamemode spectator Scout");
                scout.runAndOpen("adventurer class", "Choose");
                int wizard = scout.window().flatMap(window -> window.slotNamed("Wizard")).orElseThrow();
                server.console("e2e as Viewer adventurer class");
                Await.ticks(30);
                server.console("e2e click Viewer " + wizard);
                Await.ticks(30);
                server.console("adventurer admin level Viewer 8");
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("simple-1-sheet-d20.png"), 13);

                String answer = server.console("settings set ability-scores false");
                if (!answer.contains("is now")) {
                    answer = server.console("settings set dnd.ability-scores false");
                }
                assertThat(answer).contains("is now");
                Await.ticks(20);
                server.console("e2e as Viewer adventurer");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("simple-2-sheet-without-scores.png"));
                viewer.screenshotHovering(shots.resolve("simple-3-portrait.png"), 4);
                server.console("e2e as Viewer adventurer class");
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("simple-4-classes.png"), wizard);
                server.console("adventurer admin level Viewer 12");
                Await.ticks(20);
                assertThat(server.paper.errorsFrom("RainsCore", "RainsAdventurers")).isEmpty();
            }
        }
    }
}
