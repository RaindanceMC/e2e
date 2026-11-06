package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new roles and the skill tree on a real server: a gatherer donkey brings the diamonds lying about to its owner,
 * a support parrot keeps Speed on them, and the tree opens on a pet's page — a calling bought with its points makes it
 * a fighter. Only Core and RainsPets — {@code -Dtest=PetSkillsScenarioTest}.
 */
@Tag("e2e")
class PetSkillsScenarioTest {

    @Test
    @DisplayName("a gatherer fetches, a support sings, a calling bought in the tree changes the role")
    void rolesAndTree() throws Exception {
        try (Server server = Server.start("pets-skills", List.of("pets-standalone:RainsPets-.*"),
                List.of("Pets is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -30 63 -30 30 63 30 minecraft:grass_block");
            server.console("fill -30 64 -30 30 90 30 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("gamerule spawn_mobs false");
            server.console("time set 6000");
            server.console("settings set pets.out-at-once 3");
            Await.ticks(40);

            Bot tom = server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("tp Raindancer118 0.5 64 0.5");
            server.console("pets admin give Raindancer118 donkey Hee");
            server.console("pets admin give Raindancer118 parrot Polly");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets call Hee");
            server.console("e2e as Raindancer118 pets call Polly");
            Await.ticks(60);

            // the gatherer: diamonds lying near the donkey, out of the owner's reach, come to them
            String donkey = server.console("data get entity @e[type=minecraft:donkey,limit=1] Pos");
            System.out.println("DONKEY AT " + donkey);
            server.console("execute at @e[type=minecraft:donkey,limit=1] run summon minecraft:item ~3 ~ ~2 "
                    + "{Item:{id:\"minecraft:diamond\",count:3}}");
            Await.ticks(100);
            assertThat(server.console("clear Raindancer118 minecraft:diamond 0"))
                    .as("the donkey fetched the diamonds to its owner").contains("Found 3");

            // the support: Speed kept on its owner
            assertThat(server.console("data get entity Raindancer118 active_effects"))
                    .as("the parrot keeps Speed on Tom").contains("minecraft:speed");

            // the tree: level ten gives ten points; the fighter's calling costs three and makes it a fighter
            assertThat(server.console("pets admin level Raindancer118 Hee 10")).contains("level");
            Await.ticks(10);
            server.console("e2e as Raindancer118 pets skills Hee");
            Await.ticks(20);
            Bot.Window tree = tom.window().orElseThrow();
            System.out.println("TREE: " + tree.title() + " " + tree.items().size() + " items");
            int calling = tree.slotNamed("Calling: Fighter").orElseThrow();
            tom.clickSlot(calling);
            Await.ticks(20);
            assertThat(tom.chat().stream().map(Bot.Line::text).toList())
                    .as("Hee follows the fighter's calling").anyMatch(line -> line.contains("new calling")
                            && line.contains("Fighter"));
        }
    }
}
