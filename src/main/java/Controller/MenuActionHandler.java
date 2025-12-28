package Controller; // adapt to your package

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized handler for context-menu actions (Move, Add, Swap).
 * Currently implements Move. Add and Swap are left as placeholders.
 */
public final class MenuActionHandler {
    private static final Logger logger = LoggerFactory.getLogger(MenuActionHandler.class);

    private MenuActionHandler() { /* static utility */ }

    /**
     * Public entry point for "move" menu items.
     *
     * @param clickedElement the CardElement that was clicked (may be null)
     * @param handlerTarget  canonical target string (e.g. "Box", "Box/Group", "Deck/Main Deck")
     */
    public static void handleMove(Model.CardsLists.CardElement clickedElement, String handlerTarget) {
        if (handlerTarget == null || handlerTarget.trim().isEmpty()) return;
        try {
            // Run on FX thread if you need immediate UI updates; otherwise run directly.
            if (Platform.isFxApplicationThread()) {
                doMove(clickedElement, handlerTarget);
            } else {
                Platform.runLater(() -> doMove(clickedElement, handlerTarget));
            }
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable t) {
            logger.debug("handleMove failed for target {}", handlerTarget, t);
        }
    }

    // --- Core move implementation (private) ---
    private static void doMove(Model.CardsLists.CardElement clickedElement, String handlerTarget) {
        // 1) obtain shared owned collection (load if necessary)
        Model.CardsLists.OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection not available; cannot move card to {}", handlerTarget);
            return;
        }

        // 2) find source element (prefer exact instance)
        SourceLocation src = findSource(clickedElement, owned);

        // 3) find destination group
        Destination dest = findDestinationGroup(handlerTarget, owned);
        if (dest == null || dest.group == null) {
            logger.info("Destination not found for '{}'; skipping move", handlerTarget);
            return;
        }

        // 4) remove from source if found
        if (src != null && src.group != null && src.index >= 0) {
            try {
                List<Model.CardsLists.CardElement> srcList = src.group.getCardList();
                if (srcList != null) {
                    if (src.index < srcList.size() && srcList.get(src.index) == src.element) {
                        srcList.remove(src.index);
                    } else {
                        srcList.remove(src.element);
                    }
                }
            } catch (Throwable ex) {
                logger.debug("Failed to remove element from source; continuing", ex);
            }
        }

        // 5) prepare element to add
        Model.CardsLists.CardElement toAdd = src != null ? src.element : null;
        if (toAdd == null && clickedElement != null && clickedElement.getCard() != null) {
            toAdd = constructCardElementFallback(clickedElement);
        }
        if (toAdd == null) {
            logger.warn("No CardElement available to add for target {}", handlerTarget);
            return;
        }

