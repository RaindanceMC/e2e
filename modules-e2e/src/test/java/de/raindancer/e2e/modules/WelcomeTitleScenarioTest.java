package de.raindancer.e2e.modules;

import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RainsEssentials on a real server: whoever logs on is greeted by name with a title of their own. */
@Tag("e2e")
class WelcomeTitleScenarioTest {

    @Test
    @DisplayName("logging on shows a welcome title with the player's name and the server")
    void greeted() {
        try (Server server = Server.start("welcometitle", List.of("essentials-standalone:RainsEssentials-.*"),
                List.of("Essentials"))) {
            Bot bo = server.player("Bo");
            bo.expectTitle("Welcome home, Bo");
            assertThat(bo.subtitles()).anyMatch(line -> line.startsWith("Glad to see you on"));
            assertThat(server.paper.errorsFrom("RainsEssentials")).isEmpty();
        }
    }
}
