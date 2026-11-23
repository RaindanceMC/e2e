package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Fishing 0.14 on a real server: rare variants, the fishmonger who buys fish, and aquariums with fish in them. */
@Tag("e2e")
class FishingMarketScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Pattern INTS = Pattern.compile("\\[I;\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)]");

    private static boolean said(Bot bot, String fragment) {
        return bot.chatText().stream().anyMatch(line -> line.contains(fragment));
    }

    private static int count(Bot bot, String name) {
        return bot.items().stream().filter(item -> item.name().contains(name)).mapToInt(Bot.Item::amount).sum();
    }

    private static UUID fishmonger(Server server) {
        String answer = Await.value("a fishmonger", WAIT, () -> {
            String out = server.console("data get entity @e[type=minecraft:mannequin,limit=1,nbt={BukkitValues:"
                    + "{\"rainsfishing:fishmonger\":1b}}] UUID");
            return INTS.matcher(out).find() ? out : null;
        });
        Matcher ints = INTS.matcher(answer);
        assertThat(ints.find()).isTrue();
        long most = ((long) Integer.parseInt(ints.group(1)) << 32) | (Integer.parseInt(ints.group(2)) & 0xffffffffL);
        long least = ((long) Integer.parseInt(ints.group(3)) << 32) | (Integer.parseInt(ints.group(4)) & 0xffffffffL);
        return new UUID(most, least);
    }

    @Test
    @DisplayName("a golden fish is its own item; the fishmonger pays ten times for it; aquariums side by side are one, opened like a chest")
    void market() throws Exception {
        try (Server server = Server.start("fishing-market",
                List.of("fishing-standalone:RainsFishing-.*", "economy-standalone:RainsEconomy-.*"),
                List.of("Fishing is up", "The economy is up"))) {
            Bot ada = server.admin("Ada");
            ada.run("clear");

            // ---- variants: a golden trout is a trout underneath, named and described as golden
            ada.run("fishing give Ada fish:pebble_trout:golden");
            Bot.Item golden = ada.expectItem("a golden trout", item -> item.name().contains("Golden Pebble Trout"));
            assertThat(golden.tags().getOrDefault("rainscore:custom-item", "")).isEqualTo("fishing:fish-pebble_trout");
            assertThat(golden.lore()).anyMatch(line -> line.contains("Golden variant") && line.contains("10×"));
            ada.run("fishing give Ada fish:pebble_trout 2");
            Await.until("two plain trout besides", WAIT, () -> ada.items().stream()
                    .anyMatch(item -> item.name().equals("Pebble Trout") && item.amount() == 2));

            // ---- the fishmonger: placed by staff, opened with a right click, buys every fish carried
            ada.forgetChat();
            ada.run("fishing merchant");
            Await.until("he stands there", WAIT, () -> said(ada, "fishmonger stands here"));
            UUID merchant = fishmonger(server);
            server.console("tp Ada ~2 ~ ~");
            Await.until("Ada is shown him", WAIT, () -> ada.sees(merchant));
            ada.interact(merchant);
            Bot.Window counter = ada.awaitWindow("Fishmonger");
            assertThat(counter.slotNamed("Sell every fish you carry")).isPresent();
            assertThat(counter.top().values()).as("three fish wanted today")
                    .filteredOn(item -> item.lore().stream().anyMatch(line -> line.contains("Wanted today")))
                    .hasSize(3);
            ada.forgetChat();
            ada.click("Sell every fish you carry");
            // base price 8: two plain common trout at 8 and a golden one at 80
            Await.until("he paid", WAIT, () -> said(ada, "took 3 fish") && said(ada, "96"));
            Await.until("the trout are gone", WAIT, () -> count(ada, "Pebble Trout") == 0);
            ada.closeWindow();
            ada.run("fishing merchant remove");
            Await.until("he is gone", WAIT, () -> said(ada, "fishmonger is gone"));

            // ---- aquariums: two side by side are one, opened like a chest, a fish in and out, one tank picked up
            ada.run("clear");
            ada.run("fishing give Ada aquarium 2");
            Await.until("two aquariums", WAIT, () -> count(ada, "Aquarium") == 2);
            ada.run("fishing give Ada fish:hydro_dragonet:albino");
            ada.expectItem("an albino dragonet", item -> item.name().contains("Albino Hydro Dragonet"));
            var at = ada.position();
            int x = (int) Math.floor(at.getX()) + 2;
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("fill " + x + " " + (y - 1) + " " + z + " " + (x + 1) + " " + (y - 1) + " " + z + " stone");
            server.console("fill " + x + " " + y + " " + z + " " + (x + 1) + " " + y + " " + z + " air");
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Aquarium")));
            ada.useOn(x, y - 1, z);
            ada.useOn(x + 1, y - 1, z);
            Await.until("both tanks are down", WAIT, () -> server.console("execute if block " + x + " " + y + " " + z
                    + " barrier").contains("passed") && server.console("execute if block " + (x + 1) + " " + y + " "
                    + z + " barrier").contains("passed"));
            assertThat(server.console("data get entity @e[type=minecraft:item_display,limit=1,sort=nearest,x="
                    + (x + 0.5) + ",y=" + (y + 0.5) + ",z=" + (z + 0.5) + "] item.components"))
                    .as("the west tank wears the look joined on the east").contains("aquarium_2");

            // a fish in hand goes straight in
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Hydro Dragonet")));
            ada.forgetChat();
            ada.useOn(x + 1, y, z);
            Await.until("the fish is in", WAIT, () -> count(ada, "Hydro Dragonet") == 0);

            // opened from the other tank, both are one aquarium of six slots holding the dragonet
            ada.hold(8);
            ada.useOn(x, y, z);
            Bot.Window tank = ada.awaitWindow("Aquarium · 2 tanks");
            assertThat(tank.slotNamed("Albino Hydro Dragonet")).contains(0);
            assertThat(tank.top().get(6)).as("the seventh slot is beyond two tanks")
                    .satisfies(item -> assertThat(item.is("black_stained_glass_pane")).isTrue());
            ada.clickSlot(0);
            Await.until("the dragonet is back", WAIT, () -> count(ada, "Albino Hydro Dragonet") == 1);
            ada.closeWindow();

            // mined like a glass block in survival: it breaks, the tank drops as an item; the other stays, on its own
            server.console("gamemode survival Ada");
            var eye = ada.position();
            double dx = x + 1.5 - eye.getX();
            double dy = y + 0.5 - (eye.getY() + 1.62);
            double dz = z + 0.5 - eye.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            ada.look(yaw, pitch);
            Await.ticks(5);
            ada.dig(x + 1, y, z);
            Await.until("the tank breaks", WAIT, () -> server.console("execute if block " + (x + 1) + " " + y + " "
                    + z + " air").contains("passed"));
            Await.until("it dropped as an item", WAIT, () -> server.console("execute if entity @e[type=minecraft:item,"
                    + "x=" + (x + 1.5) + ",y=" + y + ",z=" + (z + 0.5) + ",distance=..3]").contains("passed"));
            assertThat(server.console("execute if block " + x + " " + y + " " + z + " barrier")).contains("passed");
        }
    }
}
