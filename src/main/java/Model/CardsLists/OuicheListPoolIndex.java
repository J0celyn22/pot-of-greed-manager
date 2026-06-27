package Model.CardsLists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Indexed view over a pool of owned {@link CardElement} instances, allowing
 * artwork (imagePath) and KonamiId lookups in O(1) average instead of the
 * O(pool size) linear scans previously performed for every wanted slot.
 *
 * <p>Both indexes preserve the original pool ordering for each key, so that
 * "first eligible match" semantics are identical to the previous
 * implementation. Removing a card updates both indexes and the backing list
 * consistently.
 *
 * <p>Package-private: only {@link OuicheList} and its siblings in
 * {@code Model.CardsLists} should instantiate this class.
 */
final class OuicheListPoolIndex {

    private final List<CardElement> cards;
    private final LinkedHashMap<String, LinkedList<CardElement>> byImagePath;
    private final LinkedHashMap<String, LinkedList<CardElement>> byKonamiId;

    OuicheListPoolIndex(List<CardElement> source) {
        this.cards = new ArrayList<>(source);
        this.byImagePath = new LinkedHashMap<>();
        this.byKonamiId = new LinkedHashMap<>();
        for (CardElement card : this.cards) {
            index(card);
        }
    }

    private void index(CardElement card) {
        if (card.getCard() == null) {
            return;
        }
        String imagePath = card.getCard().getImagePath();
        if (imagePath != null) {
            byImagePath.computeIfAbsent(imagePath, key -> new LinkedList<>()).add(card);
        }
        String konamiId = card.getCard().getKonamiId();
        if (konamiId != null) {
            byKonamiId.computeIfAbsent(konamiId, key -> new LinkedList<>()).add(card);
        }
    }

    /**
     * Removes {@code card} from the backing list and from both indexes.
     */
    void remove(CardElement card) {
        cards.remove(card);
        if (card.getCard() == null) {
            return;
        }
        String imagePath = card.getCard().getImagePath();
        if (imagePath != null) {
            LinkedList<CardElement> bucket = byImagePath.get(imagePath);
            if (bucket != null) {
                bucket.remove(card);
                if (bucket.isEmpty()) {
                    byImagePath.remove(imagePath);
                }
            }
        }
        String konamiId = card.getCard().getKonamiId();
        if (konamiId != null) {
            LinkedList<CardElement> bucket = byKonamiId.get(konamiId);
            if (bucket != null) {
                bucket.remove(card);
                if (bucket.isEmpty()) {
                    byKonamiId.remove(konamiId);
                }
            }
        }
    }

    /**
     * Finds and removes the first card sharing the given imagePath that
     * satisfies {@code qualityRequired} (when applicable), in original pool
     * order. When {@code requireKonamiId} is {@code true}, candidates without
     * a KonamiId are skipped (used by the loose-collection passes, which
     * require a KonamiId on both the artwork and KonamiId sub-passes).
     * Returns {@code null} if no eligible card is found.
     */
    CardElement takeByImagePath(String imagePath, CardElement wantedSlot,
                                boolean qualityRequired, boolean requireKonamiId) {
        LinkedList<CardElement> bucket = byImagePath.get(imagePath);
        if (bucket == null) {
            return null;
        }
        for (CardElement candidate : bucket) {
            if (requireKonamiId
                    && (candidate.getCard() == null
                    || candidate.getCard().getKonamiId() == null)) {
                continue;
            }
            if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, candidate)) {
                continue;
            }
            remove(candidate);
            return candidate;
        }
        return null;
    }

    CardElement takeByImagePath(String imagePath, CardElement wantedSlot, boolean qualityRequired) {
        return takeByImagePath(imagePath, wantedSlot, qualityRequired, false);
    }

    /**
     * Finds and removes the first card sharing the given KonamiId that
     * satisfies {@code qualityRequired} (when applicable), in original pool
     * order. Returns {@code null} if no eligible card is found.
     */
    CardElement takeByKonamiId(String konamiId, CardElement wantedSlot, boolean qualityRequired) {
        LinkedList<CardElement> bucket = byKonamiId.get(konamiId);
        if (bucket == null) {
            return null;
        }
        for (CardElement candidate : bucket) {
            if (candidate.getCard() == null || candidate.getCard().getKonamiId() == null) {
                continue;
            }
            if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, candidate)) {
                continue;
            }
            remove(candidate);
            return candidate;
        }
        return null;
    }

    /**
     * Returns the remaining pool as a flat list, preserving original order.
     */
    List<CardElement> toList() {
        return cards;
    }
}