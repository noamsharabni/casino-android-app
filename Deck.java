package com.example.blackjack;

import java.util.ArrayList;
import java.util.Collections;

public class Deck {

    private ArrayList<Card> cards = new ArrayList<>();

    private String[] suits = {"clubs", "diamonds", "hearts", "spades"};
    private String[] ranks = {
            "ace", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "jack", "queen", "king"
    };

    public Deck() {
        for (String s : suits) {
            for (String r : ranks) {
                cards.add(new Card(s, r));
            }
        }
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        return cards.remove(0);
    }
}
