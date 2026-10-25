package de.raindancer.e2e.modules;

import de.raindancer.e2e.Await;
import de.raindancer.e2e.Bot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** /ad in RainsChat on a real server: framed in chat, a bell, and the action bar — for everybody online. */
@Tag("e2e")
class AdScenarioTest {

    @Test
    @DisplayName("an ad is framed in chat, rings a bell and shows on the action bar; the old one-line default is upgraded")
    void adStandsOut() {
        // The pre-1.21 default written into an owner's config, as it is on live servers.
        String config = "ads:\n  enabled: true\n  price: '0'\n  cooldown-seconds: 0\n"
                + "  format: '<gold>[Ad] <reset><name><gray>: <yellow><message>'\n";
        try (Server server = Server.start("ad", List.of("chat-standalone:RainsChat-.*"), List.of(),
                Map.of("plugins/RainsChat/config.yml", config), List.of("Chat is up"))) {
            Bot ada = server.player("Ada");
            Bot bo = server.player("Bo");
            Await.ticks(10);
            bo.forgetChat();
            bo.forgetEffects();

            ada.run("ad selling cobble, cheap");

            Await.until("Bo sees the ad", Duration.ofSeconds(10),
                    () -> bo.chatText().stream().anyMatch(line -> line.contains("selling cobble, cheap")));
            String ad = bo.chatText().stream().filter(line -> line.contains("selling cobble, cheap")).findFirst().orElseThrow();
            assertThat(ad.split("\n")).hasSize(3);
            assertThat(ad.split("\n")[0].strip()).isEqualTo("Ad");
            assertThat(ad).doesNotContain("[Ad]");

            assertThat(bo.expectActionBar("selling cobble")).contains("Ada");
            Await.until("Bo hears the bell", Duration.ofSeconds(5),
                    () -> bo.soundsHeard().stream().anyMatch(sound -> sound.contains("note_block.bell")));

            assertThat(server.paper.errorsFrom("RainsCore", "RainsChat")).isEmpty();
        }
    }
}
