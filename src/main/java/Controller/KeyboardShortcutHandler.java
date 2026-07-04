package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handles global keyboard shortcuts and the numpad/enter "quick add from the
 * right pane" workflow for the main window.
 *
 * <p>Extracted from {@link RealMainController}, which still owns
 * {@code mainTabPane}, the shared {@code cardsDisplayContainer}, the
 * mosaic/list view-mode flag, and dirty-indicator refresh. Those are supplied
 * here as constructor dependencies rather than duplicated, so this class has
 * no state of its own beyond what it needs to route an input event.
 */
public class KeyboardShortcutHandler {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardShortcutHandler.class);

    private final TabPane mainTabPane;
    private final Supplier<AnchorPane> cardsDisplayContainerSupplier;
    private final Supplier<Boolean> mosaicModeSupplier;
    private final Supplier<TreeView<String>> activeMiddleTreeViewSupplier;
    private final Runnable dirtyIndicatorUpdater;

    public KeyboardShortcutHandler(TabPane mainTabPane,
                                   Supplier<AnchorPane> cardsDisplayContainerSupplier,
                                   Supplier<Boolean> mosaicModeSupplier,
                                   Supplier<TreeView<String>> activeMiddleTreeViewSupplier,
                                   Runnable dirtyIndicatorUpdater) {
        this.mainTabPane = mainTabPane;
        this.cardsDisplayContainerSupplier = cardsDisplayContainerSupplier;
        this.mosaicModeSupplier = mosaicModeSupplier;
        this.activeMiddleTreeViewSupplier = activeMiddleTreeViewSupplier;
        this.dirtyIndicatorUpdater = dirtyIndicatorUpdater;
    }

    // =========================================================================
    // Global keyboard shortcuts
    // =========================================================================

    public void setupGlobalKeyShortcuts() {
        mainTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        this::handleGlobalKeyShortcut);
            }
        });
    }

    private void handleGlobalKeyShortcut(javafx.scene.input.KeyEvent event) {
        // CTRL+S and CTRL+SHIFT+S — work even when a text field has focus.
        if (event.getCode() == javafx.scene.input.KeyCode.S && event.isControlDown()) {
            handleSaveShortcut(event.isShiftDown());
            event.consume();
            return;
        }

        // CTRL+Tab (next tab) / CTRL+SHIFT+Tab (previous tab).
        if (event.getCode() == javafx.scene.input.KeyCode.TAB && event.isControlDown()) {
            if (mainTabPane != null) {
                int total = mainTabPane.getTabs().size();
                if (total > 1) {
                    int current = mainTabPane.getSelectionModel().getSelectedIndex();
                    int next = event.isShiftDown()
                            ? (current - 1 + total) % total
                            : (current + 1) % total;
                    mainTabPane.getSelectionModel().select(next);
                }
            }
            event.consume();
            return;
        }

        // Remaining shortcuts should not fire while typing in a text field.
        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
            return;
        }

        boolean middleSelectionActive =
                "MIDDLE".equals(SelectionManager.getActivePart())
                        && !SelectionManager.getSelectedMiddleElements().isEmpty();
        boolean anySelectionActive =
                !SelectionManager.getSelectedCards().isEmpty()
                        || !SelectionManager.getSelectedMiddleElements().isEmpty();

        switch (event.getCode()) {
            case ESCAPE -> {
                if (anySelectionActive) {
                    SelectionManager.clearSelection();
                    event.consume();
                }
            }
            case DELETE -> {
                if (middleSelectionActive) {
                    handleDeleteMiddleSelection();
                    event.consume();
                }
            }
            case C -> {
                if (event.isControlDown() && anySelectionActive) {
                    MiddleSelectionActionHandler.handleCopySelectionToClipboard();
                    event.consume();
                }
            }
            case X -> {
                if (event.isControlDown() && middleSelectionActive) {
                    handleCutFromKeyboard();
                    event.consume();
                }
            }
            case D -> {
                if (event.isControlDown() && middleSelectionActive) {
                    handleDuplicateMiddleSelection();
                    event.consume();
                }
            }
            case V -> {
                if (event.isControlDown() && !CardClipboard.isEmpty()) {
                    handlePasteFromKeyboard();
                    event.consume();
                }
            }
            case NUMPAD1 -> {
                handleNumpadAddFromRightPane(1);
                event.consume();
            }
            case NUMPAD2 -> {
                handleNumpadAddFromRightPane(2);
                event.consume();
            }
            case NUMPAD3 -> {
                handleNumpadAddFromRightPane(3);
                event.consume();
            }
            case NUMPAD4 -> {
                handleNumpadAddFromRightPane(4);
                event.consume();
            }
            case NUMPAD5 -> {
                handleNumpadAddFromRightPane(5);
                event.consume();
            }
            case NUMPAD6 -> {
                handleNumpadAddFromRightPane(6);
                event.consume();
            }
            case NUMPAD7 -> {
                handleNumpadAddFromRightPane(7);
                event.consume();
            }
            case NUMPAD8 -> {
                handleNumpadAddFromRightPane(8);
                event.consume();
            }
            case NUMPAD9 -> {
                handleNumpadAddFromRightPane(9);
                event.consume();
            }
            default -> {
            }
        }
    }

    private void handleSaveShortcut(boolean saveAll) {
        if (mainTabPane == null) {
            return;
        }
        int selectedIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        try {
            if (saveAll) {
                if (UserInterfaceFunctions.isMyCollectionDirty()) {
                    UserInterfaceFunctions.saveMyCollection();
                }
                if (UserInterfaceFunctions.isAnyDeckOrCollectionDirty()) {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                }
                if (UserInterfaceFunctions.isOuicheListDirty()) {
                    UserInterfaceFunctions.saveOuicheList();
                }
            } else {
                switch (selectedIndex) {
                    case 0 -> UserInterfaceFunctions.saveMyCollection();
                    case 1 -> UserInterfaceFunctions.saveAllDecksAndCollections();
                    case 2 -> UserInterfaceFunctions.saveOuicheList();
                    default -> {
                    }
                }
            }
            dirtyIndicatorUpdater.run();
        } catch (Exception exception) {
            logger.error("Error during save shortcut", exception);
        }
    }

    public void handleDeleteMiddleSelection() {
        MiddleSelectionActionHandler.handleDeleteMiddleSelection(
                mainTabPane.getSelectionModel().getSelectedIndex(), activeMiddleTreeViewSupplier);
    }

    private void handleCutFromKeyboard() {
        MiddleSelectionActionHandler.handleCopySelectionToClipboard();
        handleDeleteMiddleSelection();
    }

    public void handleDuplicateMiddleSelection() {
        MiddleSelectionActionHandler.handleDuplicateMiddleSelection(
                activeMiddleTreeViewSupplier.get(), mainTabPane.getSelectionModel().getSelectedIndex());
    }

    /**
     * For each selected card element, counts how many copies of the same card
     * (by konamiId) are already in that element's direct container (a CardsGroup,
     * a deck section, or a ThemeCollection cardsList), then inserts copies after
     * the element until the container holds exactly 3.  Elements whose container
     * already has 3 or more copies are skipped; no cards are ever removed.
     */
    public void handleCompleteToThree() {
        MiddleSelectionActionHandler.handleCompleteToThree(
                activeMiddleTreeViewSupplier.get(), mainTabPane.getSelectionModel().getSelectedIndex());
    }

    private void handlePasteFromKeyboard() {
        if (CardClipboard.isEmpty()) {
            return;
        }
        List<CardElement> clipboardElements = CardClipboard.getContents();
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();

        // Priority 1: after the last element of the current MIDDLE selection
        if ("MIDDLE".equals(SelectionManager.getActivePart())
                && !SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> activeTreeView = activeMiddleTreeViewSupplier.get();
            if (activeTreeView != null) {
                List<CardElement> allElementsInOrder =
                        View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
                Set<CardElement> selectedElements =
                        SelectionManager.getSelectedMiddleElements();
                CardElement lastElement = null;
                for (int index = allElementsInOrder.size() - 1; index >= 0; index--) {
                    if (selectedElements.contains(allElementsInOrder.get(index))) {
                        lastElement = allElementsInOrder.get(index);
                        break;
                    }
                }
                if (lastElement != null
                        && MiddleSelectionActionHandler.pasteElementsAfterElement(
                        clipboardElements, lastElement, activeTabIndex)) {
                    return;
                }
            }
        }

        // Priority 2: after the last MIDDLE element that was explicitly clicked
        CardElement lastMiddleElement = SelectionManager.getLastMiddleElement();
        if (lastMiddleElement != null
                && MiddleSelectionActionHandler.pasteElementsAfterElement(
                clipboardElements, lastMiddleElement, activeTabIndex)) {
            return;
        }

        // Priority 3: into the last clicked navigation-menu item
        Object lastNavItem = SelectionManager.getLastClickedNavigationItem();
        if (lastNavItem != null) {
            MiddleSelectionActionHandler.pasteElementsIntoNavigationItem(clipboardElements, lastNavItem);
        }
    }

    // =========================================================================
    // Numpad / Enter "quick add from right pane"
    // =========================================================================

    private void handleNumpadAddFromRightPane(int count) {
        int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIndex != 0 && tabIndex != 1) {
            return;
        }
        List<Card> sourceCards = getNumpadSourceCards();
        if (sourceCards.isEmpty()) {
            return;
        }
        List<Card> toInsert = new ArrayList<>();
        for (Card card : sourceCards) {
            for (int repeatIndex = 0; repeatIndex < count; repeatIndex++) {
                toInsert.add(card);
            }
        }
        insertCardsAtNumpadTarget(toInsert);
    }

    public void handleEnterAddFromRightPane() {
        int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIndex != 0 && tabIndex != 1) {
            return;
        }
        List<Card> displayed = getDisplayedRightPaneCards();
        if (displayed.size() != 1) {
            return;
        }
        List<Card> toInsert = new ArrayList<>();
        toInsert.add(displayed.get(0));
        insertCardsAtNumpadTarget(toInsert);
    }

    private List<Card> getNumpadSourceCards() {
        List<Card> displayed = getDisplayedRightPaneCards();
        Set<Card> selected = SelectionManager.getSelectedCards();
        if (!selected.isEmpty()) {
            List<Card> visible = displayed.stream()
                    .filter(selected::contains)
                    .collect(Collectors.toList());
            if (!visible.isEmpty()) {
                return visible;
            }
        }
        if (displayed.size() == 1) {
            return new ArrayList<>(displayed);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Card> getDisplayedRightPaneCards() {
        AnchorPane cardsDisplayContainer = cardsDisplayContainerSupplier.get();
        if (cardsDisplayContainer == null) {
            return Collections.emptyList();
        }
        boolean isMosaicMode = mosaicModeSupplier.get();
        for (Node node : cardsDisplayContainer.getChildren()) {
            if (node instanceof ListView) {
                if (!isMosaicMode) {
                    ListView<Card> listView = (ListView<Card>) node;
                    return new ArrayList<>(listView.getItems());
                } else {
                    ListView<List<Card>> listView = (ListView<List<Card>>) node;
                    List<Card> all = new ArrayList<>();
                    for (List<Card> row : listView.getItems()) {
                        all.addAll(row);
                    }
                    return all;
                }
            }
        }
        return Collections.emptyList();
    }

    private CardElement getNumpadTargetMiddleElement() {
        if ("MIDDLE".equals(SelectionManager.getActivePart())
                && !SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> treeView = activeMiddleTreeViewSupplier.get();
            if (treeView != null) {
                List<CardElement> allElements =
                        View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
                Set<CardElement> selected =
                        SelectionManager.getSelectedMiddleElements();
                for (int index = allElements.size() - 1; index >= 0; index--) {
                    if (selected.contains(allElements.get(index))) {
                        return allElements.get(index);
                    }
                }
            }
        }
        CardElement last = SelectionManager.getLastMiddleElement();
        return MiddleSelectionActionHandler.elemBelongsToActiveTab(last, activeMiddleTreeViewSupplier.get())
                ? last : null;
    }

    private void insertCardsAtNumpadTarget(List<Card> toInsert) {
        int totalInserted = toInsert.size();
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();

        CardElement targetElement = getNumpadTargetMiddleElement();
        if (targetElement != null
                && MiddleSelectionActionHandler.pasteCardsAfterElement(toInsert, targetElement, activeTabIndex)) {
            selectLastInsertedElement(targetElement, totalInserted);
            return;
        }

        Object navItem = SelectionManager.getLastClickedNavigationItem();
        if (navItem != null && MiddleSelectionActionHandler.navItemBelongsToActiveTab(navItem, activeTabIndex)) {
            List<CardElement> backing = MiddleSelectionActionHandler.getTargetGroupElements(navItem);
            MiddleSelectionActionHandler.pasteCardsIntoNavigationItem(toInsert, navItem);
            if (!backing.isEmpty()) {
                SelectionManager.selectElement(backing.get(backing.size() - 1));
            }
            return;
        }

        TreeView<String> treeView = activeMiddleTreeViewSupplier.get();
        if (treeView == null || treeView.getRoot() == null) {
            return;
        }
        List<CardElement> allElements =
                View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
        if (!allElements.isEmpty()) {
            CardElement lastElement = allElements.get(allElements.size() - 1);
            if (MiddleSelectionActionHandler.pasteCardsAfterElement(toInsert, lastElement, activeTabIndex)) {
                selectLastInsertedElement(lastElement, totalInserted);
                return;
            }
        }

        Object lastNavModel = MiddleSelectionActionHandler.findLastNavModelInTree(treeView);
        if (lastNavModel != null) {
            List<CardElement> backing = MiddleSelectionActionHandler.getTargetGroupElements(lastNavModel);
            MiddleSelectionActionHandler.pasteCardsIntoNavigationItem(toInsert, lastNavModel);
            if (!backing.isEmpty()) {
                SelectionManager.selectElement(backing.get(backing.size() - 1));
            }
        }
    }

    private void selectLastInsertedElement(CardElement anchor, int count) {
        TreeView<String> treeView = activeMiddleTreeViewSupplier.get();
        if (treeView == null) {
            return;
        }
        List<CardElement> allElements =
                View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
        int anchorIndex = allElements.indexOf(anchor);
        if (anchorIndex < 0) {
            return;
        }
        int lastIndex = Math.min(anchorIndex + count, allElements.size() - 1);
        SelectionManager.selectElement(allElements.get(lastIndex));
    }
}