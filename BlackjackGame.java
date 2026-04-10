package com.example.blackjack;

import java.util.ArrayList;

public class BlackjackGame {

    private Deck deck;

    private ArrayList<Card> playerHand = new ArrayList<>();
    private ArrayList<Card> dealerHand = new ArrayList<>();

    public BlackjackGame() {
        deck = new Deck();

        // שני קלפים לשחקן ולדילר
        playerHand.add(deck.draw());
        playerHand.add(deck.draw());

        dealerHand.add(deck.draw());
        dealerHand.add(deck.draw());
    }

    public ArrayList<Card> getPlayerHand() {
        return playerHand;
    }

    public ArrayList<Card> getDealerHand() {
        return dealerHand;
    }

    public Card playerHit() {
        Card c = deck.draw();
        playerHand.add(c);
        return c;
    }

    public Card dealerHit() {
        Card c = deck.draw();
        dealerHand.add(c);
        return c;
    }

    public boolean dealerShouldHit() {
        return calculateHandValue(dealerHand) < 17;
    }

    public boolean isBust(ArrayList<Card> hand) {
        return calculateHandValue(hand) > 21;
    }

    public int calculateHandValue(ArrayList<Card> hand) {
        int total = 0;
        int aces = 0;

        for (Card c : hand) {
            total += c.getValue();
            if (c.getRank().equals("ace")) aces++;
        }

        while (total > 21 && aces > 0) {
            total -= 10; // ace becomes 1 instead of 11
            aces--;
        }

        return total;
    }
}
