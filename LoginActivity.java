package com.example.blackjack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    EditText edtUsername, edtPassword;
    Button btnLoginSubmit, btnGoToRegister;
    DatabaseHelper db; // נשמור אותו כדי לעדכן את ה-SQLite המקומי אחרי הכניסה

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtUsername = findViewById(R.id.edt_username_login);
        edtPassword = findViewById(R.id.edt_password_login);
        btnLoginSubmit = findViewById(R.id.btn_login_submit);
        btnGoToRegister = findViewById(R.id.btn_go_to_register);

        db = new DatabaseHelper(this);

        FirebaseDatabase database = FirebaseDatabase.getInstance("https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app");

        btnLoginSubmit.setOnClickListener(v -> {
            String user = edtUsername.getText().toString().trim();
            String pass = edtPassword.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // בדיקה ב-Firebase במקום ב-SQLite המקומי
            DatabaseReference userRef = database.getReference("users").child(user);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // המשתמש קיים בענן! בוא נבדוק סיסמה
                        String cloudPass = snapshot.child("password").getValue(String.class);

                        if (cloudPass != null && cloudPass.equals(pass)) {
                            // סיסמה נכונה! עכשיו נוריד את היתרה (Balance) מהענן
                            Integer cloudBalance = snapshot.child("balance").getValue(Integer.class);
                            if (cloudBalance == null) cloudBalance = 1000; // ברירת מחדל

                            // סנכרון הפוך: נעדכן את ה-SQLite המקומי כדי שהמשחקים יעבדו חלק
                            if (!db.userExists(user)) {
                                db.insertUser(user, pass, cloudBalance);
                            } else {
                                db.updateBalance(user, cloudBalance);
                            }

                            Toast.makeText(LoginActivity.this, "Welcome back, " + user, Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(LoginActivity.this, Lobby.class);
                            intent.putExtra("username", user);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Wrong password!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "User does not exist in Cloud!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(LoginActivity.this, "Database Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }
}