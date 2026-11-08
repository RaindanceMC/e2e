package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.ShownDialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The player screen, a dialog: who they are at a glance (online with where, or offline with when they were last
 * seen, what is in force), three doors to three rooms, what is not the viewer's greyed with the reason and saying
 * it when pressed, and the console's text version.
 */
@Tag("e2e")
class PlayerScreenScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    /** Opens {@code command}'s dialog on {@code who} and returns it. */
    private static ShownDialog open(Bot who, String command) {
        int before = who.dialogsShown();
        who.run(command);
        return dump(who, who.awaitDialog(before, WAIT));
    }

    /** Presses {@code label} on the open dialog and returns the one it leads to. */
    private static ShownDialog press(Bot who, String label) {
        int before = who.dialogsShown();
        who.pressDialogButton(label, Map.of());
        return dump(who, who.awaitDialog(before, WAIT));
    }

    private static ShownDialog dump(Bot who, ShownDialog dialog) {
        System.out.println("=== " + who.name() + ": " + dialog.title() + " ===\n" + dialog.text());
        dialog.buttons().forEach(button -> System.out.println("[" + button.label() + "] " + button.tooltip()));
        return dialog;
    }

    private static String tooltip(ShownDialog dialog, String label) {
        return dialog.button(label).orElseThrow(() -> new AssertionError("no " + label + " in " + dialog.buttons()))
                .tooltip();
    }

    @Test
    @DisplayName("an online player shows where, an offline banned one the ban; three doors; what is not yours says so")
    void playerScreen() {
        try (Server server = Server.start("player-screen", List.of("moderation-standalone:RainsModeration-.*"),
                List.of("Moderation is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            Bot cy = server.player("Cy");
            cy.say("hello");
            Await.ticks(10);

            ShownDialog online = open(ada, "mod Bo");
            assertThat(online.title()).contains("Moderation").contains("Bo");
            assertThat(online.text()).contains("Online now").contains("Ping").contains("Java edition")
                    .contains("Nothing in force").contains(bo.id().toString());
            // Three doors, nothing else: the rest is one click further, in a room of its own.
            assertThat(online.buttons()).extracting(ShownDialog.Button::label)
                    .anySatisfy(label -> assertThat(label).contains("Look into"))
                    .anySatisfy(label -> assertThat(label).contains("Punish"))
                    .anySatisfy(label -> assertThat(label).contains("Practical"))
                    .hasSize(4);
            assertThat(tooltip(online, "Punish")).contains("Nothing in force");
            assertThat(tooltip(online, "Practical")).contains("Online now");

            ShownDialog look = press(ada, "Look into");
            assertThat(look.title()).contains("Look into");
            assertThat(look.text()).as("the header comes along").contains("Online now");
            for (String door : List.of("Record", "Audit trail", "Attempts", "Reports", "Mining evidence", "Anti-cheat",
                    "Chat", "Staff notes")) {
                assertThat(look.button(door)).as(door).isPresent();
            }
            assertThat(tooltip(look, "Anti-cheat")).as("the anti-cheat is part of moderation now").contains("Click: their checks");

            open(ada, "mod Bo");
            ShownDialog punish = press(ada, "Punish");
            assertThat(punish.title()).contains("Punish");
            for (String door : List.of("Warnings", "Kicks", "Mutes", "Freezes", "Bans", "Fine", "Broke a rule",
                    "Ban for ever")) {
                assertThat(punish.button(door)).as(door).isPresent();
            }

            open(ada, "mod Bo");
            ShownDialog practical = press(ada, "Practical");
            for (String door : List.of("Inventory", "Go to them", "Fines", "Tools", "Staff rank")) {
                assertThat(practical.button(door)).as(door).isPresent();
            }

            // Support: the same page, but what is not theirs is greyed, says so, and does nothing when pressed;
            // where Bo stands is not shown to somebody who may not teleport to him.
            Bot dee = server.player("Dee");
            server.console("promote Dee support");
            Await.until("Dee is support", WAIT, () -> dee.chatText().stream().anyMatch(line -> line.contains("Support")));
            ShownDialog trial = open(dee, "mod Bo");
            assertThat(trial.text()).doesNotContain("Ping").doesNotContain(" at ");
            ShownDialog trialPractical = press(dee, "Practical");
            assertThat(tooltip(trialPractical, "Go to them")).contains("For whoever may teleport").doesNotContain("world ");
            assertThat(tooltip(trialPractical, "Staff rank")).contains("Only the server owner");
            ShownDialog refused = press(dee, "Staff rank");
            assertThat(refused.text()).as("a locked door opens nothing and says why").contains("Only the server owner");
            assertThat(dee.window()).as("nor does any chest open").isEmpty();

            cy.leave();
            Await.ticks(40);
            ada.forgetChat();
            ada.run("ban Cy griefing");
            Await.until(() -> "Cy is banned (Ada saw " + ada.chatText() + ")", WAIT,
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("Cy is now banned")));
            ShownDialog offline = open(ada, "mod Cy");
            assertThat(offline.text()).contains("Offline").contains("Last seen").contains("Banned").contains("left")
                    .contains("Griefing");
            assertThat(tooltip(offline, "Punish")).contains("Banned");
            assertThat(tooltip(offline, "Practical")).contains("Not online");

            server.console("mod Cy");
            server.paper.awaitLog("Banned", WAIT);
            assertThat(server.paper.errorsFrom("RainsCore", "RainsModeration"))
                    .filteredOn(line -> !line.contains("ran on the thread running the world")).isEmpty();
        }
    }
}
