package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every enchantment's shard in the real game's hotbar, nine to a picture, wearing the server pack Core builds
 * from what the enchants plugin offers — accepted on the client's own prompt. {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ShardScreenshotsTest};
 * pictures under target/e2e/screenshots.
 */
@Tag("screenshots")
class ShardScreenshotsTest {

    private static final List<String> SHARDS = List.of(
            "night_vision", "double_jump", "swiftness", "haste", "wisdom", "harvester", "smelting", "telekinesis",
            "xp_siphon", "veinminer", "excavator", "chest_link", "auto_sell", "boost", "volley", "bleed", "venom",
            "cleave", "armor_pierce", "flight", "launch", "soulbound", "heat_seeking", "lifesteal", "executioner",
            "berserk", "overpower", "undying", "fusion", "autopilot", "thor", "reinforced");

    @Test
    @DisplayName("every shard has its own model in the hotbar")
    void screenshots() throws java.io.IOException {
        try (Server server = Server.start("shard-screenshots", List.of("enchants-standalone:RainsEnchants-.*"),
                List.of("Enchants are up"))) {
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                server.console("op Viewer");
                Path shots = E2e.out().resolve("screenshots");
                viewer.screenshot(shots.resolve("shards-offer.png"));
                viewer.click(484, 408);
                Await.ticks(300);
                List<Path> taken = new ArrayList<>();
                for (int from = 0; from < SHARDS.size(); from += 9) {
                    server.console("clear Viewer");
                    for (String shard : SHARDS.subList(from, Math.min(SHARDS.size(), from + 9))) {
                        server.console("give Viewer " + shard + "_shard 1");
                    }
                    Await.ticks(40);
                    taken.add(viewer.screenshot(shots.resolve("shards-" + (from / 9 + 1) + ".png")));
                }
                assertThat(taken).allSatisfy(path -> assertThat(path).exists());
                assertThat(server.paper.errorsFrom("RainsCore", "RainsEnchants")).isEmpty();
                String log = Files.readString(E2e.out().resolve("client-Viewer").resolve("client.log"));
                assertThat(log).as("the client found every model and texture").doesNotContain("rainsenchants:");
            }
        }
    }
}
