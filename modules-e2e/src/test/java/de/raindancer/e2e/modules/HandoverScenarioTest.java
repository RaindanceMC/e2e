package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.PaperServer;
import de.raindancer.e2e.VelocityProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two proxies in two "data centres", as Lilly runs it since 20.10.2026: the hub behind one, the arena behind the
 * other. RainsNetworkProxy's handover sends a player switching servers to the other proxy with Minecraft's transfer,
 * and that proxy picks the server by the host name it was reached under (forced-hosts) — there and back again.
 *
 * <p>The names {@code arena.localhost}/{@code hub.localhost} resolve to the loopback address, so both proxies take
 * IPv6 too. What only a real client and real proxies show: Velocity really sends the transfer, the client really
 * comes back with it, and the receiving proxy lets a transferred player in and routes by name.
 */
@Tag("e2e")
class HandoverScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final String SECRET = "e2e-forwarding-secret";

    @Test
    @DisplayName("hub proxy → transfer → arena proxy, and /hub on the arena back to the hub proxy")
    void handsOverAndBack() {
        String paperGlobal = VelocityProxy.paperGlobal(SECRET);
        PaperServer hub = PaperServer.forRun().seed(1).in(E2e.serverFolder("handover-hub"))
                .file("config/paper-global.yml", paperGlobal).build();
        PaperServer arena = PaperServer.forRun().seed(1).in(E2e.serverFolder("handover-arena"))
                .file("config/paper-global.yml", paperGlobal).build();
        Path proxyPlugin = jar("network-proxy", "RainsNetworkProxy-.*");

        Map<String, Integer> helsinki = new LinkedHashMap<>();
        helsinki.put("hub", hub.port());
        helsinki.put("arena", arena.port());
        Map<String, Integer> frankfurt = new LinkedHashMap<>();
        frankfurt.put("arena", arena.port());
        frankfurt.put("hub", hub.port());
        VelocityProxy north = VelocityProxy.in(E2e.serverFolder("handover-proxy-north"), helsinki, SECRET)
                .bind("[::]").acceptsTransfers().forcedHost("hub.localhost", "hub").plugin(proxyPlugin);
        VelocityProxy south = VelocityProxy.in(E2e.serverFolder("handover-proxy-south"), frankfurt, SECRET)
                .bind("[::]").acceptsTransfers().forcedHost("arena.localhost", "arena").plugin(proxyPlugin);
        north.file("plugins/rainsnetworkproxy/config.properties",
                "hub=hub\nhandover.arena=arena.localhost:" + south.port() + "\n");
        south.file("plugins/rainsnetworkproxy/config.properties",
                "hub=hub\nhandover.hub=hub.localhost:" + north.port() + "\n");

        try (north; south; hub; arena) {
            hub.start();
            arena.start();
            north.start();
            south.start();
            assertThat(north.log()).contains("handed over to their own proxy");
            assertThat(south.log()).contains("handed over to their own proxy");

            Bot ada = hub.bot("Ada").through(north.port()).join();
            Await.until("Ada is on the hub", WAIT, () -> hub.console("list").contains("Ada"));

            // ── to the arena: the north proxy hands Ada over, the south proxy takes her in by name ──────────
            north.command("send Ada arena");
            Await.until("the north proxy handed Ada over", WAIT, () -> north.log().contains("Ada handed over to arena"));
            Await.until("Ada came in at the south proxy", WAIT,
                    () -> south.log().contains("Ada -> arena has connected"));
            Await.until("Ada is on the arena", WAIT, () -> arena.console("list").contains("Ada"));
            Await.until("Ada left the hub", WAIT, () -> !hub.console("list").contains("Ada"));
            assertThat(north.log()).doesNotContain("Ada -> arena has connected");

            Await.until("Ada is in the arena's world", WAIT, ada::isOnline);
            assertThat(ada.transfers()).containsExactly("arena.localhost:" + south.port());

            // ── and back by /hub, typed on the arena: the south proxy hands her over to the hub's ────────────
            ada.run("hub");
            Await.until("the south proxy handed Ada back", WAIT, () -> south.log().contains("Ada handed over to hub"));
            Await.until("Ada came in at the north proxy again", WAIT,
                    () -> north.log().split("Ada -> hub has connected", -1).length == 3);
            Await.until("Ada is on the hub again", WAIT, () -> hub.console("list").contains("Ada"));
            Await.until("Ada left the arena", WAIT, () -> !arena.console("list").contains("Ada"));
            assertThat(south.log()).doesNotContain("Ada -> hub has connected");
            assertThat(ada.transfers()).containsExactly("arena.localhost:" + south.port(), "hub.localhost:" + north.port());
        }
    }

    private static Path jar(String module, String pattern) {
        Path folder = Path.of(System.getProperty("e2e.reactor")).resolve(module).resolve("target");
        try (Stream<Path> jars = Files.list(folder)) {
            return jars.filter(path -> path.getFileName().toString().matches(pattern + "\\.jar"))
                    .filter(path -> !path.getFileName().toString().startsWith("original-"))
                    .max(Path::compareTo)
                    .orElseThrow(() -> new AssertionError("no " + pattern + " in " + folder + " — build it first"));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
