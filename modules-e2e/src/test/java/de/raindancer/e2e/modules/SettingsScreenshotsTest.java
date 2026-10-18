package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /settings photographed with the real game on a server carrying several plugins: the menu, a plugin's page, a
 * setting's tooltip, and what the command says in chat.
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=SettingsScreenshotsTest}; pictures under
 * target/e2e/screenshots. Each shot is {@code name:step,step,…}: {@code click N}, {@code hover N}, {@code cmd …}.
 */
@Tag("screenshots")
class SettingsScreenshotsTest {

    /** Chat first: once a menu is open, nothing the console can do closes it again. */
    private static final List<String> SHOTS = List.of(
            "settings-help-chat:chat settings help",
            "settings-wrong-chat:chat settings enchnats",
            "settings-get-chat:chat settings set flight-seconds-per-level 45,chat settings get switched-off",
            "settings-root:cmd settings",
            "settings-root-hover:cmd settings,hover 12",
            "settings-plugin:cmd settings,click 12",
            "settings-plugin-hover:cmd settings,click 12,hover 19",
            "settings-plugin-hover-toggles:cmd settings,click 12,hover 20",
            "settings-changed:cmd settings changed,hover 13");

    @Test
    @DisplayName("/settings: the menu, a page, a tooltip and the command's own words")
    void screenshots() {
        try (Server server = Server.start("settings-screenshots",
                List.of("enchants-standalone:RainsEnchants-.*", "essentials-standalone:RainsEssentials-.*",
                        "economy-standalone:RainsEconomy-.*", "guilds-standalone:RainsGuilds-.*"),
                List.of("Enchants are up", "The economy is up"))) {
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                server.console("op Viewer");
                Await.ticks(20);
                Path shots = E2e.out().resolve("screenshots");
                List<Path> taken = new ArrayList<>();
                for (String shot : SHOTS) {
                    String name = shot.substring(0, shot.indexOf(':'));
                    Integer hover = null;
                    for (String step : shot.substring(shot.indexOf(':') + 1).split(",")) {
                        String[] parts = step.split(" ", 2);
                        switch (parts[0]) {
                            case "cmd", "chat" -> {
                                server.console("e2e as Viewer " + parts[1]);
                                Await.ticks(30);
                            }
                            case "click" -> {
                                server.console("e2e click Viewer " + parts[1]);
                                Await.ticks(30);
                            }
                            case "hover" -> hover = Integer.parseInt(parts[1]);
                            default -> throw new IllegalArgumentException(step);
                        }
                    }
                    Path target = shots.resolve(name + ".png");
                    taken.add(hover == null ? viewer.screenshot(target) : viewer.screenshotHovering(target, hover));
                    // Closes the menu and clears the chat, so the next shot shows only its own.
                    server.console("e2e as Viewer minecraft:tell Viewer .");
                    for (int line = 0; line < 20; line++) {
                        server.console("minecraft:tellraw Viewer \"\"");
                    }
                    Await.ticks(10);
                }
                assertThat(taken).allSatisfy(path -> assertThat(path).exists());
            }
        }
    }
}
