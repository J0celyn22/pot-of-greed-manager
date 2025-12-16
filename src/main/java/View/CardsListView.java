package View;

import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Set;

/**
 * List view of cards.
 * Adds a pragmatic programmatic scrollbar styling step and robust debug logging:
 * when attached, it finds the nearest ancestor ScrollPane and applies inline styles
 * and attaches debug listeners so the UI matches the app theme and logs clicks.
 */
public class CardsListView extends VBox {

    /**
     * Constructs a list view of cards.
     *
     * @param cards       the list of Card objects to display
     * @param printedMode if true, the view is for the AllPrintedCardsList (show native name and print code),
     *                    else for the AllCardsList (show pass code)
     * @param imageWidth  the desired width for each card image
     * @param imageHeight the desired height for each card image
     */
    public CardsListView(List<Card> cards, boolean printedMode, double imageWidth, double imageHeight) {
        setSpacing(5);
        setPadding(new Insets(5));
        this.getStyleClass().add("cards-list-view");

        if (cards.isEmpty()) {
            Label noCard = new Label("No card found");
            noCard.setStyle("-fx-text-fill: white; -fx-font-size: 16;");
            getChildren().add(noCard);
        } else {
            for (Card card : cards) {
                HBox cell = createCardCell(card, printedMode, imageWidth, imageHeight);
                getChildren().add(cell);
            }
        }

        // Programmatic fix: style ancestor ScrollPane scrollbars (best-effort) and attach debug logging
        Platform.runLater(this::styleAncestorScrollPane);
    }

    private HBox createCardCell(Card card, boolean printedMode, double imageWidth, double imageHeight) {
        HBox cell = new HBox(10);
        cell.setPadding(new Insets(5));
        cell.setStyle("-fx-border-color: white; -fx-border-width: 1; -fx-border-radius: 5; " +
                "-fx-background-radius: 5;");

        // Left: card image
        Image image = LruImageCache.getImage(card.getImagePath());
        if (image == null) {
            image = new Image("file:./src/main/resources/placeholder.jpg");
        }
        ImageView iv = new ImageView(image);
        iv.setFitWidth(imageWidth);
        iv.setFitHeight(imageHeight);
        iv.setPreserveRatio(true);

        // Right: card details
        VBox info = new VBox(5);
        Label nameEnglish = new Label(card.getName_EN());
        nameEnglish.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
        info.getChildren().add(nameEnglish);

        if (printedMode) {
            Label nativeName = new Label();
            nativeName.setText(card.getName_EN());
            nativeName.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
            info.getChildren().add(nativeName);
        }

        String codeText = printedMode ? "PrintCode: " + card.getPrintCode() : "PassCode: " + card.getPassCode();
        Label codeLabel = new Label(codeText);
        codeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        info.getChildren().add(codeLabel);

        cell.getChildren().addAll(iv, info);
        return cell;
    }

    /**
     * Find the nearest ancestor ScrollPane and apply inline styles to its scrollbar parts.
     */
    private void styleAncestorScrollPane() {
        try {
            Node current = this;
            while (current != null && !(current instanceof ScrollPane)) {
                current = current.getParent();
            }
            if (current instanceof ScrollPane) {
                applyStylesToScrollPane((ScrollPane) current);
                // Attach debug logging to the same scroll pane
                attachScrollPaneDebug((ScrollPane) current, "CardsListViewScrollPane");
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void applyStylesToScrollPane(ScrollPane sp) {
        if (sp == null) return;

        // Add the style class that we'll target in CSS
        sp.getStyleClass().add("all-cards-scroll-pane");

        // This ensures the viewport is transparent
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // This ensures the content area is transparent if present
        Node viewport = sp.lookup(".viewport");
        if (viewport != null) {
            viewport.setStyle("-fx-background-color: transparent;");
        }

        // Force a CSS update
        Platform.runLater(() -> {
            sp.applyCss();
            sp.layout();
        });
    }

    // -------------------------
    // Debug helpers (copied locally so this class is self-contained)
    // -------------------------

    private void attachScrollPaneDebug(ScrollPane sp, String name) {
        if (sp == null) return;

        sp.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(() -> {
            logScrollPaneInfo(sp, name);
            attachListenersToBarsWithRetry(sp, name, 0);
        }));

        Platform.runLater(() -> {
            logScrollPaneInfo(sp, name);
            attachListenersToBarsWithRetry(sp, name, 0);
        });

        sp.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            System.out.println("DEBUG: Clicked ScrollPane: " + name);
            logScrollPaneInfo(sp, name);
            attachListenersToBarsWithRetry(sp, name, 0);
        });
    }

