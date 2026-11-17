package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import de.raindancer.e2e.ShownDialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything worn is put on in /cosmetics, the game's own window: chat colour, Capture the Flag looks, elytra looks,
 * rod skins and pet looks are buttons there, the pickers' Back and the plugins' own commands lead back to it, and
 * "clear" takes the chat colour off too. A bot checks it, the real client takes the pictures — {@code mvn -o
 * -Pscreenshots test -pl modules-e2e -Dtest=CosmeticsInOnePlaceScreenshotsTest}. Needs the six standalone jars.
 */
@Tag("screenshots")
class CosmeticsInOnePlaceScreenshotsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final List<String> PLUGINS = List.of("cosmetics-standalone:RainsCosmetics-.*",
            "chat-standalone:RainsChat-.*", "elytra-standalone:RainsElytra-.*", "fishing-standalone:RainsFishing-.*",
            "pets-standalone:RainsPets-.*", "ctf-standalone:RainsCaptureTheFlag-.*");

    @Test
    @DisplayName("every plugin's cosmetic is a button in the cosmetics window, Back leads there, clear takes all off")
    void inOnePlace() throws Exception {
        try (Server server = Server.start("cosmetics-in-one-place", PLUGINS, List.of("Cosmetics is up"))) {
            Bot ada = server.admin("Ada");
            Await.ticks(20);

            ShownDialog cosmetics = open(ada);
            assertThat(cosmetics.title()).contains("Cosmetics");
            for (String door : List.of("Name style", "Particles", "Chat colour", "Elytra looks", "Rod skins",
                    "Pet looks", "Clear my cosmetics")) {
                assertThat(cosmetics.button(door)).as(door + " is in the cosmetics window").isPresent();
            }
            assertThat(cosmetics.button("Capture the Flag")).as("not on a server that only also has it").isEmpty();
            assertThat(cosmetics.button("Unlocks")).as("nothing is for sale here").isEmpty();
            assertThat(server.console("settings set lobby-on-join true")).contains("is now");
            Await.ticks(10);
            assertThat(open(ada).button("Capture the Flag looks")).as("on a server of its own").isPresent();
            assertThat(server.console("settings set lobby-on-join false")).contains("is now");
            Await.ticks(10);
            assertThat(open(ada).button("Capture the Flag")).as("gone again with the switch").isEmpty();
            assertThat(tooltip(cosmetics, "Chat colour")).contains("Yours: plain");
            assertThat(tooltip(cosmetics, "Clear my cosmetics")).as("nothing worn, nothing to clear")
                    .contains("not wearing anything");

            ada.pressDialogButton("Chat colour", Map.of());
            Bot.Window colours = ada.awaitWindow("chat colour");
            assertThat(lore(colours, "Back")).as("the picker goes back to the window")
                    .anyMatch(line -> line.contains("Cosmetics"));
            int gold = colours.slotNamed("Gold").orElseThrow();
            ada.click("Gold");
            Await.ticks(10);
            int before = ada.dialogsShown();
            ada.click("Back");
            ShownDialog again = ada.awaitDialog(before, WAIT);
            assertThat(tooltip(again, "Chat colour")).as("the button says what is worn").doesNotContain("Yours: plain");
            assertThat(tooltip(again, "Clear my cosmetics")).as("a chat colour alone is something to clear")
                    .doesNotContain("not wearing anything");

            Bot.Window typed = ada.runAndOpen("chatstyle", "chat colour").window().orElseThrow();
            assertThat(lore(typed, "Back")).as("/chatstyle goes back to the cosmetics window")
                    .anyMatch(line -> line.contains("Cosmetics"));
            ada.closeWindow();
            Bot.Window elytra = ada.runAndOpen("elytra", "").window().orElseThrow();
            assertThat(lore(elytra, "Back")).as("/elytra goes back to the cosmetics window")
                    .anyMatch(line -> line.contains("Cosmetics"));
            ada.closeWindow();

            server.console("e2e as Ada cosmetics clear");
            Await.ticks(20);
            assertThat(tooltip(open(ada), "Chat colour")).as("clear took the chat colour off too")
                    .contains("Yours: plain");

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("op Viewer");
                server.console("give Viewer elytra");
                Await.ticks(10);
                server.console("e2e as Viewer chatstyle");
                Await.ticks(30);
                server.console("e2e click Viewer " + gold);
                Await.ticks(20);
                viewer.screenshot(shots.resolve("cosmetics-2-chat-colour.png"));
                server.console("e2e as Viewer cosmetics");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("cosmetics-1-window.png"));
                server.console("e2e as Viewer elytra");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("cosmetics-3-elytra-looks.png"));
                server.console("e2e as Viewer cosmetics particle");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("cosmetics-4-particles.png"));
            }
            assertThat(server.paper.errorsFrom("RainsCore", "RainsCosmetics", "RainsChat", "RainsElytra",
                    "RainsFishing", "RainsPets", "RainsCaptureTheFlag")).isEmpty();
        }
    }

    private static ShownDialog open(Bot bot) {
        int before = bot.dialogsShown();
        bot.run("cosmetics");
        return bot.awaitDialog(before, WAIT);
    }

    private static String tooltip(ShownDialog dialog, String button) {
        return dialog.button(button).orElseThrow(() -> new AssertionError("no " + button + " in "
                + dialog.buttons().stream().map(ShownDialog.Button::label).toList())).tooltip();
    }

    private static List<String> lore(Bot.Window window, String named) {
        int slot = window.slotNamed(named).orElseThrow(() -> new AssertionError("no " + named + " in " + window.title()));
        return window.items().get(slot).lore();
    }
}
