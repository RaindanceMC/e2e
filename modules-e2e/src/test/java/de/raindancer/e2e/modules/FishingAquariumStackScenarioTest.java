package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fishing 0.16 on a real server: an aquarium set down on another stacks onto it and joins it, without opening it. */
@Tag("e2e")
class FishingAquariumStackScenarioTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private static String lookAt(Server server, int x, int y, int z) {
        return server.console("data get entity @e[type=minecraft:item_display,limit=1,sort=nearest,x=" + (x + 0.5)
                + ",y=" + (y + 0.5) + ",z=" + (z + 0.5) + ",distance=..0.8] item.components");
    }

    private static boolean tankAt(Server server, int x, int y, int z) {
        return server.console("execute if block " + x + " " + y + " " + z + " barrier").contains("passed");
    }

    @Test
    @DisplayName("a tank set down on a tank stacks on it, opens nothing, and both wear the looks joined up and down")
    void stacked() throws Exception {
        try (Server server = Server.start("fishing-aquarium-stack", List.of("fishing-standalone:RainsFishing-.*"),
                List.of("Fishing is up"))) {
            Bot ada = server.admin("Ada");
            server.console("clear Ada");
            ada.run("fishing give Ada aquarium 2");
            Await.until("two aquariums", WAIT, () -> ada.items().stream()
                    .filter(item -> item.name().contains("Aquarium")).mapToInt(Bot.Item::amount).sum() == 2);

            var at = ada.position();
            int x = (int) Math.floor(at.getX()) + 2;
            int y = (int) Math.floor(at.getY());
            int z = (int) Math.floor(at.getZ());
            server.console("setblock " + x + " " + (y - 1) + " " + z + " stone");
            server.console("fill " + x + " " + y + " " + z + " " + x + " " + (y + 1) + " " + z + " air");
            ada.hold(ada.hotbarSlotOf(item -> item.name().contains("Aquarium")));
            ada.useOn(x, y - 1, z);
            Await.until("the first tank is down", WAIT, () -> tankAt(server, x, y, z));

            int windows = ada.windowsSeen().size();
            ada.useOn(x, y, z);
            Await.until("the second tank stands on the first", WAIT, () -> tankAt(server, x, y + 1, z));
            Await.ticks(10);
            assertThat(ada.windowsSeen()).as("setting a tank down on another opens nothing").hasSize(windows);

            Await.until("the lower tank is joined above", WAIT, () -> lookAt(server, x, y, z).contains("aquarium_16"));
            Await.until("the upper tank is joined below", WAIT, () -> lookAt(server, x, y + 1, z).contains("aquarium_32"));

            ada.hold(8);
            ada.useOn(x, y + 1, z);
            ada.awaitWindow("Aquarium · 2 tanks");
            ada.closeWindow();
        }
    }
}
