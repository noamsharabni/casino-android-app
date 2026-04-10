package com.example.blackjack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Random;

public class SlotMachine extends AppCompatActivity {

    private ImageView imgSlot1, imgSlot2, imgSlot3;
    private LinearLayout layoutHandle;
    private Button btnSpin;
    private TextView txtBalance;
    private EditText editBetAmount;

    private DatabaseHelper db;
    private String username;
    private int currentBet;

    // מערך התמונות המעודכן שלך (ב-lowercase)
    private int[] slotImages = {
            R.drawable.img_cherry, R.drawable.img_seven,
            R.drawable.img_diamond, R.drawable.img_coin, R.drawable.img_bell
    };

    private int res1, res2, res3;
    private boolean isSpinning = false;
    private Handler handler = new Handler();
    private Random random = new Random();

    private static ArrayList<String> slotHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_slot_machine);

        // אתחול רכיבים
        imgSlot1 = findViewById(R.id.img_slot1);
        imgSlot2 = findViewById(R.id.img_slot2);
        imgSlot3 = findViewById(R.id.img_slot3);
        layoutHandle = findViewById(R.id.layout_handle);
        btnSpin = findViewById(R.id.btn_spin);
        txtBalance = findViewById(R.id.txt_balance);
        editBetAmount = findViewById(R.id.edit_bet_amount);
        // קישור הכפתור מה-XML
        Button btnExitToLobby = findViewById(R.id.btn_exit_to_lobby);
        Button btnHistory = findViewById(R.id.btn_history);

        btnExitToLobby.setOnClickListener(v -> {
            // יצירת מעבר ללובי
            Intent intent = new Intent(SlotMachine.this, Lobby.class);

            // שליחת שם המשתמש (במכונה השתמשת ב-username)
            intent.putExtra("username", username);

            // ניקוי המחסנית
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            startActivity(intent);
            finish(); // סגירת המכונה
        });

        db = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            // שליחת רשימת ההיסטוריה הסטטית (וודא שקראת לה ככה)
            intent.putStringArrayListExtra("game_history", slotHistory);
            intent.putExtra("username", username);
            startActivity(intent);
        });
        updateBalanceDisplay();

        btnSpin.setOnClickListener(v -> handleSpinAction());
        layoutHandle.setOnClickListener(v -> handleSpinAction());
    }

    // רענון יתרה כשחוזרים מאיקס-עיגול
    @Override
    protected void onResume() {
        super.onResume();
        updateBalanceDisplay();
    }

    private void updateBalanceDisplay() {
        int balance = db.getUserBalance(username);
        txtBalance.setText("Balance: $" + balance);
    }

    private void handleSpinAction() {
        if (isSpinning) return;

        String betStr = editBetAmount.getText().toString();
        if (betStr.isEmpty()) {
            Toast.makeText(this, "נא להזין סכום הימור", Toast.LENGTH_SHORT).show();
            return;
        }

        currentBet = Integer.parseInt(betStr);
        int balance = db.getUserBalance(username);

        // בדיקת יתרה והצעת איקס-עיגול (בדיוק כמו ברולטה)
        if (currentBet > balance) {
            showNoMoneyDialog();
            return;
        }

        if (currentBet <= 0) {
            Toast.makeText(this, "סכום לא תקין", Toast.LENGTH_SHORT).show();
            return;
        }

        isSpinning = true;
        btnSpin.setEnabled(false);

        // אנימציית ידית ואז התחלת הסיבוב
        layoutHandle.animate().translationY(100).setDuration(300).withEndAction(() -> {
            layoutHandle.animate().translationY(0).setDuration(300);
            startSpinningWithAnimation();
        });
    }

    private void showNoMoneyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("נגמר הכסף!")
                .setMessage("אין לך מספיק כסף להימור הזה. רוצה לשחק איקס-עיגול ולזכות ב-$1000?")
                .setPositiveButton("כן", (dialog, which) -> {
                    Intent intent = new Intent(this, tic_tac_toe.class); // וודא שזה שם ה-class שלך
                    intent.putExtra("username", username);
                    intent.putExtra("challenge_mode", true);
                    startActivity(intent);
                })
                .setNegativeButton("לא", null).show();
    }

    private void startSpinningWithAnimation() {
        final long startTime = System.currentTimeMillis();

        Runnable spinRunnable = new Runnable() {
            int delay1 = 50, delay2 = 50, delay3 = 50;

            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed < 2000) {
                    res1 = random.nextInt(slotImages.length);
                    imgSlot1.setImageResource(slotImages[res1]);
                    if (elapsed > 1500) delay1 += 12; // האטה הדרגתית
                }
                if (elapsed < 3000) {
                    res2 = random.nextInt(slotImages.length);
                    imgSlot2.setImageResource(slotImages[res2]);
                    if (elapsed > 2500) delay2 += 15;
                }
                if (elapsed < 4000) {
                    res3 = random.nextInt(slotImages.length);
                    imgSlot3.setImageResource(slotImages[res3]);
                    if (elapsed > 3500) delay3 += 18;

                    handler.postDelayed(this, Math.max(delay1, Math.max(delay2, delay3)));
                } else {
                    checkWin(); // סוף האנימציה - בדיקת תוצאה
                }
            }
        };
        handler.post(spinRunnable);
    }

    private void checkWin() {
        int balance = db.getUserBalance(username);
        int winAmount = 0;

        // לוגיקת הניקוד החדשה שלך:
        if (res1 == res2 && res2 == res3) {
            // 3 צורות זהות: כפול 100
            winAmount = currentBet * 100;
            Toast.makeText(this, "🔥 JACKPOT!!! זכית ב-$" + winAmount, Toast.LENGTH_LONG).show();
        }
        else if (res1 == res2 || res2 == res3 || res1 == res3) {
            // 2 צורות זהות: כפול 2
            winAmount = currentBet * 2;
            Toast.makeText(this, "מצוין! 2 צורות זהות - זכית ב-$" + winAmount, Toast.LENGTH_SHORT).show();
        }
        else if (slotImages[res1] == R.drawable.img_seven ||
                slotImages[res2] == R.drawable.img_seven ||
                slotImages[res3] == R.drawable.img_seven) {
            // צורה נדירה (7): כפול 1.5
            winAmount = (int) (currentBet * 1.5);
            Toast.makeText(this, "מזל של שבע! זכית ב-$" + winAmount, Toast.LENGTH_SHORT).show();
        }

        if (winAmount > 0) {
            db.updateBalance(username, balance - currentBet + winAmount);
            slotHistory.add(0, "SLOTS: WIN +$" + winAmount);
        } else {
            db.updateBalance(username, balance - currentBet + winAmount);
            Toast.makeText(this, "הפסדת. נסה שוב!", Toast.LENGTH_SHORT).show();
            slotHistory.add(0, "SLOTS: LOSE -$" + currentBet);
        }
        updateBalanceDisplay();
        isSpinning = false;
        btnSpin.setEnabled(true);
    }
}