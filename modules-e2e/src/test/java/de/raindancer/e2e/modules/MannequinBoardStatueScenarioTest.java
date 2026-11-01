package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A statue standing for a place on the fishing level board: it shows who holds the place, and nobody for an empty one. */
@Tag("e2e")
class MannequinBoardStatueScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Test
    @DisplayName("a statue of place #1 on the fishing board shows the best fisher and their level; #2 stands for nobody")
    void boardStatues() throws Exception {
        try (Server server = Server.start("mannequin-board", List.of("mannequin-standalone:RainsMannequins-.*",
                "fishing-standalone:RainsFishing-.*", "claims-standalone:RainsExtendedClaims-.*"),
                List.of("Mannequins are up", "Fishing is up", "Claims are up"))) {
            Bot ada = server.admin("Ada");

            ada.run("fishing xp Ada 1000");
            Await.ticks(20);

            ada.forgetChat();
            ada.run("mannequin statue top fishing:level 1");
            Await.until("Ada is told the statue stands", WAIT,
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("place #1")));
            Await.until("the statue is of Ada, with the place, the board and her level under the name", WAIT,
                    () -> ada.entityTexts().stream().anyMatch(text -> text.contains("#1 Fishing level · level")));
            assertThat(ada.entityTexts()).anyMatch(text -> text.contains("Ada"));

            ada.forgetChat();
            ada.run("mannequin statue top fishing:level 2");
            Await.until("the second statue is placed", WAIT,
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("place #2")));
            Await.until("nobody is second", WAIT, () -> ada.entityTexts().stream()
                    .anyMatch(text -> text.contains("#2 Fishing level"))
                    && ada.entityTexts().stream().anyMatch(text -> text.contains("Nobody yet")));

            ada.forgetChat();
            ada.run("mannequin statue top fishing:nonsense 1");
            Await.until("an unknown board is refused", WAIT,
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("no leaderboard called")));
            ada.run("mannequin statue top fishing:level 11");
            Await.until("place 11 is refused", WAIT,
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("from 1 to 10")));
        }
    }
}
