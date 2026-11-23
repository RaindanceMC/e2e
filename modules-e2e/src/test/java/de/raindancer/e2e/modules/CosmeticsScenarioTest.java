package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RainsCosmetics on a real server: the commands, the refusals, the menus, and staff resetting a name. */
@Tag("e2e")
class CosmeticsScenarioTest {

    @Test
    @DisplayName("a player paints their name and wears a particle, by command and by menu; refusals say why; staff can reset it")
    void nameStyles() {
        try (Server server = Server.start("cosmetics",
                List.of("cosmetics-standalone:RainsCosmetics-.*"), List.of("Cosmetics is up"))) {
            Bot ada = server.admin("Ada");
            Bot bo = server.player("Bo");

            bo.runAndExpect("cosmetics name preset sunset", "Your name is now");
            bo.runAndExpect("cosmetics name set pink light_blue bold", "Your name is now");
            bo.runAndExpect("cosmetics name set pink obfuscated", "You may not make your name obfuscated");
            bo.runAndExpect("cosmetics name preset rainbow", "is not yours to wear");
            bo.runAndExpect("cosmetics name set sparkly", "'sparkly' is not a colour or a decoration");
            bo.runAndExpect("cosmetics name preset nope", "There is no preset called");
            ada.runAndExpect("cosmetics name preset rainbow", "Your name is now");

            // The menus: presets, then mixing a colour by hand.
            bo.runAndOpen("cosmetics name", "Your name");
            bo.click("Presets");
            bo.awaitWindow("Presets");
            bo.answer(() -> bo.click("Ocean"), answer -> answer.says("Your name is now"));
            bo.closeWindow();

            bo.runAndExpect("cosmetics name reset", "Your name is plain again");
            bo.runAndOpen("cosmetics name", "Your name");
            bo.click("Your own colours");
            bo.awaitWindow("Your colours");
            bo.click("Add a colour");
            bo.awaitWindow("Pick a colour");
            bo.click("Pink");
            Bot.Window mixed = bo.awaitWindow("Your colours");
            Await.until("the pink stop is shown", Duration.ofSeconds(10),
                    () -> bo.window().flatMap(window -> window.slotNamed("1. pink")).isPresent());
            assertThat(mixed.title()).contains("Your colours");
            bo.closeWindow();

            // Staff take it off again, and Bo is told.
            ada.runAndExpect("cosmetics name reset Bo", "name is plain again");
            Await.until("Bo is told", Duration.ofSeconds(10),
                    () -> bo.chatText().stream().anyMatch(line -> line.contains("staff member took your name style off")));
            bo.runAndExpect("cosmetics name reset Ada", "You may not do that");

            // Particles: the hub's door, the words, the refusals, a colour, and surviving a rejoin.
            int windows = bo.dialogsShown();
            bo.run("cosmetics");
            assertThat(bo.awaitDialog(windows, Duration.ofSeconds(10)).title()).contains("Cosmetics");
            bo.pressDialogButton("Particles", java.util.Map.of());
            bo.awaitWindow("Your particles");
            bo.closeWindow();
            bo.run("cosmetics particle flame");
            awaitChoice(server, "particle", "flame");
            bo.run("cosmetics particle shape halo");
            awaitChoice(server, "particle-shape", "halo");
            bo.runAndExpect("cosmetics particle shape cube", "The shapes are");
            bo.runAndExpect("cosmetics particle elder_guardian", "is not allowed here");
            bo.runAndExpect("cosmetics particle block", "is not a particle that can be worn");
            bo.run("cosmetics particle dust");
            awaitChoice(server, "particle", "dust");
            String uncoloured = choice(server);
            bo.run("cosmetics particle colour pink");
            Await.until("the colour changes", Duration.ofSeconds(10), () -> !choice(server).equals(uncoloured)
                    && choice(server).contains("rainscosmetics:particle-colour"));
            bo.run("cosmetics particle density very_dense");
            awaitChoice(server, "particle-density", "very_dense");
            bo.runAndExpect("cosmetics particle density thick", "Densities are");
            bo.run("cosmetics particle speed very_fast");
            awaitChoice(server, "particle-speed", "very_fast");
            bo.runAndExpect("cosmetics particle speed warp", "Speeds are");
            Await.ticks(60);   // the timer draws it for a few rounds, near Ada too
            bo.rejoin();
            bo.run("cosmetics particle shape trail");
            awaitChoice(server, "particle-shape", "trail");
            bo.run("cosmetics particle shape spiral");
            awaitChoice(server, "particle-shape", "spiral");
            bo.run("cosmetics particle shape ambient");
            awaitChoice(server, "particle-shape", "ambient");
            bo.runAndOpen("cosmetics particle", "Your particles");
            bo.answer(() -> bo.click("Preview"), answer -> answer.says("That is your particle"));
            bo.run("cosmetics particle off");
            Await.until("the particle is off", Duration.ofSeconds(10), () -> !choice(server).contains("rainscosmetics:particle\""));
            bo.runAndExpect("cosmetics particle shape aura", "You are not wearing a particle");

            // The styled nametag rides the players, previews on request, and goes when switched off.
            Await.until("a nametag rides somebody", Duration.ofSeconds(10),
                    () -> server.console("execute if entity @e[type=minecraft:text_display]").contains("passed"));
            bo.runAndOpen("cosmetics name", "Your name");
            bo.answer(() -> bo.click("Preview your nametag"), answer -> answer.says("That is your nametag"));
            assertThat(server.console("settings set name-above-head false")).contains("is now");
            Await.until("every nametag is gone", Duration.ofSeconds(15),
                    () -> !server.console("execute if entity @e[type=minecraft:text_display]").contains("passed"));
            assertThat(server.console("settings set name-above-head true")).contains("is now");
            Await.until("they are back", Duration.ofSeconds(10),
                    () -> server.console("execute if entity @e[type=minecraft:text_display]").contains("passed"));

            // A flowing gradient: by command, by the menu's toggle, and seen moving on the nametag.
            bo.runAndExpect("cosmetics name set pink light_blue gold animated", "Your name is now");
            String before = nametagOf(server, "Bo");
            Await.until("Bo's nametag flows", Duration.ofSeconds(5), () -> !nametagOf(server, "Bo").equals(before));
            bo.runAndOpen("cosmetics name", "Your name");
            bo.click("Your own colours");
            bo.awaitWindow("Your colours");
            bo.click("Flowing");
            Await.until("the toggle shows it held still", Duration.ofSeconds(10),
                    () -> bo.window().flatMap(window -> window.slotNamed("Flowing")).isPresent());
            bo.closeWindow();
            String still = nametagOf(server, "Bo");
            Await.ticks(30);
            assertThat(nametagOf(server, "Bo")).as("held still, it no longer moves").isEqualTo(still);

            assertThat(server.paper.errorsFrom("RainsCore", "RainsCosmetics")).isEmpty();
        }
    }

    private static String nametagOf(Server server, String player) {
        return server.console("execute as " + player
                + " at @s as @e[type=minecraft:text_display,distance=..3,sort=nearest,limit=1] run data get entity @s text");
    }

    /** Bo's stored cosmetics choices, as the server holds them. */
    private static String choice(Server server) {
        return server.console("data get entity Bo BukkitValues").toLowerCase(java.util.Locale.ROOT);
    }

    private static void awaitChoice(Server server, String key, String value) {
        java.util.regex.Pattern stored = java.util.regex.Pattern.compile(
                "\"rainscosmetics:" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(value) + "\"");
        Await.until(() -> key + " is " + value + " (" + choice(server) + ")", Duration.ofSeconds(10),
                () -> stored.matcher(choice(server)).find());
    }
}
