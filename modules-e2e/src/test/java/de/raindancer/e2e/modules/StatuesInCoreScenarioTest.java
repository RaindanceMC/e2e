package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The statues are Core's (RainsCore 2.106.0): Economy's statues from before are taken over once and keep standing
 * exactly where they stood (their old pieces reused, not put up twice); Prime offers the prime board, Ranks the
 * creator board from whoever holds the creator rank, all placed with the same {@code /eco statue}. With LuckPerms
 * (its jar at {@code -De2e.luckperms}, by default where the Hyades preview keeps it), a creator made by {@code lp}
 * alone stands too.
 */
@Tag("e2e")
class StatuesInCoreScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final List<String> RICHEST = List.of(
            "world 200.50 100.00 200.50 90.0 richest 1 -",
            "world 202.50 100.00 200.50 90.0 richest 2 -",
            "world 198.50 100.00 200.50 90.0 richest 3 -");

    private static boolean said(Bot bot, String text) {
        return bot.chatText().stream().anyMatch(line -> line.contains(text));
    }

    private static int count(Server server, String selector) {
        String out = server.console("execute if entity " + selector);
        java.util.regex.Matcher found = java.util.regex.Pattern.compile("(\\d+)").matcher(out);
        return found.find() ? Integer.parseInt(found.group(1)) : 0;
    }

    /** Statues wearing this holder: a player's id, or "-" for a place nobody holds. */
    private static String wearing(String holder) {
        return "@e[type=minecraft:mannequin,nbt={BukkitValues:{\"rainscore:statue_holder\":\"" + holder + "\"}}]";
    }

    /** Statues whose feet are at exactly this point. */
    private static String statueAt(double x, double y, double z) {
        return "@e[type=minecraft:mannequin,x=" + x + ",y=" + y + ",z=" + z + ",distance=..0.3]";
    }

    private static Path luckPerms() {
        String tmp = System.getenv().getOrDefault("TMPDIR", System.getProperty("java.io.tmpdir"));
        return Path.of(System.getProperty("e2e.luckperms",
                Path.of(tmp, "hyades-preview", "set", "LuckPerms-Bukkit-5.5.87.jar").toString()));
    }

    @Test
    @DisplayName("Economy's richest statues are taken over where they stand, old pieces reused; prime and creator statues show their people")
    void takenOverAndThanked() {
        Map<String, String> files = Map.of("plugins/RainsEconomy/config.yml",
                "statue:\n  podiums:\n" + RICHEST.stream().map(line -> "  - '" + line + "'\n")
                        .collect(java.util.stream.Collectors.joining()));
        try (Server server = Server.start("statues-in-core", List.of("economy-standalone:RainsEconomy-.*",
                        "prime-standalone:RainsPrime-.*", "ranks-standalone:RainsRanks-.*"), List.of(), files,
                List.of("The economy is up", "Ranks are up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("eco give Bo 500000");
            server.console("eco give Ada 100000");

            // ---- the #1 as Economy left it: a statue and a pedestal, tagged the old way
            server.console("forceload add 190 190 230 210");
            server.console("fill 190 99 190 230 99 210 minecraft:stone");
            String first = RICHEST.getFirst();
            server.console("summon minecraft:mannequin 200.5 101 200.5 {NoGravity:1b,BukkitValues:{"
                    + "\"rainseconomy:statue\":\"" + first + "#statue\",\"rainseconomy:statue_holder\":\"-\"}}");
            server.console("summon minecraft:block_display 200 100 200 {BukkitValues:{\"rainseconomy:statue\":\""
                    + first + "#pedestal\"}}");

            Await.until("Core says it took Economy's statues over", WAIT,
                    () -> server.log().contains("Took over 3 statue(s) from economy"));
            Await.until("Bo, the richest, stands on the #1 where it stood", WAIT,
                    () -> count(server, statueAt(200.5, 101.0, 200.5)) == 1
                            && count(server, wearing(bo.id().toString())) == 1);
            Await.until("Ada is the #2, where it stood", WAIT, () -> count(server, statueAt(202.5, 100.7, 200.5)) == 1
                    && count(server, wearing(ada.id().toString())) == 1);
            Await.until("the #3 stands empty, where it stood", WAIT,
                    () -> count(server, statueAt(198.5, 100.45, 200.5)) == 1 && count(server, wearing("-")) == 1);
            Await.ticks(140);
            assertThat(count(server, "@e[type=minecraft:mannequin]")).as("each statue once, the old #1 reused")
                    .isEqualTo(3);
            assertThat(count(server, "@e[type=minecraft:block_display]")).as("a pedestal each, the old one reused")
                    .isEqualTo(3);
            assertThat(count(server, "@e[type=minecraft:text_display]")).as("one label over the three side by side")
                    .isEqualTo(1);
            assertThat(count(server, "@e[nbt={BukkitValues:{\"rainseconomy:statue\":\"" + first + "#statue\"}}]"))
                    .as("the old tag is replaced by Core's").isZero();
            assertThat(server.console("data get entity @e[type=minecraft:mannequin,limit=1,x=200.5,y=101,z=200.5,"
                    + "distance=..0.3] CustomName")).contains("#1").contains("Bo");

            // ---- a Prime statue: Prime's board, placed with the same command
            server.console("tp Ada 220.5 100 200.5 90 0");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place prime");
            Await.until("Ada is told", WAIT, () -> said(ada, "Statue #1 of the Prime members"));
            Await.until("an empty Prime statue stands", WAIT, () -> count(server, wearing("-")) == 2);
            server.console("prime grant Bo 31");
            Await.until("Bo stands on it", WAIT, () -> count(server, wearing(bo.id().toString())) == 2);
            assertThat(server.console("data get entity " + statueAt(220.5, 100.0, 200.5).replace("[", "[limit=1,")
                    + " CustomName")).contains("PRIME").contains("Bo");

            // ---- a creator statue: Ranks' board, from whoever holds the creator rank
            server.console("tp Ada 224.5 100 200.5 90 0");
            Await.ticks(10);
            ada.forgetChat();
            ada.run("eco statue place creator");
            Await.until("Ada is told", WAIT, () -> said(ada, "Statue #1 of the creators"));
            Await.until("an empty creator statue stands", WAIT, () -> count(server, wearing("-")) == 2);
            server.console("rank give Bo creator");
            Await.until("Bo stands on it as a creator", WAIT, () -> count(server, wearing(bo.id().toString())) == 3);
            assertThat(server.console("data get entity " + statueAt(224.5, 100.0, 200.5).replace("[", "[limit=1,")
                    + " CustomName")).contains("CREATOR").contains("Bo");
            assertThat(count(server, "@e[type=minecraft:block_display]")).as("thank-you statues have no pedestal")
                    .isEqualTo(3);

            ada.forgetChat();
            ada.run("eco statue place jackpot");
            Await.until("an unknown board is refused, naming the boards there are", WAIT,
                    () -> said(ada, "is not a board") && said(ada, "creator"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy", "RainsPrime", "RainsRanks")).isEmpty();
        }
    }

    @Test
    @DisplayName("with LuckPerms, somebody put in the creator group with lp stands on the creator statue")
    void creatorThroughLuckPerms() {
        Path luckPerms = luckPerms();
        Assumptions.assumeTrue(Files.isRegularFile(luckPerms), "no LuckPerms jar at " + luckPerms);
        Path vault = luckPerms.resolveSibling("VaultUnlocked-2.20.3.jar");
        List<Path> others = Files.isRegularFile(vault) ? List.of(luckPerms, vault) : List.of(luckPerms);
        try (Server server = Server.start("statues-in-core-luckperms", List.of("economy-standalone:RainsEconomy-.*",
                "ranks-standalone:RainsRanks-.*"), others, List.of("The economy is up", "Ranks are up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");
            server.console("forceload add 290 290 310 310");
            server.console("fill 290 99 290 310 99 310 minecraft:stone");
            server.console("tp Ada 300.5 100 300.5 0 0");
            Await.ticks(20);
            ada.forgetChat();
            ada.run("eco statue place creator");
            Await.until("Ada is told", WAIT, () -> said(ada, "Statue #1 of the creators"));
            Await.until("it stands empty", WAIT, () -> count(server, wearing("-")) == 1);

            server.console("lp creategroup creator");
            server.console("lp user Bo parent add creator");
            Await.until("Bo stands on it", Duration.ofSeconds(90),
                    () -> count(server, wearing(bo.id().toString())) == 1);
            assertThat(server.console("data get entity @e[type=minecraft:mannequin,limit=1] CustomName"))
                    .contains("CREATOR").contains("Bo");
            assertThat(server.paper.errorsFrom("RainsCore", "RainsEconomy", "RainsRanks")).isEmpty();
        }
    }
}
