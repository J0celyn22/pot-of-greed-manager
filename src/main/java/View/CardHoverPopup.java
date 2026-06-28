package View;

import Model.CardsLists.Card;

/**
 * Shared utility for building card hover text used by
 * {@link CardGridCell} and {@link CardsMosaicRowCell}.
 */
public final class CardHoverPopup {

    /**
     * Style string for the hover label / tooltip, matching the app theme.
     */
    public static final String LABEL_STYLE =
            "-fx-background-color: #1a0428; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: #cdfc04; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 12; " +
                    "-fx-padding: 8 10 8 10;";

    /**
     * Style string for the hover label when a quality-upgrade or quality-downgrade
     * warning is active (orange border instead of lime).
     */
    public static final String LABEL_STYLE_ORANGE =
            "-fx-background-color: #1a0428; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: #EB9E34; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 12; " +
                    "-fx-padding: 8 10 8 10;";

    /**
     * Inner VBox style (used by CardTreeCell) with orange border — applied when the
     * card is an upgrade or downgrade candidate.
     */
    public static final String POPUP_BOX_STYLE_ORANGE =
            "-fx-background-color: #1a0428; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: #EB9E34; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6;";

    /**
     * Inner VBox style (used by CardTreeCell) with the default lime border.
     */
    public static final String POPUP_BOX_STYLE_DEFAULT =
            "-fx-background-color: #1a0428; " +
                    "-fx-background-radius: 6; " +
                    "-fx-border-color: #cdfc04; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 6;";

    /**
     * Warning text appended to cards that can be sorted to a deck or collection
     * that needs them (reason 2 glow).  Single canonical string used in both
     * the archetype/explicit-missing path and the computeCardNeedsSorting path
     * so the tooltip is always identical regardless of which code path fires.
     */
    public static final String NEEDS_SORTING_WARNING =
            "This card can be sorted to a deck or collection that needs it.";

    /**
     * Warning text appended to upgrade-candidate owned cards.
     */
    public static final String UPGRADE_CANDIDATE_WARNING =
            "⬆ This copy has better condition/rarity — can replace the one in your deck or collection.";

    /**
     * Warning text appended to degraded cards inside a deck or collection.
     */
    public static final String DOWNGRADE_WARNING =
            "⬇ A better copy exists in your collection — consider swapping this one out.";

    /**
     * Warning text appended to OuicheList slots that are satisfied by a copy
     * that does not meet the condition or rarity requirement of the slot.
     * The required condition / rarity are already shown above this line by
     * {@link #buildTooltipText(Model.CardsLists.CardElement)}, so this message
     * only needs to explain that the owned copy falls short.
     */
    public static final String SUBSTANDARD_QUALITY_WARNING =
            "⚠ The copy you own does not meet the condition or rarity requirements.";

    private CardHoverPopup() {
    }

    /**
     * Builds the hover text for a card: all known identifiers and names,
     * labeled, one per line.  Returns {@code "(no data)"} when nothing is known.
     */
    public static String buildTooltipText(Card card) {
        if (card == null) return "(no data)";
        StringBuilder sb = new StringBuilder();
        if (notEmpty(card.getPrintCode()))
            sb.append("Print code : ").append(card.getPrintCode()).append("\n");
        if (notEmpty(card.getPassCode()))
            sb.append("Passcode   : ").append(card.getPassCode()).append("\n");
        if (notEmpty(card.getKonamiId()))
            sb.append("Konami ID  : ").append(card.getKonamiId()).append("\n");
        if (notEmpty(card.getName_EN()))
            sb.append("EN : ").append(card.getName_EN()).append("\n");
        if (notEmpty(card.getName_FR()))
            sb.append("FR : ").append(card.getName_FR()).append("\n");
        if (notEmpty(card.getName_JA()))
            sb.append("JA : ").append(card.getName_JA()).append("\n");
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n')
            sb.deleteCharAt(sb.length() - 1);
        return sb.length() > 0 ? sb.toString() : "(no data)";
    }

    /**
     * Builds the hover text for a {@link Model.CardsLists.CardElement}.
     *
     * <p>Starts with the same identifiers and names as
     * {@link #buildTooltipText(Card)}, then appends Condition and Rarity when
     * they are set on this specific copy.
     */
    public static String buildTooltipText(Model.CardsLists.CardElement element) {
        if (element == null) return "(no data)";
        StringBuilder sb = new StringBuilder(buildTooltipText(element.getCard()));

        Model.CardsLists.CardCondition condition = element.getCondition();
        Model.CardsLists.CardRarity rarity = element.getRarity();

        if (condition != null || rarity != null) {
            // Ensure the preceding block ended with a newline before appending.
            if (sb.length() > 0) sb.append("\n");
            if (condition != null)
                sb.append("Condition  : ").append(condition).append("\n");
            if (rarity != null)
                sb.append("Rarity     : ").append(rarity).append("\n");
            // Trim trailing newline.
            if (sb.charAt(sb.length() - 1) == '\n')
                sb.deleteCharAt(sb.length() - 1);
        }

        return sb.length() > 0 ? sb.toString() : "(no data)";
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}