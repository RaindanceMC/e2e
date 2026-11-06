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
                server.console("e2e as Raindancer118 pets follow Nuclear_Fred");
                server.console("tp Viewer 0.5 68 -9.5 0 20");
                Await.ticks(140);
                viewer.screenshot(shots.resolve("pets-2c-exploring-a.png"));
                Await.ticks(70);
                viewer.screenshot(shots.resolve("pets-2d-exploring-b.png"));
                Await.ticks(70);
                viewer.screenshot(shots.resolve("pets-2e-exploring-c.png"));
                server.console("tp Viewer 4.5 65.6 -3.5 50 12");
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
                server.console("e2e as Viewer pets particle Rex FLAME halo");
                Await.ticks(10);
                server.console("e2e as Viewer pets particle Rex");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-4c-particles.png"));
                server.console("e2e click Viewer 11");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-4d-particle-chooser.png"));
                server.console("e2e as Viewer pets species");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-5-species.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsPets")).isEmpty();
                assertThat(server.log()).doesNotContain("pets.yml:");
            }
        }
    }

    /**
     * Core's companion brain in the real world: a wolf finds its way round a wall and a strip of lava to its owner, a
     * parrot flies over; three pets take on a pack of husks, one of them nearly dead; a hurt wolf mends at its owner's
     * side; at rest they sit, lie and perch. Only Core and RainsPets — {@code -Dtest=PetsScreenshotsTest#companionAi}.
     */
    @Test
    @DisplayName("pets find their way, fight with sense, mend near their owner and rest")
    void companionAi() throws Exception {
        try (Server server = Server.start("pets-ai", List.of("pets-standalone:RainsPets-.*"), List.of("Pets is up"))) {
            server.console("forceload add -48 -48 48 48");
            server.console("fill -40 63 -40 40 63 40 minecraft:grass_block");
            server.console("fill -40 64 -40 40 90 40 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("gamerule spawn_mobs false");
            server.console("time set 6000");
            server.console("weather clear");
            server.console("fill -6 64 4 6 66 4 minecraft:stone_bricks");
            server.console("fill -3 63 7 3 63 8 minecraft:lava");
            server.console("fill 7 64 6 9 64 6 minecraft:oak_slab");
            Await.ticks(40);

            de.raindancer.e2e.Bot tom = server.paper.bot("Raindancer118").join();
            server.console("op Raindancer118");
            server.console("effect give Raindancer118 minecraft:resistance infinite 255 true");
            server.console("tp Raindancer118 0.5 64 -1.5 0 0");
            server.console("pets admin give Raindancer118 wolf Rex");
            server.console("pets admin give Raindancer118 fox Ember");
            server.console("pets admin give Raindancer118 parrot Kiwi");
            Await.ticks(20);
            server.console("e2e as Raindancer118 pets call Rex");
            Await.ticks(40);
            tom.chat().forEach(line -> System.out.println("TOM SAW: " + line.text()));

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("gamemode spectator Viewer");

                // 1. the obstacle course: the wolf must go round the wall and the lava to reach its owner
                server.console("tp Viewer 0.5 86 3.5 0 90");
                server.console("tp Raindancer118 0.5 64 11.5 180 0");
                List<double[]> route = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    Await.ticks(10);
                    route.add(position(server, "wolf"));
                    if (i == 3 || i == 9 || i == 16) {
                        viewer.screenshot(shots.resolve("pets-ai-1-course-" + i + ".png"));
                    }
                }
                route.forEach(at -> System.out.printf("REX AT %.1f %.1f %.1f%n", at[0], at[1], at[2]));
                double[] end = route.getLast();
                assertThat(Math.hypot(end[0] - 0.5, end[2] - 11.5)).as("Rex reached Tom").isLessThan(4);
                for (int i = 1; i < route.size(); i++) {
                    double[] a = route.get(i - 1);
                    double[] b = route.get(i);
                    assertThat(Math.hypot(b[0] - a[0], b[2] - a[2])).as("walked, not brought along").isLessThan(6);
                    assertThat(b[2] > 3.4 && b[2] < 4.6 && Math.abs(b[0]) < 6.5 && b[1] < 67)
                            .as("never through the wall").isFalse();
                    assertThat(b[2] > 6.6 && b[2] < 9 && Math.abs(b[0]) < 3.5 && b[1] < 64.5)
                            .as("never in the lava").isFalse();
                }

                server.console("e2e as Raindancer118 pets call Kiwi");
                server.console("tp Raindancer118 0.5 64 -6.5 0 0");
                Await.ticks(30);
                server.console("tp Viewer 12.5 70 2.5 90 15");
                Await.ticks(30);
                viewer.screenshot(shots.resolve("pets-ai-2-parrot-over-the-wall.png"));
                Await.ticks(60);
                double[] kiwi = position(server, "parrot");
                assertThat(Math.hypot(kiwi[0] - 0.5, kiwi[2] + 6.5)).as("Kiwi flew to Tom").isLessThan(5);
                assertThat(kiwi[1] - 64).as("Kiwi keeps off the ground and out of the sky").isBetween(0.3, 8.0);

                // 2. a fight: three pets, aggressive, against four husks — one of them nearly dead
                server.console("e2e as Raindancer118 pets call Ember");
                for (String pet : List.of("Rex", "Ember", "Kiwi")) {
                    server.console("e2e as Raindancer118 pets mode " + pet + " aggressive");
                }
                server.console("e2e as Raindancer118 pets tactic Ember hit-and-run");
                server.console("tp Raindancer118 0.5 64 -14.5 0 0");
                Await.ticks(60);
                server.console("summon minecraft:husk 0.5 64 -6.5 {Health:4f,NoAI:1b}");
                server.console("summon minecraft:husk 4.5 64 -8.5");
                server.console("summon minecraft:husk -3.5 64 -8.5");
                server.console("summon minecraft:husk 2.5 64 -5.5");
                server.console("tp Viewer 0.5 74 -18.5 0 40");
                for (int i = 0; i < 3; i++) {
                    Await.ticks(30);
                    viewer.screenshot(shots.resolve("pets-ai-3-fight-" + i + ".png"));
                }
                int husks = 4;
                for (int i = 0; i < 40 && husks > 0; i++) {
                    Await.ticks(20);
                    husks = count(server, "husk");
                }
                assertThat(husks).as("the husks were beaten").isZero();

                // 3. mending: a hurt wolf by its owner gets its health back once the fight is a while over
                server.console("e2e as Raindancer118 pets home Ember");
                server.console("e2e as Raindancer118 pets home Kiwi");
                server.console("e2e as Raindancer118 pets mode Rex defensive");
                Await.ticks(40);
                server.console("damage @e[type=minecraft:wolf,limit=1] 12 minecraft:generic");
                Await.ticks(5);
                double hurt = health(server, "wolf");
                server.console("tp Viewer 0.5 66 -18.5 0 15");
                Await.ticks(20);
                viewer.screenshot(shots.resolve("pets-ai-4-hurt.png"));
                Await.ticks(240);
                double mended = health(server, "wolf");
                System.out.println("REX HEALTH " + hurt + " -> " + mended);
                assertThat(mended).as("Rex mended near Tom").isGreaterThan(hurt);

                // 4. at rest: Tom stands about, the pets settle down
                server.console("e2e as Raindancer118 pets call Ember");
                server.console("e2e as Raindancer118 pets call Kiwi");
                for (String pet : List.of("Rex", "Ember", "Kiwi")) {
                    server.console("e2e as Raindancer118 pets stay " + pet);
                }
                Await.ticks(900);
                server.console("tp Viewer 0.5 66.5 -9.5 180 25");
                Await.ticks(40);
                viewer.screenshot(shots.resolve("pets-ai-5-resting.png"));

                assertThat(server.paper.errorsFrom("RainsCore", "RainsPets")).isEmpty();
            }
        }
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?(?:E-?\\d+)?)[dfF]");

    private static double[] position(Server server, String type) {
        String said = server.console("data get entity @e[type=minecraft:" + type + ",limit=1,sort=nearest] Pos");
        java.util.regex.Matcher numbers = NUMBER.matcher(said);
        double[] at = new double[3];
        for (int i = 0; i < 3; i++) {
            assertThat(numbers.find()).as("a position in: " + said).isTrue();
            at[i] = Double.parseDouble(numbers.group(1));
        }
        return at;
    }

    private static double health(Server server, String type) {
        String said = server.console("data get entity @e[type=minecraft:" + type + ",limit=1] Health");
        java.util.regex.Matcher number = NUMBER.matcher(said);
        assertThat(number.find()).as("a health in: " + said).isTrue();
        return Double.parseDouble(number.group(1));
    }

    private static int count(Server server, String type) {
        String said = server.console("execute if entity @e[type=minecraft:" + type + "]");
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("Test passed, count: (\\d+)").matcher(said);
        return number.find() ? Integer.parseInt(number.group(1)) : 0;
    }
}
