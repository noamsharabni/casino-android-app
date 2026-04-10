package com.example.blackjack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class GameActivity extends AppCompatActivity {

    private BlackjackGame game;
    private DatabaseHelper db;
    private String loggedInUser;
    private int currentBalance;

    private TextView txtBalance, txtStatus, txtResult;
    private EditText edtBet;
    private LinearLayout dealerLayout, playerLayout;
    private Button btnHit, btnStand, btnPlay, btnExitToLobby;

    private boolean roundActive = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CountDownTimer turnTimer;

    private static ArrayList<String> historyList = new ArrayList<>();

    private GeminiAssistant aiAssistant;
    private FloatingActionButton btnAiAssistant;
    private CardView cardAiMessage;
    private TextView txtAiRecommendation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        aiAssistant = new GeminiAssistant();

        btnAiAssistant = findViewById(R.id.btn_ai_assistant);
        cardAiMessage = findViewById(R.id.card_ai_message);
        txtAiRecommendation = findViewById(R.id.txt_ai_recommendation);

        btnAiAssistant.setOnClickListener(v -> {
            // 1. בדיקה אם המשחק פעיל בכלל
            if (game == null || !roundActive) {
                Toast.makeText(this, "התחל משחק כדי לקבל עצה!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. הצגת הבועה
            cardAiMessage.setVisibility(View.VISIBLE);
            txtAiRecommendation.setText("הסוכן בוחן את הקלפים...");

            // 3. שליפת הנתונים האמיתיים מהמשחק שלך
            int pSum = game.calculateHandValue(game.getPlayerHand());
            Card dCard = game.getDealerHand().get(0); // הקלף הגלוי הראשון

            // 4. שליחה ל-Gemini עם המשתנים הנכונים
            aiAssistant.getRecommendation(game.getPlayerHand(), pSum, dCard, new GeminiAssistant.AiCallback() {
                @Override
                public void onResponse(String text) {
                    txtAiRecommendation.setText(text);

                    // טיימר להעלמת ההודעה אחרי 7 שניות (שיהיה זמן לקרוא)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        cardAiMessage.setVisibility(View.GONE);
                    }, 7000);
                }

                @Override
                public void onError(Throwable t) {
                    android.util.Log.e("GEMINI_ERROR", "Error: " + t.getMessage());
                    txtAiRecommendation.setText("הסוכן כרגע לא זמין...");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        cardAiMessage.setVisibility(View.GONE);
                    }, 5000);
                }
            }, GameActivity.this);
        });


        db = new DatabaseHelper(this);
        loggedInUser = getIntent().getStringExtra("username");
        currentBalance = db.getUserBalance(loggedInUser);

        txtBalance = findViewById(R.id.txt_balance);
        edtBet = findViewById(R.id.edt_bet);
        dealerLayout = findViewById(R.id.dealer_hand);
        playerLayout = findViewById(R.id.player_hand);
        btnHit = findViewById(R.id.btn_hit);
        btnStand = findViewById(R.id.btn_stand);
        btnPlay = findViewById(R.id.btn_play);
        btnExitToLobby = findViewById(R.id.btn_exit_to_lobby);
        txtStatus = findViewById(R.id.txt_status);
        txtResult = findViewById(R.id.txt_result);

        updateBalanceDisplay();
        setButtonsEnabled(false);
        btnPlay.setEnabled(true);

        btnExitToLobby.setOnClickListener(v -> {
            Intent intent = new Intent(GameActivity.this, Lobby.class);
            intent.putExtra("username", loggedInUser);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnPlay.setOnClickListener(v -> startNewRound());
        btnHit.setOnClickListener(v -> playerHit());
        btnStand.setOnClickListener(v -> dealerTurn());
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentBalance = db.getUserBalance(loggedInUser);
        updateBalanceDisplay();
    }

    private void updateBalanceDisplay() {
        txtBalance.setText("Balance: $" + currentBalance);
        if (currentBalance >= 2000) {
            txtBalance.setTextColor(Color.parseColor("#FFD700"));
        } else if (currentBalance < 200) {
            txtBalance.setTextColor(Color.RED);
        } else {
            txtBalance.setTextColor(Color.WHITE);
        }
    }

    private void startNewRound() {
        String betStr = edtBet.getText().toString();
        if (betStr.isEmpty()) {
            Toast.makeText(this, "Please enter a bet!", Toast.LENGTH_SHORT).show();
            return;
        }

        int betAmount = Integer.parseInt(betStr);
        if (betAmount > currentBalance) {
            showNoMoneyDialog();
            return;
        }
        if (betAmount <= 0) {
            Toast.makeText(this, "Bet must be positive!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ביטול טיימר קיים לפני תחילת סבב
        if (turnTimer != null) turnTimer.cancel();

        dealerLayout.removeAllViews();
        playerLayout.removeAllViews();
        txtResult.setVisibility(View.GONE);
        txtStatus.setText("Dealing...");

        game = new BlackjackGame();
        roundActive = true;

        setButtonsEnabled(false);
        btnPlay.setEnabled(false);
        edtBet.setEnabled(false);

        dealInitialSequence();
        // הסרתי מכאן את resetAndStartTimer() כדי שלא יתחיל מוקדם מדי
    }

    private void dealInitialSequence() {
        handler.postDelayed(() -> addCardToLayoutAnimated(dealerLayout, game.getDealerHand().get(0)), 0);
        handler.postDelayed(() -> addCardToLayoutAnimated(playerLayout, game.getPlayerHand().get(0)), 400);
        handler.postDelayed(() -> addHiddenCardToDealer(), 800);
        handler.postDelayed(() -> addCardToLayoutAnimated(playerLayout, game.getPlayerHand().get(1)), 1200);

        handler.postDelayed(() -> {
            if (roundActive) {
                if (game.calculateHandValue(game.getPlayerHand()) == 21) {
                    handleNaturalBlackjack();
                } else {
                    txtStatus.setText("Your Turn");
                    setButtonsEnabled(true);
                    resetAndStartTimer(); // הטיימר מתחיל רק כשהשחקן באמת יכול לשחק
                }
            }
        }, 1600);
    }

    private void playerHit() {
        if (!roundActive) return;
        resetAndStartTimer(); // איפוס טיימר בכל פעולה
        addCardToLayoutAnimated(playerLayout, game.playerHit());

        if (game.isBust(game.getPlayerHand())) {
            showResult("BUST! YOU LOSE");
            updateMoney("LOSE");
        }
    }

    private void dealerTurn() {
        if (!roundActive) return;
        if (turnTimer != null) turnTimer.cancel();
        setButtonsEnabled(false);
        revealHiddenCard();

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                while (game.dealerShouldHit()) {
                    runOnUiThread(() -> addCardToLayoutAnimated(dealerLayout, game.dealerHit()));
                    Thread.sleep(800);
                }
                runOnUiThread(this::checkWinner);
            } catch (Exception ignored) {}
        }).start();
    }

    private void revealHiddenCard() {
        for (int i = 0; i < dealerLayout.getChildCount(); i++) {
            View rowView = dealerLayout.getChildAt(i);
            if (rowView instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) rowView;
                View hiddenCard = row.findViewWithTag("hidden_card");
                if (hiddenCard != null) {
                    row.removeView(hiddenCard);
                    addCardToRow(row, game.getDealerHand().get(1));
                    return;
                }
            }
        }
    }

    private void checkWinner() {
        int pVal = game.calculateHandValue(game.getPlayerHand());
        int dVal = game.calculateHandValue(game.getDealerHand());

        if (dVal > 21) {
            showResult("DEALER BUST – WIN!");
            updateMoney("WIN");
        } else if (pVal > dVal) {
            showResult("YOU WIN!");
            updateMoney("WIN");
        } else if (pVal < dVal) {
            showResult("YOU LOSE!");
            updateMoney("LOSE");
        } else {
            showResult("PUSH (DRAW)");
            updateMoney("PUSH");
        }
    }

    private void handleNaturalBlackjack() {
        revealHiddenCard();
        showResult("NATURAL BLACKJACK!");
        updateMoney("BLACKJACK_WIN");
        Toast.makeText(this, "🔥 BLACKJACK! 🔥", Toast.LENGTH_SHORT).show();
    }

    private void showResult(String text) {
        roundActive = false;
        if (turnTimer != null) turnTimer.cancel();

        setButtonsEnabled(false);
        btnPlay.setEnabled(true);
        edtBet.setEnabled(true);

        txtResult.setText(text);
        txtResult.setVisibility(View.VISIBLE);
        txtStatus.setText("Round Finished");

        historyList.add(0, text + " | Score: " + game.calculateHandValue(game.getPlayerHand()));
    }

    private void updateMoney(String resultType) {
        int betAmount = Integer.parseInt(edtBet.getText().toString());
        if (resultType.equals("WIN")) {
            currentBalance += betAmount;
        } else if (resultType.equals("LOSE")) {
            currentBalance -= betAmount;
        } else if (resultType.equals("BLACKJACK_WIN")) {
            currentBalance += (int)(betAmount * 1.5);
        }
        db.updateBalance(loggedInUser, currentBalance);
        updateBalanceDisplay();
    }

    private void addCardToLayoutAnimated(LinearLayout handLayout, Card card) {
        LinearLayout lastRow;
        if (handLayout.getChildCount() == 0) {
            lastRow = createNewCardRow(handLayout);
        } else {
            lastRow = (LinearLayout) handLayout.getChildAt(handLayout.getChildCount() - 1);
            if (lastRow.getChildCount() == 4) {
                lastRow = createNewCardRow(handLayout);
            }
        }
        addCardToRow(lastRow, card);
    }

    private void addCardToRow(LinearLayout row, Card card) {
        ImageView img = new ImageView(this);
        img.setImageResource(card.getImageResId(this));

        // הקטנה קלה של הקלפים ל-200x300
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 300);
        params.setMargins(10, 10, 10, 10);
        img.setLayoutParams(params);

        img.setAlpha(0f);
        img.setTranslationY(-50f);
        row.addView(img);
        img.animate().alpha(1f).translationY(0f).setDuration(400).start();
    }

    private void addHiddenCardToDealer() {
        LinearLayout lastRow;
        if (dealerLayout.getChildCount() == 0) {
            lastRow = createNewCardRow(dealerLayout);
        } else {
            lastRow = (LinearLayout) dealerLayout.getChildAt(dealerLayout.getChildCount() - 1);
        }

        ImageView img = new ImageView(this);
        img.setTag("hidden_card");
        img.setImageResource(R.drawable.card_back);

        // הקטנה קלה של הקלפים ל-200x300
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 300);
        params.setMargins(10, 10, 10, 10);
        img.setLayoutParams(params);

        img.setAlpha(0f);
        img.setTranslationY(-50f);
        lastRow.addView(img);
        img.animate().alpha(1f).translationY(0f).setDuration(400).start();
    }

    private LinearLayout createNewCardRow(LinearLayout handLayout) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(params);
        handLayout.addView(row);
        return row;
    }

    private void resetAndStartTimer() {
        if (turnTimer != null) turnTimer.cancel();
        turnTimer = new CountDownTimer(15000, 1000) {
            public void onTick(long millisUntilFinished) {
                if (roundActive) {
                    txtStatus.setText("Time: " + millisUntilFinished / 1000 + "s");
                }
            }
            public void onFinish() {
                if (roundActive) {
                    showResult("TIMEOUT - YOU LOSE");
                    updateMoney("LOSE");
                }
            }
        }.start();
    }

    private void setButtonsEnabled(boolean playing) {
        btnHit.setEnabled(playing);
        btnStand.setEnabled(playing);
        btnPlay.setEnabled(!playing);

        float alphaValue = playing ? 1.0f : 0.4f;
        btnHit.setAlpha(alphaValue);
        btnStand.setAlpha(alphaValue);
        btnPlay.setAlpha(playing ? 0.4f : 1.0f);
    }

    private void showNoMoneyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("נגמר הכסף!")
                .setMessage("רוצה לשחק איקס-עיגול ולזכות ב-$1000?")
                .setPositiveButton("כן, בוא נשחק!", (dialog, which) -> {
                    Intent intent = new Intent(GameActivity.this, tic_tac_toe.class);
                    intent.putExtra("username", loggedInUser);
                    startActivity(intent);
                })
                .setNegativeButton("לא עכשיו", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_history) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putStringArrayListExtra("game_history", historyList);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.menu_rules) {
            showInfoDialog("📜 חוקי הבלאק ג'ק",
                    "1. המטרה: להגיע קרוב ל-21 מבלי לעבור אותם.\n" +
                            "2. נצחון רגיל משלם פי 2.\n" +
                            "3. בלאק ג'ק טבעי משלם פי 1.5 מההימור.\n" +
                            "4. הדילר חייב לעצור ב-17.");
            return true;
        }
        else if (id == R.id.menu_tips) {
            showInfoDialog("💡 טיפים למקצוענים",
                    "• תמיד תעצור (Stand) ב-17  ומעלה.\n" +
                            "• טיפ זהב: אם קיבלת זוג אסים או זוג שמיניות - תמיד כדאי לפצל (Split) כדי להגדיל סיכויי זכייה!");
            return true;
        }
        else if (id == R.id.menu_about) {
            showInfoDialog("ℹ️ אודות",
                    "נוצר על ידי נועם שהרבני בשנת 2026.");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // פונקציית העזר להצגת ה-AlertDialog
    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("הבנתי", null)
                .show();
    }
}