package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hub of a Capture the Flag server: built from its schematic into a world of its own, everybody joining lands on
 * its spawn with the vote compass in hand, nobody breaks it, and whoever walks off its edge is back on the spawn.
 */
@Tag("e2e")
class CtfHubScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(60);

    @Test
    @DisplayName("players land in the hub, holding the vote compass; it holds; off the edge is back to the spawn")
    void landInTheHub() throws Exception {
        Map<String, String> files = Map.of(
                "plugins/RainsCaptureTheFlag/config.yml", "lobby-on-join: true\nauto-start: false\n",
                "plugins/RainsCaptureTheFlag/hub/hub.yml",
                "file: hub.schem\ntitle: Test Hub\nauthor: e2e\nspawn: 10.5 1 10.5 180 0\n");
        try (Server server = Server.start("ctf-hub", List.of("ctf-standalone:RainsCaptureTheFlag-.*"), List.of(),
                files, List.of("Capture the Flag is up"))) {
            Path hub = server.paper.folder().resolve("plugins/RainsCaptureTheFlag/hub/hub.schem");
            Files.write(hub, platform(21, 21));
            server.paper.console("ctf admin hub build");
            server.paper.awaitLog("The hub is built", WAIT);

            Bot alice = server.player("alice");
            Await.until("alice is in the hub", WAIT, () -> alice.world().contains("ctf_hub"));
            // The platform is 21 wide, built centred on 0/0: its lowest corner is at -10/64/-10, the spawn at 0.5/65/0.5.
            Await.until("on the spawn", WAIT, () -> Math.abs(alice.position().getX() - 0.5) < 1
                    && Math.abs(alice.position().getZ() - 0.5) < 1 && Math.abs(alice.position().getY() - 65) < 1);
            Await.until("the vote compass in hand", WAIT, () -> alice.items().stream().anyMatch(item -> item.is("compass")));

            alice.dig(0, 64, 3);
            Await.ticks(20);
            assertThat(server.paper.console("execute in minecraft:ctf_hub if block 0 64 3 minecraft:stone"))
                    .as("nobody breaks the hub").contains("passed");

            // Off the edge and down into the void, a step at a time as a client falls.
            for (double x = 0.5; x <= 13; x += 0.5) {
                alice.moveTo(x, 65, 0.5, true);
                Await.ticks(1);
            }
            for (double y = 65; y > 40; y -= 0.8) {
                alice.moveTo(13, y, 0.5, false);
                Await.ticks(1);
            }
            Await.until("back on the spawn", WAIT, () -> Math.abs(alice.position().getX() - 0.5) < 1
                    && Math.abs(alice.position().getY() - 65) < 1);
            assertThat(alice.health()).as("the fall hurt nobody").isEqualTo(20f);
            assertThat(server.paper.errorsFrom("RainsCaptureTheFlag", "RainsCore")).isEmpty();
        }
    }

    /** A flat square of stone, one block high, as a Sponge schematic. */
    private static byte[] platform(int width, int length) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(raw)) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(3);
            out.writeUTF("Version");
            out.writeInt(2);
            out.writeByte(3);
            out.writeUTF("DataVersion");
            out.writeInt(4440);
            for (String[] size : new String[][]{{"Width", String.valueOf(width)}, {"Height", "1"},
                    {"Length", String.valueOf(length)}}) {
                out.writeByte(2);
                out.writeUTF(size[0]);
                out.writeShort(Integer.parseInt(size[1]));
            }
            out.writeByte(10);
            out.writeUTF("Palette");
            out.writeByte(3);
            out.writeUTF("minecraft:stone");
            out.writeInt(0);
            out.writeByte(0);
            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(width * length);
            out.write(new byte[width * length]);
            out.writeByte(0);
        }
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(zipped)) {
            gzip.write(raw.toByteArray());
        }
        return zipped.toByteArray();
    }
}
