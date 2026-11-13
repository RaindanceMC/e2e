package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsWorldGate on a real server: a scheduled Nether is closed until its time, everybody online is counted down to it
 * (a reminder a minute before, a boss bar, the last seconds as titles with a sound) and it opens by itself with a
 * title — then a player may go in.
 */
@Tag("e2e")
class WorldGateScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Test
    @DisplayName("a scheduled Nether is counted down to, opens by itself with a title, and lets players in")
    void scheduledOpening() {
        try (Server server = Server.start("worldgate", List.of("worldgate-standalone:RainsWorldGate-.*"),
                List.of("World Gate is up"))) {
            Bot bo = server.player("Bo");
            Await.ticks(20);
            Instant at = Instant.now().plusSeconds(75).truncatedTo(ChronoUnit.SECONDS);
            assertThat(server.console("worldgate schedule nether " + at)).contains("opens by itself");

            bo.runAndExpect("worldgate status", "opens");

            bo.expectBossBar("opens in");
            Await.until("Bo is reminded a minute before (" + bo.chatText() + ")", Duration.ofSeconds(30),
                    () -> bo.chatText().stream().anyMatch(line -> line.contains("opens in 1 minute")));
            bo.forgetEffects();
            Await.until("the last seconds are counted as titles (" + bo.titles() + ")", Duration.ofSeconds(70),
                    () -> bo.titles().containsAll(List.of("3", "2", "1")));
            assertThat(bo.soundsHeard()).as("each second ticks").isNotEmpty();
            Await.until("the Nether opens with a title (" + bo.titles() + ")", WAIT,
                    () -> bo.titles().stream().anyMatch(title -> title.contains("The Nether")));
            Await.until("Bo is told in chat", WAIT,
                    () -> bo.chatText().stream().anyMatch(line -> line.contains("is open now")));
            Await.until("the bar is gone (" + bo.bossBars() + ")", WAIT, () -> bo.bossBars().isEmpty());
            assertThat(server.console("worldgate status")).contains("OPEN");
            assertThat(server.log()).contains("The Nether opened as scheduled.");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsWorldGate"))
                    .filteredOn(line -> !line.contains("resource pack server could not start"))
                    .filteredOn(line -> !line.contains("ran on the thread running the world")).isEmpty();
        }
    }
}
