package Model.CardsLists;

import java.util.*;
import java.util.function.BiPredicate;

public class ListDifferenceIntersection {
    /*public static List<List<Card>> ListDifIntersectArtwork(List<Card> listA, List<Card> listB) {
        List<Card> listAMinusB = new ArrayList<>();
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        for (int i = 0; i < listA.size(); i++) {
            listAMinusB.add(listA.get(i));
        }

        for (int i = 0; i < listB.size(); i++) {
            listBMinusA.add(listB.get(i));
        }

        //Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Card> listAMinusBIterator = listAMinusB.iterator();
        while (listAMinusBIterator.hasNext()) {
            Card listACard = listAMinusBIterator.next();
            if (listACard.getKonamiId() != null && listACard.getImagePath() != null) {
                Iterator<Card> ListBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (ListBMinusAIterator.hasNext() && isRemoved == false) {
                    Card listBCard = ListBMinusAIterator.next();
                    if(listBCard.getKonamiId() != null && listBCard.getImagePath() != null) {
                        if (listACard.getImagePath().equals(listBCard.getImagePath())) {
                            listAMinusBIterator.remove();
                            ListBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                            //break;  // Exit the inner loop
                        }
                    }
                }
            }
        }

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectPrintcode(List<Card> listA, List<Card> listB) {
        List<Card> listAMinusB = new ArrayList<>();
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        for (int i = 0; i < listA.size(); i++) {
            listAMinusB.add(listA.get(i));
        }

        for (int i = 0; i < listB.size(); i++) {
            listBMinusA.add(listB.get(i));
        }

        //Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Card> listAMinusBIterator = listAMinusB.iterator();
        while (listAMinusBIterator.hasNext()) {
            Card listACard = listAMinusBIterator.next();
            if (listACard.getKonamiId() != null && listACard.getPrintCode() != null) {
                Iterator<Card> ListBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (ListBMinusAIterator.hasNext() && isRemoved == false) {
                    Card listBCard = ListBMinusAIterator.next();
                    if(listBCard.getKonamiId() != null && listBCard.getPrintCode() != null) {
                        if (listACard.getPrintCode().equals(listBCard.getPrintCode())) {
                            listAMinusBIterator.remove();
                            ListBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                            //break;  // Exit the inner loop
                        }
                    }
                }
            }
        }

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectCardId(List<Card> listA, List<Card> listB) {
        List<Card> listAMinusB = new ArrayList<>();
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        for (int i = 0; i < listA.size(); i++) {
            listAMinusB.add(listA.get(i));
        }

        for (int i = 0; i < listB.size(); i++) {
            listBMinusA.add(listB.get(i));
        }

        Iterator<Card> listAMinusBIterator = listAMinusB.iterator();
        while (listAMinusBIterator.hasNext()) {
            Card listACard = listAMinusBIterator.next();
            if (listACard.getCardId() != null) {
                Iterator<Card> ListBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (ListBMinusAIterator.hasNext() && isRemoved == false) {
                    Card listBCard = ListBMinusAIterator.next();
                    if(listBCard.getCardId() != null) {
                        if (listACard.getCardId().equals(listBCard.getCardId())) {
                            listAMinusBIterator.remove();
                            ListBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                            //break;  // Exit the inner loop
                        }
                    }
                }
            }
        }

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectPassCode(List<Card> listA, List<Card> listB) {
        List<Card> listAMinusB = new ArrayList<>();
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        for (int i = 0; i < listA.size(); i++) {
            listAMinusB.add(listA.get(i));
        }

        for (int i = 0; i < listB.size(); i++) {
            listBMinusA.add(listB.get(i));
        }

        //Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Card> listAMinusBIterator = listAMinusB.iterator();
        while (listAMinusBIterator.hasNext()) {
            Card listACard = listAMinusBIterator.next();
            if (listACard.getCardId() != null && listACard.getPasscode() != null) {
                Iterator<Card> ListBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (ListBMinusAIterator.hasNext() && isRemoved == false) {
                    Card listBCard = ListBMinusAIterator.next();
                    if(listBCard.getCardId() != null && listBCard.getPasscode() != null) {
                        if (listACard.getPasscode().equals(listBCard.getPasscode())) {
                            listAMinusBIterator.remove();
                            ListBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                            //break;  // Exit the inner loop
                        }
                    }
                }
            }
        }

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<Map<Card, String>> ListDifIntersectArtworkWithExceptions(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        Map<Card, String> mapBMinusA = new HashMap<>();
        Map<Card, String> mapsIntersection = new HashMap<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            mapBMinusA.put(card, "");
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            if(!entryA.getValue().contains("+") && entryA.getValue().contains("*")) {
                Card mapACard = entryA.getKey();
                if (mapACard.getCardId() != null && mapACard.getImagePath() != null) {
                    Iterator<Map.Entry<Card, String>> mapBMinusAIterator = mapBMinusA.entrySet().iterator();
                    boolean isRemoved = false;
                    while (mapBMinusAIterator.hasNext() && !isRemoved) {
                        Map.Entry<Card, String> entryB = mapBMinusAIterator.next();
                        Card listBCard = entryB.getKey();
                        if(listBCard.getCardId() != null && listBCard.getImagePath() != null) {
                            if (mapACard.getImagePath().equals(listBCard.getImagePath())) {
                                mapAMinusBIterator.remove();
                                mapBMinusAIterator.remove();
                                mapsIntersection.put(listBCard, "");
                                isRemoved = true;
                            }
                        }
                    }
                }
            }
        }

        List<Map<Card, String>> returnValue = new ArrayList<>();
        returnValue.add(mapAMinusB);
        returnValue.add(mapBMinusA);
        returnValue.add(mapsIntersection);

        return returnValue;
    }

    public static List<Map<Card, String>> ListDifIntersectCardIdWithExceptions(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        Map<Card, String> mapBMinusA = new HashMap<>();
        Map<Card, String> mapsIntersection = new HashMap<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            mapBMinusA.put(card, "");
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            if(!entryA.getValue().contains("+") && !entryA.getValue().contains("*")) {
                Card mapACard = entryA.getKey();
                if (mapACard.getCardId() != null) {
                    Iterator<Map.Entry<Card, String>> mapBMinusAIterator = mapBMinusA.entrySet().iterator();
                    boolean isRemoved = false;
                    while (mapBMinusAIterator.hasNext() && !isRemoved) {
                        Map.Entry<Card, String> entryB = mapBMinusAIterator.next();
                        Card listBCard = entryB.getKey();
                        if(listBCard.getCardId() != null) {
                            if (mapACard.getCardId().equals(listBCard.getCardId())) {
                                mapAMinusBIterator.remove();
                                mapBMinusAIterator.remove();
                                mapsIntersection.put(listBCard, "");
                                isRemoved = true;
                            }
                        }
                    }
                }
            }
        }

        List<Map<Card, String>> returnValue = new ArrayList<>();
        returnValue.add(mapAMinusB);
        returnValue.add(mapBMinusA);
        returnValue.add(mapsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectArtwork(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            listBMinusA.add(card);
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            Card mapACard = entryA.getKey();
            if (mapACard.getCardId() != null && mapACard.getImagePath() != null) {
                Iterator<Card> listBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (listBMinusAIterator.hasNext() && !isRemoved) {
                    Card listBCard = listBMinusAIterator.next();
                    if(listBCard.getCardId() != null && listBCard.getImagePath() != null) {
                        if (mapACard.getImagePath().equals(listBCard.getImagePath())) {
                            mapAMinusBIterator.remove();
                            listBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                        }
                    }
                }
            }
        }

        List<Card> listAMinusB = new ArrayList<>(mapAMinusB.keySet());

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectPrintcode(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            listBMinusA.add(card);
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            Card mapACard = entryA.getKey();
            if (mapACard.getCardId() != null && mapACard.getPrintCode() != null) {
                Iterator<Card> listBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (listBMinusAIterator.hasNext() && !isRemoved) {
                    Card listBCard = listBMinusAIterator.next();
                    if(listBCard.getCardId() != null && listBCard.getPrintCode() != null) {
                        if (mapACard.getPrintCode().equals(listBCard.getPrintCode())) {
                            mapAMinusBIterator.remove();
                            listBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                        }
                    }
                }
            }
        }

        List<Card> listAMinusB = new ArrayList<>(mapAMinusB.keySet());

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectCardId(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            listBMinusA.add(card);
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            Card mapACard = entryA.getKey();
            if (mapACard.getCardId() != null) {
                Iterator<Card> listBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (listBMinusAIterator.hasNext() && !isRemoved) {
                    Card listBCard = listBMinusAIterator.next();
                    if(listBCard.getCardId() != null) {
                        if (mapACard.getCardId().equals(listBCard.getCardId())) {
                            mapAMinusBIterator.remove();
                            listBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                        }
                    }
                }
            }
        }

        List<Card> listAMinusB = new ArrayList<>(mapAMinusB.keySet());

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }

    public static List<List<Card>> ListDifIntersectPassCode(Map<Card, String> mapA, List<Card> listB) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        List<Card> listBMinusA = new ArrayList<>();

        List<Card> listsIntersection = new ArrayList<>();

        // Add all wished elements from decks and collections
        for (Card card : listB) {
            listBMinusA.add(card);
        }

        // Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Map.Entry<Card, String>> mapAMinusBIterator = mapAMinusB.entrySet().iterator();
        while (mapAMinusBIterator.hasNext()) {
            Map.Entry<Card, String> entryA = mapAMinusBIterator.next();
            Card mapACard = entryA.getKey();
            if (mapACard.getCardId() != null && mapACard.getPasscode() != null) {
                Iterator<Card> listBMinusAIterator = listBMinusA.iterator();
                boolean isRemoved = false;
                while (listBMinusAIterator.hasNext() && !isRemoved) {
                    Card listBCard = listBMinusAIterator.next();
                    if(listBCard.getCardId() != null && listBCard.getPasscode() != null) {
                        if (mapACard.getPasscode().equals(listBCard.getPasscode())) {
                            mapAMinusBIterator.remove();
                            listBMinusAIterator.remove();
                            listsIntersection.add(listBCard);
                            isRemoved = true;
                        }
                    }
                }
            }
        }

        List<Card> listAMinusB = new ArrayList<>(mapAMinusB.keySet());

        List<List<Card>> returnValue = new ArrayList<>();
        returnValue.add(listAMinusB);
        returnValue.add(listBMinusA);
        returnValue.add(listsIntersection);

        return returnValue;
    }
*/

