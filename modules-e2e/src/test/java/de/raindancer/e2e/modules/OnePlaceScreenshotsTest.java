package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import de.raindancer.e2e.ShownDialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A player's own settings on /preferences and every way to travel on /travel, each one window gathered from the
 * plugins, and both on /hub. A bot checks it, the real client takes the pictures — {@code mvn -o -Pscreenshots test
 * -pl modules-e2e -Dtest=OnePlaceScreenshotsTest}. Needs the standalone jars below under jars/.
 */
@Tag("screenshots")
class OnePlaceScreenshotsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final List<String> PLUGINS = List.of("homes-standalone:RainsHomes-.*",
            "warp-standalone:RainsWarps-.*", "tpa-standalone:RainsTPA-.*", "rtp-standalone:RainsRandomTeleport-.*",
            "healthbars-standalone:RainsHealthBars-.*", "veintoggle-standalone:RainsVeinToggle-.*",
            "essentials-standalone:RainsEssentials-.*");

    @Test
    @DisplayName("preferences and ways to travel are each one window, reached from the hub")
    void onePlace() throws Exception {
        try (Server server = Server.start("one-place", PLUGINS, List.of())) {
            Bot bo = server.player("Bo");
            Await.ticks(40);

            ShownDialog prefs = open(bo, "preferences");
            assertThat(prefs.title()).contains("Preferences");
            assertThat(prefs.buttons()).extracting(ShownDialog.Button::label)
                    .anyMatch(label -> label.startsWith("Show health bars"))
                    .anyMatch(label -> label.contains("Teleport requests") || label.contains("teleport requests"));
            String bars = prefs.buttons().stream().map(ShownDialog.Button::label)
                    .filter(label -> label.startsWith("Show health bars")).findFirst().orElseThrow();
            int shown = bo.dialogsShown();
            bo.pressDialogButton("Show health bars", Map.of());
            ShownDialog flipped = bo.awaitDialog(shown, WAIT);
            String after = flipped.buttons().stream().map(ShownDialog.Button::label)
                    .filter(label -> label.startsWith("Show health bars")).findFirst().orElseThrow();
            assertThat(after).as("pressing it flips it").isNotEqualTo(bars);

            ShownDialog travel = open(bo, "travel");
            assertThat(travel.title()).contains("Travel");
            assertThat(travel.buttons()).extracting(ShownDialog.Button::label)
                    .contains("Homes", "Warps", "Another player", "Random spot", "Back", "Spawn");
            bo.pressDialogButton("Homes", Map.of());
            Bot.Window homes = bo.awaitWindow("");
            int back = homes.slotNamed("Back").orElseThrow();
            assertThat(homes.items().get(back).lore()).as("Back from homes goes to /travel")
                    .anyMatch(line -> line.contains("Travel"));
            shown = bo.dialogsShown();
            bo.click("Back");
            assertThat(bo.awaitDialog(shown, WAIT).title()).contains("Travel");

            Bot.Window hub = bo.runAndOpen("hub", "").window().orElseThrow();
            assertThat(hub.slotNamed("Your preferences")).isPresent();
            assertThat(hub.slotNamed("Travel")).isPresent();
            assertThat(hub.slotNamed("Server settings")).as("staff only").isEmpty();
            bo.closeWindow();

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("e2e as Viewer preferences");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("one-place-1-preferences.png"));
                server.console("e2e as Viewer travel");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("one-place-2-travel.png"));
                server.console("e2e as Viewer hub");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("one-place-3-hub.png"));
            }
            assertThat(server.paper.errorsFrom("RainsCore", "RainsHomes", "RainsWarps", "RainsTPA", "RainsRandomTeleport",
                    "RainsHealthBars", "RainsVeinToggle", "RainsEssentials")).isEmpty();
        }
    }

    private static ShownDialog open(Bot bot, String command) {
        int before = bot.dialogsShown();
        bot.run(command);
        return bot.awaitDialog(before, WAIT);
    }
}
