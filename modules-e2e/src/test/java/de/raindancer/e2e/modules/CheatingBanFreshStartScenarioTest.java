package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A ban for cheating keeps the record whole and starts the anti-cheat's counts afresh. */
@Tag("e2e")
class CheatingBanFreshStartScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Test
    @DisplayName("a ban for the no-cheating rule is on the record, and the anti-cheat starts counting afresh")
    void cheatingBan() {
        try (Server server = Server.start("cheating-ban",
                List.of("moderation-standalone:RainsModeration-.*", "essentials-standalone:RainsEssentials-.*"),
                List.of("Moderation is up", "Essentials are up"))) {
            Bot ada = server.admin("Ada");
            server.player("Bo");

            // friendly-smp's rule 4 is "No cheating": banned for 14 days the first time.
            ada.runAndExpect("broke Bo 4 flying", "Bo broke rule 4 (No cheating) for the 1st time");
            Await.until("the anti-cheat starts afresh", WAIT, () -> server.log().contains(
                    "was banned for cheating: the anti-cheat's counts on them start afresh"));

            // A ban for another rule leaves the anti-cheat alone.
            int before = (int) server.log().lines().filter(line -> line.contains("start afresh")).count();
            ada.runAndExpect("broke Bo 2 broke a house", "Bo broke rule 2 (No griefing) for the 1st time");
            Await.ticks(40);
            assertThat(server.log().lines().filter(line -> line.contains("start afresh")).count())
                    .as("a griefing ban is no fresh start").isEqualTo(before);
        }
    }
}
