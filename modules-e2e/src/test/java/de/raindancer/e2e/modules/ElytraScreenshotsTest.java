package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trimmed elytra and the chestplate looks, photographed with the real client wearing the server's pack:
 * {@code mvn -o -Pscreenshots test -pl modules-e2e -Dtest=ElytraScreenshotsTest}. Four armour stands show pattern and
 * material pairs; Rain, a bot, put his look on with /elytra like a player would.
 */
@Tag("screenshots")
class ElytraScreenshotsTest {

    private static final int Y = 64;

    @Test
    @DisplayName("trims on the wings, chestplate looks with them, and the looks menu")
    void screenshots() {
        try (Server server = Server.start("elytra-screenshots", List.of("elytra-standalone:RainsElytra-.*"),
                List.of("Elytra is up"))) {
            server.console("settings set core:packs-required true");
            server.console("forceload add -32 -32 32 32");
            server.console("fill -10 " + (Y - 1) + " -10 10 " + (Y - 1) + " 16 minecraft:smooth_stone");
            server.console("fill -10 " + Y + " -10 10 " + (Y + 12) + " 16 minecraft:air");
            server.console("time set 6000");
            server.console("gamerule advance_time false");
            stand(server, -3, "coast", "gold", null);
            stand(server, -1.5, "wild", "amethyst", "netherite");
            stand(server, 0, "silence", "diamond", "diamond");
            stand(server, 1.5, "flow", "copper", "leather");
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer", 4)) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("op Viewer");

                Bot rain = server.admin("Rain");
                server.console("give Rain minecraft:elytra[minecraft:trim={pattern:'minecraft:eye',material:'minecraft:lapis'}]");
                Await.ticks(10);
                rain.hold(0);
                rain.runAndExpect("elytra look gold", "Gold");
                server.console("item replace entity Rain armor.chest from entity Rain weapon.mainhand");
                server.console("item replace entity Rain weapon.mainhand with minecraft:air");
                server.console("tp Rain 3 " + Y + " 0 180 0");

                server.console("tp Viewer 0 " + (Y + 0.6) + " 4.2 180 12");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("elytra-1-backs.png"));

                server.console("tp Rain 3 " + Y + " 0 0 0");
                server.console("execute as @e[type=armor_stand] at @s run tp @s ~ ~ ~ 0 0");
                server.console("tp Viewer 0 " + (Y + 0.6) + " -4.2 0 12");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("elytra-2-fronts.png"));

                server.console("gamemode creative Viewer");
                server.console("give Viewer minecraft:elytra");
                Await.ticks(10);
                server.console("e2e as Viewer elytra");
                Await.ticks(60);
                viewer.screenshotHovering(shots.resolve("elytra-3-menu.png"), 5);
                assertThat(shots.resolve("elytra-3-menu.png")).exists();
            }
        }
    }

    private static void stand(Server server, double x, String pattern, String material, String look) {
        String equippable = look == null ? "" : ",\"minecraft:equippable\":{slot:\"chest\",asset_id:\"rainselytra:"
                + look + "_chestplate\"}";
        server.console("summon minecraft:armor_stand " + x + " " + Y + " 0 {Rotation:[180f,0f],NoGravity:1b,"
                + "equipment:{chest:{id:\"minecraft:elytra\",count:1,components:{\"minecraft:trim\":{pattern:\"minecraft:"
                + pattern + "\",material:\"minecraft:" + material + "\"}" + equippable + "}}}}");
    }
}
