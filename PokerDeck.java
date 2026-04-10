package com.example.blackjack;

import java.util.ArrayList;
import java.util.Collections;

public class PokerDeck {
    private ArrayList<PokerCard> cards = new ArrayList<>();
    private String[] suits = {"clubs", "diamonds", "hearts", "spades"};
    private String[] ranks = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};

    public PokerDeck() {
        for (String s : suits) {
            for (String r : ranks) {
                cards.add(new PokerCard(s, r));
            }
        }
        shuffle();
    }

    public void shuffle() { Collections.shuffle(cards); }
    public PokerCard draw() { return cards.isEmpty() ? null : cards.remove(0); }
}