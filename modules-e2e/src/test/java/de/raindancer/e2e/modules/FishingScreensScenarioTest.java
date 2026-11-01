package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsFishing's pages as a seasoned fisher sees them — level, skills learnt, an upgraded rod — and every window
 * written out as JSON ({@code -Dfishing.guis=<folder>}), so they can be drawn and looked at.
 */
@Tag("e2e")
class FishingScreensScenarioTest {

    private static final Path OUT = Path.of(System.getProperty("fishing.guis", "target/fishing-guis"));

    @Test
    @DisplayName("the fishing pages open for a fisher with levels, skills and an upgraded rod")
    void screens() throws Exception {
        Files.createDirectories(OUT);
        try (Server server = Server.start("fishing-screens", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            server.console("clear Ada");
            ada.run("fishing xp Ada 6000");
            Await.ticks(20);
            for (String fish : List.of("pebble_trout", "pearl_snapper", "astral_leviathan")) {
                ada.run("fishing give Ada fish:" + fish);
            }
            server.console("give Ada fishing_rod");
            Await.ticks(5);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));

            Bot.Window root = ada.runAndOpen("fishing", "Fishing").window().orElseThrow();
            dump("1-fishing", root);
            ada.closeWindow();

            Bot.Window skills = ada.runAndOpen("fishing skills", "Fishing skills").window().orElseThrow();
            ada.click("Rare Instinct");
            Await.ticks(15);
            ada.click("Rare Instinct");
            Await.ticks(15);
            ada.click("Treasure Sense");
            Await.ticks(15);
            skills = ada.window().orElseThrow();
            dump("2-skills", skills);
            ada.closeWindow();

            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.click("Hook");
            Await.ticks(15);
            ada.click("Hook");
            Await.ticks(15);
            ada.click("Reel");
            Await.ticks(15);
            Bot.Window rod = ada.window().orElseThrow();
            dump("3-rod", rod);
            assertThat(String.join(" ", rod.top().get(rod.slotNamed("Hook").orElseThrow()).name()))
                    .contains("Hook II");
            ada.closeWindow();

            Bot.Window collection = ada.runAndOpen("fishing collection", "Fish collection").window().orElseThrow();
            dump("4-collection", collection);
            ada.closeWindow();

            Bot.Item heldRod = ada.expectItem("the upgraded rod", item -> item.is("fishing_rod"));
            Files.writeString(OUT.resolve("5-rod-item.json"), item(heldRod));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
        }
    }

    private static void dump(String name, Bot.Window window) throws Exception {
        String slots = window.top().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey() + "\":" + item(entry.getValue()))
                .collect(Collectors.joining(","));
        Files.writeString(OUT.resolve(name + ".json"), "{\"title\":" + quote(window.title()) + ",\"size\":"
                + window.size() + ",\"slots\":{" + slots + "}}");
    }

    private static String item(Bot.Item item) {
        String lore = item.lore().stream().map(FishingScreensScenarioTest::quote).collect(Collectors.joining(","));
        String tags = item.tags().entrySet().stream().map(tag -> quote(tag.getKey()) + ":" + quote(tag.getValue()))
                .collect(Collectors.joining(","));
        return "{\"material\":" + quote(item.material()) + ",\"amount\":" + item.amount() + ",\"name\":"
                + quote(item.name()) + ",\"lore\":[" + lore + "],\"tags\":{" + tags + "}}";
    }

    private static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
