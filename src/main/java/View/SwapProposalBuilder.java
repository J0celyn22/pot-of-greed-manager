package View;

import Controller.CardGroupRegistry;
import Controller.CardQualityService;
import Controller.MenuActionHandler;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static Utils.CardNameUtils.sanitize;

/**
 * Static factory that builds the "Swap" {@link MenuItem}s shown in the card
 * context menu.
 *
 * <p>Previously this logic lived as a cluster of {@code private} methods
 * inside {@link CardTreeCell}, which forced {@link CardGridCellContextMenuBuilder}
 * to invoke it via reflection. Moving the cluster here makes it a plain
 * static call from both callers and removes the fragile reflection.</p>
 */
public final class SwapProposalBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SwapProposalBuilder.class);

    private SwapProposalBuilder() {
    }

    // =========================================================================
    // Public entry point
    // =========================================================================

    /**
     * Builds a list of "swap with…" {@link MenuItem}s for the given card and
     * the element that was right-clicked.
     *
     * <p>Two branches:</p>
     * <ul>
     *   <li><b>D&amp;C branch</b> — {@code clicked} is an element that lives in a
     *       deck section group ({@link CardGroupRegistry#DECK_SECTION_GROUPS}).
     *       Every owned copy of the same card is offered as a candidate.
     *       Orange = strict quality improvement over {@code clicked}.</li>
     *   <li><b>My Collection branch</b> — both {@code clicked} and all candidates
     *       are owned-collection elements. Orange = the candidate sits in a
     *       deck-named group <em>and</em> {@code clicked} would be an upgrade
     *       for that deck.</li>
     * </ul>
     *
     * @param card       the card whose copies to search
     * @param clicked    the element that was right-clicked
     * @param anchorNode any {@link Node} inside the originating cell, used to
     *                   walk the scene-graph for the D&amp;C element-name context
     * @return the (possibly empty) list of menu items
     */
    public static List<MenuItem> buildSwapProposals(
            Card card,
            CardElement clicked,
            Node anchorNode) {

        List<MenuItem> items = new ArrayList<>();
        try {
            CardsGroup parentGroup = getParentGroup(clicked);
            boolean inDeckContext =
                    parentGroup != null
                            && CardTreeCell.findDeckOwnerForGroup(parentGroup) != null;

            OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
            List<CardElement> allOwnedCopies =
                    CardQualityService.collectOwnedCopies(owned, card);
            DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();

            if (inDeckContext) {
                buildDeckContextSwapItems(clicked, allOwnedCopies, owned, anchorNode, items);
            } else {
                buildCollectionContextSwapItems(clicked, allOwnedCopies, owned, dac, items);
            }
        } catch (Throwable thrown) {
            logger.debug("buildSwapProposals failed", thrown);
        }
        return items;
    }

    // =========================================================================
    // Private branch builders
    // =========================================================================

    /**
     * Builds swap items for the D&amp;C context: every owned copy of the card
     * is offered; orange = strictly better quality than {@code clicked}.
     */
    private static void buildDeckContextSwapItems(
            CardElement clicked,
            List<CardElement> allOwnedCopies,
            OwnedCardsCollection owned,
            Node anchorNode,
            List<MenuItem> items) {

        String elementName = elementNameFromAnchor(anchorNode);
        if (elementName == null || elementName.trim().isEmpty()) {
            return;
        }

        List<CardElement> upgradeCandidates =
                CardQualityService.findOwnedUpgradeCandidates(clicked, elementName);
        java.util.IdentityHashMap<CardElement, Boolean> upgradeSet =
                new java.util.IdentityHashMap<>();
        for (CardElement upgradeCandidate : upgradeCandidates) {
            upgradeSet.put(upgradeCandidate, Boolean.TRUE);
        }

        for (CardElement candidate : allOwnedCopies) {
            if (candidate == clicked) {
                continue;
            }
            CardsGroup candidateGroup = findGroupOf(owned, candidate);
            int groupIndex = findIndexInOwnerGroup(owned, candidate);
            String groupName = (candidateGroup != null) ? candidateGroup.getName() : null;
            boolean isUpgrade = upgradeSet.containsKey(candidate);
            String locationSuffix = (groupName != null && !groupName.trim().isEmpty())
                    ? " in " + groupName : "";
            String label = buildSwapCandidateLabel(candidate)
                    + locationSuffix
                    + (groupIndex > 0 ? " #" + groupIndex : "");

            MenuItem menuItem = new MenuItem(label);
            menuItem.setStyle(isUpgrade ? "-fx-text-fill: #EB9E34;" : "-fx-text-fill: #cdfc04;");
            final CardElement finalCandidate = candidate;
            menuItem.setOnAction(event ->
                    MenuActionHandler.handleSwap(finalCandidate, clicked));
            items.add(menuItem);
        }
    }

    /**
     * Builds swap items for the My Collection context: every owned copy is
     * offered; orange = candidate sits in a deck-named group and {@code clicked}
     * is a quality upgrade for that deck.
     */
    private static void buildCollectionContextSwapItems(
            CardElement clicked,
            List<CardElement> allOwnedCopies,
            OwnedCardsCollection owned,
            DecksAndCollectionsList dac,
            List<MenuItem> items) {

        for (CardElement candidate : allOwnedCopies) {
            if (candidate == clicked) {
                continue;
            }
            CardsGroup candidateGroup = findGroupOf(owned, candidate);
            int groupIndex = findIndexInOwnerGroup(owned, candidate);
            String groupName = (candidateGroup != null) ? candidateGroup.getName() : null;

            boolean isUpgrade = false;
            if (dac != null && groupName != null && !groupName.trim().isEmpty()) {
                String dcName = findDcNameForGroup(dac, groupName);
                if (dcName != null) {
                    List<CardElement> upgradeCandidates =
                            CardQualityService.findOwnedUpgradeCandidates(candidate, dcName);
                    for (CardElement upgradeCandidate : upgradeCandidates) {
                        if (upgradeCandidate == clicked) {
                            isUpgrade = true;
                            break;
                        }
                    }
                }
            }

            String locationSuffix = (groupName != null && !groupName.trim().isEmpty())
                    ? " in " + groupName : "";
            String label = buildSwapCandidateLabel(candidate)
                    + locationSuffix
                    + (groupIndex > 0 ? " #" + groupIndex : "");

            MenuItem menuItem = new MenuItem(label);
            menuItem.setStyle(isUpgrade ? "-fx-text-fill: #EB9E34;" : "-fx-text-fill: #cdfc04;");
            final CardElement finalCandidate = candidate;
            menuItem.setOnAction(event ->
                    MenuActionHandler.handleSwapOwned(clicked, finalCandidate));
            items.add(menuItem);
        }
    }

    // =========================================================================
    // Static helpers (no instance state required)
    // =========================================================================

    /**
     * Returns the {@link CardsGroup} that physically contains {@code element}
     * in the owned collection (reference identity), or {@code null} if not found.
     */
    static CardsGroup findGroupOf(OwnedCardsCollection owned, CardElement element) {
        if (owned == null || element == null) {
            return null;
        }
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                for (CardElement candidate : group.getCardList()) {
                    if (candidate == element) {
                        return group;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the canonical deck or collection name whose sanitized lowercase
     * name matches {@code groupName}, or {@code null} if none matches.
     * Used to decide whether a swap candidate in a named group deserves orange
     * colouring (i.e. it sits in a group named like a known deck or collection).
     */
    static String findDcNameForGroup(DecksAndCollectionsList dac, String groupName) {
        if (dac == null || groupName == null) {
            return null;
        }
        String normalizedGroup = sanitize(groupName).toLowerCase();
        if (dac.getCollections() != null) {
            for (ThemeCollection tc : dac.getCollections()) {
                if (tc == null) {
                    continue;
                }
                if (sanitize(tc.getName()).toLowerCase().equals(normalizedGroup)) {
                    return tc.getName();
                }
                if (tc.getLinkedDecks() != null) {
                    for (List<Deck> unit : tc.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            if (sanitize(deck.getName()).toLowerCase().equals(normalizedGroup)) {
                                return deck.getName();
                            }
                        }
                    }
                }
            }
        }
        if (dac.getDecks() != null) {
            for (Deck deck : dac.getDecks()) {
                if (deck == null) {
                    continue;
                }
                if (sanitize(deck.getName()).toLowerCase().equals(normalizedGroup)) {
                    return deck.getName();
                }
            }
        }
        return null;
    }

    /**
     * Returns the 1-based position of {@code element} within its
     * {@link CardsGroup} in the owned collection, or {@code -1} if not found.
     * Uses reference identity ({@code ==}) to locate the element.
     */
    static int findIndexInOwnerGroup(OwnedCardsCollection owned, CardElement element) {
        if (owned == null || element == null) {
            return -1;
        }
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                List<CardElement> groupList = group.getCardList();
                for (int index = 0; index < groupList.size(); index++) {
                    if (groupList.get(index) == element) {
                        return index + 1;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Builds a short human-readable label describing {@code element}'s
     * condition and rarity, e.g. {@code "Near Mint / Ultra Rare"}.
     * Falls back to {@code "Copy in collection"} when both are absent.
     */
    public static String buildSwapCandidateLabel(CardElement element) {
        if (element == null) {
            return "(unknown)";
        }
        StringBuilder label = new StringBuilder();
        if (element.getCondition() != null) {
            label.append(element.getCondition().getDisplayName());
        }
        if (element.getRarity() != null) {
            if (label.length() > 0) {
                label.append(" / ");
            }
            label.append(element.getRarity().getDisplayName());
        }
        return label.length() > 0 ? label.toString() : "Copy in collection";
    }

    /**
     * Builds a parenthesised suffix describing {@code element}'s condition
     * and rarity, e.g. {@code " (Near Mint / Ultra Rare)"}.
     * Returns an empty string when both are absent.
     */
    public static String buildSlotConditionSuffix(CardElement element) {
        if (element == null) {
            return "";
        }
        StringBuilder suffix = new StringBuilder(" (");
        if (element.getCondition() != null) {
            suffix.append(element.getCondition().getDisplayName());
        }
        if (element.getRarity() != null) {
            if (suffix.length() > 2) {
                suffix.append(" / ");
            }
            suffix.append(element.getRarity().getDisplayName());
        }
        if (suffix.length() == 2) {
            return "";
        }
        suffix.append(")");
        return suffix.toString();
    }

    // =========================================================================
    // Scene-graph helper
    // =========================================================================

    /**
     * Walks up the scene-graph from {@code anchor} looking for a
     * {@link GridView} whose {@code userData} is a {@link Map} containing an
     * {@code "elementName"} key.  Returns that string, or {@code null} if not
     * found.
     *
     * <p>This is the D&amp;C context element-name needed to decide which owned
     * copies are quality upgrades.</p>
     */
    static String elementNameFromAnchor(Node anchor) {
        if (anchor == null) {
            return null;
        }
        try {
            Node current = anchor;
            while (current != null) {
                if (current instanceof GridView) {
                    Object userData = ((GridView<?>) current).getUserData();
                    if (userData instanceof Map) {
                        Object elementName = ((Map<?, ?>) userData).get("elementName");
                        if (elementName instanceof String) {
                            return (String) elementName;
                        }
                    }
                }
                current = current.getParent();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // =========================================================================
    // Private utility
    // =========================================================================

    /**
     * Returns the {@link CardsGroup} that contains {@code element}, searching
     * the D&amp;C section registry first (fast path) and then the owned
     * collection (slow path).
     */
    private static CardsGroup getParentGroup(CardElement element) {
        if (element == null) {
            return null;
        }
        try {
            for (Map<String, CardsGroup> sections :
                    CardGroupRegistry.DECK_SECTION_GROUPS.values()) {
                if (sections == null) {
                    continue;
                }
                for (CardsGroup group : sections.values()) {
                    if (group != null
                            && group.getCardList() != null
                            && group.getCardList().contains(element)) {
                        return group;
                    }
                }
            }
            OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
            if (owned != null) {
                for (Box box : owned.getOwnedCollection()) {
                    if (box == null) {
                        continue;
                    }
                    for (CardsGroup group : box.getContent()) {
                        if (group != null
                                && group.getCardList() != null
                                && group.getCardList().contains(element)) {
                            return group;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}