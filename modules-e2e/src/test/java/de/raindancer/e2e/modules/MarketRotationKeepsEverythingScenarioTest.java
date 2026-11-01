package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What players store in their stalls survives a rotation, and a stall higher than the building height moves whole.
 * Ada's plot (A: x 81–92, z 13–24, facing south) and Bo's (B: x 81–92, z 34–45, facing north) swap, Ada's turned
 * through the middle: (x, z) on A → (173 − x, 58 − z) on B. Each has a barrel full of shulker boxes where the other
 * build has something else, a standing shulker box with stock, and Ada a 40-high column — far above the 24 a plot may
 * be built.
 */
@Tag("e2e")
class MarketRotationKeepsEverythingScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(120);

    @Test
    @DisplayName("rotate: no container spills, no shulker box drops, a tall stall arrives whole and leaves nothing behind")
    void keepsEverything() throws Exception {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        Path folder = E2e.serverFolder("market-rotation-keeps");
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
            server.console("execute in minecraft:market run tp Ada 60.5 65 5.5");
            server.console("execute in minecraft:market run tp Bo 60.5 65 5.5");
            Await.ticks(20);

            // Ada: a barrel of shulker boxes at (84, 66, 16) → lands on (89, 66, 42), where Bo has stone.
            setblock(server, 84, 66, 16, "barrel[facing=up]");
            fillWithShulkers(server, 84, 66, 16);
            setblock(server, 89, 66, 42, "stone");
            // Bo: a barrel of shulker boxes at (85, 66, 40) → lands on (88, 66, 18), where Ada has a gold block.
            setblock(server, 85, 66, 40, "barrel[facing=up]");
            fillWithShulkers(server, 85, 66, 40);
            setblock(server, 88, 66, 18, "gold_block");
            // A standing shulker box with stock on each, over a plain block on the other side.
            setblock(server, 90, 66, 20, "purple_shulker_box[facing=up]");
            server.console("execute in minecraft:market run item replace block 90 66 20 container.0 with "
                    + "minecraft:emerald 33");
            setblock(server, 83, 66, 38, "oak_planks");
            setblock(server, 82, 66, 43, "purple_shulker_box[facing=up]");
            server.console("execute in minecraft:market run item replace block 82 66 43 container.0 with "
                    + "minecraft:emerald 44");
            setblock(server, 91, 66, 15, "oak_planks");
            // Ada's column: y 65..104, topped by a chest of diamonds above the building height (64 + 24).
            server.console("execute in minecraft:market run fill 86 65 22 86 103 22 minecraft:orange_concrete");
            setblock(server, 86, 104, 22, "chest[facing=south]");
            server.console("execute in minecraft:market run item replace block 86 104 22 container.0 with "
                    + "minecraft:diamond 7");
            Await.ticks(20);
            assertThat(items(server)).as("no items lying about before the rotation").isZero();

            server.console("op Ada");
            Await.ticks(10);
            ada.run("market admin rotate");
            ada.expectChat("moved one place round");
            Await.ticks(60);

            assertThat(items(server)).as("items lying about in the market after the rotation").isZero();

            assertThat(blockIs(server, 89, 66, 42, "barrel")).as("Ada's barrel on B").isTrue();
            assertThat(shulkersIn(server, 89, 66, 42)).as("all of Ada's shulker boxes in it").isEqualTo(27);
            assertThat(blockIs(server, 88, 66, 18, "barrel")).as("Bo's barrel on A").isTrue();
            assertThat(shulkersIn(server, 88, 66, 18)).as("all of Bo's shulker boxes in it").isEqualTo(27);
            assertThat(blockIs(server, 83, 66, 38, "purple_shulker_box")).as("Ada's shulker box on B").isTrue();
            assertThat(server.console("execute in minecraft:market run data get block 83 66 38 Items[0].count"))
                    .contains("33");
            assertThat(blockIs(server, 91, 66, 15, "purple_shulker_box")).as("Bo's shulker box on A").isTrue();
            assertThat(server.console("execute in minecraft:market run data get block 91 66 15 Items[0].count"))
                    .contains("44");

            // The whole column on B at (87, 36), chest on top; nothing of it left over A.
            for (int y = 65; y <= 103; y++) {
                assertThat(blockIs(server, 87, y, 36, "orange_concrete")).as("the column at y " + y).isTrue();
            }
            assertThat(blockIs(server, 87, 104, 36, "chest")).as("the chest on top of the column").isTrue();
            assertThat(server.console("execute in minecraft:market run data get block 87 104 36 Items[0].count"))
                    .contains("7");
            for (int y = 89; y <= 104; y++) {
                assertThat(blockIs(server, 86, y, 22, "air")).as("left over A at y " + y).isTrue();
            }

            assertThat(server.errorsFrom("RainsShops", "RainsCore", "RainsEconomy").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("market-rotation-keeps"));
        }
    }

    private static void fillWithShulkers(PaperServer server, int x, int y, int z) {
        for (int slot = 0; slot < 27; slot++) {
            server.console("execute in minecraft:market run item replace block " + x + " " + y + " " + z
                    + " container." + slot + " with minecraft:purple_shulker_box 1");
        }
    }

    private static int shulkersIn(PaperServer server, int x, int y, int z) {
        int found = 0;
        for (int slot = 0; slot < 27; slot++) {
            if (server.console("execute in minecraft:market if items block " + x + " " + y + " " + z
                    + " container." + slot + " minecraft:purple_shulker_box").contains("passed")) {
                found++;
            }
        }
        return found;
    }

    /** How many dropped items lie in the market world. */
    private static int items(PaperServer server) {
        String answer = server.console("execute in minecraft:market if entity @e[type=minecraft:item]");
        java.util.regex.Matcher count = java.util.regex.Pattern.compile("count: (\\d+)").matcher(answer);
        return answer.contains("passed") ? (count.find() ? Integer.parseInt(count.group(1)) : 1) : 0;
    }

    private static void setblock(PaperServer server, int x, int y, int z, String block) {
        server.console("execute in minecraft:market run setblock " + x + " " + y + " " + z + " minecraft:" + block);
    }

    private static boolean blockIs(PaperServer server, int x, int y, int z, String block) {
        return server.console("execute in minecraft:market if block " + x + " " + y + " " + z + " minecraft:"
                + block).contains("passed");
    }
}
