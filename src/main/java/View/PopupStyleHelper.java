package View;

import javafx.scene.control.Button;
import javafx.scene.control.Separator;

/**
 * Styling helpers shared by the popup dialogs ({@link ArchetypeCardSelectionPopup},
 * {@link CardEditPopup}) for their accent-colored separators and action buttons.
 */
public final class PopupStyleHelper {

    private PopupStyleHelper() {
    }

    /**
     * Creates a thin separator tinted with the given accent color.
     *
     * @param accentColor the accent color to tint the separator with
     */
    public static Separator makeSeparator(String accentColor) {
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: " + accentColor + "; -fx-opacity: 0.35;");
        return separator;
    }

    /**
     * Creates a themed action button with hover styling and, for primary buttons,
     * default-button behavior (activates on Enter).
     *
     * @param text        the button label
     * @param isPrimary   whether this is the primary (e.g. "OK") action button
     * @param accentColor the accent color used for text and border
     */
    public static Button makeButton(String text, boolean isPrimary, String accentColor) {
        Button button = new Button(text);
        String baseStyle =
                "-fx-background-color: " + (isPrimary ? "#2a0560" : "#1e0530") + ";" +
                        "-fx-text-fill: " + accentColor + ";" +
                        "-fx-font-size: 12px; -fx-font-weight: bold;" +
                        "-fx-border-color: " + accentColor + "; -fx-border-width: 1;" +
                        "-fx-border-radius: 3; -fx-background-radius: 3;" +
                        "-fx-cursor: hand; -fx-padding: 5 18 5 18;";
        button.setStyle(baseStyle);
        button.setOnMouseEntered(event -> button.setStyle(baseStyle.replace(
                isPrimary ? "#2a0560" : "#1e0530", "#3d0880")));
        button.setOnMouseExited(event -> button.setStyle(baseStyle));
        if (isPrimary) {
            button.setDefaultButton(true);
        }
        return button;
    }
}