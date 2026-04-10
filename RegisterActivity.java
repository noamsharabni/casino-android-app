package com.example.blackjack;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText edtUsername, edtPassword;
    Button btnRegisterSubmit, btnGoToLogin;
    CheckBox cbTerms;
    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtUsername = findViewById(R.id.edt_username_register);
        edtPassword = findViewById(R.id.edt_password_register);
        btnRegisterSubmit = findViewById(R.id.btn_register_submit);
        btnGoToLogin = findViewById(R.id.btn_go_to_login);
        cbTerms = findViewById(R.id.cb_terms);

        db = new DatabaseHelper(this);

        btnRegisterSubmit.setOnClickListener(v -> {
            String user = edtUsername.getText().toString().trim();
            String pass = edtPassword.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Please agree to the terms", Toast.LENGTH_SHORT).show();
                return;
            }

            // בדיקה ב-FIREBASE לפני הכל
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app");
            DatabaseReference userRef = database.getReference("users").child(user);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Toast.makeText(RegisterActivity.this, "Username already taken!", Toast.LENGTH_SHORT).show();
                    } else {
                        // אם השם פנוי בענן, נבצע את הרישום
                        performRegistration(user, pass, database);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(RegisterActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void performRegistration(String user, String pass, FirebaseDatabase database) {
        // בודק אם המשתמש קיים ב-SQLite כדי למנוע את ה-Error שקיבלת
        if (db.userExists(user)) {
            // אם הוא קיים מקומית אבל לא בענן, פשוט נסנכרן אותו לענן
            syncToFirebase(user, pass, database);
        } else {
            // אם הוא לא קיים בכלל, נוסיף אותו
            if (db.addUser(user, pass)) {
                syncToFirebase(user, pass, database);
            } else {
                Toast.makeText(this, "Fatal Error saving to local DB", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void syncToFirebase(String user, String pass, FirebaseDatabase database) {
        DatabaseReference userRef = database.getReference("users").child(user);

        Map<String, Object> userData = new HashMap<>();
        userData.put("username", user);
        userData.put("password", pass);
        userData.put("balance", 1000);
        userData.put("created_at", System.currentTimeMillis());

        userRef.setValue(userData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Registered successfully!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Cloud Sync Failed!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}