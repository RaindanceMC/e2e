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

/** Giving back one item from an inventory snapshot, from the snapshot's own page, without restoring the rest. */
@Tag("e2e")
class InvSnapSingleItemScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Test
    @DisplayName("staff click one item in a snapshot: only that item comes back, on top of what the player carries now")
    void oneItemBack() {
        try (Server server = Server.start("invsnap-item", List.of("invsnap-standalone:RainsInventorySnapshots-.*"),
                List.of("Inventory snapshots are up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("give Bo minecraft:diamond_sword[minecraft:custom_name=\"Bo's Blade\","
                    + "minecraft:enchantments={\"minecraft:sharpness\":5}] 1");
            server.console("give Bo minecraft:dirt 16");
            Await.until("Bo carries both", WAIT, () -> bo.carrying(item -> item.is("diamond_sword")).isPresent()
                    && bo.carrying(item -> item.is("dirt")).isPresent());

            bo.leave();  // a snapshot is taken on the way out
            Await.ticks(20);
            bo.rejoin();
            server.console("clear Bo");
            server.console("give Bo minecraft:stone 1");
            Await.until("Bo has only the stone", WAIT, () -> bo.carrying(item -> item.is("diamond_sword")).isEmpty()
                    && bo.carrying(item -> item.is("stone")).isPresent());

            ada.runAndOpen("invsnap Bo", "Snapshots");
            int newest = ada.window().orElseThrow().top().entrySet().stream()
                    .filter(entry -> entry.getValue().is("chest")).map(Map.Entry::getKey).min(Integer::compare).orElseThrow();
            ada.clickSlot(newest);
            ada.click("Inspect");
            Bot.Item shown = Await.value(() -> "the snapshot page shows Bo's Blade (open: " + ada.window().map(window -> window.title()
                            + " " + window.top().values().stream().map(Bot.Item::name).toList()).orElse("nothing") + ")", WAIT,
                    () -> ada.window().filter(window -> window.slotNamed("Off hand").isPresent()).flatMap(window ->
                            window.top().values().stream().filter(item -> item.name().contains("Bo's Blade")).findFirst())
                            .orElse(null));
            assertThat(shown.lore()).anyMatch(line -> line.contains("Click to give only this back to Bo"));
            ada.click("Bo's Blade");
            ada.forgetChat();
            ada.click("Yes");

            Await.until("the sword is back", WAIT, () -> bo.carrying(item -> item.is("diamond_sword")).isPresent());
            ada.expectChat("Gave 1x Bo's Blade back to Bo");
            Await.ticks(20);
            assertThat(bo.carrying(item -> item.is("stone"))).as("what Bo carries now is kept").isPresent();
            assertThat(bo.carrying(item -> item.is("dirt"))).as("the rest of the snapshot stays put").isEmpty();
            assertThat(server.paper.errorsFrom("RainsCore", "RainsInventorySnapshots")).isEmpty();
        }
    }
}
