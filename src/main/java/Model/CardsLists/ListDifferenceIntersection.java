package Model.CardsLists;

import java.util.*;
import java.util.function.BiPredicate;

public class ListDifferenceIntersection {
    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s.
     *
     * @param listA The first list
     * @param listB The second list
     * @param comparator A predicate to compare the equality of two cards
     * @param mustContain A list of strings that must be contained in the
     *     {@link CardElement#toString()} representation of the cards for them to be
     *     considered in the intersection.
     * @param mustNotContain A list of strings that must not be contained in the
     *     {@link CardElement#toString()} representation of the cards for them to be
     *     considered in the intersection.
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and that satisfy the given predicates).
     */
    public static List<List<CardElement>> ListDifIntersect(List<CardElement> listA, List<CardElement> listB, BiPredicate<Card, Card> comparator, List<String> mustContain, List<String> mustNotContain) {
        List<CardElement> listAMinusB = new ArrayList<>(listA);
        List<CardElement> listBMinusA = new ArrayList<>(listB);

        List<CardElement> listsIntersection = new ArrayList<>();

        Iterator<CardElement> mapAMinusBIterator = listAMinusB.iterator();
        while (mapAMinusBIterator.hasNext()) {
            CardElement listACard = mapAMinusBIterator.next();
            if (listACard.getCard().getKonamiId() != null) {
                String valueA = listACard.toString();
                if (valueA != null) {
                    boolean containsRequired = mustContain == null || mustContain.stream().allMatch(valueA::contains);
                    boolean doesNotContainExcluded = mustNotContain == null || mustNotContain.stream().noneMatch(valueA::contains);
                    if (containsRequired && doesNotContainExcluded) {
                        Iterator<CardElement> mapBMinusAIterator = listBMinusA.iterator();
                        boolean isRemoved = false;
                        while (mapBMinusAIterator.hasNext() && !isRemoved) {
                            CardElement listBCard = mapBMinusAIterator.next();
                            if (listBCard.getCard().getKonamiId() != null && comparator.test(listACard.getCard(), listBCard.getCard())) {
                                mapAMinusBIterator.remove();
                                mapBMinusAIterator.remove();
                                listsIntersection.add(listBCard);
                                isRemoved = true;
                            }
                        }
                    }
                }
            }
        }

        List<List<CardElement>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    /**
     * A version of ListDifIntersect that ignores cards with IDs that have a '+' in them.
     * This is because the '+' character is used by the print code to indicate that a card
     * is not available.
     *
     * @param mapA  The list of cards to remove cards from.
     * @param listB The list of cards to remove.
     * @return A list of lists. The first list contains the cards in mapA that are not in
     * listB, the second contains the cards in listB that are not in mapA, and the
     * third contains the cards that are in both.
     */
    public static List<List<CardElement>> ListDifIntersectArtworkWithExceptions(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                List.of("*"), List.of("+"));
    }

    /**
     * A version of ListDifIntersect that uses the Konami ID to determine which cards are
     * in both lists, and ignores cards with IDs that have a '+' in them. This is because
     * the '+' character is used by the print code to indicate that a card is not
     * available.
     *
     * @param mapA The list of cards to remove cards from.
     * @param listB The list of cards to remove.
     * @return A list of lists. The first list contains the cards in mapA that are not in
     *         listB, the second contains the cards in listB that are not in mapA, and the
     *         third contains the cards that are in both.
     */
    public static List<List<CardElement>> ListDifIntersectKonamiIdWithExceptions(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                List.of("*"), null);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the artwork path of the cards.
     *
     * @param listA The first list
     * @param listB The second list
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and that have the same artwork path).
     */
    public static List<List<CardElement>> ListDifIntersectArtwork(List<CardElement> listA, List<CardElement> listB) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                null, null);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the print code of the cards.
     *
     * @param listA The first list
     * @param listB The second list
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and that have the same print code).
     */
    public static List<List<CardElement>> ListDifIntersectPrintcode(List<CardElement> listA, List<CardElement> listB) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getPrintCode() != null && card2.getPrintCode() != null && card1.getPrintCode().equals(card2.getPrintCode()),
                null, null);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the Konami ID of the cards.
     *
     * @param listA The first list
     * @param listB The second list
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and have the same Konami ID).
     */
    public static List<List<CardElement>> ListDifIntersectKonamiId(List<CardElement> listA, List<CardElement> listB) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                null, null);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the pass code of the cards.
     *
     * @param listA The first list
     * @param listB The second list
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and have the same pass code).
     */
    public static List<List<CardElement>> ListDifIntersectPassCode(List<CardElement> listA, List<CardElement> listB) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getPassCode() != null && card2.getPassCode() != null && card1.getPassCode().equals(card2.getPassCode()),
                null, null);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the artwork of the cards. Cards with a "*" or "+" in their artwork
     * are considered to not be in the intersection.
     *
     * @param listA The first list
     * @param listB The second list
     * @param character The character that is used to separate the artwork from the
     *     other information in the image path.
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and have the same artwork, except for the cards with a "*" or "+"
     *     in their artwork).
     */
    public static List<List<CardElement>> ListDifIntersectArtworkWithExceptions(List<CardElement> listA, List<CardElement> listB, String character) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                List.of("*"), List.of("+"), character);
    }

    /**
     * Compute the difference and intersection of two lists of {@link CardElement}s,
     * based on the Konami ID of the cards. Cards with a "*" or "+" in their artwork
     * are considered to not be in the intersection.
     *
     * @param listA The first list
     * @param listB The second list
     * @param character The character that is used to separate the artwork from the
     *     other information in the image path.
     * @return A list of three lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), the
     *     difference of {@code listB} and {@code listA}, and the intersection of
     *     {@code listA} and {@code listB} (i.e. the elements that are common to both
     *     lists and have the same Konami ID, except for the cards with a "*" or "+"
     *     in their artwork).
     */
    public static List<List<CardElement>> ListDifIntersectKonamiIdWithExceptions(List<CardElement> listA, List<CardElement> listB, String character) {
        return ListDifIntersect(listA, listB,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                null, Arrays.asList("+", "*"), character);
    }

    /**
     * Computes the difference and intersection of two lists of {@link CardElement}s,
     * based on a given comparison function.
     *
     * @param listA The first list
     * @param listB The second list
     * @param comparator A comparison function that takes a card from each list and
     *     returns {@code true} if the cards are equal and {@code false} otherwise.
     * @param mustContain A list of strings that must be present in the value of the
     *     card element for it to be considered. If {@code null}, all cards are
     *     considered.
     * @param mustNotContain A list of strings that must not be present in the value
     *     of the card element for it to be considered. If {@code null}, all cards are
     *     considered.
     * @param character A character that is used to separate the artwork from the
     *     other information in the image path. If the value of the card element does
     *     not contain this character, the card is not considered.
     * @return A list of two lists: the difference of {@code listA} and {@code listB}
     *     (i.e. the elements of {@code listA} that are not in {@code listB}), and the
     *     intersection of {@code listA} and {@code listB} (i.e. the elements that are
     *     common to both lists and have the same artwork, except for the cards with a
     *     "*" or "+" in their artwork).
     */
    public static List<List<CardElement>> ListDifIntersect(List<CardElement> listA, List<CardElement> listB, BiPredicate<Card, Card> comparator, List<String> mustContain, List<String> mustNotContain, String character) {
        List<CardElement> listAMinusB = new ArrayList<>(listA);
        List<CardElement> listBMinusA = new ArrayList<>(listB);

        Set<Card> usedCardsInB = new HashSet<>();

        for (int i = 0; i < listA.size(); i++) {
            Card listACard = listA.get(i).getCard();
            String valueA = listA.get(i).toString();
            if (valueA != null) {
                if (listACard.getKonamiId() != null && !valueA.contains(character)) {
                    boolean containsRequired = mustContain == null || mustContain.stream().allMatch(valueA::contains);
                    boolean doesNotContainExcluded = mustNotContain == null || mustNotContain.stream().noneMatch(valueA::contains);
                    if (containsRequired && doesNotContainExcluded) {
                        for (int j = 0; j < listB.size(); j++) {
                            Card listBCard = listB.get(j).getCard();
                            String valueB = listB.get(j).toString();
                            if (valueB != null) {
                                if (listBCard.getKonamiId() != null && comparator.test(listACard, listBCard) && !valueB.contains(character) && !usedCardsInB.contains(listBCard)) {
                                    listAMinusB.get(i).setValues(valueA + character);
                                    listBMinusA.get(j).setValues(valueB + character);
                                    usedCardsInB.add(listBCard);
                                    break; // Move to the next element in mapA
                                }
                            }
                        }
                    }
                }
            }
        }

        List<List<CardElement>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);

        return returnValue;
    }
}
