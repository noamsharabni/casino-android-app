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

public class GeminiAssistant {
    private final GenerativeModelFutures model;

    public GeminiAssistant() {
        // המפתח והמודל שלך
        String APIKEY = "YOUR_API_KEY_HERE";
        GenerativeModel gm = new GenerativeModel("gemma-3-4b-it", APIKEY);
        this.model = GenerativeModelFutures.from(gm);
    }

    public interface AiCallback {
        void onResponse(String text);
        void onError(Throwable t);
    }

    public void getRecommendation(List<Card> playerHand, int playerSum, Card dealerCard, AiCallback callback, Context context) {

        StringBuilder myCardsString = new StringBuilder();
        for (Card card : playerHand) {
            myCardsString.append(card.getValue()).append(" ");
        }

        // פרומפט מלוטש יותר עם דגש על סכומים נמוכים
        String prompt = "You are a Blackjack expert. " +
                "My total: " + playerSum + ". Dealer shows: " + dealerCard.getValue() + ". " +
                "\n\nSTRATEGY:" +
                "\n- If my total is 11 or less, ALWAYS recommend HIT." + // חוק בל יעבור
                "\n- If my total is 17 or more, ALWAYS recommend STAND." +
                "\n- If my total is 10, and dealer shows 2-9, you can suggest DOUBLE or HIT." +
                "\n- Answer in Hebrew." +
                "\n\nFormat (2 lines max):" +
                "\n1. **[HIT/STAND/DOUBLE]**" +
                "\n2. [הסבר קצר מאוד]";

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiText = result.getText();

                // --- רשת ביטחון חכמה (Smart Safety) ---
                // 1. אם יש לך 11 ומטה והוא אמר Stand - נתקן ל-Hit
                if (playerSum <= 11 && aiText.toUpperCase().contains("STAND")) {
                    callback.onResponse("**[HIT]**\nיש לך רק " + playerSum + ", אין סיכון להישרף. חובה לקחת קלף.");
                }
                // 2. אם יש לך 17 ומעלה והוא אמר Hit - נתקן ל-Stand
                else if (playerSum >= 17 && aiText.toUpperCase().contains("HIT")) {
                    callback.onResponse("**[STAND]**\nיש לך " + playerSum + ", הסיכון להישרף גבוה מדי.");
                }
                else {
                    callback.onResponse(aiText);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onError(t);
            }
        }, ContextCompat.getMainExecutor(context));
    }
}