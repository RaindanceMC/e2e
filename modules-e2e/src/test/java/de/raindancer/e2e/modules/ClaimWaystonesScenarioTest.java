package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Waystones on a real server: taken from the claim's list, put down as two barriers under item displays,
 * clicked, travelled between — the fire turning to soul fire while the trip warms up — and a stranger on the
 * claim offered nowhere to go, because new stones are for the owners and the trusted.
 */
@Tag("e2e")
class ClaimWaystonesScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static void expect(Bot bot, String what, String text) {
        try {
            Await.until(what, WAIT, () -> said(bot, text));
        } catch (AssertionError failed) {
            throw new AssertionError(failed.getMessage() + " — " + bot.name() + " saw " + bot.chatText(), failed);
        }
    }

    private static int slot(Bot bot, String text) {
        return Await.value("a button named " + text, WAIT, () -> bot.window().flatMap(window -> window.top()
                .entrySet().stream().filter(entry -> entry.getValue().name().contains(text))
                .map(Map.Entry::getKey).findFirst()).orElse(null));
    }

    private static boolean passes(Server server, String test) {
        return server.console("execute " + test + " run say yes").contains("yes")
                || server.console("execute " + test).contains("passed");
    }

    private static int[] claimAround(Server server, Bot dev) {
        assertThat(server.console("settings set limits.min-area-blocks 1")).contains("is now");
        dev.run("claim new");
        expect(dev, "the selection starts", "Marking out");
        dev.hold(dev.hotbarSlotOf(item -> item.material().toLowerCase().contains("stick")
                || item.material().toLowerCase().contains("hoe")));
        var at = dev.position();
        int x = (int) Math.floor(at.getX());
        int y = (int) Math.floor(at.getY()) - 1;
        int z = (int) Math.floor(at.getZ());
        // Flat and clear: the world is new every run, and a hill where a stone should go is a failed click.
        server.console("fill " + (x - 11) + " " + y + " " + (z - 11) + " " + (x + 11) + " " + y + " " + (z + 11)
                + " minecraft:stone");
        server.console("fill " + (x - 11) + " " + (y + 1) + " " + (z - 11) + " " + (x + 11) + " " + (y + 8) + " "
                + (z + 11) + " minecraft:air");
        Await.ticks(5);
        // Corners out of reach are ignored by the server, so the dev walks — teleports — to each.
        server.console("tp Dev " + (x - 9.5) + " " + (y + 1) + " " + (z - 9.5));
        Await.ticks(10);
        dev.useOn(x - 10, y, z - 10);
        expect(dev, "the first corner", "Corner");
        server.console("tp Dev " + (x + 9.5) + " " + (y + 1) + " " + (z + 9.5));
        Await.ticks(10);
        dev.useOn(x + 10, y, z + 10);
        expect(dev, "a name is asked for", "What should it be called?");
        dev.say("keep");
        expect(dev, "the claim is made", "Claimed");
        server.console("tp Dev " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5));
        Await.ticks(10);
        return new int[]{x, y, z};
    }

    private static void takeAndPlace(Server server, Bot dev, int x, int groundY, int z, String lit) {
        server.console("tp Dev " + (x - 1.5) + " " + (groundY + 1) + " " + (z + 0.5));
        Await.ticks(10);
        dev.run("claim waystones");
        dev.clickSlot(slot(dev, "Take a waystone"));
        expect(dev, "a waystone is handed over", "Here is a waystone");
        dev.closeWindow();
        Await.ticks(5);
        dev.hold(dev.hotbarSlotOf(item -> item.material().toLowerCase().contains("lodestone")));
        Await.ticks(2);
        dev.useOn(x, groundY, z);
        expect(dev, "the stone is lit", lit);
    }

    @Test
    @DisplayName("an owner puts two waystones down and travels between them; a stranger is offered nothing")
    void waystones() {
        try (Server server = Server.start("claim-waystones", List.of("claims-standalone:RainsExtendedClaims-.*"),
                List.of("Claims are up"))) {
            Await.ticks(60);
            Bot dev = server.admin("Dev");
            server.console("gamemode survival Dev");
            Await.ticks(20);
            int[] c = claimAround(server, dev);
            int x = c[0];
            int ground = c[1];
            int z = c[2];

            takeAndPlace(server, dev, x + 5, ground, z, "Waystone is lit");
            int stoneY = ground + 1;
            try {
                Await.until("the first stone is two barriers", WAIT, () ->
                        passes(server, "if block " + (x + 5) + " " + stoneY + " " + z + " minecraft:barrier")
                                && passes(server, "if block " + (x + 5) + " " + (stoneY + 1) + " " + z
                                + " minecraft:barrier"));
            } catch (AssertionError failed) {
                throw new AssertionError(failed.getMessage() + " — expected at " + (x + 5) + " " + stoneY + " " + z
                        + "; check: " + server.console("execute if block " + (x + 5) + " " + stoneY + " " + z
                        + " minecraft:barrier") + "; displays at "
                        + server.console("data get entity @e[type=item_display,limit=1] Pos"), failed);
            }
            String nearA = "@e[type=item_display,x=" + (x + 5.5) + ",y=" + stoneY + ",z=" + (z + 0.5) + ",distance=..1.2]";
            try {
                Await.until("the stone and its fire stand as displays", WAIT, () ->
                        server.console("execute if entity " + nearA).toLowerCase().contains("count: 2"));
            } catch (AssertionError failed) {
                throw new AssertionError(failed.getMessage() + " — console: "
                        + server.console("execute if entity " + nearA) + " / all displays: "
                        + server.console("execute if entity @e[type=item_display]") + " / "
                        + server.console("data get entity @e[type=item_display,limit=1] Pos"), failed);
            }

            takeAndPlace(server, dev, x - 5, ground, z, "Waystone 2 is lit");

            server.console("tp Dev " + (x + 3.5) + " " + stoneY + " " + (z + 0.5));
        Await.ticks(10);
        // Click the first stone and pick the second: two seconds of warm-up, the fire blue meanwhile.
            dev.useOn(x + 5, stoneY + 1, z);
            dev.clickSlot(slot(dev, "Waystone 2"));
            Await.until("the fire turns to soul fire while the trip warms up", WAIT, () ->
                    server.console("execute if entity @e[type=item_display,nbt={item:{components:{"
                            + "\"minecraft:item_model\":\"rainsclaims:claim_waystone_soul_fire\"}}}]")
                            .contains("passed"));
            Await.until("the dev arrives at the second stone", Duration.ofSeconds(10), () ->
                    Math.abs(dev.position().getX() - (x - 4.5)) < 2.5 && Math.abs(dev.position().getZ() - (z + 0.5)) < 2.5);

            // A stranger at the first stone: new stones are for owners and the trusted.
            Bot guest = server.player("Guest");
            server.console("tp Guest " + (x + 3.5) + " " + stoneY + " " + (z + 0.5));
            Await.ticks(20);
            guest.useOn(x + 5, stoneY + 1, z);
            slot(guest, "Nowhere to go");

            // Taken down, the barriers go and the item comes back.
            server.console("tp Dev " + (x + 3.5) + " " + stoneY + " " + (z + 0.5));
            Await.ticks(20);
            dev.useOn(x + 5, stoneY + 1, z);
            dev.clickSlot(slot(dev, "Take it down"));
            dev.clickSlot(slot(dev, "Yes"));
            expect(dev, "the stone is taken down", "back in your pocket");
            Await.until("its barriers are gone", WAIT, () ->
                    passes(server, "if block " + (x + 5) + " " + stoneY + " " + z + " minecraft:air"));
            Await.until("its displays are gone", WAIT, () ->
                    !server.console("execute if entity " + nearA).contains("passed"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsExtendedClaims")).isEmpty();
        }
    }
}