    private void attachListenersToBarsWithRetry(ScrollPane sp, String name, int attempt) {
        final int MAX_ATTEMPTS = 6;
        final long RETRY_DELAY_MS = 120;

        try {
            Set<Node> bars = sp.lookupAll(".scroll-bar");
            bars.addAll(sp.lookupAll(".overlay-scroll-bar"));

            if (bars.isEmpty() && attempt < MAX_ATTEMPTS) {
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ignored) {
                    }
                    attachListenersToBarsWithRetry(sp, name, attempt + 1);
                });
                return;
            }

            for (Node bar : bars) {
                attachListenersToSingleBar(bar, name);
            }

            Set<Node> overlay = sp.lookupAll(".overlay-scroll-bar");
            for (Node bar : overlay) {
                attachListenersToSingleBar(bar, name);
            }
        } catch (Exception ex) {
            System.out.println("DEBUG: attachListenersToBarsWithRetry failed: " + ex);
        }
    }

    private void attachListenersToSingleBar(Node bar, String name) {
        if (bar == null) return;
        Object already = bar.getProperties().get("debug-listener-attached");
        if (already != null) return;
        bar.getProperties().put("debug-listener-attached", Boolean.TRUE);

        bar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            System.out.println("DEBUG: MOUSE_PRESSED on scroll-bar in " + name + " -> " + nodeSummary(bar));
        });
        bar.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            System.out.println("DEBUG: MOUSE_CLICKED on scroll-bar in " + name + " -> " + nodeSummary(bar));
        });

        Node thumb = bar.lookup(".thumb");
        if (thumb != null) {
            thumb.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                System.out.println("DEBUG: MOUSE_PRESSED on thumb in " + name + " -> " + nodeSummary(thumb));
            });
            thumb.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                System.out.println("DEBUG: MOUSE_CLICKED on thumb in " + name + " -> " + nodeSummary(thumb));
            });
        }

        Node inc = bar.lookup(".increment-button");
        Node dec = bar.lookup(".decrement-button");
        if (inc != null) {
            inc.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                System.out.println("DEBUG: MOUSE_PRESSED on increment-button in " + name + " -> " + nodeSummary(inc));
            });
            inc.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                System.out.println("DEBUG: MOUSE_CLICKED on increment-button in " + name + " -> " + nodeSummary(inc));
            });
            Node incArrow = dec.lookup(".decrement-arrow");
            if (incArrow == null) {
                incArrow = dec.lookup(".arrow");
            }
            final Node finalIncArrow = incArrow;  // Create a final copy for the lambda
            if (finalIncArrow != null) {
                finalIncArrow.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    System.out.println("DEBUG: MOUSE_PRESSED on decrement-arrow in " + name + " -> " + nodeSummary(finalIncArrow));
                });
            }
        }
        if (dec != null) {
            dec.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                System.out.println("DEBUG: MOUSE_PRESSED on decrement-button in " + name + " -> " + nodeSummary(dec));
            });
            dec.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                System.out.println("DEBUG: MOUSE_CLICKED on decrement-button in " + name + " -> " + nodeSummary(dec));
            });
            Node decArrow = dec.lookup(".decrement-arrow");
            if (decArrow == null) decArrow = dec.lookup(".arrow");
            final Node finalDecArrow = decArrow;  // Create a final copy
            if (finalDecArrow != null) {
                finalDecArrow.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    System.out.println("DEBUG: MOUSE_PRESSED on decrement-arrow in " + name + " -> " + nodeSummary(finalDecArrow));
                });
            }
        }
    }

    private void logScrollPaneInfo(ScrollPane sp, String name) {
        System.out.println("=== ScrollPane debug: " + name + " ===");
        System.out.println("ScrollPane id: " + sp.getId());
        System.out.println("ScrollPane styleClass: " + sp.getStyleClass());
        for (Node child : sp.getChildrenUnmodifiable()) {
            System.out.println(" child node: " + nodeSummary(child));
        }

        Set<Node> bars = sp.lookupAll(".scroll-bar");
        System.out.println("Found .scroll-bar nodes: " + bars.size());
        int i = 0;
        for (Node bar : bars) {
            System.out.println("  scroll-bar[" + (i++) + "]: " + nodeSummary(bar));
            Node track = bar.lookup(".track");
            Node thumb = bar.lookup(".thumb");
            Node inc = bar.lookup(".increment-button");
            Node dec = bar.lookup(".decrement-button");
            System.out.println("    track: " + (track != null ? nodeSummary(track) : "null"));
            System.out.println("    thumb: " + (thumb != null ? nodeSummary(thumb) : "null"));
            System.out.println("    inc:   " + (inc != null ? nodeSummary(inc) : "null"));
            System.out.println("    dec:   " + (dec != null ? nodeSummary(dec) : "null"));
        }

        Set<Node> overlay = sp.lookupAll(".overlay-scroll-bar");
        System.out.println("Found .overlay-scroll-bar nodes: " + overlay.size());
        i = 0;
        for (Node bar : overlay) {
            System.out.println("  overlay-scroll-bar[" + (i++) + "]: " + nodeSummary(bar));
        }

        System.out.println("Full subtree (limited depth 3):");
        dumpNode(sp, 0, 3);
        System.out.println("=== End debug: " + name + " ===");
    }

    private String nodeSummary(Node n) {
        String id = n.getId();
        String classes = n.getStyleClass().toString();
        String cls = n.getClass().getSimpleName();
        return String.format("%s (id=%s classes=%s toString=%s)", cls, id, classes, n.toString());
    }

    private void dumpNode(Node node, int depth, int maxDepth) {
        if (depth > maxDepth) return;
        String indent = "  ".repeat(depth);
        System.out.println(indent + nodeSummary(node));
        if (node instanceof Parent) {
            Parent p = (Parent) node;
            for (Node child : p.getChildrenUnmodifiable()) {
                dumpNode(child, depth + 1, maxDepth);
            }
        }
    }
}
