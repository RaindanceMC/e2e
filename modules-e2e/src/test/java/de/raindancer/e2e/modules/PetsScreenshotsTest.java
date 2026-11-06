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
            assertThat(server.console("settings set pets.out-at-once 3")).contains("is now");
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
                server.console("tp Viewer 1.5 77 4.5 0 90");
                server.console("tp Raindancer118 0.5 64 11.5 180 0");
                List<double[]> route = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    Await.ticks(10);
                    double[] at = position(server, "wolf");
                    route.add(new double[]{at[0], at[1], at[2], gameTime(server)});
                    if (i == 3 || i == 9 || i == 16) {
                        viewer.screenshot(shots.resolve("pets-ai-1-course-" + i + ".png"));
                    }
                }
                route.forEach(at -> System.out.printf("REX AT %.1f %.1f %.1f (tick %.0f)%n", at[0], at[1], at[2], at[3]));
                assertThat(route.stream().mapToDouble(at -> Math.hypot(at[0] - 0.5, at[2] - 11.5)).min().orElseThrow())
                        .as("Rex reached Tom (and then, Tom standing about, went exploring)").isLessThan(5);
                for (int i = 1; i < route.size(); i++) {
                    double[] a = route.get(i - 1);
                    double[] b = route.get(i);
                    assertThat(Math.hypot(b[0] - a[0], b[2] - a[2]) / Math.max(1, b[3] - a[3]))
                            .as("walked, not brought along").isLessThan(0.6);
                    // the wall's blocks are x -6..6 at z 4, so it covers x -6 to 7 and z 4 to 5
                    assertThat(b[2] > 4 && b[2] < 5 && b[0] > -6 && b[0] < 7 && b[1] < 67)
                            .as("never through the wall").isFalse();
                    assertThat(b[2] > 7 && b[2] < 9 && b[0] > -3 && b[0] < 4 && b[1] < 64.5)
                            .as("never in the lava").isFalse();
                }

                server.console("e2e as Raindancer118 pets call Kiwi");
                server.console("tp Raindancer118 0.5 64 -6.5 0 0");
                server.console("tp Viewer 14.5 68 1.5 90 10");
                Await.ticks(25);
                viewer.screenshot(shots.resolve("pets-ai-2-parrot-over-the-wall.png"));
                Await.ticks(60);
                double[] kiwi = position(server, "parrot");
                System.out.printf("KIWI AT %.1f %.1f %.1f%n", kiwi[0], kiwi[1], kiwi[2]);
                assertThat(kiwi[2]).as("Kiwi crossed the wall to Tom's side").isLessThan(3.5);
                assertThat(Math.hypot(kiwi[0] - 0.5, kiwi[2] + 6.5)).as("Kiwi is with Tom").isLessThan(10);
                assertThat(kiwi[1] - 64).as("Kiwi keeps off the ground and out of the sky").isBetween(0.3, 8.0);

                // 2. a fight: three pets, aggressive, against three husks — one of them nearly dead
                server.console("e2e as Raindancer118 pets call Ember");
                for (String pet : List.of("Rex", "Ember", "Kiwi")) {
                    server.console("e2e as Raindancer118 pets mode " + pet + " aggressive");
                }
                server.console("e2e as Raindancer118 pets tactic Ember hit-and-run");
                server.console("tp Raindancer118 0.5 64 -14.5 0 0");
                Await.ticks(60);
                server.console("summon minecraft:husk 0.5 64 -6.5 {Health:4f,NoAI:1b}");
                server.console("summon minecraft:husk 4.5 64 -8.5 {Health:12f}");
                server.console("summon minecraft:husk -3.5 64 -8.5 {Health:12f}");
                server.console("tp Viewer 0.5 74 -18.5 0 40");
                for (int i = 0; i < 3; i++) {
                    Await.ticks(30);
                    viewer.screenshot(shots.resolve("pets-ai-3-fight-" + i + ".png"));
                }
                boolean husks = true;
                for (int i = 0; i < 40 && husks; i++) {
                    Await.ticks(20);
                    System.out.println("HUSKS " + server.console(
                            "execute as @e[type=minecraft:husk] run data get entity @s Health").replace('\n', ' '));
                    husks = any(server, "husk");
                }
                System.out.println("PETS AFTER THE FIGHT: Rex " + health(server, "wolf") + ", Ember "
                        + health(server, "fox") + ", Kiwi " + health(server, "parrot"));
                assertThat(husks).as("the husks were beaten").isFalse();

                // 3. mending: a hurt wolf by its owner gets its health back once the fight is a while over
                server.console("e2e as Raindancer118 pets home Ember");
                server.console("e2e as Raindancer118 pets home Kiwi");
                server.console("e2e as Raindancer118 pets mode Rex defensive");
                Await.ticks(40);
                server.console("damage @e[type=minecraft:wolf,limit=1] 5 minecraft:generic");
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

    /**
     * Pets keeping out of each other's way: three follow their owner each at a spot of its own and never on top of
     * each other; resting while he stands still, they spread out — two may cuddle up — and a fierce pet squares up to
     * another player's for show, nobody hurt — {@code -Dtest=PetsScreenshotsTest#spacing}.
     */
    @Test
    @DisplayName("pets keep their own spots, cuddle up and squabble without hurting anybody")
    void spacing() throws Exception {
        try (Server server = Server.start("pets-spacing", List.of("pets-standalone:RainsPets-.*"),
                List.of("Pets is up"))) {
            server.console("forceload add -48 -48 48 48");
            server.console("fill -40 63 -40 40 63 40 minecraft:grass_block");
            server.console("fill -40 64 -40 40 90 40 minecraft:air");
            server.console("gamerule advance_time false");
            server.console("gamerule spawn_mobs false");
            server.console("time set 6000");
            server.console("weather clear");
            assertThat(server.console("settings set pets.out-at-once 3")).contains("is now");
            Await.ticks(40);

            de.raindancer.e2e.Bot tom = server.paper.bot("Raindancer118").join();
            de.raindancer.e2e.Bot bob = server.paper.bot("Bob").join();
            server.console("tp Raindancer118 0.5 64 0.5 0 0");
            server.console("tp Bob 6.5 64 0.5 90 0");
            for (String[] gift : new String[][]{{"Raindancer118", "wolf", "Rex"}, {"Raindancer118", "fox", "Ember"},
                    {"Raindancer118", "parrot", "Kiwi"}, {"Bob", "wolf", "Brute"}}) {
                server.console("pets admin give " + gift[0] + " " + gift[1] + " " + gift[2]);
            }
            Await.ticks(20);
            server.console("pets admin personality Raindancer118 Rex fierce");
            server.console("pets admin personality Bob Brute fierce");
            for (String pet : List.of("Rex", "Ember", "Kiwi")) {
                server.console("e2e as Raindancer118 pets call " + pet);
            }
            server.console("e2e as Bob pets call Brute");
            Await.ticks(60);
            tom.chat().forEach(line -> System.out.println("TOM SAW: " + line.text()));
            bob.chat().forEach(line -> System.out.println("BOB SAW: " + line.text()));

            try (RealClient viewer = RealClient.join(server.paper, "Viewer")) {
                Path shots = E2e.out().resolve("screenshots");
                viewer.click(484, 408);
                Await.ticks(200);
                server.console("gamemode spectator Viewer");

                // 1. following: Tom walks off; his three pets keep spots of their own behind him
                double closest = Double.MAX_VALUE;
                for (int step = 1; step <= 10; step++) {
                    server.console("tp Raindancer118 0.5 64 " + (0.5 - step * 2) + " 180 0");
                    Await.ticks(12);
                    if (step >= 4) {
                        closest = Math.min(closest, closestPair(server, List.of("wolf:Rex", "fox:Ember", "parrot:Kiwi")));
                    }
                    if (step == 8) {
                        server.console("tp Viewer 0.5 70 -10.5 180 50");
                        Await.ticks(5);
                        viewer.screenshot(shots.resolve("pets-spacing-1-following.png"));
                    }
                }
                Await.ticks(30);
                closest = Math.min(closest, closestPair(server, List.of("wolf:Rex", "fox:Ember", "parrot:Kiwi")));
                System.out.println("CLOSEST WHILE FOLLOWING " + closest);
                assertThat(closest).as("never on top of each other while following").isGreaterThan(0.5);

                // 2. resting: Tom stands still; they settle apart — cuddled up at most, never on top of each other
                double resting = Double.MAX_VALUE;
                for (int i = 0; i < 30; i++) {
                    Await.ticks(20);
                    resting = Math.min(resting, closestPair(server, List.of("wolf:Rex", "fox:Ember", "parrot:Kiwi")));
                }
                System.out.println("CLOSEST WHILE RESTING " + resting);
                server.console("tp Viewer 0.5 75 -12.5 180 65");
                Await.ticks(10);
                viewer.screenshot(shots.resolve("pets-spacing-2-resting.png"));
                assertThat(resting).as("resting, never on top of each other").isGreaterThan(0.25);

                // 3. squabbling: Rex and Bob's Brute, both fierce, near each other — for show, nobody hurt
                server.console("e2e as Raindancer118 pets home Ember");
                server.console("e2e as Raindancer118 pets home Kiwi");
                server.console("tp Bob 4.5 64 -19.5 90 0");
                Await.ticks(40);
                double rexBefore = named(server, "wolf", "Rex", "Health")[0];
                double bruteBefore = named(server, "wolf", "Brute", "Health")[0];
                double nearest = Double.MAX_VALUE;
                boolean shot = false;
                for (int i = 0; i < 60; i++) {
                    Await.ticks(20);
                    double[] rex = named(server, "wolf", "Rex", "Pos");
                    double[] brute = named(server, "wolf", "Brute", "Pos");
                    double apart = Math.hypot(rex[0] - brute[0], rex[2] - brute[2]);
                    nearest = Math.min(nearest, apart);
                    if (!shot && apart < 1.6) {
                        server.console(String.format(java.util.Locale.ROOT, "tp Viewer %.2f 66.5 %.2f 0 30",
                                (rex[0] + brute[0]) / 2, (rex[2] + brute[2]) / 2 - 3.5));
                        Await.ticks(10);
                        viewer.screenshot(shots.resolve("pets-spacing-3-squabble.png"));
                        shot = true;
                    }
                }
                System.out.println("REX AND BRUTE CAME WITHIN " + nearest);
                assertThat(named(server, "wolf", "Rex", "Health")[0]).as("Rex unhurt").isGreaterThanOrEqualTo(rexBefore);
                assertThat(named(server, "wolf", "Brute", "Health")[0]).as("Brute unhurt")
                        .isGreaterThanOrEqualTo(bruteBefore);

                assertThat(server.paper.errorsFrom("RainsCore", "RainsPets")).isEmpty();
            }
        }
    }

    /** The nearest two of these pets ({@code type:name}) come to each other on the flat, of those level with each other. */
    private static double closestPair(Server server, List<String> pets) {
        List<double[]> at = new ArrayList<>();
        for (String pet : pets) {
            at.add(named(server, pet.split(":")[0], pet.split(":")[1], "Pos"));
        }
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < at.size(); i++) {
            for (int j = i + 1; j < at.size(); j++) {
                double[] a = at.get(i);
                double[] b = at.get(j);
                if (Math.abs(a[1] - b[1]) < 1.5) {
                    closest = Math.min(closest, Math.hypot(a[0] - b[0], a[2] - b[2]));
                }
            }
        }
        return closest;
    }

    /** A pet's numbers, found by its name: its position (three) or its health (one, first). */
    private static double[] named(Server server, String type, String name, String path) {
        String said = server.console("data get entity @e[type=minecraft:" + type + ",name=" + name + ",limit=1] "
                + path);
        java.util.regex.Matcher numbers = NUMBER.matcher(said);
        double[] found = new double[3];
        for (int i = 0; i < (path.equals("Pos") ? 3 : 1); i++) {
            assertThat(numbers.find()).as(path + " of " + name + " in: " + said).isTrue();
            found[i] = Double.parseDouble(numbers.group(1));
        }
        return found;
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

    private static double gameTime(Server server) {
        String said = server.console("time query gametime");
        java.util.regex.Matcher number = java.util.regex.Pattern.compile("time is (\\d+)").matcher(said);
        assertThat(number.find()).as("a time in: " + said).isTrue();
        return Double.parseDouble(number.group(1));
    }

    private static double health(Server server, String type) {
        String said = server.console("data get entity @e[type=minecraft:" + type + ",limit=1] Health");
        java.util.regex.Matcher number = NUMBER.matcher(said);
        assertThat(number.find()).as("a health in: " + said).isTrue();
        return Double.parseDouble(number.group(1));
    }

    private static boolean any(Server server, String type) {
        return !server.console("data get entity @e[type=minecraft:" + type + ",limit=1] Pos").contains("No entity");
    }
}
