package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code /hub} and {@code /smp}: a door per installed module, hidden for those whose commands a player cannot use. */
@Tag("e2e")
class HubScenarioTest {

    static final List<String> PLUGINS = List.of("claims-standalone:.*", "homes-standalone:.*",
            "economy-standalone:.*", "essentials-standalone:.*", "moderation-standalone:.*",
            "warp-standalone:.*", "tpa-standalone:.*", "jobs-standalone:.*",
            "rtp-standalone:.*", "roles-standalone:.*", "cosmetics-standalone:.*");

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static de.raindancer.e2e.ShownDialog hub(Bot bot, String command) {
        int before = bot.dialogsShown();
        bot.run(command);
        return bot.awaitDialog(before, WAIT);
    }

    @Test
    @DisplayName("hub opens through both names, shows doors by what the viewer may use, and a door lists its commands")
    void hub() {
        try (Server server = Server.start("hub", PLUGINS, List.of())) {
            Await.ticks(60);
            Bot bo = server.player("Bo");
            Bot ada = server.admin("Ada");

            de.raindancer.e2e.ShownDialog hub = hub(bo, "hub");
            assertThat(hub.button("Warps")).isPresent();
            assertThat(hub.button("Homes")).as("every /home command needs a permission Bo lacks").isEmpty();
            assertThat(hub.button("Your preferences")).isPresent();
            assertThat(hub.button("Server settings")).as("staff only").isEmpty();
            int before = bo.dialogsShown();
            bo.pressDialogButton("Warps", Map.of());
            Await.until("Warps open, as a page or as its window of commands", WAIT, () ->
                    bo.window().map(window -> window.title().contains("Warp")).orElse(false)
                            || bo.dialogsShown() > before && bo.dialog().map(dialog -> dialog.button("/warp").isPresent())
                            .orElse(false));
            bo.closeWindow();

            assertThat(hub(bo, "smp").title()).as("/smp is the hub too").isEqualTo(hub.title());

            de.raindancer.e2e.ShownDialog staff = hub(ada, "hub");
            assertThat(staff.button("Homes")).isPresent();
            assertThat(staff.button("Moderation")).isPresent();

            assertThat(hub(ada, "hub homes").title()).contains("Homes");

            String console = server.console("hub");
            assertThat(console).contains("feature(s)");
            assertThat(server.paper.errorsFrom("RainsCore")).isEmpty();
        }
    }
}