    /**
     *
     * @param listA
     * @param listB
     * @param comparator
     * @param mustContain
     * @param mustNotContain
     * @return
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
                if(valueA != null) {
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

    public static List<List<CardElement>> ListDifIntersectArtworkWithExceptions(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                Arrays.asList("*"), Arrays.asList("+"));
    }

    public static List<List<CardElement>> ListDifIntersectKonamiIdWithExceptions(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                Arrays.asList("*"), null);
    }


    /*public static List<List<CardElement>> ListDifIntersectArtwork(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB.stream().collect(Collectors.toMap(card -> card, card -> "")),
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                null, null);
    }

    public static List<List<CardElement>> ListDifIntersectPrintcode(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB.stream().collect(Collectors.toMap(card -> card, card -> "")),
                (card1, card2) -> card1.getPrintCode() != null && card2.getPrintCode() != null && card1.getPrintCode().equals(card2.getPrintCode()),
                null, null);
    }

    public static List<List<CardElement>> ListDifIntersectCardId(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB.stream().collect(Collectors.toMap(card -> card, card -> "")),
                (card1, card2) -> card1.getCardId().equals(card2.getCardId()),
                null, null);
    }

    public static List<List<CardElement>> ListDifIntersectPassCode(List<CardElement> mapA, List<CardElement> listB) {
        return ListDifIntersect(mapA, listB.stream().collect(Collectors.toMap(card -> card, card -> "")),
                (card1, card2) -> card1.getPasscode() != null && card2.getPasscode() != null && card1.getPasscode().equals(card2.getPasscode()),
                null, null);
    }*/


