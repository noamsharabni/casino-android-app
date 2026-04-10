package com.example.blackjack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Random;

public class RouletteActivity extends AppCompatActivity {

    private TextView tvBalance, tvResultNumber;
    private EditText etBetAmount;
    private GridLayout glNumberBoard;
    private Button btnBetRed, btnBetBlack, btnExitToLobby;

    private int balance;
    private DatabaseHelper db;
    private String loggedInUser;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentBetType = "";
    private int selectedNumber = -1;
    private boolean isSpinning = false;
    private int animationDelay = 50;

    // היסטוריית משחקים סטטית שתשותף עם HistoryActivity
    private static ArrayList<String> rouletteHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roulette);

        // 1. הגדרת ה-Toolbar (חייב להיות ראשון)
        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT); // שקיפות מוחלטת

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // 2. אתחול דאטה
        db = new DatabaseHelper(this);
        loggedInUser = getIntent().getStringExtra("username");
        if (loggedInUser == null) {
            Toast.makeText(this, "Error: User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        balance = db.getUserBalance(loggedInUser);

        // 3. קישור רכיבי UI
        tvBalance = findViewById(R.id.tvBalance);
        tvResultNumber = findViewById(R.id.tvResultNumber);
        etBetAmount = findViewById(R.id.etBetAmount);
        glNumberBoard = findViewById(R.id.glNumberBoard);
        btnBetRed = findViewById(R.id.btnBetRed);
        btnBetBlack = findViewById(R.id.btnBetBlack);
        btnExitToLobby = findViewById(R.id.btn_exit_to_lobby);

        tvBalance.setText("Balance: $" + balance);

        // 4. מאזינים ללחיצות
        btnExitToLobby.setOnClickListener(v -> {
            Intent intent = new Intent(this, Lobby.class);
            intent.putExtra("username", loggedInUser);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnBetRed.setOnClickListener(v -> startBetProcess("RED", -1));
        btnBetBlack.setOnClickListener(v -> startBetProcess("BLACK", -1));

        setupBoardButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        balance = db.getUserBalance(loggedInUser);
        tvBalance.setText("Balance: $" + balance);
    }

    private void setupBoardButtons() {
        for (int i = 0; i < glNumberBoard.getChildCount(); i++) {
            View child = glNumberBoard.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                btn.setOnClickListener(v -> {
                    try {
                        int num = Integer.parseInt(btn.getText().toString());
                        startBetProcess("NUMBER", num);
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    private void startBetProcess(String type, int num) {
        if (isSpinning) return;

        String amountStr = etBetAmount.getText().toString();
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Enter bet amount", Toast.LENGTH_SHORT).show();
            return;
        }

        int bet = Integer.parseInt(amountStr);
        if (bet > balance) {
            showNoMoneyDialog();
            return;
        }
        if (bet <= 0) return;

        currentBetType = type;
        selectedNumber = num;

        updateBalance(-bet);
        runSlowdownAnimation(bet);
    }

    private void runSlowdownAnimation(int betAmount) {
        isSpinning = true;
        animationDelay = 50;
        final long startTime = System.currentTimeMillis();
        final long totalDuration = 3000;
        final int finalWinningNumber = random.nextInt(37); // רולטה עד 36 כולל ה-0

        Runnable animationRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < totalDuration) {
                    int tempNum = random.nextInt(37);
                    tvResultNumber.setText(String.valueOf(tempNum));
                    tvResultNumber.setTextColor(getRouletteColor(tempNum));

                    if (elapsed > totalDuration * 0.7) animationDelay += 20;
                    handler.postDelayed(this, animationDelay);
                } else {
                    tvResultNumber.setText(String.valueOf(finalWinningNumber));
                    tvResultNumber.setTextColor(getRouletteColor(finalWinningNumber));
                    checkWin(finalWinningNumber, betAmount);
                }
            }
        };
        handler.post(animationRunnable);
    }

    private void checkWin(int winNum, int bet) {
        isSpinning = false;
        boolean won = false;
        int profit = 0;
        int winningColor = getRouletteColor(winNum);

        if (currentBetType.equals("NUMBER") && selectedNumber == winNum) {
            won = true; profit = bet * 36;
        } else if (currentBetType.equals("RED") && winningColor == Color.RED && winNum != 0) {
            won = true; profit = bet * 2;
        } else if (currentBetType.equals("BLACK") && winningColor == Color.BLACK && winNum != 0) {
            won = true; profit = bet * 2;
        }

        if (won) {
            updateBalance(profit);
            Toast.makeText(this, "Winner! +$" + profit, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Number: " + winNum + ". You Lost.", Toast.LENGTH_SHORT).show();
        }

        // --- כאן ההוספה להיסטוריה ---
        String colorStr = (winNum == 0) ? "Green" : (winNum % 2 != 0 ? "Red" : "Black");
        String resultLine = "ROULETTE: " + (won ? "WIN" : "LOSE") + " - Num: " + winNum + " (" + colorStr + ")";
        rouletteHistory.add(0, resultLine);
    }

    private int getRouletteColor(int n) {
        if (n == 0) return Color.parseColor("#44BB44");
        return (n % 2 != 0) ? Color.RED : Color.BLACK;
    }

    private void updateBalance(int amount) {
        balance += amount;
        tvBalance.setText("Balance: $" + balance);
        db.updateBalance(loggedInUser, balance);
    }

    private void showNoMoneyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("נגמר הכסף!")
                .setMessage("רוצה לשחק איקס-עיגול ולזכות ב-$1000?")
                .setPositiveButton("כן, בוא נשחק!", (dialog, which) -> {
                    Intent intent = new Intent(RouletteActivity.this, tic_tac_toe.class);
                    intent.putExtra("username", loggedInUser);
                    startActivity(intent);
                })
                .setNegativeButton("לא עכשיו", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.roulette_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_history) {
            Intent intent = new Intent(this, HistoryActivity.class);
            // כאן התיקון הקריטי: שולחים את הרשימה הסטטית להיסטוריה
            intent.putStringArrayListExtra("game_history", rouletteHistory);
            intent.putExtra("username", loggedInUser);
            startActivity(intent);
            return true;
        }

        else if (id == R.id.menu_rules) {
            new AlertDialog.Builder(this)
                    .setTitle("🎰 חוקי הרולטה")
                    .setMessage("• הימור על צבע (אדום/שחור): זכייה פי 2.\n" +
                            "• הימור על מספר בודד: זכייה פי 36!\n" +
                            "💡 הבית מנצח את כולם שהמספר על ה-0 הירוק!")
                    .setPositiveButton("הבנתי", null)
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("סגור", null)
                .show();
    }
}