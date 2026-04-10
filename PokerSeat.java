package com.example.blackjack;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;

public class PokerSeat {
    View seatView;
    TextView txtName, txtChips, txtStatus;
    ImageView imgCard1, imgCard2;
    CardView cardViewBg;
    // שינוי ל-PokerCard
    private PokerCard[] currentCards;

    public PokerSeat(View view) {
        this.seatView = view;
        txtName = view.findViewById(R.id.txt_player_name);
        txtChips = view.findViewById(R.id.txt_player_chips);
        imgCard1 = view.findViewById(R.id.img_card1);
        imgCard2 = view.findViewById(R.id.img_card2);
        txtStatus = view.findViewById(R.id.txt_status_overlay);
        cardViewBg = findCardView(view);
    }

    private CardView findCardView(View v) {
        if (v instanceof CardView) return (CardView) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                CardView res = findCardView(vg.getChildAt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    public void setStatus(String status, int color) {
        if (txtStatus != null) {
            txtStatus.setText(status);
            txtStatus.setTextColor(color);
            txtStatus.setVisibility(View.VISIBLE);
        }
    }

    public void clearStatus() {
        if (txtStatus != null) txtStatus.setVisibility(View.GONE);
    }

    public void showPlayer(String name, String chips) {
        seatView.setVisibility(View.VISIBLE);
        txtName.setText(name);
        txtChips.setText(chips);
    }

    public void setCardsBack() {
        imgCard1.setImageResource(R.drawable.card_back);
        imgCard2.setImageResource(R.drawable.card_back);
    }

    // שינוי הפרמטרים ל-PokerCard
    public void setCards(PokerCard c1, PokerCard c2, Context context) {
        this.currentCards = new PokerCard[]{c1, c2};
        int resId1 = c1.getImageResId(context);
        int resId2 = c2.getImageResId(context);
        if (resId1 != 0) imgCard1.setImageResource(resId1);
        if (resId2 != 0) imgCard2.setImageResource(resId2);
    }

    public void setHighlight(boolean isTurn) {
        if (cardViewBg != null) {
            cardViewBg.setCardBackgroundColor(isTurn ? Color.parseColor("#2E7D32") : Color.WHITE);
        }
    }
    public String getPlayerName() {
        return txtName != null ? txtName.getText().toString() : "Unknown";
    }

    // שינוי סוג ההחזרה ל-PokerCard
    public PokerCard[] getCards() {
        return currentCards;
    }

    public String getStatusText() {
        if (txtStatus != null && txtStatus.getVisibility() == View.VISIBLE) {
            return txtStatus.getText().toString();
        }
        return "";
    }
}