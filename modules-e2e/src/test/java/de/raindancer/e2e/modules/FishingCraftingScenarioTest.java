package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;


/**
 * Fishing's crafted items on a real server. The aquarium carries Core's no-ingredient mark; Core 2.117.0 refused
 * taking any marked result, so the aquarium showed in the result slot and could not be taken out.
 */
@Tag("e2e")
class FishingCraftingScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    /** A crafting table's window: 0 the result, 1–9 the grid, 37–45 the hotbar. */
    private static final int RESULT = 0;
    private static final int HOTBAR = 37;

    private static int countOf(Bot bot, String material) {
        return bot.items().stream().filter(item -> item.material().toLowerCase(java.util.Locale.ROOT).endsWith(material))
                .mapToInt(Bot.Item::amount).sum();
    }

    @Test
    @DisplayName("an aquarium crafted at a crafting table can be taken out of the result slot")
    void aquarium() throws Exception {
        try (Server server = Server.start("fishing-crafting", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            server.console("clear Ada");
            server.console("item replace entity Ada hotbar.0 with minecraft:glass 5");
            server.console("item replace entity Ada hotbar.1 with minecraft:water_bucket");
            server.console("item replace entity Ada hotbar.2 with minecraft:sand");
            server.console("item replace entity Ada hotbar.3 with minecraft:oak_planks 2");
            Await.until("the ingredients arrived", WAIT, () -> countOf(ada, "oak_planks") == 2 && countOf(ada, "glass") == 5);

            var at = ada.position();
            int x = (int) Math.floor(at.getX()) + 2;
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("setblock " + x + " " + y + " " + z + " minecraft:crafting_table");
            Await.until("the table stands", WAIT, () -> server.console("execute if block " + x + " " + y + " " + z
                    + " minecraft:crafting_table").contains("passed"));
            ada.useOn(x, y, z);
            Await.until("a crafting window", WAIT, () -> ada.window()
                    .filter(window -> window.type().toUpperCase(java.util.Locale.ROOT).contains("CRAFTING")).isPresent());

            // GGG / GWG / PSP
            ada.clickSlot(HOTBAR);
            for (int grid : new int[]{1, 2, 3, 4, 6}) {
                ada.rightClickSlot(grid);
            }
            ada.clickSlot(HOTBAR + 1).clickSlot(5);
            ada.clickSlot(HOTBAR + 2).clickSlot(8);
            ada.clickSlot(HOTBAR + 3).rightClickSlot(7).rightClickSlot(9);
            Await.until("the aquarium shows as the result", WAIT, () -> ada.window()
                    .map(window -> window.items().get(RESULT)).filter(item -> item.name().contains("Aquarium"))
                    .isPresent());

            ada.shiftClickSlot(RESULT);
            // While a window is open the bot's inventory mirrors it, result slot included; the grid tells the truth.
            Await.until("the grid is used up, only the empty bucket left", WAIT, () -> ada.window()
                    .map(window -> window.items().entrySet().stream().filter(slot -> slot.getKey() <= 9)
                            .allMatch(slot -> slot.getValue().is("BUCKET")))
                    .orElse(false));
            ada.closeWindow();
            // Asked of the server: the bot files items taken from a crafting window under odd slot numbers.
            Await.until("Ada carries the aquarium", WAIT, () -> server.console(
                    "clear Ada minecraft:paper[minecraft:custom_data~{PublicBukkitValues:{\"rainscore:custom-item\":\"fishing:aquarium\"}}] 0")
                    .contains("Found 1 matching"));
        }
    }
}
