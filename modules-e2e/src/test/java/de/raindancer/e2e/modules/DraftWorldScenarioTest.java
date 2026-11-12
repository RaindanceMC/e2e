package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The draft world on a real server with FastAsyncWorldEdit: a builder's own things stay outside and the draft world's
 * inside, whichever way they cross; WorldEdit's clipboard and schematics carry builds both ways; and nobody else gets in.
 */
@Tag("e2e")
class DraftWorldScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final int Y = 71;
    /** The surface of a flat world: grass at -61, standing at -60. */
    private static final int FLAT = -60;

    private static boolean passes(Server server, String test) {
        return server.console("execute " + test).contains("passed");
    }

    private static boolean carries(Server server, String who, String item) {
        return passes(server, "if items entity " + who + " container.* minecraft:" + item);
    }

    private static boolean inDraftWorld(Server server, String who) {
        return server.console("data get entity " + who + " Dimension").contains("build");
    }

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    @Test
    @DisplayName("/draftworld swaps a builder's life both ways, carries //copy and schematics across, refuses what "
            + "does not belong inside, and keeps everybody else out")
    void draftWorld() {
        try (Server server = Server.start("draftworld", List.of("draftworld-standalone:RainsDraftWorld-.*",
                        "fawe:FastAsyncWorldEdit-Paper-.*"),
                List.of("Draft world 'build' is ready \\(kept still\\)"))) {
            server.console("forceload add -16 -16 16 16");
            server.console("fill -6 " + (Y - 3) + " -6 8 " + (Y - 1) + " 6 minecraft:stone");
            server.console("fill -6 " + Y + " -6 8 " + (Y + 3) + " 6 minecraft:air");
            server.console("setblock 3 " + Y + " 0 minecraft:gold_block");
            server.console("setblock 4 " + Y + " 0 minecraft:diamond_block");
            server.console("execute in minecraft:build run forceload add -16 -16 16 16");

            Bot ada = server.admin("Ada");
            server.console("tp Ada 0.5 " + Y + " 0.5 -90 0");
            server.console("gamemode survival Ada");
            server.console("clear Ada");
            server.console("give Ada minecraft:diamond_sword 1");
            Await.ticks(20);

            // ---- a build copied and saved as a schematic in the main world
            ada.run("//pos1 3," + Y + ",0");
            ada.run("//pos2 4," + Y + ",0");
            Await.ticks(10);
            ada.run("//copy");
            Await.ticks(20);
            ada.run("//schem save e2e-draft -f");
            Await.until("the schematic is on disk", WAIT, () -> server.paper.folder()
                    .resolve("plugins/FastAsyncWorldEdit/schematics/e2e-draft.schem").toFile().isFile());

            // ---- in: own things put away, creative, empty
            ada.forgetChat();
            ada.run("draftworld");
            ada.expectChat("You are in the draft world");
            Await.until("Ada is in the draft world", WAIT, () -> inDraftWorld(server, "Ada"));
            assertThat(carries(server, "Ada", "diamond_sword")).as("her own sword came in").isFalse();
            assertThat(passes(server, "if entity @a[name=Ada,gamemode=creative]")).as("creative inside").isTrue();

            // ---- the clipboard came along: paste it, then the schematic beside it
            server.console("execute in minecraft:build run tp Ada 0.5 " + FLAT + " 0.5 -90 0");
            Await.ticks(20);
            ada.run("//paste");
            Await.until("the copied build stands in the draft world", WAIT,
                    () -> passes(server, "in minecraft:build if block 3 " + FLAT + " 0 minecraft:gold_block")
                            && passes(server, "in minecraft:build if block 4 " + FLAT + " 0 minecraft:diamond_block"));
            server.console("execute in minecraft:build run tp Ada 0.5 " + FLAT + " 5.5 -90 0");
            Await.ticks(20);
            ada.run("//schem load e2e-draft");
            Await.ticks(20);
            ada.run("//paste");
            Await.until("the schematic stands in the draft world too", WAIT,
                    () -> passes(server, "in minecraft:build if block 3 " + FLAT + " 5 minecraft:gold_block"));

            // ---- a build in the draft world, copied to take back out
            server.console("execute in minecraft:build run setblock 0 " + FLAT + " 10 minecraft:emerald_block");
            ada.run("//pos1 0," + FLAT + ",10");
            ada.run("//pos2 0," + FLAT + ",10");
            Await.ticks(10);
            server.console("execute in minecraft:build run tp Ada 0.5 " + FLAT + " 10.5 -90 0");
            Await.ticks(20);
            ada.run("//copy");
            Await.ticks(20);

            // ---- what does not belong inside is refused
            server.console("give Ada minecraft:bedrock 1");
            ada.forgetChat();
            ada.run("seed");
            ada.expectChat("does not work in the draft world");
            ada.forgetChat();
            ada.run("gamemode survival");
            Await.ticks(20);
            assertThat(passes(server, "if entity @a[name=Ada,gamemode=creative]")).as("still creative").isTrue();
            assertThat(said(ada, "cannot be in")).as("told why (heard " + ada.chatText() + ")").isTrue();

            // ---- out, another way than the command: her own things back, the creative ones stay inside
            server.console("execute in minecraft:overworld run tp Ada 0.5 " + Y + " 2.5 -90 0");
            Await.until("Ada is out", WAIT, () -> !inDraftWorld(server, "Ada"));
            Await.until("her own sword is back", WAIT, () -> carries(server, "Ada", "diamond_sword"));
            assertThat(carries(server, "Ada", "bedrock")).as("creative bedrock walked out").isFalse();
            assertThat(passes(server, "if entity @a[name=Ada,gamemode=survival]")).as("survival again").isTrue();

            // ---- the draft world's build pasted outside
            server.console("op Ada");
            ada.run("//paste");
            Await.until("the emerald came out with the clipboard", WAIT,
                    () -> passes(server, "in minecraft:overworld if block 0 " + Y + " 2 minecraft:emerald_block"));

            // ---- in again: the draft side was kept; out with the command to where she stood
            ada.forgetChat();
            ada.run("draftworld");
            Await.until("in again", WAIT, () -> inDraftWorld(server, "Ada"));
            Await.until("the draft side came back", WAIT, () -> carries(server, "Ada", "bedrock"));
            assertThat(carries(server, "Ada", "diamond_sword")).isFalse();
            ada.forgetChat();
            ada.run("draftworld");
            ada.expectChat("Back where you were");
            Await.until("out again", WAIT, () -> !inDraftWorld(server, "Ada"));
            Await.until("as herself", WAIT, () -> carries(server, "Ada", "diamond_sword"));

            // ---- everybody else stays out, however they try
            Bot bob = server.player("Bob");
            Await.ticks(10);
            bob.forgetChat();
            server.console("execute in minecraft:build run tp Bob 0.5 " + FLAT + " 0.5");
            Await.ticks(20);
            assertThat(inDraftWorld(server, "Bob")).as("Bob got in").isFalse();
            Await.until(() -> "Bob is told (heard " + bob.chatText() + ")", WAIT,
                    () -> said(bob, "Only builders"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsDraftWorld")).isEmpty();
        }
    }
}
