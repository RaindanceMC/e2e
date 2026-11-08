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

/**
 * A chat report from start to end on a real server: a player reports another for harassment through the
 * reason menus; staff see it in the queue coloured by how bad it is, open its card, read the chat around it
 * — both sides of the conversation — deal with it, find it again under Closed, and by searching the name.
 */
@Tag("e2e")
class ReportsScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static int slotNamed(Bot bot, String text) {
        return Await.value("a button named " + text, WAIT, () -> bot.window().flatMap(window -> window.top()
                .entrySet().stream().filter(entry -> entry.getValue().name().contains(text))
                .map(Map.Entry::getKey).findFirst()).orElse(null));
    }

    private static String button(Bot bot, String text) {
        Bot.Item item = bot.window().orElseThrow().top().get(slotNamed(bot, text));
        return item.material() + " " + item.name() + " | " + String.join(" / ", item.lore());
    }

    private static void expect(Bot bot, String what, String text) {
        Await.until(what + " (" + bot.name() + " saw " + bot.chatText() + ")", WAIT,
                () -> bot.chatText().stream().anyMatch(line -> line.contains(text)));
    }

    @Test
    @DisplayName("a chat report: reason menus, severity colour, the card, the chat around it, closing, Closed, search")
    void chatReport() {
        try (Server server = Server.start("reports",
                List.of("moderation-standalone:RainsModeration-.*"), List.of("Moderation is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            Bot cy = server.player("Cy");
            Await.ticks(40);

            bo.say("hey cy, nice house");
            Await.ticks(10);
            cy.say("nobody asked you, loser");
            Await.ticks(10);

            // Bo reports: the kind of thing, then the exact reason and what happened, in one window.
            int shown = bo.dialogsShown();
            bo.run("report Cy");
            var kinds = bo.awaitDialog(shown, WAIT);
            assertThat(kinds.title()).contains("Report Cy");
            assertThat(kinds.button("Chat & behaviour").orElseThrow().tooltip()).contains("Harassment or bullying")
                    .contains("Hate speech");
            shown = bo.dialogsShown();
            bo.pressDialogButton("Chat & behaviour", java.util.Map.of());
            var form = bo.awaitDialog(shown, WAIT);
            assertThat(form.inputs()).containsExactly("reason", "what");
            bo.pressDialogButton("Send the report", java.util.Map.of("reason", "harassment",
                    "what", "he insults me every time I say hello"));
            expect(bo, "Bo's report is filed", "Reported.");
            expect(ada, "staff hear of it with a link to open it", "[Open]");

            // Nobody without the right to read reports gets a card, even typing its id.
            cy.run("mod report R1");
            Await.ticks(20);
            assertThat(cy.window()).as("no report card for a player").isEmpty();
            assertThat(cy.dialog()).as("not as a dialog either").isEmpty();

            // The hub says how many wait and how bad the worst is.
            ada.run("mod reports");
            ada.awaitWindow("Reports");
            assertThat(button(ada, "Waiting")).contains("1 report").contains("worst: High");
            ada.clickSlot(slotNamed(ada, "Waiting"));
            ada.awaitWindow("Waiting");
            String queued = button(ada, "Cy");
            assertThat(queued).startsWith("ORANGE_CONCRETE").contains("Harassment or bullying")
                    .contains("he insults me").contains("every time I say hello").contains("Filed by Bo, just now");

            // The card: everything on it.
            shown = ada.dialogsShown();
            ada.clickSlot(slotNamed(ada, "Cy"));
            var card = ada.awaitDialog(shown, WAIT);
            assertThat(card.title()).contains("Report R1");
            assertThat(card.text()).contains("High").contains("Harassment or bullying").contains("he insults me")
                    .contains("every time I say hello").contains("Filed").contains("Waiting");
            assertThat(card.button("Where it was reported").orElseThrow().tooltip()).contains("world");
            assertThat(card.button("Chat around it").orElseThrow().tooltip()).contains("This report is about chat");

            ada.pressDialogButton("Chat around it", java.util.Map.of());
            ada.awaitWindow("Chat around");
            assertThat(button(ada, "nobody asked you")).contains("The reported player").contains("s before the report");
            assertThat(button(ada, "hey cy")).contains("Who filed the report");
            ada.closeWindow();

            // Dealt with, from the card; the reporter is told.
            shown = ada.dialogsShown();
            ada.run("mod report R1");
            ada.awaitDialog(shown, WAIT);
            bo.forgetChat();
            ada.pressDialogButton("Dealt with", java.util.Map.of("outcome", "warned him for insults"));
            expect(bo, "Bo hears what became of it", "warned him for insults");

            ada.run("mod reports");
            ada.awaitWindow("Reports");
            assertThat(button(ada, "Waiting")).contains("0 reports");
            assertThat(button(ada, "Closed")).contains("1 report");
            ada.clickSlot(slotNamed(ada, "Closed"));
            ada.awaitWindow("Closed");
            assertThat(button(ada, "Cy")).startsWith("LIGHT_GRAY_CONCRETE").contains("warned him for insults");
            ada.closeWindow();

            // Search by name.
            ada.run("mod reports");
            ada.awaitWindow("Reports");
            int asked = ada.dialogsShown();
            ada.clickSlot(slotNamed(ada, "Search by name"));
            ada.awaitDialog(asked, WAIT);
            ada.pressDialogButton("Use", java.util.Map.of("answer", "cy"));
            ada.awaitWindow("Cy");
            assertThat(button(ada, "Cy")).contains("Harassment or bullying");

            assertThat(server.paper.errorsFrom("RainsCore", "RainsModeration")).isEmpty();
        }
    }
}
