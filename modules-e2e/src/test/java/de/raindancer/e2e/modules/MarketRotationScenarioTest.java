package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The market world on a real server: two players claim plots facing different streets — Ada's (A: x 81–92, z 13–24,
 * facing south) nearer the square than Bo's (B: x 81–92, z 34–45, facing north) — and build; Ada sets up a chest
 * shop. The stalls rotate: the world is backed up first, Ada's build lands on B turned round (front to the street),
 * Bo's on A, chest contents, frame and sign with them; the shop sells at its new place; the claims swap; and nobody
 * builds on somebody else's plot.
 */
@Tag("e2e")
class MarketRotationScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(120);

    @Test
    @DisplayName("claim, build, rotate: builds, contents and shops move with their plots, turned to their street")
    void rotation() throws Exception {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        Path folder = E2e.serverFolder("market-rotation");
        PaperServer server = PaperServer.forRun().seed(1).in(folder)
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(economy).plugin(shops)
                .build();
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            assertThat(server.console("settings set market-world true")).contains("is now");
            server.awaitLog("The market world 'market' is open", WAIT);
            server.console("execute in minecraft:market run forceload add 60 0 110 60");
            Await.ticks(60);

            Bot ada = server.bot("Ada").join();
            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Ada");
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");

            // ── /market takes them there; each claims a plot ────────────────────────────────────────────────────
            ada.run("market");
            ada.expectChat("Welcome to the market");
            server.console("execute in minecraft:market run tp Ada 86.5 65 18.5");
            Await.ticks(20);
            ada.run("market claim");
            ada.expectChat("is yours");
            server.console("execute in minecraft:market run tp Bo 86.5 65 40.5");
            Await.ticks(20);
            bo.run("market claim");
            bo.expectChat("is yours");
            server.console("execute in minecraft:market run tp Bo 86.5 65 18.5");
            Await.ticks(20);
            bo.run("market claim");
            bo.expectChat("taken");

            // ── Bo may not build on Ada's plot; Ada may ─────────────────────────────────────────────────────────
            server.console("gamemode creative Bo");
            Await.ticks(10);
            bo.dig(83, 65, 19); // a corner post of the stall on Ada's plot
            Await.ticks(30);
            assertThat(block(server, 83, 65, 19)).as("Bo broke a block on Ada's plot").isNotEqualTo("air");

            // ── Ada builds: a chest shop with a frame and a sign, and a gold block at the back; Bo a diamond block
            setblock(server, 86, 65, 21, "chest[facing=south]");
            server.console("execute in minecraft:market run item replace block 86 65 21 container.0 with "
                    + "minecraft:diamond 20");
            server.console("execute in minecraft:market run summon minecraft:item_frame 86.5 65.5 22.03 "
                    + "{Facing:3b,block_pos:[I;86,65,22],Item:{id:\"minecraft:diamond\",count:1}}");
            setblock(server, 87, 65, 21, "oak_wall_sign[facing=east]");
            setblock(server, 82, 65, 15, "gold_block");
            setblock(server, 82, 65, 36, "diamond_block");
            server.console("execute in minecraft:market run tp Ada 88.5 65 23.5 135 20");
            Await.ticks(20);
            ShopsScenarioTest.writeSign(ada, 87, 65, 21, "[market]", "10", "", "Gems");
            ada.expectChat("is open for business");

            // ── no mob comes into being there, not even from an egg on one's own plot ───────────────────────────
            server.console("give Ada minecraft:zombie_spawn_egg 1");
            server.console("execute in minecraft:market run tp Ada 90.5 65 17.5 0 60");
            Await.ticks(20);
            ada.hold(ada.hotbarSlotOf(item -> item.material().toLowerCase().contains("zombie_spawn_egg")));
            ada.useOn(90, 64, 18);
            Await.ticks(40);
            assertThat(server.console("execute in minecraft:market if entity @e[type=minecraft:zombie,x=90,y=65,z=18,distance=..40]"))
                    .as("a zombie hatched in the market").doesNotContain("passed");

            // ── the stalls rotate ───────────────────────────────────────────────────────────────────────────────
            server.console("op Ada");
            Await.ticks(10);
            ada.run("market admin rotate");
            ada.expectChat("moved one place round");
            Await.ticks(60);

            // Ada's build on B, turned through the middle: (x, z) on A → (92 − (x − 81), 45 − (z − 13)) on B.
            assertThat(block(server, 87, 65, 37)).as("the chest moved to B").startsWith("chest");
            assertThat(server.console("execute in minecraft:market run data get block 87 65 37 Items"))
                    .as("with its stock").contains("diamond").contains("20");
            assertThat(server.console("execute in minecraft:market run data get block 86 65 37 front_text.messages[0]"))
                    .as("the sign still says what the shop is").contains("Market");
            assertThat(block(server, 86, 65, 37)).as("the sign moved, facing its new way").contains("wall_sign");
            assertThat(block(server, 91, 65, 43)).as("the gold block moved").isEqualTo("gold_block");
            assertThat(block(server, 91, 65, 22)).as("Bo's build is on A").isEqualTo("diamond_block");
            assertThat(block(server, 86, 65, 21)).as("nothing of Ada's left on A").isNotEqualTo("chest");
            assertThat(server.console("execute in minecraft:market if entity @e[type=minecraft:item_frame,"
                    + "x=87,y=65,z=36,distance=..1.5]")).as("the frame moved with it").contains("passed");

            // ── the shop sells where it is now ──────────────────────────────────────────────────────────────────
            bo.forgetChat();
            server.console("gamemode survival Bo");
            bo.run("market browse diamond");
            bo.clickSlot(ShopsScenarioTest.slot(bo, "Diamond"));
            bo.clickSlot(ShopsScenarioTest.slot(bo, "Buy one bundle"));
            bo.expectChat("Bought 1× Diamond");
            bo.closeWindow();
            Await.until("the chest at its new place sold it", WAIT, () -> server.console(
                    "execute in minecraft:market run data get block 87 65 37 Items").contains("19"));

            // ── the claims moved too ────────────────────────────────────────────────────────────────────────────
            ada.run("market home");
            Await.until("Ada stands in front of B", WAIT, () -> {
                Matcher at = Pattern.compile("\\[(-?[\\d.]+)d, (-?[\\d.]+)d, (-?[\\d.]+)d]")
                        .matcher(server.console("data get entity Ada Pos"));
                return at.find() && Math.abs(Double.parseDouble(at.group(3)) - 32.5) < 1.5;
            });

            // ── and the world was backed up before anything moved ───────────────────────────────────────────────
            try (Stream<Path> backups = Files.list(folder.resolve("plugins/RainsShops/market-backups"))) {
                List<Path> made = backups.toList();
                assertThat(made).as("a backup").hasSize(1);
                assertThat(made.get(0).resolve("region")).isDirectory();
            }

            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("market-rotation"));
        }
    }

    private static void setblock(PaperServer server, int x, int y, int z, String block) {
        server.console("execute in minecraft:market run setblock " + x + " " + y + " " + z + " minecraft:" + block);
    }

    /** The block's id without "minecraft:", with its state, as the server names it. */
    private static String block(PaperServer server, int x, int y, int z) {
        for (String candidate : List.of("air", "chest", "gold_block", "diamond_block", "oak_wall_sign")) {
            if (server.console("execute in minecraft:market if block " + x + " " + y + " " + z + " minecraft:"
                    + candidate).contains("passed")) {
                return candidate;
            }
        }
        return "something else";
    }
}
