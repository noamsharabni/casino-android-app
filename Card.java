package com.example.blackjack;

import android.content.Context;

public class Card {

    private String suit;   // clubs, diamonds, hearts, spades
    private String rank;   // 2–10, jack, queen, king, ace

    public Card(String suit, String rank) {
        this.suit = suit.toLowerCase();
        this.rank = rank.toLowerCase();
    }

    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    // מתאים לפורמט: clubs_10_of
    public int getImageResId(Context context) {
        String name = suit + "_" + rank + "_of";
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    public int getValue() {
        switch (rank) {
            case "ace": return 11;
            case "king":
            case "queen":
            case "jack": return 10;
            default:
                return Integer.parseInt(rank);
        }
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}
