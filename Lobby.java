package com.example.blackjack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class Lobby extends AppCompatActivity {

    private TextView tvBalance;
    private DatabaseHelper db;
    private String username;

    private TextView tvTopPlayer;
    private DatabaseReference mDatabase;
    private EditText etRoomId;

    private static final int MIN_BALANCE = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        db = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");

        tvBalance   = findViewById(R.id.tv_lobby_balance);
        tvTopPlayer = findViewById(R.id.tv_top_player);
        etRoomId    = findViewById(R.id.et_room_id);

        mDatabase = FirebaseDatabase.getInstance(
                        "https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("users");
        getCasinoKing();

        Button btnTicTacToe    = findViewById(R.id.btn_goto_tictactoe);
        Button btnBlackjack    = findViewById(R.id.btn_lobby_blackjack);
        Button btnRoulette     = findViewById(R.id.btn_lobby_roulette);
        Button btnSlots        = findViewById(R.id.btn_lobby_slots);
        Button btnOfflinePoker = findViewById(R.id.btn_play_poker);
        Button btnLogout       = findViewById(R.id.btn_lobby_logout);
        Button btnCreateRoom   = findViewById(R.id.btn_create_room);
        Button btnJoinRoom     = findViewById(R.id.btn_join_room);

        // ===== OFFLINE POKER =====
        btnOfflinePoker.setOnClickListener(v -> {
            Intent intent = new Intent(this, Poker.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });

        // ===== CREATE ROOM =====
        btnCreateRoom.setOnClickListener(v -> {
            int balance = db.getUserBalance(username);
            if (balance < MIN_BALANCE) {
                Toast.makeText(this, "אין מספיק כסף! מינימום $" + MIN_BALANCE + " כדי לשחק", Toast.LENGTH_LONG).show();
                return;
            }

            String randomId = String.valueOf((int)(Math.random() * 9000) + 1000);

            DatabaseReference roomRef = FirebaseDatabase.getInstance(
                            "https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("poker_rooms").child(randomId);

            roomRef.child("game_state").child("status").setValue("waiting");

            Intent intent = new Intent(this, PokerOnline.class);
            intent.putExtra("username", username);
            intent.putExtra("room_id", randomId);
            intent.putExtra("role", "host");
            startActivity(intent);
        });

        // ===== JOIN ROOM =====
        btnJoinRoom.setOnClickListener(v -> {
            String roomId = etRoomId.getText().toString().trim();
            if (roomId.isEmpty()) {
                Toast.makeText(this, "Please enter Room ID", Toast.LENGTH_SHORT).show();
                return;
            }

            int balance = db.getUserBalance(username);
            if (balance < MIN_BALANCE) {
                Toast.makeText(this, "אין מספיק כסף! מינימום $" + MIN_BALANCE + " כדי לשחק", Toast.LENGTH_LONG).show();
                return;
            }

            DatabaseReference checkRoom = FirebaseDatabase.getInstance(
                            "https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("poker_rooms").child(roomId);

            checkRoom.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Intent intent = new Intent(Lobby.this, PokerOnline.class);
                        intent.putExtra("username", username);
                        intent.putExtra("room_id", roomId);
                        intent.putExtra("role", "guest");
                        startActivity(intent);
                    } else {
                        Toast.makeText(Lobby.this, "Room not found!", Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        });

        // ===== שאר המשחקים =====
        btnTicTacToe.setOnClickListener(v -> navigateTo(tic_tac_toe.class));
        btnBlackjack.setOnClickListener(v -> navigateTo(GameActivity.class));
        btnRoulette.setOnClickListener(v -> navigateTo(RouletteActivity.class));
        btnSlots.setOnClickListener(v -> navigateTo(SlotMachine.class));
        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void navigateTo(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.putExtra("username", username);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int balance = db.getUserBalance(username);
        tvBalance.setText("Your Balance: $" + balance);
        mDatabase.child(username).child("balance").setValue(balance);
        mDatabase.child(username).child("username").setValue(username);
    }

    private void getCasinoKing() {
        Query kingQuery = mDatabase.orderByChild("balance").limitToLast(1);
        kingQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        String name    = userSnapshot.child("username").getValue(String.class);
                        Long   balance = userSnapshot.child("balance").getValue(Long.class);
                        if (name != null && balance != null) {
                            tvTopPlayer.setText("👑 King: " + name + " ($" + balance + ")");
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}