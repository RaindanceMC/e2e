package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** /seed from anybody but an owner: a warning, then a ban for a day — both on the record. */
@Tag("e2e")
class SeedAttemptScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static boolean said(Bot bot, String fragment) {
        return bot.chatText().stream().anyMatch(line -> line.contains(fragment));
    }

    @Test
    @DisplayName("a player trying /seed is warned, then banned for a day; an owner is left alone")
    void seedIsACheatAttempt() {
        try (Server server = Server.start("seed-attempt",
                List.of("moderation-standalone:RainsModeration-.*", "essentials-standalone:RainsEssentials-.*"),
                List.of("Moderation is up", "Essentials are up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");

            ada.run("seed");
            Await.ticks(40);

            bo.forgetChat();
            bo.run("seed");
            Await.until("Bo is warned", WAIT, () -> said(bo, "warn"));

            bo.run("seed");
            Await.until("Bo is banned", WAIT, () -> server.log().contains("Trying to cheat (/seed)")
                    && server.log().lines().anyMatch(line -> line.contains("Bo") && line.toLowerCase().contains("ban")));

            ada.forgetChat();
            ada.run("history Bo");
            Await.until("the record shows both", WAIT, () -> ada.chatText().stream()
                    .filter(line -> line.contains("Trying to cheat")).count() >= 2);
            ada.forgetChat();
            ada.run("history Ada");
            Await.ticks(40);
            assertThat(ada.chatText()).as("the owner has nothing on the record")
                    .noneMatch(line -> line.contains("Trying to cheat"));
        }
    }
}
