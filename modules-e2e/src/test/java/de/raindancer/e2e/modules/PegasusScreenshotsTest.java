package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Taming's Sky branch in the real client, which steers the horse itself:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=PegasusScreenshotsTest}. The branch is learnt through the
 * tree; then the horse glides down instead of falling, and with Pegasus climbs while jump is held.
 */
@Tag("screenshots")
class PegasusScreenshotsTest {

    private static int slot(Bot scout, String named) {
        return scout.window().flatMap(window -> window.slotNamed(named))
                .orElseThrow(() -> new AssertionError("no '" + named + "' in " + scout.window()));
    }

    /** Scout and Viewer press the same button; the scout's page is waited for. */
    private static void both(Server server, Bot scout, String named) {
        int at = slot(scout, named);
        server.console("e2e click Viewer " + at);
        scout.clickSlot(at);
        Await.ticks(30);
    }

    private static void learn(Server server, Bot scout, String node) {
        int said = scout.chatText().size();
        both(server, scout, node);
        Await.until(node + " learnt", java.time.Duration.ofSeconds(10),
                () -> scout.chatText().stream().skip(said).anyMatch(line -> line.contains(node + " rank")));
    }

    private static double horseY(Server server) {
        Matcher m = Pattern.compile("\\[[-0-9.]+d, ([-0-9.]+)d")
                .matcher(server.console("data get entity @e[type=horse,limit=1] Pos"));
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    @Test
    @DisplayName("the Sky branch learnt; the horse glides down, and climbs while jump is held")
    void pegasus() throws Exception {
        try (Server server = Server.start("pegasus", List.of("skills-standalone:RainsSkills-.*"),
                List.of("Skills are up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 120 24 minecraft:air");
            server.console("gamerule spawn_mobs false");
            server.console("gamerule advance_time false");
            server.console("time set 6000");
            server.console("weather clear");
            Bot scout = server.player("Scout");
            server.console("gamemode spectator Scout");
            server.console("tp Scout 20 80 20");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode survival Viewer");
                server.console("tp Viewer 0.5 64 0.5 0 0");
                server.console("skills admin level Viewer 40");
                server.console("skills admin level Scout 40");
                Await.ticks(60);

                // --- Taming's tree: the middle button turns to Stable, then to Sky ---------------------------------
                scout.runAndOpen("skills taming", "Taming");
                server.console("e2e as Viewer skills taming");
                Await.ticks(40);
                both(server, scout, "Taming");
                both(server, scout, "Taming");
                Await.until("the Sky branch in view", java.time.Duration.ofSeconds(10),
                        () -> scout.window().flatMap(window -> window.slotNamed("Featherfall")).isPresent());
                for (String node : List.of("Featherfall", "Wingbeat", "Updraft", "Updraft", "Tailwind", "Tailwind",
                        "Jet Stream", "Pegasus")) {
                    learn(server, scout, node);
                }
                viewer.screenshotHovering(shots.resolve("pegasus-1-tree.png"), slot(scout, "Pegasus"));
                server.console("e2e click Viewer " + slot(scout, "Close"));
                scout.closeWindow();
                Await.ticks(20);

                // --- a tamed, saddled horse, ridden by the real client ----------------------------------------------
                server.console("summon minecraft:horse 0.5 64 4.5 {Tame:1b,Owner:[I;0,0,0,0],"
                        + "equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
                Await.ticks(20);
                server.console("ride Viewer mount @e[type=horse,limit=1]");
                Await.ticks(40);
                assertThat(server.console("data get entity Viewer RootVehicle.Entity.id")).contains("horse");

                // --- Featherfall: 25 blocks up, it drifts down instead of dropping ---------------------------------
                server.console("tp @e[type=horse,limit=1] 0.5 89 4.5 0 0");
                Await.ticks(20);
                double glideFrom = horseY(server);
                Await.ticks(40);
                double glideTo = horseY(server);
                viewer.screenshot(shots.resolve("pegasus-2-gliding.png"));
                assertThat(glideFrom - glideTo).as("two seconds of gliding, " + glideFrom + " to " + glideTo)
                        .isBetween(0.5, 6.0);

                // --- Pegasus: jump held in the air, it climbs --------------------------------------------------------
                double before = horseY(server);
                viewer.holdKey(KeyEvent.VK_SPACE, 1500);
                double after = horseY(server);
                viewer.screenshot(shots.resolve("pegasus-3-climbing.png"));
                assertThat(after - before).as("a second and a half of climbing, " + before + " to " + after)
                        .isGreaterThan(4.0);

                // --- the wings tire, then rest on the ground ---------------------------------------------------------
                viewer.holdKey(KeyEvent.VK_SPACE, 16_000);
                double tired = horseY(server);
                Await.ticks(60);
                assertThat(horseY(server)).as("past fifteen seconds the wings are tired: down it glides")
                        .isLessThan(tired);
                server.console("tp @e[type=horse,limit=1] 0.5 64 4.5 0 0");
                Await.ticks(40);
                server.console("tp @e[type=horse,limit=1] 0.5 80 4.5 0 0");
                Await.ticks(10);
                double rested = horseY(server);
                viewer.holdKey(KeyEvent.VK_SPACE, 1000);
                assertThat(horseY(server)).as("landed once: the wings are rested").isGreaterThan(rested + 2.0);

                assertThat(server.paper.errorsFrom("RainsCore", "RainsSkills")).isEmpty();
            }
        }
    }
}
