package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.LocalPostgres;
import de.raindancer.e2e.PaperServer;
import de.raindancer.e2e.VelocityProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lilly's network with the whitelist at the proxy (RainsNetworkProxy 1.4.0, RainsCore 2.38.0): one list in the
 * network's database, checked before any server sees a player — by the default address (the hub), by the SMP's own
 * name and by a minigame's. Somebody not on it is turned away at the door with a Discord /link code written where
 * Discord Link's bot redeems it; {@code /whitelist add|remove} on any server changes the network's list. The
 * backends keep their own whitelist on: a player the proxy lets in must not be refused by a backend that has not
 * heard of them yet.
 */
@Tag("e2e")
class ProxyWhitelistScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final String SECRET = "e2e-forwarding-secret";
    private static final Pattern CODE = Pattern.compile("/link ([A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5})");

    @Test
    @DisplayName("default address, smp. and ctf.: on the list in, off it a /link code at the door, /whitelist anywhere")
    void whitelistAtTheDoor() {
        LocalPostgres postgres = LocalPostgres.start(E2e.serverFolder("proxy-whitelist-postgres"));
        String paperGlobal = VelocityProxy.paperGlobal(SECRET);
        PaperServer hub = backend("hub", "lobby", postgres, paperGlobal)
                .plugin(jar("discordlink-standalone", "RainsDiscordLink-.*")).build();
        PaperServer smp = backend("smp", "survival", postgres, paperGlobal).build();
        PaperServer ctf = backend("ctf", "minigames", postgres, paperGlobal).build();

        Map<String, Integer> servers = new LinkedHashMap<>();
        servers.put("hub", hub.port());
        servers.put("lillyxoxo", smp.port());
        servers.put("ctf", ctf.port());
        VelocityProxy proxy = VelocityProxy.in(E2e.serverFolder("proxy-whitelist-proxy"), servers, SECRET)
                .bind("[::]").forcedHost("smp.localhost", "lillyxoxo").forcedHost("ctf.localhost", "ctf")
                .plugin(jar("network-proxy", "RainsNetworkProxy-.*"));
        proxy.file("plugins/rainsnetworkproxy/config.properties", "hub=hub\n"
                + "database.host=127.0.0.1\ndatabase.port=" + postgres.port() + "\ndatabase.name=" + LocalPostgres.DATABASE
                + "\ndatabase.user=" + LocalPostgres.USER + "\ndatabase.password=\ndatabase.sslmode=disable\n"
                + "whitelist=true\nwhitelist.code-minutes=30\n");

        try (proxy; hub; smp; ctf; postgres) {
            hub.start();
            smp.start();
            ctf.start();
            for (PaperServer backend : List.of(hub, smp, ctf)) {
                backend.console("whitelist on");
            }
            proxy.start();
            assertThat(proxy.log()).contains("whitelist is checked at the door");

            // ── /whitelist add on the SMP puts Ada on the network's list ─────────────────────────────────────────
            smp.console("whitelist add Ada");
            Bot ada = hub.bot("Ada");
            Await.until("Ada is on the network's list", WAIT, () -> sql(postgres,
                    "SELECT count(*) FROM core.whitelist WHERE player = '" + ada.id() + "'").equals("1"));

            ada.through("localhost", proxy.port()).join();
            Await.until("Ada is on the hub by the default address", WAIT, () -> hub.console("list").contains("Ada"));
            ada.leave();
            Await.until("Ada left", WAIT, () -> !hub.console("list").contains("Ada"));

            smp.bot("Ada").through("smp.localhost", proxy.port()).join().leave();
            assertThat(smp.log()).contains("Ada joined the game");

            ctf.bot("Ada").through("ctf.localhost", proxy.port()).join().leave();
            assertThat(ctf.log()).as("ctf heard nothing yet, and let her in all the same").contains("Ada joined the game");

            // ── Bo is not on it: no bot running, the plain refusal at the door ───────────────────────────────────
            Bot bo = hub.bot("Bo").through("localhost", proxy.port());
            assertThat(bo.joinRefused()).contains("not whitelisted on this network");

            // ── with the bot running somewhere: a code, by any address, written where the bot redeems it ─────────
            botRunning(postgres);
            String byCtf = bo.through("ctf.localhost", proxy.port()).joinRefused();
            Matcher found = CODE.matcher(byCtf);
            assertThat(found.find()).as("a /link code on the screen: " + byCtf).isTrue();
            String code = found.group(1);
            assertThat(sql(postgres, "SELECT player || ' ' || name || ' ' || purpose FROM discordlink.discord_link_code"
                    + " WHERE code = '" + code + "'")).isEqualTo(bo.id() + " Bo JOIN");
            botRunning(postgres);
            assertThat(bo.through("smp.localhost", proxy.port()).joinRefused()).as("the same code again").contains(code);
            for (PaperServer backend : List.of(hub, smp, ctf)) {
                assertThat(backend.log()).as("no server saw Bo").doesNotContain("Bo joined", "Bo[/");
            }

            // ── /whitelist add on a minigame lets Bo in everywhere; /whitelist remove on the hub shuts the door ──
            ctf.console("whitelist add Bo");
            Await.until("Bo is on the network's list", WAIT, () -> sql(postgres,
                    "SELECT count(*) FROM core.whitelist WHERE player = '" + bo.id() + "'").equals("1"));
            smp.bot("Bo").through("smp.localhost", proxy.port()).join().leave();
            assertThat(smp.log()).contains("Bo joined the game");

            hub.console("whitelist remove Bo");
            Await.until("Bo is off the network's list", WAIT, () -> sql(postgres,
                    "SELECT count(*) FROM core.whitelist WHERE player = '" + bo.id() + "'").equals("0"));
            assertThat(hub.bot("Bo").through("localhost", proxy.port()).joinRefused()).contains("whitelist");

            for (PaperServer backend : List.of(hub, smp, ctf)) {
                // Three servers on one machine: only the first gets the resource-pack port.
                assertThat(backend.errorsFrom("RainsCore", "RainsDiscordLink").stream()
                        .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
            }
        }
    }

    private static PaperServer.Builder backend(String id, String group, LocalPostgres postgres, String paperGlobal) {
        return PaperServer.forRun().seed(1).in(E2e.serverFolder("proxy-whitelist-" + id))
                .plugin(Path.of(System.getProperty("e2e.rainscore")))
                .file("config/paper-global.yml", paperGlobal)
                .file("plugins/RainsCore/network.yml", postgres.networkYml(id, group));
    }

    /** What Discord Link's bot does every few seconds while it runs: renew its network role. */
    private static void botRunning(LocalPostgres postgres) {
        sql(postgres, "INSERT INTO core.network_role (role, server, heartbeat_at) VALUES ('discordlink-bot', 'hub', "
                + System.currentTimeMillis() + ") ON CONFLICT (role) DO UPDATE SET heartbeat_at = excluded.heartbeat_at");
    }

    private static String sql(LocalPostgres postgres, String query) {
        try {
            Process psql = new ProcessBuilder("psql", "-h", "127.0.0.1", "-p", String.valueOf(postgres.port()),
                    "-U", LocalPostgres.USER, "-d", LocalPostgres.DATABASE, "-tA", "-v", "ON_ERROR_STOP=1", "-c", query)
                    .redirectErrorStream(true).start();
            String out = new String(psql.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (psql.waitFor() != 0) {
                throw new AssertionError("psql: " + out);
            }
            return out;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
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
