package com.example.blackjack;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class tic_tac_toe extends AppCompatActivity {

    private Button[] buttons = new Button[9];
    private boolean playerTurn = true; // Player is X
    private int moves = 0;
    private DatabaseHelper db;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tic_tac_toe);
        db = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");

        for (int i = 0; i < 9; i++) {
            String buttonID = "btn_" + i;
            int resID = getResources().getIdentifier(buttonID, "id", getPackageName());
            buttons[i] = findViewById(resID);
            int finalI = i;
            buttons[i].setOnClickListener(v -> onButtonClick(finalI));
        }

        findViewById(R.id.btn_tictac_exit).setOnClickListener(v -> finish());
    }

    private void onButtonClick(int index) {
        if (!playerTurn || !buttons[index].getText().toString().equals("")) return;

        buttons[index].setText("X");
        moves++;
        if (checkWin("X")) {
            handleEndGame("WIN");
            return;
        }

        if (moves < 9) {
            playerTurn = false;
            computerMove();
        } else {
            handleEndGame("DRAW");
        }
    }

    private void computerMove() {
        // 1. ניסיון לנצח או לחסום
        int move = findBestMove();

        // 2. אם אין מהלך קריטי, בחר באקראי
        if (move == -1) {
            Random rand = new Random();
            do {
                move = rand.nextInt(9);
            } while (!buttons[move].getText().toString().equals(""));
        }

        buttons[move].setText("O");
        moves++;
        if (checkWin("O")) {
            handleEndGame("LOSE");
        }
        playerTurn = true;
    }

    // פונקציית עזר לבוט חכם
    private int findBestMove() {
        // בדיקה עבור 'O' (ניצחון) ואז עבור 'X' (חסימה)
        String[] players = {"O", "X"};
        for (String p : players) {
            int[][] winPositions = {{0,1,2}, {3,4,5}, {6,7,8}, {0,3,6}, {1,4,7}, {2,5,8}, {0,4,8}, {2,4,6}};
            for (int[] pos : winPositions) {
                String b1 = buttons[pos[0]].getText().toString();
                String b2 = buttons[pos[1]].getText().toString();
                String b3 = buttons[pos[2]].getText().toString();

                if (b1.equals(p) && b2.equals(p) && b3.equals("")) return pos[2];
                if (b1.equals(p) && b3.equals(p) && b2.equals("")) return pos[1];
                if (b2.equals(p) && b3.equals(p) && b1.equals("")) return pos[0];
            }
        }
        return -1; // לא נמצא מהלך קריטי
    }

    private boolean checkWin(String s) {
        int[][] winPositions = {{0,1,2}, {3,4,5}, {6,7,8}, {0,3,6}, {1,4,7}, {2,5,8}, {0,4,8}, {2,4,6}};
        for (int[] pos : winPositions) {
            if (buttons[pos[0]].getText().toString().equals(s) &&
                    buttons[pos[1]].getText().toString().equals(s) &&
                    buttons[pos[2]].getText().toString().equals(s)) return true;
        }
        return false;
    }

    private void handleEndGame(String result) {
        String msg;
        if (result.equals("WIN")) {
            msg = "You won $1000!";
            int newBalance = db.getUserBalance(username) + 1000;
            db.updateBalance(username, newBalance);
        } else if (result.equals("LOSE")) {
            msg = "You lost!";
        } else {
            msg = "Draw!";
        }

        // התיקון כאן: בודקים אם זה לא WIN (כלומר זה או LOSE או DRAW)
        String buttonText = result.equals("WIN") ? "Back to the game" : "Play Again";

        new AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(msg)
                .setPositiveButton(buttonText, (d, w) -> {
                    if (!result.equals("WIN")) {
                        // אם זה הפסד או תיקו - מאפסים ומשחקים שוב
                        resetGame();
                    } else {
                        // אם זה ניצחון - חוזרים לתפריט/משחק
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void resetGame() {
        for (Button b : buttons) b.setText("");
        moves = 0;
        playerTurn = true;
    }
}