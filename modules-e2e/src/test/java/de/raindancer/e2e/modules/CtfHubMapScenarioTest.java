package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real hub a server is given: it builds, and somebody joining stands on its spawn — not falling, not stuck in a
 * block. A download that may not be published, so it is not in this repository: point {@code CTF_HUB} at the folder
 * holding its {@code hub.schem} and {@code hub.yml}.
 */
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "CTF_HUB", matches = ".+")
class CtfHubMapScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(90);

    @Test
    @DisplayName("the real hub builds and everybody lands on their feet")
    void realHub() throws Exception {
        Path source = Path.of(System.getenv("CTF_HUB"));
        try (Server server = Server.start("ctf-hub-map", List.of("ctf-standalone:RainsCaptureTheFlag-.*"), List.of(),
                Map.of("plugins/RainsCaptureTheFlag/config.yml", "lobby-on-join: true\nauto-start: false\n"),
                List.of("Capture the Flag is up"))) {
            Path hub = server.paper.folder().resolve("plugins/RainsCaptureTheFlag/hub");
            Files.copy(source.resolve("hub.schem"), hub.resolve("hub.schem"));
            Files.copy(source.resolve("hub.yml"), hub.resolve("hub.yml"));
            server.paper.console("ctf admin hub build");
            server.paper.awaitLog("The hub is built", WAIT);

            Bot alice = server.player("alice");
            Await.until("alice is in the hub", WAIT, () -> alice.world().contains("ctf_hub"));
            Await.ticks(40);
            double y = alice.position().getY();
            Await.ticks(60);
            System.out.println("[ctf-hub] landed at " + alice.position());
            assertThat(Math.abs(alice.position().getY() - y)).as("standing, not falling").isLessThan(0.1);
            assertThat(alice.health()).as("not suffocating in a block").isEqualTo(20f);
            assertThat(server.paper.errorsFrom("RainsCaptureTheFlag", "RainsCore")).isEmpty();
        }
    }
}
