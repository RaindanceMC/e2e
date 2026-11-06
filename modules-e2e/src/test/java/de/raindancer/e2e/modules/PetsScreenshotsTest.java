package de.raindancer.e2e.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.raindancer.e2e.Await;
import de.raindancer.e2e.E2e;
import de.raindancer.e2e.RealClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rain's Pets in the real client: a player as they look on a live server — their skin, name style, rank and guild
 * tag, armour — beside their wither "Nuclear Fred", and the pet screens — {@code mvn -o -Pscreenshots test
 * -pl modules-e2e -Dtest=PetsScreenshotsTest}. Needs jars/pets-standalone/target/RainsPets-*.jar and jars/pets-shot/
 * with the other plugins' jars, the ranks' yml files and the player's signed skin (raindancer118.json).
 */
@Tag("screenshots")
class PetsScreenshotsTest {

    private static final Path HERE = Path.of(System.getProperty("e2e.reactor")).resolve("pets-shot");
    private static final String STYLE = "#8932B8 #1D1D21 bold animated";

    @Test
    @DisplayName("a wither called Nuclear Fred beside its owner, and the pet screens")
    void nuclearFred() throws Exception {
        List<Path> others = new ArrayList<>();
        try (var jars = Files.list(HERE)) {
            jars.filter(path -> path.toString().endsWith(".jar")).forEach(others::add);
        }
        Map<String, String> files = Map.of(
                "plugins/RainsRanks/custom-ranks.yml", Files.readString(HERE.resolve("custom-ranks.yml")),
                "plugins/RainsRanks/ranks.yml", Files.readString(HERE.resolve("ranks.yml")),
                "plugins/RainsRanks/holders.yml", Files.readString(HERE.resolve("holders.yml")));
        JsonObject skin = JsonParser.parseString(Files.readString(HERE.resolve("raindancer118.json")))
                .getAsJsonObject().getAsJsonArray("properties").get(0).getAsJsonObject();
        try (Server server = Server.start("pets", List.of("pets-standalone:RainsPets-.*"), others, files,
                List.of("Pets is up"))) {
            server.console("forceload add -32 -32 32 32");
            server.console("fill -24 63 -24 24 63 24 minecraft:grass_block");
            server.console("fill -24 64 -24 24 90 24 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("time set 6000");
            server.console("weather clear");
            Await.ticks(60);

            de.raindancer.e2e.Bot tom = server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("e2e skin Raindancer118 " + skin.get("value").getAsString() + " "
                    + skin.get("signature").getAsString());
            server.console("tp Raindancer118 0.5 64 0.5 180 0");
            for (String slot : List.of("armor.head with minecraft:netherite_helmet", "armor.chest with minecraft:netherite_chestplate",
                    "armor.legs with minecraft:netherite_leggings", "armor.feet with minecraft:netherite_boots",
                    "weapon.mainhand with minecraft:netherite_sword")) {
                server.console("item replace entity Raindancer118 " + slot + "[enchantments={unbreaking:3}]");
            }
            server.console("e2e as Raindancer118 cosmetics name set " + STYLE);
            server.console("e2e as Raindancer118 cosmetics particle trial_spawner_detection");
            server.console("e2e as Raindancer118 cosmetics particle shape aura");
            server.console("e2e as Raindancer118 guild create Dawn SMP DAWN");
            Await.ticks(20);
            server.console("pets admin give Raindancer118 wither Nuclear Fred");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets colours Nuclear_Fred " + STYLE);
            server.console("e2e as Raindancer118 pets particle Nuclear_Fred TRIAL_SPAWNER_DETECTION aura");
            server.console("e2e as Raindancer118 pets call Nuclear_Fred");
            Await.ticks(60);
            server.console("e2e as Raindancer118 pets stay Nuclear_Fred");
            Await.ticks(20);
            tom.chat().forEach(line -> System.out.println("TOM SAW: " + line.text()));

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("gamemode spectator Viewer");
                server.console("tp Viewer 0.5 65.2 -5.5 0 8");
                Await.ticks(60);
                viewer.screenshot(shots.resolve("pets-1-nuclear-fred.png"));
                server.console("tp Viewer 4.5 65.6 -3.5 50 12");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("pets-2-side.png"));
                tom.say("Meet Nuclear Fred!");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("pets-2b-chat.png"));
                server.console("ride Raindancer118 mount @e[type=minecraft:wither,limit=1,sort=nearest]");
                Await.ticks(40);
                server.console("tp Viewer 0.5 66 -6.5 0 10");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("pets-3-riding.png"));

                server.console("gamemode creative Viewer");
                server.console("pets admin give Viewer wolf Rex");
                server.console("pets admin give Viewer fox");
                server.console("pets admin give Viewer allay");
                Await.ticks(20);
                server.console("e2e as Viewer pets");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-4-collection.png"));
                server.console("e2e as Viewer pets colours Rex");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-4b-pet-menu.png"));
                server.console("e2e as Viewer pets species");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-5-species.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsPets")).isEmpty();
                assertThat(server.log()).doesNotContain("pets.yml:");
            }
        }
    }
}
