package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Everything worn is put on in /cosmetics: chat colour, Capture the Flag looks, elytra looks, rod skins and pet looks
 * are doors there, their own commands go back to it, and "clear" takes the chat colour off too. A bot checks it, the
 * real client takes the pictures — {@code mvn -o -Pscreenshots test -pl modules-e2e
 * -Dtest=CosmeticsInOnePlaceScreenshotsTest}. Needs the six standalone jars under jars/.
 */
@Tag("screenshots")
class CosmeticsInOnePlaceScreenshotsTest {

    private static final List<String> PLUGINS = List.of("cosmetics-standalone:RainsCosmetics-.*",
            "chat-standalone:RainsChat-.*", "elytra-standalone:RainsElytra-.*", "fishing-standalone:RainsFishing-.*",
            "pets-standalone:RainsPets-.*", "ctf-standalone:RainsCaptureTheFlag-.*");

    @Test
    @DisplayName("every plugin's cosmetic is a door on the cosmetics page, Back leads there, clear takes all off")
    void inOnePlace() throws Exception {
        try (Server server = Server.start("cosmetics-in-one-place", PLUGINS, List.of("Cosmetics is up"))) {
            Bot ada = server.admin("Ada");
            Await.ticks(20);

            Bot.Window page = ada.runAndOpen("cosmetics", "Cosmetics").window().orElseThrow();
            for (String door : List.of("Chat colour", "Capture the Flag looks", "Elytra looks", "Rod skins", "Pet looks")) {
                assertThat(page.slotNamed(door)).as(door + " is a door on the cosmetics page").isPresent();
            }
            assertThat(lore(page, "Chat colour")).anyMatch(line -> line.contains("Yours: plain"));
            assertThat(lore(page, "Clear my cosmetics")).as("nothing worn, nothing to clear")
                    .anyMatch(line -> line.contains("not wearing anything"));

            ada.click("Chat colour");
            Bot.Window colours = ada.awaitWindow("chat colour");
            assertThat(colours.slotNamed("Back")).as("Back from the door").isPresent();
            int gold = colours.slotNamed("Gold").orElseThrow();
            ada.click("Gold");
            Await.ticks(10);
            ada.closeWindow();

            Bot.Window typed = ada.runAndOpen("chatstyle", "chat colour").window().orElseThrow();
            assertThat(lore(typed, "Back")).as("/chatstyle goes back to the cosmetics page")
                    .anyMatch(line -> line.contains("Cosmetics"));
            ada.click("Back");
            Bot.Window again = ada.awaitWindow("Cosmetics");
            assertThat(lore(again, "Chat colour")).as("the door says what is worn")
                    .noneMatch(line -> line.contains("Yours: plain"));
            assertThat(lore(again, "Clear my cosmetics")).as("a chat colour alone is something to clear")
                    .noneMatch(line -> line.contains("not wearing anything"));
            ada.closeWindow();

            Bot.Window elytra = ada.runAndOpen("elytra", "").window().orElseThrow();
            assertThat(lore(elytra, "Back")).as("/elytra goes back to the cosmetics page")
                    .anyMatch(line -> line.contains("Cosmetics"));
            ada.closeWindow();

            server.console("e2e as Ada cosmetics clear");
            Await.ticks(20);
            Bot.Window cleared = ada.runAndOpen("cosmetics", "Cosmetics").window().orElseThrow();
            assertThat(lore(cleared, "Chat colour")).as("clear took the chat colour off too")
                    .anyMatch(line -> line.contains("Yours: plain"));
            ada.closeWindow();

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("op Viewer");
                server.console("give Viewer elytra");
                Await.ticks(10);
                server.console("e2e as Viewer chatstyle");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("cosmetics-2-chatstyle-command.png"));
                server.console("e2e as Viewer cosmetics");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("cosmetics-1-page.png"));
                for (String door : List.of("Capture the Flag looks", "Pet looks", "Elytra looks", "Chat colour")) {
                    server.console("e2e as Viewer cosmetics");
                    Await.ticks(20);
                    int slot = page.slotNamed(door).orElseThrow();
                    server.console("e2e click Viewer " + slot);
                    Await.ticks(30);
                    viewer.screenshot(shots.resolve("cosmetics-3-" + door.toLowerCase().replace(' ', '-') + ".png"));
                }
                server.console("e2e click Viewer " + gold);
                Await.ticks(20);
                server.console("e2e as Viewer cosmetics");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("cosmetics-4-wearing-gold.png"));
                server.console("e2e as Viewer cosmetics clear");
                Await.ticks(20);
                server.console("e2e as Viewer cosmetics");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("cosmetics-5-after-clear.png"));
            }
            assertThat(server.paper.errorsFrom("RainsCore", "RainsCosmetics", "RainsChat", "RainsElytra",
                    "RainsFishing", "RainsPets", "RainsCaptureTheFlag")).isEmpty();
        }
    }

    private static List<String> lore(Bot.Window window, String named) {
        int slot = window.slotNamed(named).orElseThrow(() -> new AssertionError("no " + named + " in " + window.title()));
        return window.items().get(slot).lore();
    }
}
