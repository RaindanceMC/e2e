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
 * The moderation windows as the game's own dialogs, photographed with the real client: a player's page, punishing
 * (a kind, the reasons, the length, the question), a fine, the notes, a report card and filing a report.
 * Each walk starts with a command and goes on by clicking where the dialog draws its buttons;
 * {@code -Dmod.walks="mod Notch|300,200;..."} replaces the default walks, {@code -Dmod.setup} the console commands before.
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ModerationDialogsScreenshotsTest -Drainscore.version=2.67.0}.
 */
@Tag("screenshots")
class ModerationDialogsScreenshotsTest {

    /** A command run as the viewer, then clicks — x,y in the client's window — each followed by a picture. */
    private static final String WALKS = String.join(";",
            "mod Notch",                                               // a player's page, their face big
            "mod Notch|640,549",                                       // punish
            "mod Notch|640,549|488,545",                               // mutes
            "mod Notch|640,549|488,545|640,433",                       // why
            "mod Notch|640,549|488,545|640,433|640,375",               // how long
            "mod Notch|640,549|488,545|640,433|640,375|488,285",       // are you sure
            "mod Notch|640,549|792,633",                               // ban for ever
            "mod Notch|640,549|792,589",                               // a fine without money on the server: says why
            "mod Notch|640,505",                                       // look into
            "mod Notch|640,505|792,633",                               // notes
            "mod Notch|640,593",                                       // practical
            "report Notch",                                            // reporting: what kind
            "report Notch|488,451",                                    // reporting: exactly what, and what happened
            "mod report R1");                                          // a report card

    @Test
    @DisplayName("the moderation dialogs, walked through with the real game")
    void screenshots() {
        try (Server server = Server.start("moderation-dialogs-screenshots",
                List.of("moderation-standalone:RainsModeration-.*"), List.of("Moderation is up"))) {
            server.player("Notch");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                // Core's resource pack carries the font the big faces are drawn in: accept its offer.
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("op Viewer");
                Await.ticks(40);
                for (String setUp : System.getProperty("mod.setup", "e2e as Notch report Viewer griefed the wheat field by the spawn").split(";")) {
                    if (!setUp.isBlank()) {
                        server.console(setUp.strip());
                        Await.ticks(20);
                    }
                }
                Path shots = E2e.out().resolve("screenshots");
                List<Path> taken = new ArrayList<>();
                int walk = 0;
                for (String steps : System.getProperty("mod.walks", WALKS).split(";")) {
                    String[] parts = steps.split("\\|");
                    server.console("e2e as Viewer " + parts[0]);
                    Await.ticks(60);
                    for (int at = 1; at < parts.length; at++) {
                        String[] xy = parts[at].split(",");
                        viewer.click(Integer.parseInt(xy[0].strip()), Integer.parseInt(xy[1].strip()));
                        Await.ticks(60);
                    }
                    taken.add(viewer.screenshot(shots.resolve("moderation-dialog-" + walk++ + ".png")));
                }
                assertThat(taken).allSatisfy(path -> assertThat(path).exists());
                // Core's own complaint about a YAML store touched on the world's thread when somebody is opped comes before
                // any window opens and is not these windows' business.
                assertThat(server.paper.errorsFrom("RainsCore", "RainsModeration"))
                        .filteredOn(line -> !line.contains("ran on the thread running the world")).isEmpty();
            }
        }
    }
}
