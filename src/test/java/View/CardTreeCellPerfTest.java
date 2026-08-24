package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Diagnostic timing for {@link CardTreeCell#collectAllElementsInTreeOrder}, added while
 * investigating the "scanner gets slow after a few cards" report (2026-08-24). Every card
 * insertion (scanner or manual) walks the whole owned-collection tree at least once via this
 * method to find its insertion target — this measures how that cost scales with collection size.
 * Temporary, like {@code Utils.PerfLog} itself: drop once the bottleneck is confirmed/fixed.
 */
class CardTreeCellPerfTest {

    private static final Logger logger = LoggerFactory.getLogger(CardTreeCellPerfTest.class);

    /**
     * {@code TreeItem} construction touches {@code javafx.scene.control.Control}'s static
     * initializer, which needs the FX toolkit running — unlike the rest of this test suite, which
     * avoids building real {@code TreeView}/{@code TreeItem} trees specifically to dodge this (see
     * {@code MiddleSelectionActionHandlerQuickAddTargetTest}'s javadoc). {@link Platform#startup}
     * is the lightweight way to bring the toolkit up without a visible {@code Stage}.
     */
    @BeforeAll
    static void startFxToolkit() throws InterruptedException {
        CountDownLatch toolkitReady = new CountDownLatch(1);
        try {
            Platform.startup(toolkitReady::countDown);
        } catch (IllegalStateException alreadyStarted) {
            toolkitReady.countDown();
        }
        toolkitReady.await();
    }

    private static TreeItem<String> buildSyntheticTree(int groupCount, int cardsPerGroup) {
        DataTreeItem<Object> root = new DataTreeItem<>("ROOT", "ROOT");
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            List<CardElement> cardList = new ArrayList<>(cardsPerGroup);
            for (int cardIndex = 0; cardIndex < cardsPerGroup; cardIndex++) {
                Card card = new Card();
                card.setName_EN("Card " + groupIndex + "-" + cardIndex);
                cardList.add(new CardElement(card));
            }
            CardsGroup group = new CardsGroup("Group " + groupIndex, cardList);
            root.getChildren().add(new DataTreeItem<>("Group " + groupIndex, group));
        }
        return root;
    }

    @Test
    void collectAllElementsInTreeOrder_scalingByCollectionSize() {
        int[] sizes = {1_000, 5_000, 10_000, 20_000};
        for (int totalCards : sizes) {
            int groupCount = 50;
            int cardsPerGroup = totalCards / groupCount;
            TreeItem<String> tree = buildSyntheticTree(groupCount, cardsPerGroup);

            // Warm up the JIT on the smallest size before trusting any timing.
            CardTreeCell.collectAllElementsInTreeOrder(tree);

            long startNanos = System.nanoTime();
            List<CardElement> result = CardTreeCell.collectAllElementsInTreeOrder(tree);
            long elapsedMicros = (System.nanoTime() - startNanos) / 1_000;

            logger.info("[PERF-INVESTIGATION] collectAllElementsInTreeOrder over {} cards ({} groups): "
                            + "{} us ({} elements returned)",
                    totalCards, groupCount, elapsedMicros, result.size());
        }
    }
}
