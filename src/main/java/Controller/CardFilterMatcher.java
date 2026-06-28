package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.SubListCreator;
import View.FilterPane;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Holds the card/element filter-matching logic used by the middle pane, the right
 * pane, and the OuicheList compact views.
 * <p>
 * Extracted from {@link RealMainController}, which still exposes
 * {@code getCompactOuicheListElementFilter}, {@code buildMiddlePaneEmptySpaceFilter}
 * and {@code buildRightPaneEmptySpaceClearHandler} as thin delegates so existing
 * call sites in the sub-controllers are unaffected. Every method here is a pure
 * function of its parameters — none of it depends on coordinator instance state,
 * which is what made the move possible without any behavior change.
 * </p>
 */
final class CardFilterMatcher {

    private CardFilterMatcher() {
    }

    /**
     * Collects the {@link FilterPane.FilterPageState}s that currently apply to the
     * middle-pane / compact OuicheList display: enabled pages whose bottom-left
     * arrow is enabled.
     *
     * @param filterPane the active filter pane, or {@code null} if none
     * @return the list of applicable page states, possibly empty
     */
    static List<FilterPane.FilterPageState> getActiveLeftFilterStates(FilterPane filterPane) {
        List<FilterPane.FilterPageState> activeStates = new ArrayList<>();
        if (filterPane != null) {
            for (int pageIndex = 0; pageIndex < 5; pageIndex++) {
                FilterPane.FilterPageState pageState = filterPane.getPageState(pageIndex);
                if (pageState != null && pageState.enabled && pageState.bottomLeftEnabled) {
                    activeStates.add(pageState);
                }
            }
        }
        return activeStates;
    }

