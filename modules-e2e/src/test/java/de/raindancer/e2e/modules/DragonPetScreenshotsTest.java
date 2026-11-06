package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mini ender dragon pet in the real client — Core's rig of item displays from the pets' resource pack, since the
 * client draws the real dragon at full size: {@code mvn -o -Pscreenshots test -pl modules-e2e
 * -Dtest=DragonPetScreenshotsTest}.
 */
@Tag("screenshots")
class DragonPetScreenshotsTest {

    @Test
    @DisplayName("a mini ender dragon beside its owner, flapping, and ridden")
    void miniDragon() {
        try (Server server = Server.start("dragon-pet", List.of("pets-standalone:RainsPets-.*"), List.of("Pets is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 90 24 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("time set 6000");
            server.console("weather clear");
            Await.ticks(40);
            server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("tp Raindancer118 0.5 64 0.5 180 0");
            server.console("pets admin give Raindancer118 ender_dragon Smaug");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets call Smaug");
            Await.ticks(40);
            server.console("pets admin personality Raindancer118 Smaug lazy");
            server.console("pets admin personality Raindancer118 Smaug lazy");
            server.console("e2e as Raindancer118 pets stay Smaug");
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                // creative, not spectator: a spectator sees invisible mobs (the carrier) as ghosts
                server.console("gamemode creative Viewer");
                server.console("attribute Viewer minecraft:gravity base set 0");
                server.console("gamemode spectator Raindancer118");
                server.console("tp Raindancer118 0.5 64 9.5");
                server.console("tp @e[type=minecraft:allay,limit=1] 0.5 66 0.5");
                Await.ticks(40);
                int i = 0;
                // around wherever the dragon is right now: distance, height, yaw of the camera
                for (double[] view : new double[][]{{4.5, 0.6, 0}, {4.5, 0.6, 90}, {4.5, 0.6, 180}, {4.5, 2.8, 225},
                        {4.0, -1.6, 45}}) {
                    double[] at = position(server.console("data get entity @e[type=minecraft:allay,limit=1] Pos"));
                    double yaw = Math.toRadians(view[2]);
                    double x = at[0] + Math.sin(yaw) * view[0];
                    double z = at[2] - Math.cos(yaw) * view[0];
                    double y = at[1] + view[1];
                    double pitch = Math.toDegrees(Math.atan2(view[1] - 0.3, view[0]));
                    server.console(String.format(java.util.Locale.ROOT, "tp Viewer %.2f %.2f %.2f %.1f %.1f", x, y, z,
                            view[2], pitch));
                    System.out.println("DRAGON AT " + java.util.Arrays.toString(at));
                    Await.ticks(6);
                    viewer.screenshot(shots.resolve("dragon-close-" + (++i) + ".png"));
                }
                server.console("gamemode survival Raindancer118");
                server.console("tp Raindancer118 0.5 64 0.5");
                server.console("tp Viewer 0.5 65.5 -5.5 0 10");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("dragon-pet-1.png"));
                server.console("tp Viewer 5.5 66.5 -1.5 70 15");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("dragon-pet-2-side.png"));
                server.console("e2e as Raindancer118 pets follow Smaug");
                server.console("ride Raindancer118 mount @e[type=minecraft:allay,limit=1,sort=nearest]");
                Await.ticks(40);
                server.console("tp Viewer 0.5 66 -6.5 0 10");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("dragon-pet-3-ridden.png"));
                assertThat(server.paper.errorsFrom("RainsCore", "RainsPets")).isEmpty();
            }
        }
    }

    /** The x, y, z out of "... has the following entity data: [1.2d, 64.0d, 3.4d]". */
    private static double[] position(String answer) {
        java.util.regex.Matcher numbers = java.util.regex.Pattern.compile("(-?[0-9.]+)d").matcher(answer);
        double[] at = new double[]{0.5, 66, 0.5};
        for (int i = 0; i < 3 && numbers.find(); i++) {
            at[i] = Double.parseDouble(numbers.group(1));
        }
        return at;
    }
}
