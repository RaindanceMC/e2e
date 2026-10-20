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
 * A claim's flight perk ends when its flier leaves: flying out, they lose flight after the grace and come down on
 * Slow Falling — also when {@code /fly} was on as they flew in (found on Lilly, 20.10.2026: they kept flying).
 * A bot has no physics, so "comes down" is what the server makes of them: no flight, not flying, Slow Falling on.
 */
@Tag("e2e")
class ClaimFlightScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);
    /** The grace is two seconds; this leaves room for a slow runner. */
    private static final Duration AFTER_GRACE = Duration.ofSeconds(8);

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

    private static String data(Server server, String name, String path) {
        return server.console("data get entity " + name + " " + path);
    }

    private static boolean mayFly(Server server, String name) {
        return data(server, name, "abilities.mayfly").contains("1b");
    }

    private static void flyTo(Bot bot, double x, double y, double z) {
        moveTo(bot, x, y, z, false);
    }

    private static void walkTo(Bot bot, double x, double y, double z) {
        moveTo(bot, x, y, z, true);
    }

    /** There a little at a time — the server refuses a client that moves too far at once. */
    private static void moveTo(Bot bot, double x, double y, double z, boolean onGround) {
        var from = bot.position();
        double dx = x - from.getX();
        double dy = y - from.getY();
        double dz = z - from.getZ();
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.6);
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            bot.moveTo(from.getX() + dx * t, from.getY() + dy * t, from.getZ() + dz * t, onGround);
            Await.ticks(1);
        }
    }

    /** A five by five claim round where the dev stands, with its flight perk switched on. @return its centre */
    private static double[] flyingClaim(Server server, Bot dev) {
        assertThat(server.console("settings set limits.min-area-blocks 1")).contains("is now");
        dev.run("claim new");
        expect(dev, "the selection starts", "Marking out");
        dev.hold(dev.hotbarSlotOf(item -> item.material().toLowerCase().contains("stick")
                || item.material().toLowerCase().contains("hoe")));
        var at = dev.position();
        int x = (int) Math.floor(at.getX());
        int y = (int) Math.floor(at.getY()) - 1;
        int z = (int) Math.floor(at.getZ());
        dev.useOn(x - 2, y, z - 2);
        expect(dev, "the first corner", "Corner");
        dev.useOn(x + 2, y, z + 2);
        expect(dev, "a name is asked for", "What should it be called?");
        dev.say("pad");
        expect(dev, "the claim is made", "Claimed");

        dev.run("claim");
        dev.clickSlot(slot(dev, "pad"));
        dev.clickSlot(slot(dev, "Configuration"));
        dev.clickSlot(slot(dev, "Perks"));
        dev.clickSlot(slot(dev, "Flight"));
        dev.closeWindow();
        return new double[]{x + 0.5, at.getY(), z + 0.5};
    }

    private static void comesDown(Server server, String name) {
        try {
            Await.until(name + " loses flight after the grace", AFTER_GRACE, () -> !mayFly(server, name));
        } catch (AssertionError failed) {
            throw new AssertionError(failed.getMessage() + " — abilities: " + data(server, name, "abilities"), failed);
        }
        assertThat(data(server, name, "abilities.flying")).as("no longer flying").contains("0b");
        assertThat(data(server, name, "active_effects")).as("floats down").contains("slow_falling");
    }

    @Test
    @DisplayName("a dev flies in a flying claim, flies out, and comes down")
    void flyOut() {
        try (Server server = Server.start("claim-flight", List.of("claims-standalone:RainsExtendedClaims-.*"),
                List.of("Claims are up"))) {
            Await.ticks(60);
            Bot dev = server.admin("Dev");
            server.console("gamemode survival Dev");
            Await.ticks(20);
            double[] pad = flyingClaim(server, dev);

            Await.until("standing in the claim, the dev may fly", WAIT, () -> mayFly(server, "Dev"));
            dev.flying(true);
            flyTo(dev, pad[0], pad[1] + 4, pad[2]);
            Await.ticks(10);
            assertThat(mayFly(server, "Dev")).as("still flying over the claim").isTrue();

            flyTo(dev, pad[0] + 10, pad[1] + 4, pad[2]);
            comesDown(server, "Dev");
            assertThat(server.paper.errorsFrom("RainsCore", "RainsExtendedClaims")).isEmpty();
        }
    }

    @Test
    @DisplayName("/fly on, into the flying claim, /fly off, out of it: the claim's flight ends too")
    void flyCommandThenOut() {
        try (Server server = Server.start("claim-flight-fly", List.of("claims-standalone:RainsExtendedClaims-.*",
                "playerutils-standalone:RainsPlayerUtils-.*"), List.of("Claims are up", "Player utils are up"))) {
            Await.ticks(60);
            Bot dev = server.admin("Dev");
            server.console("gamemode survival Dev");
            Await.ticks(20);
            double[] pad = flyingClaim(server, dev);
            // Out on the ground first, /fly on there, then back in.
            walkTo(dev, pad[0] + 10, pad[1], pad[2]);
            Await.ticks(10);
            dev.runAndExpect("fly Dev on", "can fly");
            walkTo(dev, pad[0], pad[1], pad[2]);
            Await.ticks(20);
            dev.run("fly Dev off");
            Await.ticks(10);
            assertThat(mayFly(server, "Dev")).as("the claim still lets them fly").isTrue();

            dev.flying(true);
            flyTo(dev, pad[0], pad[1] + 4, pad[2]);
            flyTo(dev, pad[0] + 10, pad[1] + 4, pad[2]);
            comesDown(server, "Dev");
            assertThat(server.paper.errorsFrom("RainsCore", "RainsExtendedClaims", "RainsPlayerUtils")).isEmpty();
        }
    }
}
