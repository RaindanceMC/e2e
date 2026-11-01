package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RainsFishing on a real server: the items it makes, a sea chest opened in hand, and its pages. */
@Tag("e2e")
class FishingScenarioTest {

    @Test
    @DisplayName("fish, dishes and sea chests are given out; a chest is placed, opened where it stands and taken back; the pages open")
    void fishingWorks() throws Exception {
        try (Server server = Server.start("fishing", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");

            ada.run("fishing give Ada fish:astral_leviathan");
            ada.expectItem("an Astral Leviathan", item -> item.name().contains("Astral Leviathan"));
            ada.run("fishing give Ada dish:astral_sushi");
            ada.expectItem("Astral Sushi", item -> item.name().contains("Astral Sushi"));

            // A sea chest is placed first and opened where it stands; only its opener hears about it.
            var at = ada.position();
            int x = (int) Math.floor(at.getX()) + 2;
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("setblock " + x + " " + (y - 1) + " " + z + " stone");
            server.console("setblock " + x + " " + y + " " + z + " air");
            ada.run("fishing give Ada chest:void");
            Bot.Item chest = ada.expectItem("a Void Chest", item -> item.name().contains("Void Chest"));
            assertThat(chest.lore()).anyMatch(line -> line.contains("Place it"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Void Chest")));
            ada.forgetChat();
            ada.useOn(x, y - 1, z);
            Await.until("the chest is down", Duration.ofSeconds(10),
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("Void Chest is down")));
            ada.expectNoItem("the placed chest", item -> item.name().contains("Void Chest"));
            assertThat(server.console("execute if block " + x + " " + y + " " + z + " barrier"))
                    .as("a sea chest stands as a barrier wearing its model").contains("passed");
            int before = ada.items().size();
            ada.forgetChat();
            ada.useOn(x, y, z);
            Await.until("its rewards come out one by one", Duration.ofSeconds(10), () -> ada.items().size() > before);
            Await.until("the chest has opened", Duration.ofSeconds(30),
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("Void Chest held")));
            assertThat(ada.chatText()).noneMatch(line -> line.contains("opened a Void Chest"));
            assertThat(server.console("execute if block " + x + " " + y + " " + z + " air"))
                    .as("empty, the chest is gone").contains("passed");

            // The owner takes a placed chest back with a sneaking left click.
            server.console("clear Ada");
            Await.ticks(5);
            ada.run("fishing give Ada chest:driftwood");
            ada.expectItem("a Driftwood Chest", item -> item.name().contains("Driftwood Chest"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Driftwood Chest")));
            ada.useOn(x, y - 1, z);
            Await.until("the second chest is down", Duration.ofSeconds(10), () -> ada.items().stream()
                    .noneMatch(item -> item.name().contains("Driftwood Chest")));
            ada.sneak(true);
            ada.dig(x, y, z);
            ada.sneak(false);
            ada.expectItem("the Driftwood Chest back", item -> item.name().contains("Driftwood Chest"));

            Bot.Window root = ada.runAndOpen("fishing", "Fishing").window().orElseThrow();
            assertThat(root.slotNamed("Skills")).isPresent();
            assertThat(root.slotNamed("Rod upgrades")).isPresent();
            ada.click("Collection");
            Bot.Window collection = ada.awaitWindow("Fish collection");
            assertThat(collection.slotNamed("Astral Leviathan").isPresent()
                    || collection.slotNamed("Not caught yet").isPresent()).isTrue();
            ada.closeWindow();

            server.console("clear Ada");
            server.console("give Ada fishing_rod");
            Await.ticks(5);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));
            Bot.Window rod = ada.runAndOpen("fishing rod", "Rod upgrades").window().orElseThrow();
            assertThat(rod.slotNamed("Hook")).isPresent();
            assertThat(rod.slotNamed("Line")).isPresent();
            // Upgrades are earned, not bought: a fresh fisher sees what the hook costs in fishing experience, and why
            // it is not theirs yet.
            Bot.Item hook = rod.top().get(rod.slotNamed("Hook").orElseThrow());
            assertThat(hook.lore()).anyMatch(line -> line.contains("fishing experience"));
            assertThat(String.join(" ", hook.lore())).contains("fishing level");
            ada.closeWindow();

            assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
        }
    }
}