    public static List<List<CardElement>> ListDifIntersectArtwork(List<CardElement> listA, List<CardElement> listB) {
        List<List<CardElement>> lists = ListDifIntersect(listA/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                null, null);
        return lists;
    }

    public static List<List<CardElement>> ListDifIntersectPrintcode(List<CardElement> listA, List<CardElement> listB) {
        List<List<CardElement>> lists = ListDifIntersect(listA/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getPrintCode() != null && card2.getPrintCode() != null && card1.getPrintCode().equals(card2.getPrintCode()),
                null, null);
        return lists;
    }

    public static List<List<CardElement>> ListDifIntersectKonamiId(List<CardElement> listA, List<CardElement> listB) {
        List<List<CardElement>> lists = ListDifIntersect(listA/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                null, null);
        return lists;
    }

    public static List<List<CardElement>> ListDifIntersectPassCode(List<CardElement> listA, List<CardElement> listB) {
        List<List<CardElement>> lists = ListDifIntersect(listA/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getPassCode() != null && card2.getPassCode() != null && card1.getPassCode().equals(card2.getPassCode()),
                null, null);
        return lists;
    }

    public static List<List<CardElement>> ListDifIntersectArtworkWithExceptions(List<CardElement> listA, List<CardElement> listB, String character) throws Exception {
        return ListDifIntersect(listA, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getImagePath() != null && card2.getImagePath() != null && card1.getImagePath().equals(card2.getImagePath()),
                Arrays.asList("*"), Arrays.asList("+"), character);
    }

