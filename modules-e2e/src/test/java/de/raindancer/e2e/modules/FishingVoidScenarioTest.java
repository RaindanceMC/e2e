package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The End's void is fished like water: the float rests on its surface at y 47, a fish bites, the catch lands. */
@Tag("e2e")
class FishingVoidScenarioTest {

    @Test
    @DisplayName("over the End's void a float rests at y 47 and something bites")
    void voidFishing() throws Exception {
        try (Server server = Server.start("fishing-void", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            // A ledge far out from the main island, nothing but void below its edge.
            server.console("execute in minecraft:the_end run forceload add 1000 0 1016 16");
            server.console("execute in minecraft:the_end run fill 1000 64 0 1004 64 4 minecraft:end_stone");
            server.console("execute in minecraft:the_end run tp Ada 1003.5 65 2.5 -90 0");
            Await.ticks(60);
            server.console("clear Ada");
            server.console("give Ada fishing_rod");
            Await.ticks(10);
            ada.hold(ada.hotbarSlotOf(item -> item.is("fishing_rod")));

            ada.run("fishing xp Ada 200000");
            Await.ticks(20);
            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.forgetChat();
            ada.click("Line");
            Await.until("the Ember Line is fitted", Duration.ofSeconds(10), () -> ada.chatText().stream()
                    .anyMatch(line -> line.contains("Ember Line")));
            ada.closeWindow();
            ada.runAndOpen("fishing rod", "Rod upgrades");
            ada.forgetChat();
            ada.click("Line");
            Await.until(() -> "the Void Line is fitted (chat " + ada.chatText() + ")", Duration.ofSeconds(10),
                    () -> ada.chatText().stream().anyMatch(line -> line.contains("Void Line")
                            && !line.contains("Next")));
            ada.closeWindow();

            ada.look(-90f, 20f);
            Await.ticks(5);
            int before = ada.items().size();
            ada.forgetEffects();
            ada.useHeld();
            Await.until(() -> "the float rests on the void's surface (bobbers: " + server.console(
                    "execute in minecraft:the_end as @e[type=minecraft:fishing_bobber] run data get entity @s Pos")
                    + "; chat " + ada.chatText() + ")", Duration.ofSeconds(20), () -> server.console(
                    "execute in minecraft:the_end if entity @e[type=minecraft:fishing_bobber,x=900,dx=300,z=-100,dz=200,y=46.5,dy=1]")
                    .contains("passed"));
            Await.until("a fish bites in the void", Duration.ofSeconds(45),
                    () -> ada.particlesSeen().contains("END_ROD"));
            ada.useHeld();
            Await.until("the catch lands", Duration.ofSeconds(15), () -> ada.items().size() > before
                    || ada.items().stream().anyMatch(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                    .startsWith("fishing:")));
            assertThat(ada.items()).anyMatch(item -> item.tags().getOrDefault("rainscore:custom-item", "")
                    .matches("fishing:(fish|chest)-.*"));
        }
    }
}
