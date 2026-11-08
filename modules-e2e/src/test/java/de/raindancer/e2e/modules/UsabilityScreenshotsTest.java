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
 * The pages reworked for readability, photographed with the real client — and the way back between them:
 * {@code mvn -o -Pe2e test -pl modules-e2e -De2e.groups=screenshots -Dtest=UsabilityScreenshotsTest}.
 */
@Tag("screenshots")
class UsabilityScreenshotsTest {

    private static final List<String> PLUGINS = List.of("economy-standalone:.*", "guilds-standalone:.*");

    /** A click on one slot of the viewer's open window, done server-side by the probe. */
    private static void clickSlot(Server server, int slot) {
        server.console("e2e click Viewer " + slot);
        Await.ticks(40);
    }

    @Test
    @DisplayName("bank, shop, guild and the way back — photographed")
    void screenshots() {
        try (Server server = Server.start("usability-screenshots", PLUGINS, List.of())) {
            Await.ticks(60);
            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                server.console("op Viewer");
                server.console("eco give Viewer 12500");
                server.console("e2e as Viewer guild found The Elves ELF");
                Await.ticks(40);

                // ── the bank, then the shop reached from it: Back goes to the bank ──────────────────
                server.console("e2e as Viewer bank");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("usability-1-bank.png"));
                viewer.screenshotHovering(shots.resolve("usability-2-bank-shop-tooltip.png"), 3 * 9 + 1);
                clickSlot(server, 3 * 9 + 1);
                viewer.screenshot(shots.resolve("usability-3-shop-from-bank.png"));
                viewer.screenshotHovering(shots.resolve("usability-4-shop-back-tooltip.png"), 5 * 9);
                clickSlot(server, 5 * 9);
                viewer.screenshot(shots.resolve("usability-5-back-in-bank.png"));

                // ── the shop on its own, a drawer opened from it ────────────────────────────────────
                clickSlot(server, 5 * 9 + 8);
                server.console("e2e as Viewer shop");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("usability-6-shop.png"));
                clickSlot(server, 1 * 9 + 0);
                viewer.screenshot(shots.resolve("usability-7-shop-drawer.png"));

                // ── the hub, a feature's page, a command's page with Back to the hub ────────────────
                clickSlot(server, 5 * 9 + 8);
                server.console("e2e as Viewer hub economy");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("usability-8-hub-economy.png"));
                clickSlot(server, 1 * 9 + 1);
                Await.ticks(40);
                viewer.screenshotHovering(shots.resolve("usability-8b-from-hub-back-tooltip.png"), 5 * 9);

                // ── the guild page and its choices, a name typed in a window ────────────────────────
                clickSlot(server, 5 * 9 + 8);
                server.console("e2e as Viewer guild");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("usability-9-guild.png"));
                clickSlot(server, 3 * 9 + 2);
                viewer.screenshot(shots.resolve("usability-10-guild-choices.png"));
                clickSlot(server, 1 * 9 + 1);
                Await.ticks(40);
                viewer.screenshot(shots.resolve("usability-11-guild-name-window.png"));
                // Cancel sits at the bottom right of the dialog at GUI scale 2 on this display.
                viewer.click(768, 688);
                Await.ticks(40);

                // ── leaving the guild: the question in the game's own dialog ──────────────────────────
                clickSlot(server, 5 * 9);
                clickSlot(server, 5 * 9 + 4);
                Await.ticks(40);
                viewer.screenshot(shots.resolve("usability-12-leave-guild-dialog.png"));
                assertThat(shots.resolve("usability-12-leave-guild-dialog.png")).exists();
            }
        }
    }
}