    public static List<List<CardElement>> ListDifIntersectKonamiIdWithExceptions(List<CardElement> listA, List<CardElement> listB, String character) throws Exception {
        return ListDifIntersect(listA, listB/*.stream().collect(Collectors.toMap(card -> card, card -> ""))*/,
                (card1, card2) -> card1.getKonamiId().equals(card2.getKonamiId()),
                null, Arrays.asList("+", "*"), character);
    }

    /*public static List<Map<Card, String>> ListDifIntersect(Map<Card, String> mapA, Map<Card, String> mapB, BiPredicate<Card, Card> comparator, List<String> mustContain, List<String> mustNotContain, String character) {
        Map<Card, String> mapAMinusB = new HashMap<>(mapA);
        Map<Card, String> mapBMinusA = new HashMap<>(mapB);

        for (Map.Entry<Card, String> entryA : mapAMinusB.entrySet()) {
            Card mapACard = entryA.getKey();
            String valueA = entryA.getValue();
            if (mapACard.getCardId() != null && !valueA.contains(character)) {
                boolean containsRequired = mustContain == null || mustContain.stream().allMatch(valueA::contains);
                boolean doesNotContainExcluded = mustNotContain == null || mustNotContain.stream().noneMatch(valueA::contains);
                if (containsRequired && doesNotContainExcluded) {
                    for (Map.Entry<Card, String> entryB : mapBMinusA.entrySet()) {
                        Card mapBCard = entryB.getKey();
                        String valueB = entryB.getValue();
                        if (mapBCard.getCardId() != null && comparator.test(mapACard, mapBCard) && !valueB.contains(character)) {
                            mapAMinusB.put(mapACard, valueA + character);
                            mapBMinusA.put(mapBCard, valueB + character);
                        }
                    }
                }
            }
        }

        List<Map<Card, String>> returnValue = new ArrayList<>();
        returnValue.add(mapAMinusB);
        returnValue.add(mapBMinusA);

        return returnValue;
    }*/

    public static List<List<CardElement>> ListDifIntersect(List<CardElement> listA, List<CardElement> listB, BiPredicate<Card, Card> comparator, List<String> mustContain, List<String> mustNotContain, String character) throws Exception {
        List<CardElement> listAMinusB = new ArrayList<>(listA);
        List<CardElement> listBMinusA = new ArrayList<>(listB);

        Set<Card> usedCardsInB = new HashSet<>();

        for (int i = 0; i < listA.size(); i++/*CardElement entryA : listA*/) {
            Card listACard = listA.get(i).getCard();
            String valueA = listA.get(i).toString();
            if(valueA != null) {
                if (listACard.getKonamiId() != null && !valueA.contains(character)) {
                    boolean containsRequired = mustContain == null || mustContain.stream().allMatch(valueA::contains);
                    boolean doesNotContainExcluded = mustNotContain == null || mustNotContain.stream().noneMatch(valueA::contains);
                    if (containsRequired && doesNotContainExcluded) {
                        for (int j = 0; j < listB.size(); j++/*CardElement entryB : listB*/) {
                            Card listBCard = listB.get(j).getCard();
                            String valueB = listB.get(j).toString();
                            if(valueB != null) {
                                if (listBCard.getKonamiId() != null && comparator.test(listACard, listBCard) && !valueB.contains(character) && !usedCardsInB.contains(listBCard)) {
                                    //listAMinusB.add(new CardElement(listACard, valueA + character));
                                    //listBMinusA.add(new CardElement(listBCard, valueB + character));
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