        // 6) add to destination
        try {
            List<Model.CardsLists.CardElement> destList = dest.group.getCardList();
            if (destList == null) {
                // try to initialize via setter if available
                try {
                    List<Model.CardsLists.CardElement> newList = new ArrayList<>();
                    newList.add(toAdd);
                    Method setList = dest.group.getClass().getMethod("setCardList", List.class);
                    setList.invoke(dest.group, newList);
                } catch (Throwable ex) {
                    logger.debug("Could not initialize destination list via reflection", ex);
                    return;
                }
            } else {
                destList.add(toAdd);
            }
        } catch (Throwable ex) {
            logger.debug("Failed to add element to destination", ex);
            return;
        }
    }

    // --- Helpers ---
    private static Model.CardsLists.OwnedCardsCollection safeGetOwnedCollection() {
        Model.CardsLists.OwnedCardsCollection owned = null;
        try {
            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (owned == null) {
            try {
                Controller.UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        return owned;
    }

    private static SourceLocation findSource(Model.CardsLists.CardElement clickedElement, Model.CardsLists.OwnedCardsCollection owned) {
        if (owned == null || owned.getOwnedCollection() == null) return null;
        if (clickedElement != null) {
            // try exact instance
            outer:
            for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                if (b == null || b.getContent() == null) continue;
                for (Model.CardsLists.CardsGroup g : b.getContent()) {
                    if (g == null || g.getCardList() == null) continue;
                    List<Model.CardsLists.CardElement> list = g.getCardList();
                    for (int i = 0; i < list.size(); i++) {
                        Model.CardsLists.CardElement ce = list.get(i);
                        if (ce == clickedElement) return new SourceLocation(b, g, ce, i);
                    }
                }
            }
            // fallback: match by card identity
            Model.CardsLists.Card targetCard = clickedElement.getCard();
            if (targetCard != null) {
                outer2:
                for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                    if (b == null || b.getContent() == null) continue;
                    for (Model.CardsLists.CardsGroup g : b.getContent()) {
                        if (g == null || g.getCardList() == null) continue;
                        List<Model.CardsLists.CardElement> list = g.getCardList();
                        for (int i = 0; i < list.size(); i++) {
                            Model.CardsLists.CardElement ce = list.get(i);
                            if (ce != null && cardsMatch(ce.getCard(), targetCard)) {
                                return new SourceLocation(b, g, ce, i);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Model.CardsLists.CardElement constructCardElementFallback(Model.CardsLists.CardElement clickedElement) {
        if (clickedElement == null) return null;
        try {
            // try direct constructor new CardElement(Card)
            try {
                Constructor<?> ctor = Class.forName("Model.CardsLists.CardElement").getConstructor(Model.CardsLists.Card.class);
                return (Model.CardsLists.CardElement) ctor.newInstance(clickedElement.getCard());
            } catch (Throwable ignored) {
            }
            // fallback: default ctor + setter
            try {
                Class<?> ceClass = Class.forName("Model.CardsLists.CardElement");
                Object ceObj = ceClass.getDeclaredConstructor().newInstance();
                try {
                    Method setCard = ceClass.getMethod("setCard", Model.CardsLists.Card.class);
                    setCard.invoke(ceObj, clickedElement.getCard());
                } catch (Throwable ignored) {
                }
                return (Model.CardsLists.CardElement) ceObj;
            } catch (Throwable ex) {
                logger.debug("Reflection fallback failed constructing CardElement", ex);
            }
        } catch (Throwable t) {
            logger.debug("constructCardElementFallback failed", t);
        }
        return null;
    }

    private static boolean cardsMatch(Card a, Card b) {
        if (a == null || b == null) return false;
        try {
            // best-effort identity: compare Konami id, pass, print or other available fields
            if (a.getKonamiId() != null && b.getKonamiId() != null && a.getKonamiId().equals(b.getKonamiId()))
                return true;
            if (a.getPassCode() != null && b.getPassCode() != null && a.getPassCode().equals(b.getPassCode()))
                return true;
            if (a.getPrintCode() != null && b.getPrintCode() != null && a.getPrintCode().equals(b.getPrintCode()))
                return true;
        } catch (Throwable ignored) {
        }
        // fallback to equals
        return a.equals(b);
    }

    // Destination finder similar to earlier logic
    // Destination finder similar to earlier logic
    private static Destination findDestinationGroup(String handlerTarget, OwnedCardsCollection owned) {
        if (handlerTarget == null || handlerTarget.trim().isEmpty() || owned == null || owned.getOwnedCollection() == null) {
            return null;
        }

        // normalize helper (accent-insensitive, case-insensitive, collapse spaces)
        java.util.function.Function<String, String> norm = s -> {
            if (s == null) return "";
            String t = s.trim().replaceAll("\\s+", " ");
            t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            t = t.replaceAll("[\\p{Punct}]+", " ");
            return t.toLowerCase().trim();
        };

        String[] parts = handlerTarget.split("/");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();

        // detect if last component is a deck list name (Main Deck / Extra Deck / Side Deck)
        String last = parts[parts.length - 1];
        String lastNorm = norm.apply(last);
        boolean isDeckList = lastNorm.equals("main deck") || lastNorm.equals("extra deck") || lastNorm.equals("side deck")
                || lastNorm.equals("maindeck") || lastNorm.equals("extradeck") || lastNorm.equals("sidedeck")
                || lastNorm.equals("main") || lastNorm.equals("extra") || lastNorm.equals("side");

        // Helper: search owned boxes/groups by normalized box name and optional group name.
        // IMPORTANT: if groupName is provided (non-empty), do NOT fallback to the first group of the box.
        java.util.function.BiFunction<String, String, Destination> findBoxAndGroupByBoxThenGroup = (boxName, groupName) -> {
            String bNorm = norm.apply(boxName);
            String gNorm = groupName == null ? "" : norm.apply(groupName);
            for (Box b : owned.getOwnedCollection()) {
                if (b == null) continue;
                if (norm.apply(b.getName()).equals(bNorm)) {
                    if (b.getContent() != null) {
                        // If a group name was provided, require an exact group match
                        if (!gNorm.isEmpty()) {
                            for (CardsGroup g : b.getContent()) {
                                if (g == null) continue;
                                if (norm.apply(g.getName()).equals(gNorm)) return new Destination(b, g);
                            }
                            // explicit group requested but not found -> do not return a fallback group
                            return null;
                        }
                        // No group requested: prefer a box-level group (empty name) if present
                        for (CardsGroup g : b.getContent()) {
                            if (g == null) continue;
                            String gName = g.getName();
                            if (gName == null || gName.trim().isEmpty()) {
                                return new Destination(b, g);
                            }
                        }
                        // If no box-level group exists, create one and add it to the box content (safe via reflection)
                        try {
                            Class<?> cgClass = Class.forName("Model.CardsLists.CardsGroup");
                            Object newGroupObj = null;
                            try {
                                // try no-arg constructor first
                                newGroupObj = cgClass.getDeclaredConstructor().newInstance();
                            } catch (NoSuchMethodException ns) {
                                // try constructor with String name if available
                                try {
                                    java.lang.reflect.Constructor<?> ctor = cgClass.getDeclaredConstructor(String.class);
                                    ctor.setAccessible(true);
                                    newGroupObj = ctor.newInstance("");
                                } catch (NoSuchMethodException ignored) {
                                    // give up creating via reflection
                                    newGroupObj = null;
                                }
                            }
                            if (newGroupObj != null) {
                                // try to set name to empty string if setter exists
                                try {
                                    java.lang.reflect.Method setName = cgClass.getMethod("setName", String.class);
                                    setName.invoke(newGroupObj, "");
                                } catch (Throwable ignored) {
                                }
                                // try to initialize card list if setter exists
                                try {
                                    java.lang.reflect.Method setCardList = cgClass.getMethod("setCardList", java.util.List.class);
                                    setCardList.invoke(newGroupObj, new java.util.ArrayList<Model.CardsLists.CardElement>());
                                } catch (Throwable ignored) {
                                }
                                // add to box content
                                try {
                                    b.getContent().add((CardsGroup) newGroupObj);
                                    return new Destination(b, (CardsGroup) newGroupObj);
                                } catch (ClassCastException cc) {
                                    // If cast fails, ignore and continue fallback below
                                }
                            }
                        } catch (Throwable t) {
                            // reflection failed; fall back to returning first group if present
                        }

                        // Fallback: return the first group if present (legacy behavior)
                        if (!b.getContent().isEmpty()) return new Destination(b, b.getContent().get(0));
                    }
                }
            }
            return null;
        };

        // 1) If target points to a deck list (e.g., "Collection/Deck/Main Deck" or "Deck/Main Deck")
        if (isDeckList) {
            // deck name is the component before the last one
            String deckName = parts.length >= 2 ? parts[parts.length - 2] : null;
            String collectionName = parts.length >= 3 ? parts[0] : null; // if present

            // Try: if collectionName present, find the collection in DecksAndCollectionsList and then the deck inside it
            DecksAndCollectionsList dac = null;
            try {
                dac = UserInterfaceFunctions.getDecksList();
            } catch (Throwable ignored) {
            }
            if (dac != null && collectionName != null && deckName != null) {
                String collNorm = norm.apply(collectionName);
                String deckNorm = norm.apply(deckName);
                if (dac.getCollections() != null) {
                    for (ThemeCollection tc : dac.getCollections()) {
                        if (tc == null) continue;
                        if (norm.apply(tc.getName()).equals(collNorm)) {
                            if (tc.getLinkedDecks() != null) {
                                for (List<Deck> unit : tc.getLinkedDecks()) {
                                    if (unit == null) continue;
                                    for (Deck d : unit) {
                                        if (d == null) continue;
                                        if (norm.apply(d.getName()).equals(deckNorm)) {
                                            // Prefer Box = collectionName, Group = deckName/listName (require exact group match)
                                            Destination dest = findBoxAndGroupByBoxThenGroup.apply(tc.getName(), deckName);
                                            if (dest != null) {
                                                // try to find the specific list group (Main/Extra/Side) inside that box
                                                String listName = last;
                                                String listNorm = norm.apply(listName);
                                                if (dest.box.getContent() != null) {
                                                    for (CardsGroup g : dest.box.getContent()) {
                                                        if (g == null) continue;
                                                        if (norm.apply(g.getName()).equals(listNorm))
                                                            return new Destination(dest.box, g);
                                                    }
                                                }
                                                // If deckName matched a group and list not found, do not fallback to another group
                                                return dest;
                                            }
                                            // fallback: try Box = deckName, Group = listName (require exact group match)
                                            Destination dest2 = findBoxAndGroupByBoxThenGroup.apply(deckName, last);
                                            if (dest2 != null) return dest2;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2) If not found via collection, try top-level decks (not in collections)
            if (dac != null && deckName != null && dac.getDecks() != null) {
                String deckNorm = norm.apply(deckName);
                for (Deck d : dac.getDecks()) {
                    if (d == null) continue;
                    if (norm.apply(d.getName()).equals(deckNorm)) {
                        // try to find Box named like deckName and group named like list (require exact group match)
                        Destination dest = findBoxAndGroupByBoxThenGroup.apply(d.getName(), last);
                        if (dest != null) return dest;
                        // fallback: find any group named like list across owned (exact match)
                        String listNorm = norm.apply(last);
                        for (Box b : owned.getOwnedCollection()) {
                            if (b == null || b.getContent() == null) continue;
                            for (CardsGroup g : b.getContent()) {
                                if (g == null) continue;
                                if (norm.apply(g.getName()).equals(listNorm)) return new Destination(b, g);
                            }
                        }
                    }
                }
            }

            // 3) Generic fallback: try to find a group whose name matches the deckName or the list name (exact match)
            if (deckName != null) {
                String deckNorm = norm.apply(deckName);
                for (Box b : owned.getOwnedCollection()) {
                    if (b == null || b.getContent() == null) continue;
                    for (CardsGroup g : b.getContent()) {
                        if (g == null) continue;
                        String gNorm = norm.apply(g.getName());
                        if (gNorm.equals(deckNorm) || gNorm.equals(lastNorm)) return new Destination(b, g);
                    }
                }
            }

            // 4) permissive: match last component (list) anywhere (exact match)
            for (Box b : owned.getOwnedCollection()) {
                if (b == null || b.getContent() == null) continue;
                for (CardsGroup g : b.getContent()) {
                    if (g == null) continue;
                    if (norm.apply(g.getName()).equals(lastNorm)) return new Destination(b, g);
                }
            }

            // nothing found for deck target
            return null;
        }

        // 2) If not a deck list, treat as a collection or a box/group target.
        // Try exact Box name -> return its box-level group (preferred) or first group only when no group was requested
        String comp0 = parts[0];
        String comp0Norm = norm.apply(comp0);
        for (Box b : owned.getOwnedCollection()) {
            if (b == null) continue;
            if (norm.apply(b.getName()).equals(comp0Norm)) {
                if (parts.length == 1) {
                    if (b.getContent() != null && !b.getContent().isEmpty()) {
                        // Prefer a box-level group (empty name) if present, otherwise create one or fallback to first group
                        // Use helper to find or create box-level group
                        Destination dest = findBoxAndGroupByBoxThenGroup.apply(b.getName(), "");
                        if (dest != null) return dest;
                    }
                } else {
                    // a group was requested; require exact group match
                    String requestedGroup = parts[parts.length - 1];
                    if (b.getContent() != null) {
                        for (CardsGroup g : b.getContent()) {
                            if (g == null) continue;
                            if (norm.apply(g.getName()).equals(norm.apply(requestedGroup)))
                                return new Destination(b, g);
                        }
                    }
                }
            }
        }

        // Try to match a group name equal to the first component across all boxes (exact match)
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                if (norm.apply(g.getName()).equals(comp0Norm)) return new Destination(b, g);
            }
        }

        // Try matching any component as a group name (useful for "Collection/Deck" where deck is a group)
        for (String part : parts) {
            String pNorm = norm.apply(part);
            if (pNorm.isEmpty()) continue;
            for (Box b : owned.getOwnedCollection()) {
                if (b == null || b.getContent() == null) continue;
                for (CardsGroup g : b.getContent()) {
                    if (g == null) continue;
                    if (norm.apply(g.getName()).equals(pNorm)) return new Destination(b, g);
                }
            }
        }

        // final permissive fallback: partial contains match on last component
        String lastNorm2 = norm.apply(parts[parts.length - 1]);
        if (!lastNorm2.isEmpty()) {
            for (Box b : owned.getOwnedCollection()) {
                if (b == null || b.getContent() == null) continue;
                for (CardsGroup g : b.getContent()) {
                    if (g == null) continue;
                    String gNorm = norm.apply(g.getName());
                    if (gNorm.contains(lastNorm2) || lastNorm2.contains(gNorm)) return new Destination(b, g);
                }
            }
        }

        // nothing found
        return null;
    }

    // small holders
    private static final class SourceLocation {
        final Model.CardsLists.Box box;
        final Model.CardsLists.CardsGroup group;
        final Model.CardsLists.CardElement element;
        final int index;

        SourceLocation(Model.CardsLists.Box box, Model.CardsLists.CardsGroup group, Model.CardsLists.CardElement element, int index) {
            this.box = box;
            this.group = group;
            this.element = element;
            this.index = index;
        }
    }

    private static final class Destination {
        final Model.CardsLists.Box box;
        final Model.CardsLists.CardsGroup group;

        Destination(Model.CardsLists.Box box, Model.CardsLists.CardsGroup group) {
            this.box = box;
            this.group = group;
        }
    }
}