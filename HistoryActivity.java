package com.example.blackjack;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. קישור הרכיבים מה-XML
        ListView listView = findViewById(R.id.history_list_view);
        Button btnBack = findViewById(R.id.btn_back_to_game);

        // 2. קבלת הנתונים שנשלחו (Intent Extra)
        ArrayList<String> realHistory = getIntent().getStringArrayListExtra("game_history");

        // בדיקה אם הרשימה ריקה
        if (realHistory == null || realHistory.isEmpty()) {
            realHistory = new ArrayList<>();
            realHistory.add("No games recorded yet.");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                realHistory
        ) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                android.widget.TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(android.graphics.Color.parseColor("#C9A84C"));
                tv.setBackgroundColor(android.graphics.Color.parseColor("#161616"));
                tv.setPadding(24, 20, 24, 20);
                return view;
            }
        };

        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedGame = (String) parent.getItemAtPosition(position);
                Toast.makeText(HistoryActivity.this, "Details: " + selectedGame, Toast.LENGTH_SHORT).show();
            }
        });
        btnBack.setOnClickListener(v -> finish());
    }
}