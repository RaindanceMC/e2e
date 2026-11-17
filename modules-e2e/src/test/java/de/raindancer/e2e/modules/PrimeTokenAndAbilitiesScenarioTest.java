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
 * On a real server: somebody who becomes Prime is handed one warp token in its own look, a renewal hands none, and
 * Double Jump bought with skill points in the enchants' abilities tree lets them jump without enchanted boots.
 */
@Tag("e2e")
class PrimeTokenAndAbilitiesScenarioTest {

    private static final List<String> PLUGINS = List.of("warp-standalone:RainsWarps-.*",
            "skills-standalone:RainsSkills-.*", "enchants-standalone:RainsEnchants-.*", "prime-standalone:RainsPrime-.*");

    private static long tokens(Bot bot) {
        return bot.items().stream().filter(item -> item.name().contains("Warp Token"))
                .mapToLong(Bot.Item::amount).sum();
    }

    @Test
    @DisplayName("Prime brings one warp token, a renewal none; Double Jump is bought with skill points and works")
    void tokenAndAbilities() throws Exception {
        try (Server server = Server.start("prime-token", PLUGINS, List.of("Warps is up", "Skills are up",
                "Enchants are up"))) {
            Bot bo = server.player("Bo");
            server.console("gamemode survival Bo");
            Await.ticks(20 * 6);   // the look after joining has been: not Prime yet
            assertThat(tokens(bo)).isZero();

            // Buying while online: the once-a-minute look finds it.
            server.console("prime grant Bo 31");
            Await.until(() -> "Bo got a warp token (has " + bo.items() + ")", Duration.ofSeconds(75),
                    () -> tokens(bo) == 1);
            assertThat(server.console("data get entity Bo Inventory[{id:\"minecraft:nether_star\"}].components.\"minecraft:item_model\"")).as("worn in its own model")
                    .contains("rainswarps:token");
            bo.expectChat("Thank you for Prime!");

            server.console("prime grant Bo 31");
            Await.ticks(20 * 70);   // the once-a-minute look has been round at least once
            assertThat(tokens(bo)).as("a renewal is the same Prime").isEqualTo(1);

            server.console("skills admin level Bo 20");
            Await.ticks(20);
            assertThat(server.console("data get entity Bo abilities.mayfly")).as("no double jump yet")
                    .contains("0b");
            bo.run("enchants abilities");
            Bot.Window tree = bo.awaitWindow("Abilities");
            assertThat(tree.slotNamed("Double Jump")).isPresent();
            bo.click("Double Jump");
            Await.until(() -> "Bo's Double Jump rank is kept", Duration.ofSeconds(10),
                    () -> server.console("data get entity Bo BukkitValues").contains("rainsenchants:ability_double_jump"));
            Await.until(() -> "Bo may jump in the air without enchanted boots", Duration.ofSeconds(10),
                    () -> server.console("data get entity Bo abilities.mayfly").contains("1b"));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsWarps", "RainsSkills", "RainsEnchants")).isEmpty();
        }
    }
}
