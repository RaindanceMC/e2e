package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A legendary or mythic pet's ability on a real server: riding a wither, a swing at the air sends wither skulls where
 * the rider looks — they wither the zombies ahead, leave the villager beside them alone and break no block; a second
 * swing right after waits for the cooldown. Only Core and RainsPets — {@code -Dtest=PetAbilityScenarioTest}.
 */
@org.junit.jupiter.api.Tag("e2e")
class PetAbilityScenarioTest {

    @Test
    @DisplayName("a swing on a ridden wither: skulls hit the monsters ahead, not the villager, not a block")
    void witherSkulls() throws Exception {
        try (Server server = Server.start("pets-ability", List.of("pets-standalone:RainsPets-.*"),
                List.of("Pets is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -30 63 -30 30 63 30 minecraft:grass_block");
            server.console("fill -30 64 -30 30 90 30 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("gamerule spawn_mobs false");
            server.console("time set 6000");
            // a wall of glass behind the targets: whatever the skulls do, it must still stand
            server.console("fill -4 64 14 4 70 14 minecraft:glass");
            Await.ticks(40);

            Bot tom = server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("effect give Raindancer118 minecraft:resistance infinite 255 true");
            server.console("tp Raindancer118 0.5 64 0.5 0 0");
            server.console("pets admin give Raindancer118 wither Fred");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets call Fred");
            Await.ticks(60);
            // passive: whatever the zombies lose is the ability's, not the wither's own fighting
            server.console("e2e as Raindancer118 pets mode Fred passive");
            Await.ticks(10);
            assertThat(server.console("ride Raindancer118 mount @e[type=minecraft:wither,limit=1,sort=nearest]"))
                    .as("riding the wither").contains("started riding");
            Await.ticks(20);

            for (int x = -1; x <= 1; x++) {
                server.console("summon minecraft:zombie " + (x * 1.2 + 0.5)
                        + " 64 9.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"target\"]}");
            }
            server.console("summon minecraft:villager 4.5 64 9.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"bystander\"]}");
            Await.ticks(10);
            double before = health(server, "target");
            // look straight at the middle zombie, then swing at the air
            // turned without a teleport — a teleport would throw the rider off
            server.console("execute as Raindancer118 at @s run rotate @s facing 0.5 65 9.5");
            Await.ticks(10);
            tom.swing();
            Await.ticks(60);

            String used = String.join("\n", tom.actionBars());
            System.out.println("ACTION BAR: " + used);
            double after = health(server, "target");
            System.out.printf("ZOMBIES: %.1f -> %.1f%n", before, after);
            assertThat(after).as("the zombies took the skulls (health summed, the dead count 0)").isLessThan(before);
            assertThat(server.console("data get entity @e[tag=bystander,limit=1] Health"))
                    .as("the villager beside them is a bystander").contains("20.0f");
            assertThat(server.console("execute if block 0 70 14 minecraft:glass")).contains("passed");
            assertThat(server.console("execute if block 0 66 14 minecraft:glass")).contains("passed");

            assertThat(String.join(" ", tom.actionBars())).contains("Wither Skulls!");

            // right after, it is not ready again: no skull leaves, the rider is told how long to wait
            tom.swing();
            Await.ticks(3);
            assertThat(server.console("execute if entity @e[type=minecraft:wither_skull]"))
                    .as("a second swing within the cooldown sends nothing").contains("failed");
            Await.ticks(10);
            assertThat(String.join(" ", tom.actionBars())).contains("is ready in");
        }
    }

    @Test
    @DisplayName("a pet monster beside the bed does not keep its owner up; a real zombie still does")
    void sleepingBesidePets() throws Exception {
        try (Server server = Server.start("pets-sleep", List.of("pets-standalone:RainsPets-.*"),
                List.of("Pets is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -30 63 -30 30 63 30 minecraft:grass_block");
            server.console("fill -30 64 -30 30 90 30 minecraft:air");
            server.console("gamerule spawn_mobs false");
            server.console("time set 18000");
            server.console("setblock 2 64 0 minecraft:red_bed[facing=east,part=foot]");
            server.console("setblock 3 64 0 minecraft:red_bed[facing=east,part=head]");
            Await.ticks(40);

            Bot tom = server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("tp Raindancer118 0.5 64 0.5");
            server.console("pets admin give Raindancer118 zombie Groan");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets call Groan");
            Await.ticks(40);
            server.console("e2e as Raindancer118 pets stay Groan");
            server.console("tp @e[type=minecraft:zombie,limit=1] 4.5 64 2.5");
            Await.ticks(20);

            server.console("time set 18000");
            tom.useOn(2, 64, 0);
            Await.ticks(140);
            // the only player asleep: the night is skipped — the proof they lay down
            assertThat(daytime(server)).as("slept through the night with only the pet zombie about")
                    .isLessThan(12000);

            server.console("tp Raindancer118 0.5 64 0.5");
            Await.ticks(20);
            server.console("summon minecraft:zombie 5.5 64 -2.5 {NoAI:1b,PersistenceRequired:1b}");
            Await.ticks(10);
            server.console("time set 18000");
            tom.useOn(2, 64, 0);
            Await.ticks(140);
            assertThat(daytime(server)).as("a real zombie still keeps them up").isGreaterThan(18000);
        }
    }

    private static long daytime(Server server) {
        String said = server.console("time query day");
        java.util.regex.Matcher time = java.util.regex.Pattern.compile("at (\\d+)").matcher(said);
        assertThat(time.find()).as("a time in: " + said).isTrue();
        return Long.parseLong(time.group(1)) % 24000;
    }

    private static double health(Server server, String tag) {
        double total = 0;
        String list = server.console("execute as @e[tag=" + tag + "] run data get entity @s Health");
        java.util.regex.Matcher numbers = java.util.regex.Pattern.compile("has the following entity data: (-?[\\d.]+)f")
                .matcher(list);
        while (numbers.find()) {
            total += Double.parseDouble(numbers.group(1));
        }
        return total;
    }
}
