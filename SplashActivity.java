package com.example.blackjack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    ImageView logo;
    View rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        logo = findViewById(R.id.logo);

        rootLayout = findViewById(android.R.id.content);

        new Handler().postDelayed(() -> {

            Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);

            rootLayout.startAnimation(fadeOut);

            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {

                    // הופך את המסך לשקוף לגמרי – שלא יראה שוב!
                    rootLayout.setVisibility(View.INVISIBLE);

                    // מעביר ל־LOGIN
                    Intent i = new Intent(SplashActivity.this, LoginActivity.class);
                    startActivity(i);

                    // אין אנימציה מעבר
                    overridePendingTransition(0, 0);

                    finish();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });

        }, 2000);
    }
}
