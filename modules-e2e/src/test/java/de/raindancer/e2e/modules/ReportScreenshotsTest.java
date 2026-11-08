package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report lists, photographed with the real game: the hub and the queue coloured by severity. Filing a report
 * and the report card are dialogs now; ModerationDialogsScreenshotsTest photographs those.
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ReportScreenshotsTest}; pictures under target/e2e/screenshots.
 */
@Tag("screenshots")
class ReportScreenshotsTest {

    @Test
    @DisplayName("reports: the hub and the queue")
    void screenshots() {
        try (Server server = Server.start("report-screenshots", List.of("moderation-standalone:RainsModeration-.*"),
                List.of("Moderation is up"))) {
            Bot bo = server.player("Bo");
            Bot cy = server.player("Cy");
            Bot dee = server.player("Dee");
            Bot eve = server.player("Eve");
            Await.ticks(40);
            bo.say("hey cy, nice house");
            Await.ticks(10);
            cy.say("nobody asked you, loser");
            Await.ticks(10);
            bo.say("why are you like this");
            Await.ticks(10);
            cy.say("go cry about it lol");
            Await.ticks(20);

            // A chat report through the windows, and a few typed ones: one player three people reported.
            int shown = bo.dialogsShown();
            bo.run("report Cy");
            bo.awaitDialog(shown, java.time.Duration.ofSeconds(15));
            shown = bo.dialogsShown();
            bo.pressDialogButton("Chat & behaviour", Map.of());
            bo.awaitDialog(shown, java.time.Duration.ofSeconds(15));
            bo.pressDialogButton("Send the report", Map.of("reason", "harassment",
                    "what", "he insults me every time I say hello, see chat"));
            Await.ticks(20);
            dee.run("report Cy griefing the village by spawn, blew up the church");
            Await.ticks(70);
            eve.run("report Cy stealing from the community chests at spawn");
            Await.ticks(70);
            cy.run("report Dee spam in chat, same message ten times");
            Await.ticks(20);

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                server.console("op Viewer");
                Path shots = E2e.out().resolve("screenshots");
                List<Path> taken = new ArrayList<>();
                taken.add(shot(server, viewer, "mod reports", null, 11, shots.resolve("report-3-hub.png")));
                taken.add(shot(server, viewer, "mod reports", 11, 0, shots.resolve("report-4-waiting.png")));
                assertThat(taken).allSatisfy(path -> assertThat(path).exists());
                assertThat(server.paper.errorsFrom("RainsCore", "RainsModeration")).isEmpty();
            }
        }
    }

    private static Path shot(Server server, RealClient viewer, String command, Integer click, Integer hover, Path target) {
        server.console("e2e as Viewer " + command);
        Await.ticks(40);
        if (click != null) {
            server.console("e2e click Viewer " + click);
            Await.ticks(40);
        }
        return hover == null ? viewer.screenshot(target) : viewer.screenshotHovering(target, hover);
    }
}
