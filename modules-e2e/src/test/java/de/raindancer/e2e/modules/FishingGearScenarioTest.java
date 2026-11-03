package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fishing 0.2 on a real server: fixed spots, bait, the fishing net and rod skins. */
@Tag("e2e")
class FishingGearScenarioTest {

    private static int count(Bot bot, String name) {
        return bot.items().stream().filter(item -> item.name().contains(name)).mapToInt(Bot.Item::amount).sum();
    }

    private static boolean said(Bot bot, String fragment) {
        return bot.chatText().stream().anyMatch(line -> line.contains(fragment));
    }

    @Test
    @DisplayName("an admin's fixed spot stays and blocks others; bait goes one per cast; a net holds fish; a skin token dresses a rod")
    void gear() throws Exception {
        try (Server server = Server.start("fishing-gear", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            var at = ada.position();
            int x = (int) Math.floor(at.getX());
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("fill " + (x + 2) + " " + (y - 3) + " " + (z - 6) + " " + (x + 14) + " " + (y - 1) + " "
                    + (z + 6) + " water");
            server.console("fill " + (x + 2) + " " + y + " " + (z - 6) + " " + (x + 14) + " " + (y + 3) + " "
                    + (z + 6) + " air");
            ada.look(-90f, 30f);
            Await.ticks(10);

            // fixed spots: put down for good, nothing else on top of them, listed, taken away
            ada.forgetChat();
            ada.run("fishing spot create epic 6");
            Await.until("the fixed spot is out", Duration.ofSeconds(10), () -> said(ada, "for good"));
            assertThat(ada.chatText()).anyMatch(line -> line.contains("Abyss Shoal") && line.contains("6 blocks"));
            ada.forgetChat();
            ada.run("fishing spot");
            Await.until("no spot inside another", Duration.ofSeconds(5), () -> said(ada, "already covers"));
            ada.forgetChat();
            ada.run("fishing spot list");
            Await.until("the list names it", Duration.ofSeconds(5), () -> said(ada, "Abyss Shoal"));

            // bait: one off the off hand per cast
            server.console("clear Ada");
            ada.run("fishing give Ada bait:wriggler 3");
            ada.expectItem("wriggler bait", item -> item.name().contains("Wriggler Bait"));
            int baitSlot = ada.hotbarSlotOf(item -> item.name().contains("Wriggler Bait"));
            server.console("item replace entity Ada weapon.offhand from entity Ada hotbar." + baitSlot);
            server.console("item replace entity Ada hotbar." + baitSlot + " with air");
            ada.run("fishing give Ada rod:reed");
            Bot.Item reed = ada.expectItem("a Reed Rod", item -> item.name().contains("Reed Rod"));
            assertThat(reed.lore()).as("the lore compares the rod with a plain one")
                    .anyMatch(line -> line.contains("sooner than with a plain rod"))
                    .anyMatch(line -> line.contains("Sea chests"));
            ada.run("fishing xp Ada 10");
            Await.ticks(10);
            server.console("give Ada fishing_rod");
            Await.ticks(10);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod") && !item.name().contains("Reed")));
            Await.until("the bait is in the off hand", Duration.ofSeconds(5), () -> count(ada, "Wriggler Bait") == 3);
            ada.useHeld();
            Await.until("a cast takes one bait", Duration.ofSeconds(10), () -> count(ada, "Wriggler Bait") == 2);
            ada.useHeld();
            Await.ticks(10);

            // the fishing net: fish go in, fish come out
            ada.run("fishing give Ada fish:silver_herring 5");
            ada.expectItem("herring", item -> item.name().contains("Silver Herring"));
            ada.run("fishing give Ada net:fishing");
            ada.expectItem("a net", item -> item.name().contains("Fishing Net"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Fishing Net")));
            ada.useHeld();
            Await.until("the net opens", Duration.ofSeconds(10),
                    () -> ada.window().map(window -> window.title().contains("Fishing Net")).orElse(false));
            ada.click("Put every fish in");
            Await.until("the herring are in the net", Duration.ofSeconds(10), () -> ada.window()
                    .map(window -> window.top().values().stream().anyMatch(item -> item.name().contains("Silver Herring")
                            && item.amount() == 5)).orElse(false));
            assertThat(count(ada, "Silver Herring")).as("none left in the bag").isZero();
            ada.clickSlot(0);
            Await.until("the herring are back", Duration.ofSeconds(10), () -> count(ada, "Silver Herring") == 5);
            ada.closeWindow();

            // a skin token used, then worn on a rod
            ada.forgetChat();
            ada.run("fishing give Ada skin:starfall");
            ada.expectItem("a skin token", item -> item.name().contains("Starfall Rod Skin"));
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Starfall Rod Skin")));
            ada.useHeld();
            Await.until("the skin is hers", Duration.ofSeconds(10), () -> said(ada, "rod skin is yours"));
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod") && !item.name().contains("Reed")));
            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.click("Rod skins");
            Await.until("the skins page opens", Duration.ofSeconds(10),
                    () -> ada.window().map(window -> window.title().contains("Rod skins")).orElse(false));
            ada.forgetChat();
            ada.click("Starfall");
            Await.until("the rod wears it", Duration.ofSeconds(10), () -> said(ada, "now wears"));
            ada.closeWindow();

            // Wearing a skin is for supporters: Bo keeps the skin he redeems but cannot put it on.
            Bot bo = server.player("Bo");
            server.console("fishing give Bo skin:starfall");
            server.console("fishing give Bo rod:reed");
            bo.expectItem("a skin token", item -> item.name().contains("Starfall Rod Skin"));
            bo.hold(bo.hotbarSlotOf(item -> item.name().contains("Starfall Rod Skin")));
            bo.useHeld();
            Await.until("the skin is his", Duration.ofSeconds(10), () -> said(bo, "rod skin is yours"));
            bo.hold(bo.hotbarSlotOf(item -> item.name().contains("Reed Rod")));
            bo.runAndOpen("fishing rod", "Rod upgrades");
            bo.click("Rod skins");
            Await.until("his skins page opens", Duration.ofSeconds(10),
                    () -> bo.window().map(window -> window.title().contains("Rod skins")).orElse(false));
            bo.forgetChat();
            bo.click("Starfall");
            Await.until("a thank-you for supporters", Duration.ofSeconds(10), () -> said(bo, "thank-you for supporters"));
            bo.closeWindow();

            ada.forgetChat();
            ada.run("fishing spot remove");
            Await.until("the fixed spot is gone", Duration.ofSeconds(10), () -> said(ada, "gone for good"));
        }
    }
}
