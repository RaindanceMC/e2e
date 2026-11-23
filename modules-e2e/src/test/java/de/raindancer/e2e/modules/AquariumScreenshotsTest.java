package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A big aquarium in the real client, wearing the pack: six tanks side by side as one, with fish of every temper in it,
 * seen from the front, at an angle and close up, twice a few seconds apart so the swimming shows:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=AquariumScreenshotsTest}.
 */
@Tag("screenshots")
class AquariumScreenshotsTest {

    private static final List<String> FISH = List.of("fish:abyssal_angler", "fish:amethyst_koi", "fish:glow_goby",
            "fish:silver_herring", "fish:silver_herring", "fish:silver_herring", "fish:bog_catfish",
            "fish:pebble_trout:golden", "fish:hydro_dragonet", "fish:raindancer_hoodfin");

    private static int count(Bot bot, String name) {
        return bot.items().stream().filter(item -> item.name().contains(name)).mapToInt(Bot.Item::amount).sum();
    }

    @Test
    @DisplayName("six tanks side by side read as one aquarium, its fish swimming each their own way")
    void aquarium() throws java.io.IOException {
        try (Server server = Server.start("aquarium-looks", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -16 63 -16 16 63 16 minecraft:polished_deepslate");
            server.console("fill -16 64 -16 16 80 16 minecraft:air");
            server.console("time set 6000");
            server.console("gamerule doDaylightCycle false");

            // ---- a bot builds it: tanks at x 2..4, z 0..1, standing on the floor
            Bot ada = server.admin("Ada");
            server.console("tp Ada 0.5 64 0.5");
            Await.ticks(20);
            ada.run("clear");
            ada.run("fishing give Ada aquarium 6");
            Await.until("six aquariums", Duration.ofSeconds(10), () -> count(ada, "Aquarium") == 6);
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Aquarium")));
            for (int x = 2; x <= 4; x++) {
                for (int z = 0; z <= 1; z++) {
                    ada.useOn(x, 63, z);
                    Await.ticks(4);
                }
            }
            Await.until("all six are down", Duration.ofSeconds(10), () -> {
                for (int x = 2; x <= 4; x++) {
                    for (int z = 0; z <= 1; z++) {
                        if (!server.console("execute if block " + x + " 64 " + z + " barrier").contains("passed")) {
                            return false;
                        }
                    }
                }
                return true;
            });
            // the fish, put in one at a time with a right click on the tanks, spread over the blocks
            int n = 0;
            for (String fish : FISH) {
                ada.run("clear");
                ada.run("fishing give Ada " + fish);
                Await.until(fish, Duration.ofSeconds(10), () -> ada.items().stream().anyMatch(item ->
                        item.tags().getOrDefault("rainscore:custom-item", "").startsWith("fishing:fish-")));
                ada.hold(ada.hotbarSlotOf(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                        .startsWith("fishing:fish-")));
                ada.useOn(2 + n % 3, 64, n / 3 % 2);
                n++;
                Await.ticks(6);
            }
            Await.ticks(20);
            String displays = server.console(
                    "execute if entity @e[type=minecraft:item_display,x=3.5,y=64.5,z=1,distance=..3]");
            assertThat(displays).as("six tanks and ten fish shown; Ada heard: " + ada.chatText())
                    .contains("Count: 16");
            server.console("tp Ada -12 64 -12");

            // ---- the real client looks at it
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("tp Viewer 3.5 65.4 -3.6 0 22");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("aquarium-1-front.png"));
                Await.ticks(60);
                viewer.screenshot(shots.resolve("aquarium-1b-front-later.png"));
                server.console("tp Viewer 7.8 66.2 -2.6 50 30");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("aquarium-2-angle.png"));
                Await.ticks(60);
                viewer.screenshot(shots.resolve("aquarium-2b-angle-later.png"));
                server.console("tp Viewer 3 64.7 -1.4 0 8");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("aquarium-3-close.png"));
                Await.ticks(40);
                viewer.screenshot(shots.resolve("aquarium-3b-close-later.png"));
                server.console("tp Viewer 3.5 68.5 1 0 90");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("aquarium-4-top.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsFishing")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every model and texture")
                        .doesNotContain("rainsfishing:").doesNotContain("rainscore:");
            }
        }
    }
}
