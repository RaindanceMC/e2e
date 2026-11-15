package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Approved map art arrives as one bundle holding every map of the picture. */
@Tag("e2e")
class PrimeMapArtScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final Pattern ASKED = Pattern.compile("Map art (\\w+) \\(2×2 maps\\)");

    @Test
    @DisplayName("a 2×2 map art is handed over as a bundle with its four maps")
    void deliveredInABundle() throws IOException {
        String scenario = "prime-mapart";
        try (Server server = Server.start(scenario, List.of("prime-standalone:RainsPrime-.*"), List.of("Done \\("))) {
            Bot ada = server.admin("Ada");
            server.console("clear Ada");
            // The link is never fetched: the picture is put where the request keeps it, as if fetched at asking time.
            ada.run("prime mapart https://example.invalid/castle.png 2x2");
            String id = Await.value("Ada is told her request waits", WAIT, () -> ada.chatText().stream()
                    .map(ASKED::matcher).filter(Matcher::find).map(found -> found.group(1)).findFirst().orElse(null));

            // a plugin with one module owns its whole data folder
            Path data = server.folder().resolve("plugins").resolve("RainsPrime");
            Path kept = data.resolve("mapart").resolve(id).resolve("asked.png");
            Files.createDirectories(kept.getParent());
            ImageIO.write(new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB), "png", kept.toFile());

            String approved = server.console("prime mapart approve " + id);
            Await.until(() -> "the maps reach Ada after: " + approved + " / " + server.console("data get entity Ada Inventory"),
                    WAIT, () -> server.console("data get entity Ada Inventory").contains("minecraft:bundle"));

            // the console cuts long NBT short, so count with execute if data, which answers "Count: N"
            assertThat(count(server, "Inventory[]")).as("one item in Ada's inventory, no map lies loose").isEqualTo(1);
            assertThat(count(server, "Inventory[{Slot:0b,id:\"minecraft:bundle\"}]")).as("and it is a bundle").isEqualTo(1);
            assertThat(count(server, "Inventory[0].components.\"minecraft:bundle_contents\"[{id:\"minecraft:filled_map\"}]"))
                    .as("holding the four maps").isEqualTo(4);
        }
    }

    private static int count(Server server, String path) {
        Matcher found = Pattern.compile("(?i)count: (\\d+)").matcher(server.console("execute if data entity Ada " + path));
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }
}
