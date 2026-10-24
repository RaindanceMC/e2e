package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import de.raindancer.e2e.RealClient;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chest shops photographed with the real client, and checked on the way:
 * {@code mvn -o -Pe2e test -pl modules-e2e -De2e.groups=screenshots -Dtest=ChestShopsScreenshotsTest}.
 *
 * <p>Three market stalls in a row — a chest on a log, an item frame on the chest, a {@code [market]} sign on the log
 * under the frame. A bot writes each sign the way a player does (opens it, sends the lines); the shop opens, the sign
 * is rewritten and waxed, the frame fixed, and the item, price and name float over the chest.
 */
@Tag("screenshots")
class ChestShopsScreenshotsTest {

    private static final Duration WAIT = Duration.ofSeconds(90);

    private static final int X = 8;
    private static final int Y = 120;
    private static final int Z = 8;

    private record Stall(int x, String item, int stock, String name, String prices, String bundle) {
    }

    private static final List<Stall> STALLS = List.of(
            new Stall(X, "diamond", 24, "Gem Corner", "B 120 S 80", ""),
            new Stall(X + 4, "bread", 64, "Fresh Bread", "5", "x8"),
            new Stall(X + 8, "oak_log", 64, "Timber", "B 12 S 6", "x16"));

    @Test
    @DisplayName("three chest shops set up with signs, floating, browsed and traded — photographed")
    void screenshots() {
        Path economy = OneBlockNetworkScenarioTest.jar("economy-standalone", "RainsEconomy-.*");
        Path shops = OneBlockNetworkScenarioTest.jar("shops-standalone", "RainsShops-.*");
        PaperServer server = PaperServer.forRun().seed(1).in(E2e.serverFolder("chest-shops-screenshots"))
                .plugin(Path.of(System.getProperty("e2e.rainscore"))).plugin(economy).plugin(shops)
                .build();
        Path shots = E2e.out().resolve("screenshots");
        try (server) {
            server.start();
            server.awaitLog("Shops is up", WAIT);
            server.console("forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 24) + " " + (Z + 24));
            server.console("time set 1000");
            server.console("gamerule doDaylightCycle false");
            server.console("weather clear");
            Await.ticks(40);
            market(server);

            Bot ada = server.bot("Ada").join();
            server.console("gamemode survival Ada");
            for (Stall stall : STALLS) {
                setUp(server, ada, stall);
            }

            Stall gems = STALLS.get(0);
            assertThat(server.console("data get block " + gems.x() + " " + Y + " " + (Z + 1) + " front_text.messages[0]"))
                    .as("the sign says what the shop is").contains("Market");
            assertThat(server.console("execute if entity @e[type=minecraft:item_frame]")).as("the frames hang")
                    .contains("passed");
            assertThat(server.console("data get block " + gems.x() + " " + Y + " " + (Z + 1) + " is_waxed"))
                    .as("the sign is waxed").contains("1b");
            assertThat(server.console("data get entity @e[type=minecraft:item_frame,limit=1,sort=nearest,x="
                    + gems.x() + ",y=" + (Y + 1) + ",z=" + (Z + 1) + "] Fixed")).as("the frame is fixed").contains("1b");
            Await.until("the shops float", WAIT, () -> server.console("execute if entity @e[type=minecraft:text_display,"
                    + "x=" + gems.x() + ",y=" + (Y + 2) + ",z=" + Z + ",distance=..2]").contains("passed"));

            // ── Ada switches the bread stall's price line off in its back room ──────────────────────────────────
            Stall bread = STALLS.get(1);
            server.console("tp Ada " + bread.x() + ".5 " + Y + " " + (Z + 3) + ".5 180 40");
            Await.ticks(20);
            server.console("clear Ada");
            ada.sneak(true);
            Await.ticks(5);
            ada.useOn(bread.x(), Y + 1, Z);
            ada.clickSlot(slot(ada, "What floats over the chest"));
            ada.clickSlot(slot(ada, "Price"));
            Await.ticks(20);
            ada.sneak(false);
            ada.closeWindow();

            Bot bo = server.bot("Bo").join();
            server.console("gamemode survival Bo");
            server.console("eco give Bo 1000");
            server.console("eco give Ada 1000");

            server.console("tp Ada " + (X - 6) + " " + Y + " " + (Z + 12));
            server.console("tp Bo " + (X + 14) + " " + Y + " " + (Z + 12));
            try (RealClient viewer = RealClient.join(server, "Viewer", 8)) {
                Await.ticks(100);
                // The server's resource pack, as a player takes it: "Yes" on the client's own offer screen.
                viewer.click(484, 408);
                Await.ticks(300);
                server.console("gamemode spectator Viewer");
                server.console("tp Viewer " + (X + 4) + ".5 " + (Y + 3.4) + " " + (Z + 10.5) + " 180 16");
                Await.ticks(120);
                viewer.screenshot(shots.resolve("chest-shops-1-market-row.png"));

                server.console("tp Viewer " + (gems.x() + 0.5) + " " + (Y + 1.9) + " " + (Z + 4.2) + " 180 10");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("chest-shops-2-gem-corner.png"));

                server.console("tp Viewer " + (X + 11.5) + " " + (Y + 2.2) + " " + (Z + 5.5) + " 130 14");
                Await.ticks(80);
                viewer.screenshot(shots.resolve("chest-shops-3-street-side.png"));

                // ── /market: every shop; the counter of one ─────────────────────────────────────────────────────
                server.console("gamemode survival Viewer");
                server.console("tp Viewer " + (X + 3) + ".5 " + Y + " " + (Z + 5) + ".5 180 10");
                server.console("eco give Viewer 500");
                Await.ticks(20);
                bo.run("market");
                int gemSlot = slot(bo, "Diamond");
                server.runAs("Viewer", "market");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("chest-shops-4-market-browser.png"));
                server.console("e2e click Viewer " + gemSlot);
                bo.clickSlot(gemSlot);
                int buySlot = slot(bo, "Buy one bundle");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("chest-shops-5-counter.png"));
                bo.clickSlot(buySlot);
                bo.expectChat("Bought 1× Diamond");
                bo.closeWindow();
                ada.expectChat("Bo bought");

                server.console("e2e click Viewer " + buySlot);
                Await.ticks(40);
                viewer.screenshot(shots.resolve("chest-shops-6-bought.png"));
                assertThat(shots.resolve("chest-shops-6-bought.png")).exists();
            }
            assertThat(server.errorsFrom("RainsShops")).isEmpty();
        } finally {
            server.keepLogsIn(E2e.logsFolder("chest-shops-screenshots"));
        }
    }

    /**
     * A market street: a stone brick square with a paved lane, and three stalls — a chest on a log, its item frame and
     * sign, and a striped canopy on spruce posts high enough for what floats over the chest.
     */
    private static void market(PaperServer server) {
        server.console("fill " + (X - 8) + " " + Y + " " + (Z - 8) + " " + (X + 16) + " " + (Y + 10) + " " + (Z + 16)
                + " minecraft:air");
        server.console("fill " + (X - 8) + " " + (Y - 1) + " " + (Z - 8) + " " + (X + 16) + " " + (Y - 1) + " "
                + (Z + 16) + " minecraft:stone_bricks");
        server.console("fill " + (X - 8) + " " + (Y - 1) + " " + (Z + 3) + " " + (X + 16) + " " + (Y - 1) + " "
                + (Z + 6) + " minecraft:cobblestone");
        server.console("fill " + (X - 8) + " " + (Y - 1) + " " + (Z + 4) + " " + (X + 16) + " " + (Y - 1) + " "
                + (Z + 5) + " minecraft:andesite");
        String[] canopies = {"minecraft:red_wool", "minecraft:blue_wool", "minecraft:green_wool"};
        int at = 0;
        for (Stall stall : STALLS) {
            int x = stall.x();
            server.console("setblock " + x + " " + Y + " " + Z + " minecraft:spruce_log");
            server.console("setblock " + x + " " + (Y + 1) + " " + Z + " minecraft:chest[facing=south]");
            server.console("item replace block " + x + " " + (Y + 1) + " " + Z
                    + " container.0 with minecraft:" + stall.item() + " " + stall.stock());
            for (int side : new int[]{-1, 1}) {
                server.console("setblock " + (x + side) + " " + Y + " " + Z + " minecraft:barrel[facing=up]");
                server.console("fill " + (x + side) + " " + (Y + 1) + " " + (Z + 1) + " " + (x + side) + " " + (Y + 4)
                        + " " + (Z + 1) + " minecraft:spruce_fence");
                server.console("fill " + (x + side) + " " + (Y + 1) + " " + (Z - 1) + " " + (x + side) + " " + (Y + 4)
                        + " " + (Z - 1) + " minecraft:spruce_fence");
            }
            for (int dx = -1; dx <= 1; dx++) {
                String wool = dx == 0 ? "minecraft:white_wool" : canopies[at];
                server.console("fill " + (x + dx) + " " + (Y + 5) + " " + (Z - 1) + " " + (x + dx) + " " + (Y + 5) + " "
                        + (Z + 1) + " " + wool);
            }
            server.console("setblock " + (x - 1) + " " + (Y + 4) + " " + (Z + 2)
                    + " minecraft:lantern[hanging=true]");
            server.console("summon minecraft:item_frame " + x + ".5 " + (Y + 1) + ".5 " + (Z + 1) + ".03"
                    + " {Facing:3b,block_pos:[I;" + x + "," + (Y + 1) + "," + (Z + 1) + "],"
                    + "Item:{id:\"minecraft:" + stall.item() + "\",count:1}}");
            server.console("setblock " + x + " " + Y + " " + (Z + 1) + " minecraft:spruce_wall_sign[facing=south]");
            at++;
        }
        Await.ticks(20);
    }

    /** The owner opens the sign under the frame and writes it, as a player does. */
    private static void setUp(PaperServer server, Bot owner, Stall stall) {
        server.console("clear " + owner.name());
        server.console("tp " + owner.name() + " " + stall.x() + ".5 " + Y + " " + (Z + 3) + ".5 180 20");
        Await.ticks(20);
        owner.useOn(stall.x(), Y, Z + 1);
        Await.ticks(10);
        owner.send(new ServerboundSignUpdatePacket(Vector3i.from(stall.x(), Y, Z + 1),
                List.of("[market]", stall.prices(), stall.bundle(), stall.name()), true));
        owner.expectChat("is open for business");
    }

    private static int slot(Bot bot, String label) {
        Supplier<Integer> found = () -> bot.window().flatMap(window -> window.slotNamed(label)).orElse(null);
        Await.until("a page with '" + label + "' is open for " + bot.name(), WAIT, () -> found.get() != null);
        return found.get();
    }
}
