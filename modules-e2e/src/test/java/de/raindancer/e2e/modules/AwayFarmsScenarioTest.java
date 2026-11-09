package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The performance module's slowdown of away players' farms, on a real server, with the rate at zero so nothing is
 * left to chance: next to an active player a piglin barters as usual; next to one who went /afk it does not, and the
 * gold it was given is not lost but handed back to try again. Wheat grows next to the active player and stands still
 * next to the away one.
 */
class AwayFarmsScenarioTest {

    private static final Pattern COUNT = Pattern.compile("count: (\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String PIGLIN = "summon piglin 8 100 8 {IsImmuneToZombification:1b,PersistenceRequired:1b,Tags:[\"trader\"]}";
    private static final String GOLD = "summon item 8 100 8 {Item:{id:\"minecraft:gold_ingot\",count:1}}";

    @Test
    @DisplayName("a piglin barters and wheat grows next to an active player; next to an away one neither happens, and the gold is not eaten")
    void bartering() {
        try (Server server = Server.start("away-farms",
                List.of("essentials-standalone:RainsEssentials-.*", "performance-standalone:RainsPerformanceOptimizer-.*"),
                List.of("away players' farms at 10%"))) {
            assertThat(server.console("settings set performance:away-farm-rate 0")).contains("is now");
            Bot idler = server.player("Idler");
            Await.ticks(40);
            server.console("fill -2 98 -2 18 98 18 minecraft:stone");
            server.console("fill 6 99 6 10 102 10 minecraft:glass hollow");
            server.console("tp Idler 3 99 8");
            server.console("time set noon");
            server.console("gamerule random_tick_speed 300");
            server.console("gamerule randomTickSpeed 300");
            Await.ticks(60);

            server.console(PIGLIN);
            server.console(GOLD);
            plant(server);
            Await.ticks(20 * 15);
            assertThat(server.console("execute if block 0 99 0 minecraft:wheat[age=0]"))
                    .as("wheat next to an active player").doesNotContain("passed");
            assertThat(count(server, "@e[type=item,nbt=!{Item:{id:\"minecraft:gold_ingot\"}}]"))
                    .as("what an active player's piglin bartered").isPositive();

            server.console("kill @e[type=item]");
            server.console("kill @e[tag=trader]");
            idler.run("afk");
            Await.ticks(40);
            server.console(PIGLIN);
            server.console(GOLD);
            plant(server);
            Await.ticks(20 * 20);
            assertThat(server.console("execute if block 0 99 0 minecraft:wheat[age=0]"))
                    .as("wheat next to an away player").contains("passed");
            assertThat(count(server, "@e[type=item,nbt=!{Item:{id:\"minecraft:gold_ingot\"}}]"))
                    .as("what an away player's piglin bartered").isZero();
            String piglin = server.console("data get entity @e[tag=trader,limit=1]");
            int goldOnTheGround = count(server, "@e[type=item,nbt={Item:{id:\"minecraft:gold_ingot\"}}]");
            assertThat(goldOnTheGround == 1 || piglin.contains("gold_ingot"))
                    .as("the gold is still there — on the ground: " + goldOnTheGround + ", piglin: " + piglin).isTrue();
            assertThat(server.log()).contains("Idler issued server command: /afk");
            assertThat(server.paper.errorsFrom("RainsPerformanceOptimizer")).isEmpty();
        }
    }

    private static void plant(Server server) {
        server.console("setblock 0 98 0 minecraft:farmland[moisture=7]");
        server.console("setblock 0 99 0 minecraft:wheat[age=0]");
    }

    private static int count(Server server, String selector) {
        String said = server.console("execute if entity " + selector);
        Matcher matcher = COUNT.matcher(said);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
