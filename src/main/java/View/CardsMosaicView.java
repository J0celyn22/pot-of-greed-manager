package View;

import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;

import java.util.List;
import java.util.Set;

/**
 * Mosaic view of cards.
 * Programmatic scrollbar styling and robust debug logging for ancestor ScrollPane.
 */
public class CardsMosaicView extends FlowPane {

    /**
     * Constructs a mosaic view.
     *
     * @param cards      the list of Card objects to display
     * @param cellWidth  the desired width for each card image
     * @param cellHeight the desired height for each card image
     */
    public CardsMosaicView(List<Card> cards, double cellWidth, double cellHeight) {
        setHgap(5);
        setVgap(5);
        setPadding(new Insets(5));
        this.getStyleClass().add("cards-mosaic-view");

        for (Card card : cards) {
            Image image = LruImageCache.getImage(card.getImagePath());
            if (image == null) {
                image = new Image("file:./src/main/resources/placeholder.jpg");
            }
            ImageView iv = new ImageView(image);
            iv.setFitWidth(cellWidth);
            iv.setFitHeight(cellHeight);
            iv.setPreserveRatio(true);
            getChildren().add(iv);
        }

        // Programmatically style the ancestor ScrollPane's scrollbars and attach debug logging
        Platform.runLater(this::styleAncestorScrollPane);
    }

    /**
     * Find the nearest ancestor ScrollPane and apply inline styles to its scrollbar parts.
     * This is a pragmatic, immediate fix that bypasses CSS selector issues.
     */
    private void styleAncestorScrollPane() {
        try {
            Node current = this;
            while (current != null && !(current instanceof ScrollPane)) {
                current = current.getParent();
            }
            if (current instanceof ScrollPane) {
                applyStylesToScrollPane((ScrollPane) current);
                attachScrollPaneDebug((ScrollPane) current, "CardsMosaicViewScrollPane");
            }
        } catch (Exception ignored) {
            // best-effort; do not crash the UI
        }
    }

    private void applyStylesToScrollPane(ScrollPane sp) {
        if (sp == null) return;
        // Ensure skin created before lookup
        Platform.runLater(() -> {
            try {
                Set<Node> bars = sp.lookupAll(".scroll-bar");
                bars.addAll(sp.lookupAll(".overlay-scroll-bar"));
                for (Node bar : bars) {
                    // base container
                    bar.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-background-image: null; " +
                                    "-fx-padding: 0;"
                    );

                    // track
                    Node track = bar.lookup(".track");
                    if (track != null) {
                        track.setStyle(
                                "-fx-background-color: #100317; " +
                                        "-fx-background-image: null; " +
                                        "-fx-background-insets: 0; " +
                                        "-fx-background-radius: 4;"
                        );
                    }

                    // thumb
                    Node thumb = bar.lookup(".thumb");
                    if (thumb != null) {
                        thumb.setStyle(
                                "-fx-background-color: #cdfc04; " +
                                        "-fx-background: #cdfc04; " +
                                        "-fx-background-image: null; " +
                                        "-fx-background-insets: 2; " +
                                        "-fx-background-radius: 6; " +
                                        "-fx-pref-width: 10; " +
                                        "-fx-pref-height: 24; " +
                                        "-fx-opacity: 1; " +
                                        "-fx-effect: null;"
                        );
                    }

                    // increment / decrement buttons and arrow regions
                    Node inc = bar.lookup(".increment-button");
                    Node dec = bar.lookup(".decrement-button");
                    if (inc != null) {
                        inc.setStyle("-fx-background-color: #100317; -fx-background-image: null; -fx-padding: 2; -fx-background-radius: 4;");
                        Node incArrow = inc.lookup(".increment-arrow");
                        if (incArrow == null) incArrow = inc.lookup(".arrow");
                        if (incArrow != null) incArrow.setStyle("-fx-background-color: #cdfc04;");
                    }
                    if (dec != null) {
                        dec.setStyle("-fx-background-color: #100317; -fx-background-image: null; -fx-padding: 2; -fx-background-radius: 4;");
                        Node decArrow = dec.lookup(".decrement-arrow");
                        if (decArrow == null) decArrow = dec.lookup(".arrow");
                        if (decArrow != null) decArrow.setStyle("-fx-background-color: #cdfc04;");
                    }
                }
            } catch (Exception ignored) {
                // best-effort
            }
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
