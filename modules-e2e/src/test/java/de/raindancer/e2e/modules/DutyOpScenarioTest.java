package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** With staff powers needing admin mode, an operator is op only in admin mode — and op again for good when it goes off. */
@Tag("e2e")
class DutyOpScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    /** Whether Ada may run an op-only vanilla command right now, asked the way she would. */
    private static boolean mayOp(Bot ada) {
        ada.forgetChat();
        ada.run("seed");
        Await.ticks(20);
        return ada.chatText().stream().anyMatch(line -> line.contains("commands.seed.success"));
    }

    @Test
    @DisplayName("an operator is op only in admin mode while powers need it, and op for good once that is off")
    void opOnlyOnDuty() throws Exception {
        try (Server server = Server.start("dutyop", List.of("essentials-standalone:RainsEssentials-.*",
                "moderation-standalone:RainsModeration-.*"), List.of("Essentials are up", "Moderation is up"))) {
            Bot ada = server.admin("Ada");
            boolean before = mayOp(ada);
            assertThat(before).as("an operator, with the switch off; chat: " + ada.chatText()).isTrue();

            assertThat(server.console("settings set staff.powers-need-admin-mode true")).contains("is now");
            Await.until("she is op no more off duty", WAIT, () -> !mayOp(ada));

            ada.run("admin on");
            Await.until("in admin mode she is op", WAIT, () -> mayOp(ada));
            ada.run("admin off");
            Await.until("out of it she is not", WAIT, () -> !mayOp(ada));

            assertThat(server.console("settings set staff.powers-need-admin-mode false")).contains("is now");
            Await.until("with the switch off she is op again for good", WAIT, () -> mayOp(ada));
        }
    }
}
