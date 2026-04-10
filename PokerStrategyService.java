package com.example.blackjack;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.core.content.ContextCompat;
import android.content.Context;
import java.util.List;

public class PokerStrategyService {
    private final GenerativeModelFutures model;

    public PokerStrategyService() {
        // שימוש באותן ספריות, אותו מפתח ואותו מודל Gemma שביקשת
        String APIKEY = "YOUR_API_KEY_HERE";
        GenerativeModel gm = new GenerativeModel("gemma-3-4b-it", APIKEY);
        this.model = GenerativeModelFutures.from(gm);
    }

    public interface AiCallback {
        void onResponse(String text);
        void onError(Throwable t);
    }

    public void getPokerRecommendation(List<PokerCard> playerHand,
                                       PokerCard[] communityCards,
                                       int pot,
                                       int amountToCall,
                                       AiCallback callback,
                                       Context context) {

        // 1. חילוץ טקסטואלי של קלפי השחקן (כדי שלא נקבל כתובות זיכרון)
        // 1. הפיכת הקלפים לטקסט ברור (כדי שלא תקבל שוב PokerCard@...)
        StringBuilder handStr = new StringBuilder();
        for (PokerCard card : playerHand) {
            if (card != null) handStr.append(card.getRank()).append(" ").append(card.getSuit()).append(", ");
        }

        StringBuilder boardStr = new StringBuilder();
        for (PokerCard card : communityCards) {
            if (card != null) boardStr.append(card.getRank()).append(" ").append(card.getSuit()).append(", ");
        }

// 2. פרומפט קצר ולעניין - בלי בולד ובלי חפירות
        String prompt = "אתה אסטרטג פוקר מקצועי ומאוזן. נתח את הנתונים:" +
                "\n- יד שלי: " + handStr.toString() +
                "\n- שולחן: " + (boardStr.length() > 0 ? boardStr.toString() : "Pre-flop") +
                "\n- סכום להשלמה: $" + amountToCall +
                "\n\nהנחיות אסטרטגיות חובה:" +
                "\n1. יד חזקה מאוד (A-K, A-Q, או זוג 10 ומעלה): תמליץ תמיד על RAISE כדי לבנות קופה." +
                "\n2. אם יש לך זוג נמוך או קלפים גבוהים (כמו J-Q) ואין הימור נגדך: תגיד CHECK." +
                "\n3. אם היד חלשה (קלפים נמוכים ללא קשר) ויש הימור נגדך: תגיד FOLD." +
                "\n4. אם ה-Call Amount הוא 0 ויש לך יד בינונית: תגיד CHECK." +
                "\n\nמשימה: מילה אחת להחלטה והסבר קצר של 5 מילים בעברית שמסביר את עוצמת היד.";

        // ... שאר הקוד של שליחת התוכן נשאר אותו דבר
        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                callback.onResponse(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onError(t);
            }
        }, ContextCompat.getMainExecutor(context));
    }
}