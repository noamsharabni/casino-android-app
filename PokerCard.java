package com.example.blackjack;

import android.content.Context;

public class PokerCard {
    private String suit;
    private String rank;

    public PokerCard(String suit, String rank) {
        this.suit = suit.toLowerCase();
        this.rank = rank.toLowerCase();
    }

    // --- הוספתי: בניית קלף מתוך מחרוזת של Firebase ---
    // מצפה לפורמט כמו "hearts_ace"
    public PokerCard(String cardID) {
        if (cardID != null && cardID.contains("_")) {
            String[] parts = cardID.split("_");
            this.suit = parts[0].toLowerCase();
            this.rank = parts[1].toLowerCase();
        }
    }

    // --- הוספתי: הפיכת קלף למחרוזת בשביל Firebase ---
    public String getCardID() {
        return suit + "_" + rank;
    }

    public String getSuit() { return suit; }
    public String getRank() { return rank; }

    public int getImageResId(Context context) {
        // שים לב: הפורמט כאן חייב להתאים לשמות הקבצים שלך ב-drawable
        String name = suit + "_" + rank + "_of";
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    public int getValue() {
        switch (rank) {
            case "ace":   return 14;
            case "king":  return 13;
            case "queen": return 12;
            case "jack":  return 11;
            default:
                try {
                    return Integer.parseInt(rank);
                } catch (NumberFormatException e) {
                    return 0; // למקרה של תקלה בטקסט
                }
        }
    }
}