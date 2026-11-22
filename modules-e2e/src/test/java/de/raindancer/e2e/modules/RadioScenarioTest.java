package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RainsRadio on a real server, beside RainsEssentials' music on logging on: /music tunes in, skips, changes
 * station and volume, stops; the radio comes back on by itself after logging on again and the greeting stays
 * quiet then. The tracks are three seconds long, so one following the next is seen too.
 */
@Tag("e2e")
class RadioScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(40);

    private static boolean heard(Bot bot, String part) {
        return bot.soundsHeard().stream().anyMatch(sound -> sound.contains(part));
    }

    private static long count(Bot bot, String part) {
        return bot.soundsHeard().stream().filter(sound -> sound.contains(part)).count();
    }

    @Test
    @DisplayName("/music: four stations, skip, volume, stop, and back on after logging on again")
    void radio() {
        Path ogg = Path.of("src/test/resources/music/three-seconds.ogg");
        Map<String, Path> music = Map.of(
                "plugins/RainsRadio/music/calm/Dawn - Test Artist.ogg", ogg,
                "plugins/RainsRadio/music/calm/Tide.ogg", ogg,
                "plugins/RainsRadio/music/night/Moon.ogg", ogg,
                "plugins/RainsEssentials/music/Welcome.ogg", ogg);
        try (Server server = Server.start("radio",
                List.of("radio-standalone:RainsRadio-.*", "essentials-standalone:RainsEssentials-.*"),
                List.of(), Map.of("plugins/RainsEssentials/config.yml", "join-music:\n  enabled: true\n"), music,
                List.of("Radio is up: Calm 2, Adventure 0, Cozy 0, Night 1", "Essentials"))) {
            Bot bo = server.paper.bot("Bo").acceptPacks().join();

            // ---- radio off: the greeting plays as before
            Await.until(() -> "Bo hears the music on logging on " + bo.soundsHeard(), WAIT,
                    () -> heard(bo, "rainsessentials:music.welcome"));

            // ---- tuning in
            bo.runAndExpect("music station calm", "Tuned in to Calm");
            Await.until(() -> "Bo hears Calm " + bo.soundsHeard(), WAIT, () -> heard(bo, "rainsradio:music.calm."));
            bo.expectActionBar("Calm");
            assertThat(bo.actionBars()).anyMatch(bar -> bar.contains("Dawn - Test Artist") || bar.contains("Tide"));

            // ---- a three-second track is followed by the next by itself
            long before = count(bo, "rainsradio:music.calm.");
            Await.until(() -> "the next Calm track follows " + bo.soundsHeard(), Duration.ofSeconds(20),
                    () -> count(bo, "rainsradio:music.calm.") > before);

            // ---- skip, volume
            long beforeSkip = count(bo, "rainsradio:music.calm.");
            bo.runAndExpect("music skip", "Skipped");
            Await.until(() -> "skipping plays another track " + bo.soundsHeard(), Duration.ofSeconds(5),
                    () -> count(bo, "rainsradio:music.calm.") > beforeSkip);
            bo.runAndExpect("music volume 30", "Radio volume: 30%");
            bo.runAndExpect("music volume loud", "Radio volume is 30%");

            // ---- stations: an empty one, an unknown one, the short form
            bo.runAndExpect("music station cozy", "Cozy has no tracks yet");
            bo.runAndExpect("music station jazz", "There is no station called jazz");
            bo.forgetEffects();
            bo.runAndExpect("music night", "Tuned in to Night");
            Await.until(() -> "Bo hears Night " + bo.soundsHeard(), WAIT, () -> heard(bo, "rainsradio:music.night.moon"));
            assertThat(bo.runAndExpect("music status", "Night").chat()).anyMatch(line -> line.contains("Night"));
            assertThat(bo.complete("/music station n")).contains("night");

            // ---- the window: the game's own dialog, what plays on top, four stations, buttons that work
            int windows = bo.dialogsShown();
            bo.run("music");
            de.raindancer.e2e.ShownDialog window = bo.awaitDialog(windows, Duration.ofSeconds(10));
            assertThat(window.title()).contains("Radio");
            assertThat(window.text()).contains("Night").contains("30%");
            assertThat(window.buttons()).extracting(de.raindancer.e2e.ShownDialog.Button::label)
                    .contains("Calm", "Adventure", "Cozy", "▶ Night", "Skip", "Turn off", "− Quieter", "+ Louder");
            bo.forgetEffects();
            windows = bo.dialogsShown();
            bo.pressDialogButton("Calm", Map.of());
            window = bo.awaitDialog(windows, Duration.ofSeconds(10));
            assertThat(window.buttons()).extracting(de.raindancer.e2e.ShownDialog.Button::label).contains("▶ Calm");
            Await.until(() -> "pressing Calm tunes in " + bo.soundsHeard(), WAIT, () -> heard(bo, "rainsradio:music.calm."));
            windows = bo.dialogsShown();
            bo.pressDialogButton("+ Louder", Map.of());
            assertThat(bo.awaitDialog(windows, Duration.ofSeconds(10)).text()).contains("40%");
            windows = bo.dialogsShown();
            bo.pressDialogButton("Cozy", Map.of());
            assertThat(bo.awaitDialog(windows, Duration.ofSeconds(10)).text()).contains("Cozy has no tracks yet");

            // ---- logging on again: the radio comes back by itself, and the greeting stays quiet
            Bot again = bo.rejoin();
            again.forgetEffects();
            Await.until(() -> "the radio comes back after logging on " + again.soundsHeard(), WAIT,
                    () -> heard(again, "rainsradio:music.calm."));
            Await.never("the greeting plays over the radio", Duration.ofSeconds(8),
                    () -> heard(again, "rainsessentials:music.welcome"));

            // ---- stop: quiet, and it stays off
            again.runAndExpect("music stop", "Radio off");
            again.forgetEffects();
            Await.never("anything from the radio after stopping", Duration.ofSeconds(9),
                    () -> heard(again, "rainsradio:"));
            again.runAndExpect("music stop", "already off");

            assertThat(server.paper.errorsFrom("RainsRadio")).isEmpty();
            assertThat(server.paper.errorsFrom("RainsEssentials")).isEmpty();
            assertThat(server.paper.errorsFrom("RainsCore")).isEmpty();
        }
    }
}
