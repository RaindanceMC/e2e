package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A player's very first join is quiet: a book with getting started, the rules and what is kept about them lands in the
 * inventory, and none of the plugins' first-join lines fill the chat. {@code /rules} opens the rules as a book.
 */
@Tag("e2e")
class FirstJoinBookScenarioTest {

    private static final List<String> PLUGINS = List.of("essentials-standalone:RainsEssentials-.*",
            "skills-standalone:RainsSkills-.*", "jobs-standalone:RainsJobs-.*", "roles-standalone:RainsRoles-.*");

    /** What used to arrive on a first join, every line of it. */
    private static final List<String> NOT_IN_CHAT = List.of("Server rules", "1. Be kind", "New here?",
            "Chat on this server is kept", "Every adventurer level", "You have no role yet", "quest(s) open today");

    @Test
    @DisplayName("a first join hands over the book and leaves the chat to the welcome line; /rules opens a book")
    void quietFirstJoin() throws Exception {
        try (Server server = Server.start("first-join-book", PLUGINS, List.of("Done \\("))) {
            server.console("settings set remind-on-join false");   // as on Hyades
            Bot newbie = server.player("Newbie");
            Await.until(() -> "the book is in Newbie's inventory (has " + newbie.items() + ")", Duration.ofSeconds(20),
                    () -> newbie.items().stream().anyMatch(item -> item.is("written_book")));
            // Contents, getting started, the eight rules of the default preset (a long one over two pages), your data.
            String pages = "Inventory[{id:\"minecraft:written_book\"}].components.\"minecraft:written_book_content\".pages";
            assertThat(server.console("execute if data entity Newbie " + pages + "[10]")).contains("passed");
            assertThat(server.console("execute if data entity Newbie " + pages + "[20]")).doesNotContain("passed");

            Await.ticks(20 * 15);   // past every plugin's delayed first-join line
            List<String> said = newbie.chat().stream().map(Bot.Line::text).toList();
            for (String line : NOT_IN_CHAT) {
                assertThat(said).as("not in chat: " + line).noneMatch(text -> text.contains(line));
            }
            assertThat(said).as("the welcome line still greets them").anyMatch(text -> text.contains("Welcome Newbie"));

            newbie.run("rules");
            newbie.expectNoChat("Be kind", Duration.ofSeconds(3));
            assertThat(server.paper.errorsFrom("RainsCore", "RainsEssentials", "RainsSkills", "RainsJobs", "RainsRoles"))
                    .isEmpty();
        }
    }
}