    /**
     * Builds a single {@link Predicate} on {@link CardElement} that combines every
     * active left-pane filter (card-level fields plus the per-copy Tags filter), for
     * use by views that operate on {@link CardElement} maps directly, such as the
     * OuicheList compact List/Mosaic.
     *
     * @param filterPane    the active filter pane, or {@code null} if none
     * @param isPrintedMode whether PrintCode (vs. PassCode) is the active code field
     * @return a predicate to apply to each {@link CardElement}, or {@code null} when
     * no left-pane filter is currently active (meaning every element passes)
     */
    static Predicate<CardElement> buildElementFilter(FilterPane filterPane, boolean isPrintedMode) {
        List<FilterPane.FilterPageState> activeStates = getActiveLeftFilterStates(filterPane);

        if (activeStates.isEmpty()) {
            return null;
        }

        final List<FilterPane.FilterPageState> captured = activeStates;
        return element -> {
            if (element == null || element.getCard() == null) {
                return false;
            }
            for (FilterPane.FilterPageState pageState : captured) {
                if (!matchesPageFilter(element.getCard(), pageState, isPrintedMode)) {
                    return false;
                }
                if (!matchesTagsFilter(element, pageState.tags)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Returns {@code true} when {@code card} matches all conditions specified in
     * {@code pageState}. This method is the single source of truth for card-filter
     * logic; it is used by both the right-panel and the middle-panel filters.
     *
     * @param card          the card to test
     * @param pageState     the filter-page state to test against
     * @param isPrintedMode whether PrintCode (vs. PassCode) is the active code field
     * @return {@code true} if the card passes all conditions in the page
     */
    static boolean matchesPageFilter(Card card, FilterPane.FilterPageState pageState, boolean isPrintedMode) {
        if (card == null || pageState == null) {
            return true;
        }

        // ── Name filter ───────────────────────────────────────────────────────
        String nameFilter = pageState.name.toLowerCase().trim();
        if (!nameFilter.isEmpty()) {
            boolean matchesName =
                    (card.getName_EN() != null
                            && card.getName_EN().toLowerCase().contains(nameFilter))
                            || (card.getName_FR() != null
                            && card.getName_FR().toLowerCase().contains(nameFilter))
                            || (card.getName_JA() != null
                            && card.getName_JA().toLowerCase().contains(nameFilter));
            if (!matchesName) {
                return false;
            }
        }

        // ── PrintCode / PassCode filter ───────────────────────────────────────
        String codeFilter = pageState.printCode.toLowerCase().trim();
        if (!codeFilter.isEmpty()) {
            boolean matchesCode = isPrintedMode
                    ? (card.getPrintCode() != null
                    && card.getPrintCode().toLowerCase().contains(codeFilter))
                    : (card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(codeFilter));
            if (!matchesCode) {
                return false;
            }
        }

        // ── PassCode filter ───────────────────────────────────────────────────
        String passCodeFilter = pageState.passCode.toLowerCase().trim();
        if (!passCodeFilter.isEmpty()) {
            boolean matches = card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(passCodeFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Konami ID filter ──────────────────────────────────────────────────
        String konamiIdFilter = pageState.konamiId.toLowerCase().trim();
        if (!konamiIdFilter.isEmpty()) {
            boolean matches = card.getKonamiId() != null
                    && card.getKonamiId().toLowerCase().contains(konamiIdFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Effect / Description filter ───────────────────────────────────────
        String effectFilter = pageState.effect.toLowerCase().trim();
        if (!effectFilter.isEmpty()) {
            boolean matches = card.getDescription() != null
                    && card.getDescription().toLowerCase().contains(effectFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Category (card type) filter ───────────────────────────────────────
        if (!"(All)".equals(pageState.cardType)) {
            if (card.getCardType() == null
                    || !card.getCardType().contains(pageState.cardType)) {
                return false;
            }
        }

        // ── Subtype filter ────────────────────────────────────────────────────
        if (!pageState.cardSubtypes.isEmpty()) {
            if (card.getCardProperties() == null) {
                return false;
            }
            for (String subtype : pageState.cardSubtypes) {
                if (!card.getCardProperties().contains(subtype)) {
                    return false;
                }
            }
        }

        // ── Monster-only fields ───────────────────────────────────────────────
        if ("Monster".equals(pageState.cardType) || "(All)".equals(pageState.cardType)) {
            if (!"(All)".equals(pageState.attribute)) {
                if (card.getAttribute() == null
                        || !card.getAttribute().equalsIgnoreCase(pageState.attribute)) {
                    return false;
                }
            }
            if (!"(All)".equals(pageState.type)) {
                if (card.getCardProperties() == null
                        || !card.getCardProperties().contains(pageState.type)) {
                    return false;
                }
            }
            if (!matchesIntField(pageState.atk, card.getAtk())) {
                return false;
            }
            if (!matchesIntField(pageState.def, card.getDef())) {
                return false;
            }
            if (!pageState.level.isEmpty()) {
                boolean matchesLvRnkLnk =
                        matchesIntField(pageState.level, card.getLevel())
                                || matchesIntField(pageState.level, card.getRank())
                                || matchesIntField(pageState.level, card.getLinkVal());
                if (!matchesLvRnkLnk) {
                    return false;
                }
            }
            if (pageState.cardSubtypes.isEmpty() || pageState.cardSubtypes.contains("Pendulum")) {
                if (!matchesIntField(pageState.scale, card.getScale())) {
                    return false;
                }
            }
        }

        // ── Link Marker filter ────────────────────────────────────────────────
        if (!"Spell".equals(pageState.cardType) && !"Trap".equals(pageState.cardType)) {
            if (pageState.linkMarkers != null && !pageState.linkMarkers.isEmpty()) {
                List<String> cardMarkers = card.getLinkMarker();
                if (cardMarkers == null) {
                    return false;
                }
                for (String marker : pageState.linkMarkers) {
                    if (!cardMarkers.contains(marker)) {
                        return false;
                    }
                }
            }
        }

        // ── Word Count filter ─────────────────────────────────────────────────
        if (!pageState.wordCount.isEmpty()) {
            String desc = card.getDescription();
            int wordCount = (desc == null || desc.isBlank()) ? 0
                    : desc.trim().split("\\s+").length;
            if (!matchesIntField(pageState.wordCount, wordCount)) {
                return false;
            }
        }

        // ── Price filter ──────────────────────────────────────────────────────
        if (!pageState.price.isEmpty()) {
            if (!matchesDoubleField(pageState.price, card.getPrice())) {
                return false;
            }
        }

        // ── Archetype filter ──────────────────────────────────────────────────
        if (!pageState.archetype.isBlank() && !"(All)".equals(pageState.archetype)) {
            List<String> archetypeNames = SubListCreator.getArchetypesList();
            List<List<Card>> archetypeCardLists =
                    SubListCreator.getArchetypesCardsLists();
            if (archetypeNames == null || archetypeNames.isEmpty()) {
                return false;
            }
            int archetypeIndex = -1;
            for (int index = 0; index < archetypeNames.size(); index++) {
                if (pageState.archetype.equalsIgnoreCase(archetypeNames.get(index))) {
                    archetypeIndex = index;
                    break;
                }
            }
            if (archetypeIndex < 0 || archetypeIndex >= archetypeCardLists.size()) {
                return false;
            }
            List<Card> members = archetypeCardLists.get(archetypeIndex);
            if (members == null) {
                return false;
            }
            String cardKonamiId = card.getKonamiId();
            String cardPassCode = card.getPassCode();
            boolean isMember = false;
            for (Card member : members) {
                if (member == null) {
                    continue;
                }
                if (cardKonamiId != null && cardKonamiId.equals(member.getKonamiId())) {
                    isMember = true;
                    break;
                }
                if (cardPassCode != null && cardPassCode.equals(member.getPassCode())) {
                    isMember = true;
                    break;
                }
            }
            if (!isMember) {
                return false;
            }
        }

        // ── Multiple artworks filter ──────────────────────────────────────────
        if (pageState.multipleArtworks) {
            boolean hasMultipleArtworks = false;
            String passCodeString = card.getPassCode();
            if (passCodeString != null && !passCodeString.isBlank()) {
                try {
                    List<Model.CardsLists.Card> aliases =
                            Model.Database.CardDatabaseManager.getAliasCards(
                                    Integer.parseInt(passCodeString));
                    hasMultipleArtworks = aliases != null && aliases.size() > 1;
                } catch (NumberFormatException ignored) {
                    // Non-numeric passCode: treat as no multiple artworks.
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }
            if (!hasMultipleArtworks) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true when the given element passes the Tags filter.
     *
     * <p>Inactive (returns true) when {@code tagsFilter} is blank.
     * Otherwise splits on {@code ","}, trims each token, and returns true if
     * at least one token is a case-insensitive substring of at least one of the
     * element's {@link CardElement#getCustomTags() customTags}. OR semantics:
     * any single matching token is sufficient.
     *
     * @param element    the individual owned copy being tested
     * @param tagsFilter the raw filter string from the Tags text field
     */
    static boolean matchesTagsFilter(CardElement element, String tagsFilter) {
        if (tagsFilter == null || tagsFilter.isBlank()) {
            return true;
        }
        List<String> elementTags = element.getCustomTags();
        if (elementTags == null || elementTags.isEmpty()) {
            return false;
        }
        String[] tokens = tagsFilter.split(",");
        for (String token : tokens) {
            String trimmedToken = token.trim().toLowerCase();
            if (trimmedToken.isEmpty()) {
                continue;
            }
            for (String ownedTag : elementTags) {
                if (ownedTag != null && ownedTag.toLowerCase().contains(trimmedToken)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean matchesIntField(String filterText, Integer cardValue) {
        if (filterText == null || filterText.isBlank()) {
            return true;
        }
        filterText = filterText.trim();
        if (filterText.contains("-")) {
            String[] parts = filterText.split("-", 2);
            try {
                int low = Integer.parseInt(parts[0].trim());
                int high = Integer.parseInt(parts[1].trim());
                return cardValue != null && cardValue >= low && cardValue <= high;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        try {
            int target = Integer.parseInt(filterText);
            return cardValue != null && cardValue == target;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    static boolean matchesDoubleField(String filterText, String cardPriceStr) {
        if (filterText == null || filterText.isBlank()) {
            return true;
        }
        filterText = filterText.trim();
        if (cardPriceStr == null || cardPriceStr.isBlank()) {
            return false;
        }
        double cardValue;
        try {
            cardValue = Double.parseDouble(cardPriceStr.replace(',', '.').trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (filterText.contains("-")) {
            String[] parts = filterText.split("-", 2);
            try {
                double low = Double.parseDouble(parts[0].trim().replace(',', '.'));
                double high = Double.parseDouble(parts[1].trim().replace(',', '.'));
                return cardValue >= low && cardValue <= high;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        try {
            double target = Double.parseDouble(filterText.replace(',', '.'));
            return cardValue == target;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Returns an event filter for the middle-pane TreeView that clears the card
     * selection when the user clicks on an empty area below all tree nodes.
     *
     * @return an EventHandler suitable for {@code treeView.addEventFilter(...)}
     */
    static EventHandler<MouseEvent> buildMiddlePaneEmptySpaceFilter() {
        return event -> {
            if (event.isControlDown() || event.isShiftDown()) {
                return;
            }
            Node current = (Node) event.getTarget();
            while (current != null) {
                if (Boolean.TRUE.equals(current.getProperties().get("cardWrapper"))) {
                    return;
                }
                if (current instanceof TreeView) {
                    break;
                }
                current = current.getParent();
            }
            SelectionManager.clearSelection();
        };
    }

    /**
     * Returns an event handler for the right-panel ListView that clears the selection
     * when the user clicks on empty space (i.e. a non-consumed click).
     *
     * @return an EventHandler suitable for {@code listView.addEventHandler(...)}
     */
    static EventHandler<MouseEvent> buildRightPaneEmptySpaceClearHandler() {
        return event -> {
            if (!event.isConsumed() && !event.isControlDown() && !event.isShiftDown()) {
                SelectionManager.clearSelection();
            }
        };
    }
}