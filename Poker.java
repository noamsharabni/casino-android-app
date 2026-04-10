package com.example.blackjack;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;

public class Poker extends AppCompatActivity {

    // ===== UI =====
    private PokerSeat me, bot;
    private Button btnFold, btnCheck, btnCall, btnBetRaise;
    private ImageButton btnExit;
    private EditText etBetAmount;
    private TextView txtPot, txtAnnouncer, txtTimer;
    private LinearLayout communityCardsLayout;
    private FloatingActionButton fabGemini, btnAllIn;
    private CardView cardAiMessage;
    private TextView txtAiRecommendation;

    // ===== Game State =====
    private String username;
    private DatabaseHelper db;
    private int userBalance, botBalance = 10000, pot = 0;
    private final int SMALL_BLIND = 50;
    private boolean roundActive = false;
    private boolean isShowdownActive = false;
    private PokerDeck deck;

    private Handler handler = new Handler(Looper.getMainLooper());

    // שלב: 0=PreFlop, 1=Flop, 2=Turn, 3=River
    private int currentPhase = 0;
    private int lastRevealedPhase = -1;

    private int currentHighestBet = 0;
    private int myCurrentBet = 0;
    private int botCurrentBet = 0;
    private boolean userActedThisRound = false;

    private PokerCard[] communityCards = new PokerCard[5];

