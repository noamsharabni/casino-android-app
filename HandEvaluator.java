package com.example.blackjack;

import java.util.*;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD("High Card", 1),
        PAIR("Pair", 2),
        TWO_PAIR("Two Pair", 3),
        THREE_OF_A_KIND("Three of a Kind", 4),
        STRAIGHT("Straight", 5),
        FLUSH("Flush", 6),
        FULL_HOUSE("Full House", 7),
        FOUR_OF_A_KIND("Four of a Kind", 8),
        STRAIGHT_FLUSH("Straight Flush", 9),
        ROYAL_FLUSH("Royal Flush", 10);

        public final String name;
        public final int power;
        HandRank(String name, int power) { this.name = name; this.power = power; }
    }

    public static class EvaluationResult {
        public HandRank rank;
        public String desc;
        public int weightedScore; // משמש להכרעת תיקו (Kicker)

        public EvaluationResult(HandRank rank, String desc, int weightedScore) {
            this.rank = rank;
            this.desc = desc;
            this.weightedScore = weightedScore;
        }
    }

    public static EvaluationResult evaluate(PokerCard[] holeCards, PokerCard[] communityCards) {
        List<PokerCard> allCards = new ArrayList<>();
        if (holeCards != null) for (PokerCard c : holeCards) if (c != null) allCards.add(c);
        if (communityCards != null) for (PokerCard c : communityCards) if (c != null) allCards.add(c);

        if (allCards.size() < 5) return new EvaluationResult(HandRank.HIGH_CARD, "N/A", 0);

        // מיון הקלפים לפי הערך החדש (Ace=14, King=13...)
        Collections.sort(allCards, (c1, c2) -> Integer.compare(c1.getValue(), c2.getValue()));

        Map<Integer, Integer> valCounts = new HashMap<>();
        Map<String, Integer> suitCounts = new HashMap<>();
        for (PokerCard c : allCards) {
            valCounts.put(c.getValue(), valCounts.getOrDefault(c.getValue(), 0) + 1);
            suitCounts.put(c.getSuit(), suitCounts.getOrDefault(c.getSuit(), 0) + 1);
        }

        // בדיקת צבע (Flush)
        String flushSuit = null;
        for (Map.Entry<String, Integer> entry : suitCounts.entrySet()) {
            if (entry.getValue() >= 5) flushSuit = entry.getKey();
        }

        // בדיקת רצף (Straight)
        List<Integer> sortedUnique = new ArrayList<>(valCounts.keySet());
        Collections.sort(sortedUnique);
        int straightHigh = -1;
        int count = 1;
        for (int i = 0; i < sortedUnique.size() - 1; i++) {
            if (sortedUnique.get(i + 1) == sortedUnique.get(i) + 1) {
                count++;
                if (count >= 5) straightHigh = sortedUnique.get(i + 1);
            } else count = 1;
        }
        // Ace נמוך (A-2-3-4-5)
        if (sortedUnique.contains(14) && sortedUnique.contains(2) && sortedUnique.contains(3) && sortedUnique.contains(4) && sortedUnique.contains(5)) {
            if (straightHigh < 5) straightHigh = 5;
        }

        // זיהוי סטים
        int quads = -1, trips = -1, pair1 = -1, pair2 = -1;
        List<Integer> keys = new ArrayList<>(valCounts.keySet());
        Collections.sort(keys, Collections.reverseOrder());
        for (int v : keys) {
            int c = valCounts.get(v);
            if (c == 4) quads = v;
            else if (c == 3 && trips == -1) trips = v;
            else if (c == 2) {
                if (pair1 == -1) pair1 = v;
                else if (pair2 == -1) pair2 = v;
            }
        }

        // קביעת התוצאה לפי היררכיית פוקר (מהחזק לחלש)
        if (flushSuit != null && straightHigh != -1)
            return new EvaluationResult(HandRank.STRAIGHT_FLUSH, "Straight Flush", straightHigh);

        if (quads != -1)
            return new EvaluationResult(HandRank.FOUR_OF_A_KIND, "Four of a Kind", quads * 100);

        if (trips != -1 && pair1 != -1)
            return new EvaluationResult(HandRank.FULL_HOUSE, "Full House", (trips * 100) + pair1);

        if (flushSuit != null) {
            int topFlush = 0;
            for (PokerCard c : allCards) if (c.getSuit().equals(flushSuit)) topFlush = c.getValue();
            return new EvaluationResult(HandRank.FLUSH, "Flush", topFlush);
        }

        if (straightHigh != -1)
            return new EvaluationResult(HandRank.STRAIGHT, "Straight to " + valToName(straightHigh), straightHigh);

        if (trips != -1) {
            int k = findKicker(allCards, new int[]{trips});
            return new EvaluationResult(HandRank.THREE_OF_A_KIND, "Three of a Kind", (trips * 100) + k);
        }

        if (pair1 != -1 && pair2 != -1) {
            int k = findKicker(allCards, new int[]{pair1, pair2});
            return new EvaluationResult(HandRank.TWO_PAIR, "Two Pair: " + valToName(pair1) + "s & " + valToName(pair2) + "s", (pair1 * 1000) + (pair2 * 100) + k);
        }

        if (pair1 != -1) {
            int k = findKicker(allCards, new int[]{pair1});
            return new EvaluationResult(HandRank.PAIR, "Pair of " + valToName(pair1) + "s", (pair1 * 100) + k);
        }

        int highVal = allCards.get(allCards.size() - 1).getValue();
        return new EvaluationResult(HandRank.HIGH_CARD, "High Card " + valToName(highVal), highVal);
    }

    private static int findKicker(List<PokerCard> cards, int[] exclude) {
        for (int i = cards.size() - 1; i >= 0; i--) {
            int val = cards.get(i).getValue();
            boolean isExcluded = false;
            for (int ex : exclude) if (val == ex) isExcluded = true;
            if (!isExcluded) return val;
        }
        return 0;
    }

    private static String valToName(int v) {
        if (v == 14) return "Ace";
        if (v == 13) return "King";
        if (v == 12) return "Queen";
        if (v == 11) return "Jack";
        return String.valueOf(v);
    }
}