package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.LocalPostgres;
import de.raindancer.e2e.PaperServer;
import de.raindancer.e2e.VelocityProxy;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
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
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hyades' welcome server behind the proxy (RainsNetworkProxy 1.8.0 newcomers.server, RainsWelcome 0.2.0): a player
 * the network has never seen lands on the welcome server — which runs on a database of its own — watches the intro
 * (a camera flight in spectator mode through the recorded shots, their game mode given back after), is greeted, and
 * /skip takes them on to the SMP; from then on they go straight there. /trailer and /tutorial on the SMP take them back
 * over — /tutorial straight to the guide, told so by a cookie that rides along through Velocity. Sneaking skips an intro. With the welcome server down, a newcomer still lands on
 * the SMP instead of being turned away.
 */
@Tag("e2e")
class WelcomeScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final String SECRET = "e2e-forwarding-secret";
    private static final String WELCOME_DB = "rains_welcome";

    @Test
    @DisplayName("newcomer: welcome server first, /skip to the SMP, then always the SMP; welcome down: the SMP")
    void newcomers() {
        LocalPostgres postgres = LocalPostgres.start(E2e.serverFolder("welcome-postgres"));
        sql(postgres, LocalPostgres.DATABASE, "CREATE DATABASE " + WELCOME_DB);
        String paperGlobal = VelocityProxy.paperGlobal(SECRET);
        PaperServer smp = PaperServer.forRun().seed(1).in(E2e.serverFolder("welcome-smp"))
                .plugin(Path.of(System.getProperty("e2e.rainscore")))
                .plugin(jar("welcome-standalone", "RainsWelcome-.*"))
                .file("config/paper-global.yml", paperGlobal)
                .file("plugins/RainsCore/network.yml", postgres.networkYml("smp", "survival")).build();
        PaperServer welcome = PaperServer.forRun().seed(1).in(E2e.serverFolder("welcome-welcome"))
                .plugin(Path.of(System.getProperty("e2e.rainscore")))
                .plugin(jar("welcome-standalone", "RainsWelcome-.*"))
                .file("config/paper-global.yml", paperGlobal)
                .file("plugins/RainsCore/network.yml", postgres.networkYml("welcome", "welcome")
                        .replace("database: " + LocalPostgres.DATABASE, "database: " + WELCOME_DB))
                .file("plugins/RainsWelcome/intro.yml", """
                        shots:
                        - {world: world, x: 160.5, y: 110.0, z: 160.5, yaw: 0.0, pitch: 30.0, caption: 'Welcome to Hyades | test'}
                        - {world: world, x: 175.5, y: 112.0, z: 168.5, yaw: 90.0, pitch: 20.0, caption: 'Guild halls'}
                        - {world: world, x: 168.5, y: 115.0, z: 182.5, yaw: 180.0, pitch: 10.0, caption: ''}
                        """).build();

        Map<String, Integer> servers = new LinkedHashMap<>();
        servers.put("smp", smp.port());
        servers.put("welcome", welcome.port());
        VelocityProxy proxy = VelocityProxy.in(E2e.serverFolder("welcome-proxy"), servers, SECRET)
                .bind("[::]").plugin(jar("network-proxy", "RainsNetworkProxy-.*"));
        proxy.file("plugins/rainsnetworkproxy/config.properties", "hub=smp\n"
                + "database.host=127.0.0.1\ndatabase.port=" + postgres.port() + "\ndatabase.name=" + LocalPostgres.DATABASE
                + "\ndatabase.user=" + LocalPostgres.USER + "\ndatabase.password=\ndatabase.sslmode=disable\n"
                + "newcomers.server=welcome\n");

        try (proxy; smp; welcome; postgres) {
            smp.start();
            // On the SMP the welcome module only offers /trailer.
            smp.console("settings set welcome:role network");
            smp.restart();
            smp.awaitLog("Welcome on a network server: /trailer sends players to welcome", WAIT);
            welcome.start();
            welcome.awaitLog("Welcome is up: newcomers are greeted and sent on to smp", WAIT);
            welcome.awaitLog("Intro: 3 shot\\(s\\), 0 track\\(s\\) of music", WAIT);
            welcome.console("settings set welcome:seconds-per-shot 3");
            proxy.start();
            assertThat(proxy.log()).contains("Players new to the network go to welcome first");

            // ── Ada, new: the welcome server, which writes nothing into the network's database ──────────────────
            Bot ada = welcome.bot("Ada").record(ClientboundSetCameraPacket.class).record(ClientboundAddEntityPacket.class)
                    .through("localhost", proxy.port()).join();
            Await.until("Ada is on the welcome server", WAIT, () -> welcome.console("list").contains("Ada"));
            assertThat(proxy.log()).contains("Ada is new to the network: first to welcome");
            // The intro waits for her resource pack (a bot never answers: 30 s), then flies in spectator mode.
            Await.until("Ada watches the intro", Duration.ofSeconds(90),
                    () -> welcome.console("data get entity Ada playerGameType").contains(": 3"));
            int camera = Await.value("a camera for Ada to watch through", WAIT, () -> ada.received(
                    ClientboundSetCameraPacket.class).stream().map(ClientboundSetCameraPacket::getCameraEntityId)
                    .filter(id -> id != ada.entityId()).findFirst().orElse(null));
            // The shots are near her spawn: a bot never confirms chunk batches, so after a teleport far away
            // Paper sends it no more chunks — and no entities in them. A real client does (WelcomeIntroScreenshotsTest).
            assertThat(ada.received(ClientboundAddEntityPacket.class)).as("she was sent the display she watches through")
                    .anyMatch(added -> added.getEntityId() == camera && added.getType() == EntityType.ITEM_DISPLAY);
            Await.until("the first caption", WAIT, () -> ada.titles().stream().anyMatch(t -> t.contains("Welcome to Hyades")));
            Await.until("the intro is over and her game mode is back", WAIT,
                    () -> !welcome.console("data get entity Ada playerGameType").contains(": 3"));
            Await.until("Ada was greeted", WAIT,
                    () -> ada.titles().stream().filter(t -> t.contains("Welcome to Hyades")).count() >= 2);
            assertThat(welcome.console("execute if entity @e[type=item_display]")).as("the camera is gone")
                    .containsIgnoringCase("failed");

            // ── sneaking skips an intro (a staff preview here) ──────────────────────────────────────────────────
            welcome.console("op Ada");
            ada.run("intro play");
            Await.until("Ada watches again", WAIT, () -> welcome.console("data get entity Ada playerGameType").contains(": 3"));
            Await.ticks(40);
            ada.keys(false, false, false, false, false, true, false);
            Await.until("sneaking skipped it", Duration.ofSeconds(10),
                    () -> !welcome.console("data get entity Ada playerGameType").contains(": 3"));
            ada.keys(false, false, false, false, false, false, false);
            welcome.console("deop Ada");
            assertThat(sql(postgres, LocalPostgres.DATABASE,
                    "SELECT count(*) FROM core.network_first_seen WHERE player = '" + ada.id() + "'"))
                    .as("the network has still not seen her").isEqualTo("0");
            assertThat(sql(postgres, WELCOME_DB,
                    "SELECT count(*) FROM core.network_first_seen WHERE player = '" + ada.id() + "'"))
                    .as("the welcome server keeps its own records").isEqualTo("1");

            // ── /skip: on to the SMP, through the proxy ─────────────────────────────────────────────────────────
            ada.run("skip");
            Await.until("Ada is on the SMP", WAIT, () -> smp.console("list").contains("Ada"));
            Await.until("the network has seen her now", WAIT, () -> sql(postgres, LocalPostgres.DATABASE,
                    "SELECT count(*) FROM core.network_first_seen WHERE player = '" + ada.id() + "'").equals("1"));
            ada.leave();
            Await.until("Ada left", WAIT, () -> !smp.console("list").contains("Ada"));

            // ── back again: straight to the SMP ─────────────────────────────────────────────────────────────────
            Bot back = smp.bot("Ada").through("localhost", proxy.port()).join();
            Await.until("Ada is on the SMP at once", WAIT, () -> smp.console("list").contains("Ada"));
            assertThat(welcome.console("list")).doesNotContain("Ada");
            assertThat(proxy.log().split("Ada is new to the network", -1)).as("only new once").hasSize(2);

            // ── /trailer on the SMP: over to the welcome server to watch it ─────────────────────────────────────
            back.run("trailer");
            Await.until("Ada went over for the trailer", WAIT, () -> welcome.console("list").contains("Ada"));

            // ── /tutorial on the SMP: over to the welcome server, straight to the guide, no intro first ─────────
            welcome.console("op Ada");
            back.run("tutorial island generate");
            Await.until("the island is built", WAIT,
                    () -> String.join("\n", back.chatText()).contains("tutorial island is built"));
            welcome.console("deop Ada");
            back.run("skip");
            Await.until("Ada is back on the SMP", WAIT, () -> smp.console("list").contains("Ada"));
            int dialogs = back.dialogsShown();
            back.run("tutorial");
            Await.until("Ada went over for the tour", WAIT, () -> welcome.console("list").contains("Ada"));
            // The note rode along as a cookie on her client, through Velocity.
            var hello = back.awaitDialog(dialogs, WAIT);
            assertThat(hello.title()).as("her guide greets her at once").contains("Hi, I'm Iris");
            assertThat(welcome.console("data get entity Ada playerGameType")).as("no intro in between").doesNotContain(": 3");
            assertThat(welcome.console("execute in tutorial if entity @a[name=Ada]")).containsIgnoringCase("passed");
            back.leave();
            Await.until("Ada left", WAIT, () -> !welcome.console("list").contains("Ada"));

            // ── the welcome server down: a newcomer lands on the SMP instead of being turned away ───────────────
            welcome.stop();
            Bot bo = smp.bot("Bo").through("localhost", proxy.port()).join();
            Await.until("Bo is on the SMP", WAIT, () -> smp.console("list").contains("Bo"));
            assertThat(proxy.log()).contains("Bo: the welcome server could not be reached, on to smp");
            assertThat(bo.isOnline()).isTrue();

            assertThat(smp.errorsFrom("RainsCore").stream()
                    .filter(line -> !line.contains("resource pack server could not start"))).isEmpty();
        }
    }

    private static String sql(LocalPostgres postgres, String database, String query) {
        try {
            Process psql = new ProcessBuilder("psql", "-h", "127.0.0.1", "-p", String.valueOf(postgres.port()),
                    "-U", LocalPostgres.USER, "-d", database, "-tA", "-v", "ON_ERROR_STOP=1", "-c", query)
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
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no " + pattern + " in " + folder + " — build it first"));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
