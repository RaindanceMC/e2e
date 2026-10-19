package de.raindancer.e2e.modules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every item the game has — read from the running server, so a new version's items are asked too — has a price
 * the shop pays.
 */
@Tag("e2e")
class EconomyEverythingSellsScenarioTest {

    private static final Pattern PAGE = Pattern.compile("page (\\d+)/(\\d+).*?: ([a-z0-9_, ]+)");

    @Test
    @DisplayName("the shop buys every item there is")
    void everyItemSells() {
        try (Server server = Server.start("economy-everything-sells", List.of("economy-standalone:RainsEconomy-.*"),
                List.of("The economy is up"))) {
            List<String> missing = new ArrayList<>();
            int pages = 1;
            for (int page = 1; page <= pages; page++) {
                String said = server.console("eco unpriced " + page);
                if (said.contains("The shop buys every item there is")) {
                    break;
                }
                Matcher found = PAGE.matcher(said);
                assertThat(found.find()).as(said).isTrue();
                pages = Integer.parseInt(found.group(2));
                missing.addAll(List.of(found.group(3).trim().split(",\\s*")));
            }
            assertThat(missing).as("items the shop does not buy").isEmpty();
        }
    }
}
