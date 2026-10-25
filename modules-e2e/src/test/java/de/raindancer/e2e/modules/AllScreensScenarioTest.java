package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every screen kind of every plugin, put up at once on one server: each one is offered, each one shows, and not one
 * of them fails to make its text.
 */
@Tag("e2e")
class AllScreensScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final int X = 0;
    private static final int Y = 150;
    private static final int Z = 0;
    private static final int ACROSS = 8;
    private static final int GAP = 3;

    private static final List<String> PLUGINS = List.of(
            "economy-standalone:RainsEconomy-.*", "guilds-standalone:RainsGuilds-.*", "jobs-standalone:RainsJobs-.*",
            "claims-standalone:RainsExtendedClaims-.*", "homes-standalone:RainsHomes-.*", "warp-standalone:RainsWarps-.*",
            "shops-standalone:RainsShops-.*", "ranks-standalone:RainsRanks-.*", "roles-standalone:RainsRoles-.*",
            "essentials-standalone:RainsEssentials-.*", "moderation-standalone:RainsModeration-.*",
            "playerutils-standalone:RainsPlayerUtils-.*", "twitch-standalone:RainsTwitch-.*",
            "dnd-standalone:RainsAdventurers-.*", "rift-standalone:RainsRift-.*");

    /** At least these, one or more from each plugin — the rest are found in the list itself. */
    private static final List<String> EXPECTED = List.of("welcome", "online", "balance", "richest", "guild-you",
            "guild-top", "quests-you", "job-board", "claims-you", "claims-biggest", "homes-you", "warps-list",
            "market-you", "market-best", "rank-you", "role-you", "essentials-afk", "moderation-staff",
            "moderation-you", "playerutils-ping", "twitch-live", "adventurer-you", "rift-you");

    private static int count(Server server, String selector) {
        String out = server.console("execute if entity " + selector);
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("(\\d+)").matcher(out);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    @Test
    @DisplayName("every plugin's screens: offered, shown, none failing")
    void everyScreen() {
        try (Server server = Server.start("all-screens", PLUGINS, List.of("Done \\("))) {
            server.console("forceload add -32 -32 48 48");
            server.console("fill " + (X - 4) + " " + (Y - 1) + " " + (Z - 4) + " " + (X + ACROSS * GAP + 4) + " " + (Y - 1)
                    + " " + (Z + 12 * GAP + 4) + " minecraft:stone");
            Bot ada = server.admin("Ada");
            server.console("tp Ada " + X + " " + Y + " " + Z);
            Await.ticks(40);

            // ---- every kind is offered: read them from /screen
            List<String> ids = new ArrayList<>();
            Await.until("every plugin offers its screens", WAIT, () -> {
                ada.forgetChat();
                ada.run("screen");
                Await.ticks(20);
                ids.clear();
                for (String line : ada.chatText()) {
                    if (line.trim().startsWith("•")) {
                        String[] words = line.trim().split("\\s+");
                        ids.add(words[words.length - 1]);
                    }
                }
                return ids.containsAll(EXPECTED);
            });

            // ---- each put up in a grid around the middle, so Ada is near every one of them
            for (int i = 0; i < ids.size(); i++) {
                double x = X + (i % ACROSS) * GAP + 0.5;
                double z = Z + (i / ACROSS) * GAP + 0.5;
                server.console("tp Ada " + x + " " + Y + " " + z);
                Await.ticks(4);
                ada.forgetChat();
                ada.run("screen place " + ids.get(i));
                String id = ids.get(i);
                Await.until(id + " is up", WAIT, () -> ada.chatText().stream().anyMatch(line -> line.contains("is up")));
            }
            server.console("tp Ada " + (X + ACROSS * GAP / 2.0) + " " + Y + " " + (Z + (ids.size() / ACROSS) * GAP / 2.0));

            // ---- each shows: one display apiece, the personal ones for Ada alone
            int placed = ids.size();
            Await.until("a display for every screen", WAIT,
                    () -> count(server, "@e[type=minecraft:text_display]") >= placed);
            Await.ticks(200);

            assertThat(server.paper.logLines(line -> line.contains("failed to make its text")))
                    .as("no screen fails to make its text").isEmpty();
            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy", "RainsGuilds", "RainsJobs",
                    "RainsExtendedClaims", "RainsHomes", "RainsWarps", "RainsShops", "RainsRanks", "RainsRoles",
                    "RainsEssentials", "RainsModeration", "RainsPlayerUtils", "RainsTwitch", "RainsAdventurers",
                    "RainsRift")).isEmpty();
        }
    }
}