    // ===== Timer =====
    private int timeLeft = 45;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isRaiseLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.poker_activity);

        initViews();

        db = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");
        if (username == null) username = "Player";
        userBalance = db.getUserBalance(username);

        startNewHand();
    }

    private void initViews() {
        txtAnnouncer         = findViewById(R.id.txt_round_announcer);
        txtTimer             = findViewById(R.id.txt_timer);
        txtPot               = findViewById(R.id.txt_pot);
        etBetAmount          = findViewById(R.id.et_bet_amount);
        communityCardsLayout = findViewById(R.id.community_cards_layout);
        btnFold              = findViewById(R.id.btn_fold);
        btnCheck             = findViewById(R.id.btn_check);
        btnCall              = findViewById(R.id.btn_call);
        btnBetRaise          = findViewById(R.id.btn_bet_raise);
        btnExit              = findViewById(R.id.btn_exit_poker);
        btnAllIn             = findViewById(R.id.btn_all_in);
        fabGemini            = findViewById(R.id.fab_gemini);
        cardAiMessage        = findViewById(R.id.card_ai_message);
        txtAiRecommendation  = findViewById(R.id.txt_ai_recommendation);
        me  = new PokerSeat(findViewById(R.id.slot_bottom_me));
        bot = new PokerSeat(findViewById(R.id.slot_top_center));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        // ===== AI =====
        PokerStrategyService pokerAI = new PokerStrategyService();
        fabGemini.setOnClickListener(v -> {
            if (cardAiMessage.getVisibility() == View.GONE) {
                cardAiMessage.setVisibility(View.VISIBLE);
                txtAiRecommendation.setText("בוחן את היד שלך...");
                pokerAI.getPokerRecommendation(
                        Arrays.asList(me.getCards()), communityCards, pot,
                        currentHighestBet - myCurrentBet,
                        new PokerStrategyService.AiCallback() {
                            @Override public void onResponse(String text) {
                                txtAiRecommendation.setText(text);
                                handler.postDelayed(() -> cardAiMessage.setVisibility(View.GONE), 3000);
                            }
                            @Override public void onError(Throwable t) {
                                txtAiRecommendation.setText("שגיאה בחיבור ל-AI");
                                handler.postDelayed(() -> cardAiMessage.setVisibility(View.GONE), 3000);
                            }
                        }, this);
            } else {
                cardAiMessage.setVisibility(View.GONE);
            }
        });

        btnExit.setOnClickListener(v -> finish());
        btnAllIn.setOnClickListener(v -> { if (roundActive && !isShowdownActive) handleAllIn(); });
        btnFold.setOnClickListener(v -> handleFold());
        btnCheck.setOnClickListener(v -> handleCheck());
        btnCall.setOnClickListener(v -> handleCall());
        btnBetRaise.setOnClickListener(v -> {
            String val = etBetAmount.getText().toString().trim();
            if (!val.isEmpty()) {
                int betAmount = Integer.parseInt(val);
                int toCall = currentHighestBet - myCurrentBet;

                // בדיקה: האם ההימור החדש גבוה לפחות ב-1 מההימור הנוכחי?
                if (betAmount <= toCall) {
                    Toast.makeText(this, "עליך להמר יותר מ-$" + toCall + " כדי להעלות!", Toast.LENGTH_SHORT).show();
                } else {
                    performBet(myCurrentBet + betAmount, false);
                }
            } else {
                Toast.makeText(this, "הכנס סכום הימור", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================================================================
    //  GAME FLOW
    // ================================================================

    private void startNewHand() {
        isRaiseLocked = false;
        if (userBalance < SMALL_BLIND) {
            Toast.makeText(this, "הפסדת את כל הכסף!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (botBalance <= 0) {
            botBalance = 10000;
            Toast.makeText(this, "הבוט הוטען מחדש ב-$10,000", Toast.LENGTH_SHORT).show();
        }
        roundActive      = true;
        isShowdownActive = false;
        currentPhase     = 0;
        lastRevealedPhase = -1;
        pot              = SMALL_BLIND * 2;
        userBalance     -= SMALL_BLIND;
        botBalance      -= SMALL_BLIND;

        resetRoundBets();

        deck = new PokerDeck();
        me.setCards(deck.draw(), deck.draw(), this);
        bot.setCards(deck.draw(), deck.draw(), this);
        bot.setCardsBack();

        communityCards = new PokerCard[5];
        for (int i = 0; i < 5; i++) communityCards[i] = deck.draw();
        resetCommunityCardViews();

        updateUI();
        announcePhase("PREFLOP");
    }

    // ================================================================
    //  PHASE
    // ================================================================

    private void announcePhase(String name) {
        disableButtons();
        stopTimer();
        txtAnnouncer.setText(name);
        txtAnnouncer.setTextColor(Color.WHITE);
        txtAnnouncer.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            txtAnnouncer.setVisibility(View.GONE);

            // פתיחת קלפים קהילתיים לפי שלב
            if (currentPhase > 0 && currentPhase > lastRevealedPhase) {
                lastRevealedPhase = currentPhase;
                revealCommunityCardsForPhase(currentPhase);
            }

            if (!isShowdownActive) {
                userActedThisRound = false;
                prepareMyTurn();
            }
        }, 2000);
    }

    private void advancePhase() {
        isRaiseLocked = false;
        resetRoundBets();

        // ← בדיקת ALL-IN לפני מעבר שלב
        if (userBalance == 0 || botBalance == 0) {
            startAllInShowdown();
            return;
        }

        currentPhase++;
        if (currentPhase > 3) {
            endHand();
            return;
        }

        String name = currentPhase == 1 ? "FLOP" : currentPhase == 2 ? "TURN" : "RIVER";
        announcePhase(name);
    }

    // ================================================================
    //  BOT TURN
    // ================================================================

    private void playBotTurn() {
        if (isShowdownActive) return;
        me.setHighlight(false);
        bot.setHighlight(true);
        disableButtons();
        startTimer(bot.getPlayerName());

        // בניית תיאור היד עבור Gemini
        String botCardsDesc = bot.getCards()[0].toString() + " and " + bot.getCards()[1].toString();
        String communityDesc = "";
        if (currentPhase == 0) communityDesc = "None (Pre-flop)";
        else {
            for (int i = 0; i < 5; i++) {
                if (communityCards[i] != null && i < (currentPhase == 1 ? 3 : currentPhase + 2)) {
                    communityDesc += communityCards[i].toString() + " ";
                }
            }
        }

        // ה-Prompt שגורם לבוט להיות חכם
        String raiseOption = isRaiseLocked ? "ONLY CALL, CHECK, or FOLD (RAISE is not allowed now)" : "CHECK, CALL, FOLD, or RAISE [amount]";

        String prompt = "You are a professional Poker AI. Game state:\n" +
                "- Your Hand: " + botCardsDesc + "\n" +
                "- Amount to Call: $" + (currentHighestBet - botCurrentBet) + "\n" +
                "- Your Balance: $" + botBalance + "\n" +
                "Decide your move: " + raiseOption + ". Respond with ONLY the action name.";
        // שליחה ל-Gemini דרך השירות הקיים שלך
        PokerStrategyService pokerAI = new PokerStrategyService();
        pokerAI.getPokerRecommendation(Arrays.asList(bot.getCards()), communityCards, pot,
                (currentHighestBet - botCurrentBet), new PokerStrategyService.AiCallback() {
                    @Override
                    public void onResponse(String response) {
                        // Gemini מחזיר תשובה (למשל "RAISE 200")
                        handler.post(() -> handleBotAiDecision(response.trim().toUpperCase()));
                    }

                    @Override
                    public void onError(Throwable t) {
                        // הגנה: אם ה-AI לא עונה, הבוט עושה Check/Call רגיל
                        handler.post(() -> handleBotAiDecision("CHECK"));
                    }
                }, this);
    }

    private void handleBotAiDecision(String decision) {
        if (!roundActive) return;
        stopTimer();

        // 1. חישוב כמה הבוט צריך לשלם כדי להשוות
        int toCall = Math.max(0, currentHighestBet - botCurrentBet);

        // 2. טיפול במקרה שאתה (המשתמש) כבר ב-All-In (יתרה 0)
        if (userBalance == 0 || botBalance <= toCall) {
            if (decision.contains("FOLD")) {
                handleBotFold();
                return;
            } else {
                int actualToPay = Math.min(toCall, botBalance);
                pot += actualToPay;
                botCurrentBet += actualToPay;
                botBalance -= actualToPay;
                bot.setStatus("CALL ALL-IN", Color.YELLOW);
                updateUI();
                startAllInShowdown();
                return;
            }
        }

        // 3. טיפול ב-FOLD רגיל
        if (decision.contains("FOLD")) {
            handleBotFold();
            return;
        }

        // 4. טיפול ב-RAISE / BET (רק אם לא נעול)
        if ((decision.contains("RAISE") || decision.contains("BET")) && !isRaiseLocked) {
            int amount = 100;
            try {
                String numeric = decision.replaceAll("[^0-9]", "");
                if (!numeric.isEmpty()) amount = Integer.parseInt(numeric);
            } catch (Exception e) { e.printStackTrace(); }

            isRaiseLocked = true;
            botPerformBet(currentHighestBet + amount);
            prepareMyTurn();
        }

        // 5. טיפול ב-CALL (או Raise כשזה נעול - הופך ל-Call אוטומטית)
        else if (decision.contains("CALL") || (decision.contains("RAISE") && isRaiseLocked)) {
            int actualCall = Math.min(toCall, botBalance);
            botBalance -= actualCall;
            botCurrentBet += actualCall;
            pot += actualCall;
            bot.setStatus(botBalance == 0 ? "ALL IN" : "CALL",
                    botBalance == 0 ? Color.YELLOW : Color.CYAN);
            updateUI();

            // ← אם מישהו ב-0 → SHOWDOWN ישיר
            if (userBalance == 0 || botBalance == 0) {
                startAllInShowdown();
            } else {
                advancePhase();
            }
        }

        // 6. טיפול ב-CHECK
        else {
            bot.setStatus("CHECK", Color.WHITE);
            updateUI();
            if (userActedThisRound) advancePhase();
            else prepareMyTurn();
        }

        updateUI();
    }

    // ================================================================
    //  BOT AI ACTIONS (FIX FOR ERRORS)
    // ================================================================

    private void handleBotFold() {
        stopTimer();
        roundActive = false;
        bot.setStatus("FOLD", Color.RED);

        txtAnnouncer.setVisibility(View.VISIBLE);
        txtAnnouncer.setText("BOT FOLDED! YOU WIN: $" + pot);

        userBalance += pot;
        updateUI();

        handler.postDelayed(this::startNewHand, 3000);
    }

    private void botPerformBet(int betAmount) {
        stopTimer();

        // כמה הבוט צריך להוסיף מעבר למה שכבר שם בסיבוב הזה
        int extra = betAmount - botCurrentBet;

        // אם הבוט מנסה להמר יותר ממה שיש לו - זה ALL IN
        if (extra >= botBalance) {
            extra = botBalance; // הוא שם את כל מה שנשאר לו
            botBalance = 0;     // היתרה יורדת ל-0
            botCurrentBet += extra;
            pot += extra;
            bot.setStatus("ALL IN", Color.YELLOW);
        } else {
            // הימור רגיל
            botBalance -= extra;
            botCurrentBet = betAmount;
            pot += extra;
            bot.setStatus("RAISE $" + betAmount, Color.GREEN);
        }

        currentHighestBet = Math.max(currentHighestBet, botCurrentBet);
        updateUI(); // חשוב מאוד כדי שהטקסט ב-UI ישתנה ל-0
    }

    // ================================================================
    //  MY TURN
    // ================================================================

    private void prepareMyTurn() {
        if (isShowdownActive) return;
        bot.setHighlight(false);
        me.setHighlight(true);
        enableButtons();
        startTimer(username);
    }

    // ================================================================
    //  ACTIONS
    // ================================================================

    private void handleCheck() {
        stopTimer();

        if (currentHighestBet > myCurrentBet) {
            Toast.makeText(this, "יש הימור פתוח — לחץ CALL", Toast.LENGTH_SHORT).show();
            prepareMyTurn();
            return;
        }

        me.setStatus("CHECK", Color.WHITE);
        userActedThisRound = true;
        updateUI();
        disableButtons();

        // אחרי Check שלי — תור הבוט
        // הבוט יחליט אם להעביר שלב (אם גם הוא כבר עשה Check)
        playBotTurn();
    }

    private void handleCall() {
        int toCall = currentHighestBet - myCurrentBet;
        if (toCall <= 0) {
            handleCheck();
            return;
        }

        // ביצוע התשלום
        userBalance -= toCall;
        myCurrentBet += toCall;
        pot += toCall;
        me.setStatus("CALL $" + toCall, Color.CYAN);
        updateUI();
        disableButtons();

        // החלק החשוב: אם השוויתי, הסיבוב נגמר!
        if (userBalance == 0 || botBalance == 0) {
            startAllInShowdown();
        } else {
            // עוברים לשלב הבא (Flop/Turn/River) ולא נותנים לבוט לשחק שוב בסיבוב הזה
            advancePhase();
        }
    }

    private void handleFold() {
        stopTimer();
        roundActive = false;
        me.setStatus("FOLD", Color.RED);
        disableButtons();
        botBalance += pot;
        updateUI();
        handler.postDelayed(this::startNewHand, 2000);
    }

    private void handleAllIn() {
        if (!roundActive || isShowdownActive) return;

        int botTotalStack = botBalance + botCurrentBet;
        int myTotalStack = userBalance + myCurrentBet;

        if (myTotalStack > botTotalStack) {
            performBet(botTotalStack, true);
        } else {
            performBet(myTotalStack, true);
        }
    }

    private void performBet(int betAmount, boolean isAllIn) {
        stopTimer();

        // 1. חישוב הסטאק האפקטיבי: המקסימום שהבוט יכול להפסיד (מה שיש לו + מה שכבר שם)
        int botMaxStack = botBalance + botCurrentBet;

        // 2. אם אתה ב-All-in, אנחנו בודקים כמה באמת מותר לך לשים
        if (isAllIn) {
            // אם יש לך יותר מהבוט, אנחנו מהמרים רק עד הגובה שלו
            if ((userBalance + myCurrentBet) > botMaxStack) {
                betAmount = botMaxStack; // ← ההימור שלך = סטאק הבוט
            } else {
                // אם יש לך פחות, ההימור הוא כל מה שיש לך
                betAmount = userBalance + myCurrentBet;
            }
        }

        // 3. חישוב ה-Extra (כמה כסף פיזית יורד מהכיס שלך עכשיו)
        int extra = betAmount - myCurrentBet;

        // 4. עדכון היתרה שלך (מוודאים שלא יורד יותר מדי)
        if (extra >= userBalance) {
            extra = userBalance;
            userBalance = 0;
        } else {
            userBalance -= extra;
        }

        // 5. עדכון משתני המשחק
        myCurrentBet = betAmount;
        currentHighestBet = myCurrentBet; // ← וידוא שהבוט יודע כמה לשלם
        pot += extra;
        userActedThisRound = true;

        // 6. עדכון ויזואלי
        String label = (userBalance == 0) ? "ALL IN" : "RAISE $" + betAmount;
        me.setStatus(label, (userBalance == 0) ? Color.YELLOW : Color.GREEN);
        updateUI();
        disableButtons();

        // 7. מעבר לבוט - הוא חייב להגיב כדי שהכסף שלו ירד!
        playBotTurn();
    }

    // ================================================================
    //  SHOWDOWN
    // ================================================================

    private void checkAndHandleShowdown() {
        if ((userBalance == 0 || botBalance == 0) && myCurrentBet == botCurrentBet) {
            startAllInShowdown();
        } else {
            advancePhase();
        }
    }

    private void startAllInShowdown() {
        isShowdownActive = true; // ← כבר קיים ✅
        stopTimer();
        disableButtons();
        bot.setCards(bot.getCards()[0], bot.getCards()[1], this);
        // ← הסר את announcePhase ותחליף ל:
        txtAnnouncer.setText("ALL-IN SHOWDOWN");
        txtAnnouncer.setTextColor(Color.parseColor("#C9A84C"));
        txtAnnouncer.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            txtAnnouncer.setVisibility(View.GONE);
            runRemainingCards();
        }, 2000);
    }

    private void runRemainingCards() {
        if (currentPhase >= 3) {
            handler.postDelayed(this::endHand, 800);
            return;
        }
        currentPhase++;
        if (currentPhase > lastRevealedPhase) {
            lastRevealedPhase = currentPhase;
            revealCommunityCardsForPhase(currentPhase);
        }
        // ← מחכה יותר זמן בין שלב לשלב
        handler.postDelayed(this::runRemainingCards, 1800);
    }

    // ================================================================
    //  END HAND
    // ================================================================

    private void endHand() {
        roundActive = false;
        stopTimer();
        disableButtons();

        bot.setCards(bot.getCards()[0], bot.getCards()[1], this);

        HandEvaluator.EvaluationResult myRes  = HandEvaluator.evaluate(me.getCards(),  communityCards);
        HandEvaluator.EvaluationResult botRes = HandEvaluator.evaluate(bot.getCards(), communityCards);

        String winner, desc;
        int winAmount = pot; // שומרים את הסכום בצד לפני האיפוס

        if (myRes.rank.power > botRes.rank.power ||
                (myRes.rank.power == botRes.rank.power && myRes.weightedScore > botRes.weightedScore)) {
            winner = username; desc = myRes.desc; userBalance += winAmount;
        } else if (botRes.rank.power > myRes.rank.power ||
                (botRes.rank.power == myRes.rank.power && botRes.weightedScore > myRes.weightedScore)) {
            winner = "BOT"; desc = botRes.desc; botBalance += winAmount;
        } else {
            winner = "SPLIT POT"; desc = myRes.desc;
            userBalance += winAmount / 2; botBalance += winAmount / 2;
        }

        // עדכון היתרה ב-DB (אבל עדיין לא מאפסים את הטקסט של ה-POT)
        db.updateBalance(username, userBalance);
        me.showPlayer(username, "$" + userBalance);
        bot.showPlayer("Bot", "$" + botBalance);

        txtAnnouncer.bringToFront();
        txtAnnouncer.setText(winner.toUpperCase() + " WINS $" + winAmount + "\n" + desc.toUpperCase());
        txtAnnouncer.setVisibility(View.VISIBLE);

        handler.postDelayed(() -> {
            txtAnnouncer.setVisibility(View.GONE);
            pot = 0; // רק עכשיו מאפסים
            updateUI(); // ורק עכשיו ה-UI יתעדכן ל-0
            startNewHand();
        }, 5000);
    }

    // ================================================================
    //  COMMUNITY CARDS
    // ================================================================

    private void resetCommunityCardViews() {
        for (int i = 0; i < 5; i++) {
            ImageView img = (ImageView) communityCardsLayout.getChildAt(i);
            img.setImageResource(R.drawable.card_back);
            img.setTag(null);
        }
    }

    private void revealCommunityCardsForPhase(int phase) {
        if (phase == 1) {
            openCard(0);
            handler.postDelayed(() -> openCard(1), 700);
            handler.postDelayed(() -> openCard(2), 1400);
        } else if (phase == 2) {
            openCard(3);
        } else if (phase == 3) {
            openCard(4);
        }
    }

    private void openCard(int i) {
        if (communityCards == null || communityCards[i] == null) return;
        ImageView img = (ImageView) communityCardsLayout.getChildAt(i);
        if ("opened".equals(img.getTag())) return;

        AnimatorSet out = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.card_flip_out);
        AnimatorSet in  = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.card_flip_in);
        out.setDuration(150);
        in.setDuration(150);
        out.setTarget(img);
        out.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                img.setImageResource(communityCards[i].getImageResId(Poker.this));
                img.setTag("opened");
                in.setTarget(img);
                in.start();
            }
        });
        out.start();
    }

    // ================================================================
    //  UI
    // ================================================================

    private void updateUI() {
        txtPot.setText("POT: $" + pot);
        me.showPlayer(username, "$" + userBalance);
        bot.showPlayer("Bot", "$" + botBalance);
        db.updateBalance(username, userBalance);
    }

    private void resetRoundBets() {
        myCurrentBet       = 0;
        botCurrentBet      = 0;
        currentHighestBet  = 0;
        userActedThisRound = false;
        me.clearStatus();
        bot.clearStatus();
    }

    // ================================================================
    //  TIMER
    // ================================================================

    private void startTimer(String playerName) {
        stopTimer();
        if (isShowdownActive) return;
        timeLeft = 45;
        txtTimer.setVisibility(View.VISIBLE);
        txtTimer.setTextColor(Color.WHITE);

        timerRunnable = new Runnable() {
            @Override public void run() {
                if (timeLeft <= 0) {
                    stopTimer();
                    if (playerName.equals(username)) handleFold();
                } else {
                    txtTimer.setText(playerName + ": " + timeLeft);
                    if (timeLeft <= 10) txtTimer.setTextColor(Color.RED);
                    timeLeft--;
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (txtTimer != null) txtTimer.setVisibility(View.INVISIBLE);
    }

    // ================================================================
    //  BUTTONS
    // ================================================================

    private void disableButtons() {
        btnCheck.setEnabled(false);    btnCheck.setAlpha(0.4f);
        btnCall.setEnabled(false);     btnCall.setAlpha(0.4f);
        btnFold.setEnabled(false);     btnFold.setAlpha(0.4f);
        btnBetRaise.setEnabled(false); btnBetRaise.setAlpha(0.4f);
        btnAllIn.setEnabled(false);    btnAllIn.setAlpha(0.4f);
    }

    private void enableButtons() {
        if (isShowdownActive) return;
        btnFold.setEnabled(true);     btnFold.setAlpha(1.0f);
        btnBetRaise.setEnabled(true); btnBetRaise.setAlpha(1.0f);
        btnAllIn.setEnabled(true);    btnAllIn.setAlpha(1.0f);
        // Check רק אם אין הימור פתוח
        boolean canCheck = (currentHighestBet <= myCurrentBet);
        btnCheck.setEnabled(canCheck); btnCheck.setAlpha(canCheck ? 1.0f : 0.4f);
        // Call רק אם יש הימור פתוח
        boolean canCall = (currentHighestBet > myCurrentBet);
        btnCall.setEnabled(canCall);  btnCall.setAlpha(canCall ? 1.0f : 0.4f);
    }
}