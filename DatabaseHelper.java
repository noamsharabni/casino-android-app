package com.example.blackjack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "users.db";
    public static final String TABLE_NAME = "users";
    public static final String COL_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD = "password";
    public static final String COL_BALANCE = "balance";

    // כתובת ה-Firebase שלך
    private static final String FIREBASE_URL = "https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_USERNAME + " TEXT UNIQUE," +
                COL_PASSWORD + " TEXT," +
                COL_BALANCE + " INTEGER DEFAULT 1000)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_BALANCE + " INTEGER DEFAULT 1000");
        } catch (Exception e) {
            // העמודה כבר קיימת
        }
    }

    // --- עדכון: רישום משתמש חדש גם ב-SQLite וגם ב-Firebase ---
    public boolean addUser(String username, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USERNAME, username);
        cv.put(COL_PASSWORD, password);
        cv.put(COL_BALANCE, 1000); // יתרת התחלה

        long res = db.insert(TABLE_NAME, null, cv);

        if (res != -1) {
            // --- עדכון ל-Firebase ---
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app");
            DatabaseReference userRef = database.getReference("users").child(username);

            Map<String, Object> userData = new HashMap<>();
            userData.put("username", username);
            userData.put("password", password); // הוספנו את הסיסמה לענן!
            userData.put("balance", 1000);

            userRef.setValue(userData);
            return true;
        }
        return false;
    }

    // --- הוספת פונקציה: הכנסת משתמש מהענן למכשיר (משמש ב-Login) ---
    public void insertUser(String username, String password, int balance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USERNAME, username);
        cv.put(COL_PASSWORD, password);
        cv.put(COL_BALANCE, balance);
        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean validate(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME +
                " WHERE username=? AND password=?", new String[]{username, password});
        boolean valid = cursor.getCount() > 0;
        cursor.close();
        return valid;
    }

    public boolean userExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME +
                " WHERE username=?", new String[]{username});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public int getUserBalance(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        int balance = 0;
        Cursor cursor = db.rawQuery("SELECT " + COL_BALANCE + " FROM " + TABLE_NAME +
                " WHERE " + COL_USERNAME + "=?", new String[]{username});

        if (cursor.moveToFirst()) {
            balance = cursor.getInt(0);
        }
        cursor.close();
        return balance;
    }

    public boolean updateBalance(String username, int newBalance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_BALANCE, newBalance);

        int result = db.update(TABLE_NAME, values, COL_USERNAME + " = ?", new String[]{username});

        if (result > 0) {
            FirebaseDatabase database = FirebaseDatabase.getInstance(FIREBASE_URL);
            DatabaseReference userRef = database.getReference("users").child(username);
            userRef.child("balance").setValue(newBalance);
            return true;
        }
        return false;
    }
}