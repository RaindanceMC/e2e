package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

/** A statue stays: its chunk unloading (nobody near it) and loading again puts it back. */
@Tag("e2e")
class MannequinChunkReloadScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Test
    @DisplayName("a statue whose chunk unloads is there again when somebody comes back")
    void statueSurvivesItsChunk() {
        try (Server server = Server.start("mannequin-chunks",
                List.of("mannequin-standalone:RainsMannequins-.*", "claims-standalone:RainsExtendedClaims-.*"),
                List.of("Mannequins are up", "Claims are up"))) {
            Bot ada = server.admin("Ada");
            // Spawn chunks would keep it loaded for ever; a quiet corner of a real server does not.
            server.console("gamerule spawnChunkRadius 0");
            server.console("tp Ada 400 100 400");
            server.console("fill 395 99 395 405 99 405 minecraft:stone");
            Await.ticks(40);

            ada.forgetChat();
            ada.run("mannequin statue Ada");
            Await.until("the statue stands", WAIT, () -> server.console(
                    "execute if entity @e[type=minecraft:mannequin,x=400,y=100,z=400,distance=..5]").contains("passed"));

            server.console("tp Ada 6000 120 6000");
            Await.until("its chunk has unloaded", WAIT, () -> !server.console(
                    "execute if loaded 400 100 400").contains("passed"));

            server.console("tp Ada 401 100 401");
            Await.until("the statue is back", WAIT, () -> server.console(
                    "execute if entity @e[type=minecraft:mannequin,x=400,y=100,z=400,distance=..5]").contains("passed"));
            Await.ticks(40);
            org.assertj.core.api.Assertions.assertThat(server.console(
                    "execute if entity @e[type=minecraft:mannequin,x=400,y=100,z=400,distance=..5]"))
                    .as("the statue stands once, not twice").contains("Count: 1");
        }
    }
}
