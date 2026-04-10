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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PokerOnline extends AppCompatActivity {

    // ===== UI =====
    private TextView txtRoomId, txtStatus, txtPot, txtTimer;
    private int secondsLeft = 45;
    private Runnable timerRunnable;
    private Button btnFold, btnCheck, btnCall, btnBetRaise;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnAllIn;
    private ImageButton btnExit;
    private EditText etBetAmount;
    private LinearLayout communityCardsLayout;
    private PokerSeat me, bot;

    // ===== Firebase =====
    private String roomId;
    private String role;
    private DatabaseReference roomRef;
    private ValueEventListener mainListener;
    private ValueEventListener cardsListener;

    // ===== Players =====
    private String username;
    private String opponentName = "Opponent";
    private DatabaseHelper db;
    private int userBalance, botBalance = 0;

    private Handler handler = new Handler(Looper.getMainLooper());

    // ===== State Machine =====
    private boolean gameStarted     = false;
    private boolean handInProgress  = false;
    private boolean showdownHandled = false;
    private boolean isAllInShowdown = false;

    // ===== Game State =====
    private String  currentHandId    = "";
    private String  lastHandId       = "";

    private PokerCard[] communityCards    = new PokerCard[5];
    private final int   SMALL_BLIND       = 50;
    private int         pot               = 0;
    private int         currentPhase      = 0;
    private int         lastRevealedPhase = -1;
    private int         myCurrentBet      = 0;
    private int         currentHighestBet = 0;
    private boolean     myTurnActive      = false;

    // ================================================================
    //  onCreate
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.poker_activity);
        initViews();

        db          = new DatabaseHelper(this);
        username    = getIntent().getStringExtra("username");
        if (username == null) username = "Player";
        userBalance = db.getUserBalance(username);
        roomId      = getIntent().getStringExtra("room_id");
        role        = getIntent().getStringExtra("role");

        if (roomId == null || role == null) {
            Toast.makeText(this, "שגיאה: חסר מזהה חדר", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomRef = FirebaseDatabase.getInstance(
                        "https://blackjack-9111f-default-rtdb.europe-west1.firebasedatabase.app")
                .getReference("poker_rooms").child(roomId);

        if (role.equals("host")) roomRef.onDisconnect().removeValue();
        else roomRef.child("seats").child("1").onDisconnect().removeValue();

        registerPlayer();
        listenForConnection();
    }

    // ================================================================
    //  INIT VIEWS
    // ================================================================

    private void initViews() {
        txtRoomId            = findViewById(R.id.txt_room_display);
        txtStatus            = findViewById(R.id.txt_round_announcer);
        txtPot               = findViewById(R.id.txt_pot);
        txtTimer             = findViewById(R.id.txt_timer);
        etBetAmount          = findViewById(R.id.et_bet_amount);
        communityCardsLayout = findViewById(R.id.community_cards_layout);
        btnFold              = findViewById(R.id.btn_fold);
        btnCheck             = findViewById(R.id.btn_check);
        btnCall              = findViewById(R.id.btn_call);
        btnBetRaise          = findViewById(R.id.btn_bet_raise);
        btnAllIn             = findViewById(R.id.btn_all_in);
        btnExit              = findViewById(R.id.btn_exit_poker);
        me  = new PokerSeat(findViewById(R.id.slot_bottom_me));
        bot = new PokerSeat(findViewById(R.id.slot_top_center));

        disableAllButtons();
        txtTimer.setVisibility(View.INVISIBLE);
        txtPot.setText("POT: $0");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leaveRoom(); finish(); }
        });

        btnExit.setOnClickListener(v -> { leaveRoom(); finish(); });
        findViewById(R.id.fab_gemini).setOnClickListener(v -> askAiRecommendation());
        btnCheck.setOnClickListener(v -> handleCheck());
        btnCall.setOnClickListener(v -> handleCall());
        btnFold.setOnClickListener(v -> handleFold());
        btnAllIn.setOnClickListener(v -> handleAllIn());
        btnBetRaise.setOnClickListener(v -> {
            String val = etBetAmount.getText().toString().trim();
            if (!val.isEmpty()) {
                int amount = Integer.parseInt(val);
                if (amount <= 0) { Toast.makeText(this, "הכנס סכום תקין", Toast.LENGTH_SHORT).show(); return; }
                performBet(myCurrentBet + amount);
                etBetAmount.setText("");
            } else {
                Toast.makeText(this, "הכנס סכום הימור", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================================================================
    //  TIMER
    // ================================================================

    private void startTurnTimer() {
        stopTurnTimer();
        secondsLeft = 45;
        txtTimer.setVisibility(View.VISIBLE);
        txtTimer.bringToFront();
        final String nameToDisplay = myTurnActive ? username : opponentName;
        txtTimer.setText(nameToDisplay.toUpperCase() + ": " + secondsLeft);
        txtTimer.setTextColor(Color.WHITE);
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (secondsLeft <= 0) {
                    txtTimer.setText(nameToDisplay.toUpperCase() + ": 0");
                    if (myTurnActive) { Toast.makeText(PokerOnline.this, "נגמר הזמן!", Toast.LENGTH_SHORT).show(); handleFold(); }
                    stopTurnTimer(); return;
                }
                txtTimer.setText(nameToDisplay.toUpperCase() + ": " + secondsLeft);
                txtTimer.setTextColor(secondsLeft <= 10 ? Color.RED : Color.WHITE);
                secondsLeft--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }

    private void stopTurnTimer() {
        if (timerRunnable != null) handler.removeCallbacks(timerRunnable);
        txtTimer.setVisibility(View.INVISIBLE);
    }

    // ================================================================
    //  AI
    // ================================================================

    private void askAiRecommendation() {
        final View aiCard     = findViewById(R.id.card_ai_message);
        final TextView aiText = findViewById(R.id.txt_ai_recommendation);
        if (me.getCards() == null || communityCards == null) return;

        int cardsToTake = 0;
        if (currentPhase == 1) cardsToTake = 3;
        else if (currentPhase == 2) cardsToTake = 4;
        else if (currentPhase == 3) cardsToTake = 5;

        PokerCard[] visibleCommunity = new PokerCard[cardsToTake];
        for (int i = 0; i < cardsToTake; i++) visibleCommunity[i] = communityCards[i];

        aiCard.setVisibility(View.VISIBLE);
        aiCard.setAlpha(1f);
        aiText.setText("Gemini מנתח את היד שלך...");

        new PokerStrategyService().getPokerRecommendation(
                java.util.Arrays.asList(me.getCards()), visibleCommunity, pot,
                (currentHighestBet - myCurrentBet),
                new PokerStrategyService.AiCallback() {
                    @Override public void onResponse(String text) {
                        aiText.setText(text);
                        handler.postDelayed(() -> aiCard.animate().alpha(0f).setDuration(500)
                                .withEndAction(() -> aiCard.setVisibility(View.GONE)).start(), 6000);
                    }
                    @Override public void onError(Throwable t) {
                        aiText.setText("שגיאה בחיבור לשרתי ה-AI");
                        handler.postDelayed(() -> aiCard.setVisibility(View.GONE), 3000);
                    }
                }, this);
    }

    // ================================================================
    //  CONNECTION
    // ================================================================

    private void registerPlayer() {
        txtRoomId.setText("ROOM ID: " + roomId);
        if (role.equals("host")) {
            roomRef.child("seats").child("0").child("name").setValue(username);
            roomRef.child("seats").child("0").child("balance").setValue(userBalance);
            roomRef.child("game_state").child("status").setValue("waiting");
            showStatus("WAITING FOR PLAYER...\nROOM: " + roomId, Color.WHITE);
        } else {
            roomRef.child("seats").child("1").child("name").setValue(username);
            roomRef.child("seats").child("1").child("balance").setValue(userBalance);
            showStatus("CONNECTING...", Color.WHITE);
        }
        me.showPlayer(username, "$" + userBalance);
    }

    private void listenForConnection() {
        mainListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                if (gameStarted) { listenForGameActions(snapshot); return; }

                DataSnapshot seats  = snapshot.child("seats");
                String       status = snapshot.child("game_state").child("status").getValue(String.class);

                if (role.equals("host") && seats.hasChild("1") && !gameStarted) {
                    String  guestName = seats.child("1").child("name").getValue(String.class);
                    Integer guestBal  = seats.child("1").child("balance").getValue(Integer.class);
                    if (guestName != null) {
                        opponentName = guestName;
                        if (guestBal != null) botBalance = guestBal;
                        bot.showPlayer(opponentName, "$" + botBalance);
                        roomRef.child("game_state").child("status").setValue("ready");
                        showStatus("PLAYER JOINED!\nSTARTING...", Color.GREEN);
                        handler.postDelayed(() -> onBothPlayersConnected(), 2000);
                    }
                }
                if (role.equals("guest") && !gameStarted) {
                    String  hostName = seats.child("0").child("name").getValue(String.class);
                    Integer hostBal  = seats.child("0").child("balance").getValue(Integer.class);
                    if (hostName != null) {
                        opponentName = hostName;
                        if (hostBal != null) botBalance = hostBal;
                        bot.showPlayer(opponentName, "$" + botBalance);
                    }
                }
                if ("ready".equals(status) && !gameStarted) {
                    String  oppSeat = role.equals("host") ? "1" : "0";
                    String  oppName = seats.child(oppSeat).child("name").getValue(String.class);
                    Integer oppBal  = seats.child(oppSeat).child("balance").getValue(Integer.class);
                    if (oppName != null) opponentName = oppName;
                    if (oppBal  != null) botBalance   = oppBal;
                    bot.showPlayer(opponentName, "$" + botBalance);
                    if (role.equals("guest")) {
                        showStatus("CONNECTED!\nSTARTING...", Color.GREEN);
                        handler.postDelayed(() -> onBothPlayersConnected(), 2000);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        roomRef.addValueEventListener(mainListener);

        // ===== מאזין ליציאת שחקן (רק המארח) =====
        if (role.equals("host")) {
            roomRef.child("seats").child("1").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!gameStarted) return;
                    if (!snapshot.exists() || snapshot.child("name").getValue() == null) {
                        // ===== האורח יצא =====
                        stopTurnTimer();
                        disableAllButtons();
                        me.setHighlight(false);
                        bot.setHighlight(false);

                        if (handInProgress) {
                            // באמצע יד → המארח מנצח
                            showdownHandled = true;
                            handInProgress  = false;
                            userBalance    += pot;
                            pot             = 0;
                            updateUI();
                            db.updateBalance(username, userBalance);
                            showStatus("OPPONENT LEFT!\nYOU WIN!", Color.parseColor("#FFD700"));
                        } else {
                            handInProgress = false;        // ← הוסף גם כאן
                            showStatus("OPPONENT LEFT THE GAME", Color.RED);
                        }

                        handler.postDelayed(() -> {
                            // איפוס מצב ← חזרה ל-waiting
                            gameStarted     = false;
                            handInProgress  = false;
                            showdownHandled = false;
                            isAllInShowdown = false;
                            myTurnActive    = false;
                            pot             = 0;
                            txtStatus.setVisibility(View.GONE);
                            bot.clearStatus();
                            bot.setHighlight(false);
                            roomRef.child("game_state").child("status").setValue("waiting");
                            showStatus("WAITING FOR PLAYER...\nROOM: " + roomId, Color.WHITE);
                        }, 3000);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
        }
    }

    private void onBothPlayersConnected() {
        gameStarted = true;
        showStatus("GAME STARTING!", Color.parseColor("#FFD700"));
        handler.postDelayed(() -> { txtStatus.setVisibility(View.GONE); startNewHand(); }, 2000);
    }

    // ================================================================
    //  START NEW HAND
    // ================================================================

    private void startNewHand() {
        if (userBalance < SMALL_BLIND) {
            db.updateBalance(username, userBalance); // שומר 0 ב-DB
            Toast.makeText(this, "נגמר הכסף! מינימום $" + SMALL_BLIND + " כדי לשחק", Toast.LENGTH_LONG).show();
            leaveRoom();
            finish();
            return;
        }
        showdownHandled   = false;
        isAllInShowdown   = false;
        handInProgress    = true;
        currentPhase      = 0;
        lastRevealedPhase = -1;
        myCurrentBet      = 0;
        currentHighestBet = 0;
        myTurnActive      = false;
        pot               = SMALL_BLIND * 2;

        communityCards = new PokerCard[5];
        resetCommunityCardViews();
        me.clearStatus(); bot.clearStatus();
        me.setHighlight(false); bot.setHighlight(false);
        updateUI();

        if (role.equals("host")) { roomRef.child("game_state").child("status").setValue("playing"); dealCards(); }
        else { lastHandId = ""; listenForCards(); }
    }

    // ================================================================
    //  CARD DEALING
    // ================================================================

    private void dealCards() {
        userBalance -= SMALL_BLIND;
        pot          = SMALL_BLIND * 2;
        myCurrentBet = SMALL_BLIND;
        String handId = String.valueOf(System.currentTimeMillis());
        currentHandId = handId;

        PokerDeck deck = new PokerDeck();
        PokerCard hc1 = deck.draw(), hc2 = deck.draw();
        PokerCard gc1 = deck.draw(), gc2 = deck.draw();
        communityCards = new PokerCard[5];
        for (int i = 0; i < 5; i++) communityCards[i] = deck.draw();

        StringBuilder comm = new StringBuilder();
        for (int i = 0; i < 5; i++) comm.append(communityCards[i].getCardID()).append(i < 4 ? "," : "");

        DatabaseReference gsRef = roomRef.child("game_state");
        gsRef.child("current_hand_id").setValue(handId);

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", "playing"); updates.put("phase", 0);
        updates.put("checks_this_round", 0); updates.put("last_action", "NONE");
        updates.put("turn", "host"); updates.put("pot", pot);
        updates.put("current_highest_bet", SMALL_BLIND);
        updates.put("host_balance", userBalance); updates.put("guest_balance", botBalance);
        updates.put("cards/host_hand",  hc1.getCardID() + "," + hc2.getCardID());
        updates.put("cards/guest_hand", gc1.getCardID() + "," + gc2.getCardID());
        updates.put("cards/community",  comm.toString());
        updates.put("cards/hand_id",    handId);


        gsRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            me.setCards(hc1, hc2, this);
            bot.setCardsBack();
            announceRound("PREFLOP");
        });
    }

    private void listenForCards() {
        showStatus("WAITING FOR CARDS...", Color.WHITE);
        if (cardsListener != null) { roomRef.child("game_state").child("cards").removeEventListener(cardsListener); cardsListener = null; }
        cardsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.hasChild("hand_id")) return;
                String incomingId = snapshot.child("hand_id").getValue(String.class);
                if (incomingId == null || incomingId.equals(currentHandId)) return;
                currentHandId = incomingId; lastHandId = incomingId;
                try {
                    String gStr = snapshot.child("guest_hand").getValue(String.class);
                    String hStr = snapshot.child("host_hand").getValue(String.class);
                    String cStr = snapshot.child("community").getValue(String.class);
                    if (gStr == null || hStr == null || cStr == null) return;
                    String[] gIds = gStr.split(","), hIds = hStr.split(","), cIds = cStr.split(",");
                    me.setCards(new PokerCard(gIds[0]), new PokerCard(gIds[1]), PokerOnline.this);
                    bot.setCards(new PokerCard(hIds[0]), new PokerCard(hIds[1]), PokerOnline.this);
                    bot.setCardsBack();
                    communityCards = new PokerCard[5];
                    for (int i = 0; i < 5; i++) communityCards[i] = new PokerCard(cIds[i]);
                    userBalance  -= SMALL_BLIND;
                    myCurrentBet  = SMALL_BLIND;
                    roomRef.child("game_state").child("guest_balance").setValue(userBalance);
                    roomRef.child("seats").child("1").child("balance").setValue(userBalance);
                    updateUI();
                    resetCommunityCardViews();
                    lastRevealedPhase = -1; currentPhase = 0;
                    txtStatus.setVisibility(View.GONE);
                    announceRound("PREFLOP");
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        roomRef.child("game_state").child("cards").addValueEventListener(cardsListener);
    }

    // ================================================================
    //  GAME ACTIONS LISTENER
    // ================================================================

    private void listenForGameActions(DataSnapshot snapshot) {
        DataSnapshot gs = snapshot.child("game_state");

        String snapshotHandId = gs.child("current_hand_id").getValue(String.class);
        if (snapshotHandId == null || !snapshotHandId.equals(currentHandId)) return;

        String  turn       = gs.child("turn").getValue(String.class);
        String  lastAction = gs.child("last_action").getValue(String.class);
        Integer cloudPhase = gs.child("phase").getValue(Integer.class);
        Integer cloudPot   = gs.child("pot").getValue(Integer.class);
        Integer cloudBet   = gs.child("current_highest_bet").getValue(Integer.class);
        String  status     = gs.child("status").getValue(String.class);

        Integer hostBal  = gs.child("host_balance").getValue(Integer.class);
        Integer guestBal = gs.child("guest_balance").getValue(Integer.class);
        if (role.equals("host")  && guestBal != null) botBalance = guestBal;
        if (role.equals("guest") && hostBal  != null) botBalance = hostBal;
        if (cloudPot != null) pot               = cloudPot;
        if (cloudBet != null) currentHighestBet = cloudBet;

        // ===== ALL-IN Showdown =====
        if ("allin_showdown".equals(status) && !showdownHandled && !isAllInShowdown) {
            isAllInShowdown = true;
            disableAllButtons(); stopTurnTimer();
            String oppKey = role.equals("host") ? "guest_hand" : "host_hand";
            roomRef.child("game_state").child("cards").child(oppKey)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot s) {
                            String str = s.getValue(String.class);
                            if (str != null) { String[] ids = str.split(","); bot.setCards(new PokerCard(ids[0]), new PokerCard(ids[1]), PokerOnline.this); }
                            showStatus("ALL-IN!", Color.YELLOW);
                            handler.postDelayed(() -> { txtStatus.setVisibility(View.GONE); revealRemainingPhases(); }, 2000);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) { revealRemainingPhases(); }
                    });
            return;
        }

        // ===== Showdown =====
        if ("showdown".equals(status)) {
            String winnerRole = gs.child("winner").getValue(String.class);
            String desc       = gs.child("winner_desc").getValue(String.class);
            if (winnerRole != null && desc != null && !showdownHandled) handleShowdownUI(winnerRole, desc);
            return;
        }

        if (!handInProgress) return;

        // ===== FOLD =====
        if ("FOLD".equals(lastAction)) { if (role.equals(turn)) handleOpponentFold(); return; }

        // ===== Phase sync =====
        if (cloudPhase != null && cloudPhase > currentPhase) {
            if (currentPhase == 0 && cloudPhase == 3) return;
            currentPhase = cloudPhase; myCurrentBet = 0; currentHighestBet = 0;
            if (currentPhase > lastRevealedPhase) {
                lastRevealedPhase = currentPhase;
                me.clearStatus(); bot.clearStatus();
                String name = currentPhase == 1 ? "FLOP" : currentPhase == 2 ? "TURN" : "RIVER";
                announceRound(name); return;
            }
        }

        updateUI();
        if (turn == null) return;

        // ===== advance_phase =====
        if ("advance_phase".equals(turn) && role.equals("host")) {
            roomRef.child("game_state").child("turn").setValue("none");
            handler.postDelayed(() -> { me.clearStatus(); bot.clearStatus(); doAdvancePhase(); }, 500);
            return;
        }

        // ===== Turn management =====
        if (turn.equals(role) && !myTurnActive) {
            myTurnActive = true;
            if (lastAction != null && !"NONE".equals(lastAction) && !"BOTH_CHECKED".equals(lastAction))
                bot.setStatus(lastAction, Color.CYAN);
            prepareMyTurn();
        } else if (!turn.equals(role) && myTurnActive) {
            myTurnActive = false; prepareOpponentTurn();
        } else if (!turn.equals(role) && !myTurnActive && !"advance_phase".equals(turn) && !"none".equals(turn)) {
            prepareOpponentTurn();
        }
    }

    // ================================================================
    //  TURN MANAGEMENT
    // ================================================================

    private void prepareMyTurn() {
        if (!handInProgress) return; // ← הוסף
        bot.setHighlight(false); me.setHighlight(true);
        enableButtons(); startTurnTimer();
    }

    private void prepareOpponentTurn() {
        if (!handInProgress) return; // ← הוסף
        me.setHighlight(false); bot.setHighlight(true);
        disableAllButtons(); startTurnTimer();
    }

    // ================================================================
    //  ACTIONS
    // ================================================================

    /**
     * ALL-IN = BET/CALL על כל הכסף, מוגבל לסטאק של היריב.
     * לפני כל מעבר שלב נבדוק אם מישהו ב-0 → Showdown.
     */
    private void handleAllIn() {
        stopTurnTimer();
        if (!myTurnActive) return;

        if (currentHighestBet > myCurrentBet) {
            // ===== יש BET פתוח → ALL-IN = CALL מוגבל ליתרה שלי =====
            handleCall();
        } else {
            // ===== אין BET פתוח → ALL-IN = BET על כל הכסף, מוגבל לסטאק של היריב =====
            int myStack  = userBalance + myCurrentBet;
            int oppStack = botBalance  + currentHighestBet;
            int amount   = Math.min(myStack, oppStack);
            if (amount <= 0) return;
            myTurnActive = true; // performBet יכבה
            performBet(amount);
        }
    }

    private void handleCheck() {
        stopTurnTimer();
        if (!myTurnActive) return;
        myTurnActive = false; disableAllButtons();
        me.setStatus("CHECK", Color.WHITE);
        DatabaseReference gs = roomRef.child("game_state");
        gs.child("checks_this_round").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer checks = snapshot.getValue(Integer.class);
                if (checks == null) checks = 0;
                int newChecks = checks + 1;
                if (newChecks >= 2) {
                    handler.postDelayed(() -> {
                        me.clearStatus(); bot.clearStatus();
                        gs.child("checks_this_round").setValue(0);
                        gs.child("last_action").setValue("BOTH_CHECKED");
                        gs.child("pot").setValue(pot);
                        if (role.equals("host")) doAdvancePhase();
                        else gs.child("turn").setValue("advance_phase");
                    }, 1000);
                } else {
                    gs.child("checks_this_round").setValue(newChecks);
                    gs.child("last_action").setValue("CHECK");
                    gs.child("turn").setValue(role.equals("host") ? "guest" : "host");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    private void handleCall() {
        stopTurnTimer();
        if (!myTurnActive) return;
        int toCall = currentHighestBet - myCurrentBet;
        if (toCall <= 0) { handleCheck(); return; }
        myTurnActive = false; disableAllButtons();

        if (toCall > userBalance) toCall = userBalance;
        userBalance  -= toCall; myCurrentBet += toCall; pot += toCall;
        me.setStatus("CALL $" + toCall, Color.CYAN);
        updateUI();

        DatabaseReference gs    = roomRef.child("game_state");
        String            seat  = role.equals("host") ? "0" : "1";
        String            balKey = role.equals("host") ? "host_balance" : "guest_balance";
        roomRef.child("seats").child(seat).child("current_bet").setValue(myCurrentBet);

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("last_action",   "CALL $" + toCall);
        updates.put("pot",           pot);
        updates.put(balKey,          userBalance);
        updates.put("host_balance",  role.equals("host") ? userBalance : botBalance);
        updates.put("guest_balance", role.equals("guest") ? userBalance : botBalance);

        if (role.equals("host")) {
            gs.updateChildren(updates).addOnSuccessListener(v ->
                    handler.postDelayed(() -> { me.clearStatus(); bot.clearStatus(); doAdvancePhase(); }, 1000));
        } else {
            updates.put("turn", "advance_phase");
            gs.updateChildren(updates);
        }
    }

    private void handleFold() {
        stopTurnTimer();
        if (!myTurnActive) return;
        myTurnActive = false; disableAllButtons();
        me.setStatus("FOLD", Color.RED);
        DatabaseReference gs = roomRef.child("game_state");
        gs.child("last_action").setValue("FOLD");
        gs.child("turn").setValue(role.equals("host") ? "guest" : "host");
    }

    private void performBet(int betAmount) {
        stopTurnTimer();
        if (!myTurnActive) return;
        myTurnActive = false; disableAllButtons();

        // Table Stakes: לא יותר ממה שיש ליריב
        int oppMax = botBalance + currentHighestBet;
        if (betAmount > oppMax) betAmount = oppMax;

        int extra = betAmount - myCurrentBet;
        if (extra > userBalance) { extra = userBalance; betAmount = myCurrentBet + extra; }
        if (extra <= 0) return;

        userBalance      -= extra;
        boolean isRaise   = (currentHighestBet > 0 && betAmount > currentHighestBet);
        myCurrentBet      = betAmount;
        currentHighestBet = Math.max(currentHighestBet, myCurrentBet);
        pot              += extra;

        String label = isRaise ? "RAISE $" + betAmount : "BET $" + betAmount;
        me.setStatus(label, Color.GREEN);
        updateUI();

        DatabaseReference gs    = roomRef.child("game_state");
        String            seat  = role.equals("host") ? "0" : "1";
        String            balKey = role.equals("host") ? "host_balance" : "guest_balance";
        roomRef.child("seats").child(seat).child("current_bet").setValue(myCurrentBet);

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("last_action",         label);
        updates.put("last_amount",         betAmount);
        updates.put("pot",                 pot);
        updates.put("current_highest_bet", currentHighestBet);
        updates.put("checks_this_round",   0);
        updates.put(balKey,                userBalance);
        // תמיד מעדכן שתי היתרות כדי ש-doAdvancePhase יזהה ALL-IN נכון
        updates.put("host_balance",  role.equals("host") ? userBalance : botBalance);
        updates.put("guest_balance", role.equals("guest") ? userBalance : botBalance);
        updates.put("turn", role.equals("host") ? "guest" : "host");

        gs.updateChildren(updates);
    }

    // ================================================================
    //  PHASE MANAGEMENT
    //  בדיקת יתרה 0 לפני כל מעבר שלב → ALL-IN Showdown
    // ================================================================

    private void doAdvancePhase() {
        if (!role.equals("host")) return;

        roomRef.child("game_state").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot gs) {
                Integer hBal = gs.child("host_balance").getValue(Integer.class);
                Integer gBal = gs.child("guest_balance").getValue(Integer.class);
                if (hBal == null) hBal = userBalance;
                if (gBal == null) gBal = botBalance;

                // ===== אם למישהו יש 0 → ALL-IN Showdown =====
                if (hBal == 0 || gBal == 0) {
                    runAllInShowdown();
                    return;
                }

                // מעבר שלב רגיל
                currentPhase++;
                myCurrentBet = 0; currentHighestBet = 0;

                if (currentPhase > 3) { startShowdown(); return; }

                me.clearStatus(); bot.clearStatus();

                DatabaseReference gsRef = roomRef.child("game_state");
                gsRef.child("phase").setValue(currentPhase);
                gsRef.child("last_action").setValue("NONE");
                gsRef.child("checks_this_round").setValue(0);
                gsRef.child("turn").setValue("host");
                gsRef.child("current_highest_bet").setValue(0);

                if (currentPhase > lastRevealedPhase) {
                    lastRevealedPhase = currentPhase;
                    String name = currentPhase == 1 ? "FLOP" : currentPhase == 2 ? "TURN" : "RIVER";
                    announceRound(name);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // ================================================================
    //  ALL-IN SHOWDOWN
    // ================================================================

    private void runAllInShowdown() {
        stopTurnTimer(); // ← הוסף
        // סמן ב-Firebase → גם האורח יזהה ויציג
        roomRef.child("game_state").child("status").setValue("allin_showdown");

        // המארח מריץ ישירות
        isAllInShowdown = true;
        roomRef.child("game_state").child("cards").child("guest_hand")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot s) {
                        String str = s.getValue(String.class);
                        if (str != null) {
                            String[] ids = str.split(",");
                            bot.setCards(new PokerCard(ids[0]), new PokerCard(ids[1]), PokerOnline.this);
                        }
                        showStatus("ALL-IN!", Color.YELLOW);
                        handler.postDelayed(() -> { txtStatus.setVisibility(View.GONE); revealRemainingPhases(); }, 2000);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) { revealRemainingPhases(); }
                });
    }

    private void revealRemainingPhases() {
        if (currentPhase < 3) {
            currentPhase++;
            if (currentPhase > lastRevealedPhase) {
                lastRevealedPhase = currentPhase;
                revealCommunityCardsForPhase(currentPhase);
            }
            handler.postDelayed(this::revealRemainingPhases, 1500);
        } else {
            handler.postDelayed(this::startShowdown, 1500);
        }
    }

    // ================================================================
    //  ANNOUNCE ROUND
    // ================================================================

    private void announceRound(String name) {
        stopTurnTimer(); // ← הוסף
        disableAllButtons();
        showStatus(name, Color.WHITE);
        handler.postDelayed(() -> {
            txtStatus.setVisibility(View.GONE);
            if (currentPhase > 0) revealCommunityCardsForPhase(currentPhase);
            if (isAllInShowdown) return;
            roomRef.child("game_state").child("turn").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot s) {
                    String turn = s.getValue(String.class);
                    if (role.equals(turn)) { myTurnActive = true; prepareMyTurn(); }
                    else prepareOpponentTurn();
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
        }, 2000);
    }

    // ================================================================
    //  SHOWDOWN
    // ================================================================

    private void startShowdown() {
        if (!role.equals("host")) return;
        roomRef.child("game_state").child("cards").child("guest_hand")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String gStr = snapshot.getValue(String.class);
                        if (gStr != null) { String[] ids = gStr.split(","); bot.setCards(new PokerCard(ids[0]), new PokerCard(ids[1]), PokerOnline.this); }

                        HandEvaluator.EvaluationResult hostRes  = HandEvaluator.evaluate(me.getCards(),  communityCards);
                        HandEvaluator.EvaluationResult guestRes = HandEvaluator.evaluate(bot.getCards(), communityCards);

                        String winnerRole, desc;
                        if (hostRes.rank.power > guestRes.rank.power || (hostRes.rank.power == guestRes.rank.power && hostRes.weightedScore > guestRes.weightedScore)) {
                            winnerRole = "host";  desc = hostRes.desc;
                        } else if (guestRes.rank.power > hostRes.rank.power || (guestRes.rank.power == hostRes.rank.power && guestRes.weightedScore > hostRes.weightedScore)) {
                            winnerRole = "guest"; desc = guestRes.desc;
                        } else { winnerRole = "split"; desc = hostRes.desc; }

                        if (winnerRole.equals("host"))       userBalance += pot;
                        else if (winnerRole.equals("guest")) botBalance  += pot;
                        else { userBalance += pot / 2; botBalance += pot / 2; }

                        DatabaseReference gsDb = roomRef.child("game_state");
                        gsDb.child("winner").setValue(winnerRole);
                        gsDb.child("winner_desc").setValue(desc);
                        gsDb.child("host_balance").setValue(userBalance);
                        gsDb.child("guest_balance").setValue(botBalance);
                        gsDb.child("status").setValue("showdown");
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    private void handleShowdownUI(String winnerRole, String desc) {
        stopTurnTimer();
        if (showdownHandled) return;
        showdownHandled = true; handInProgress = false;
        disableAllButtons(); me.setHighlight(false); bot.setHighlight(false);

        roomRef.child("game_state").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot gs) {
                Integer hBal = gs.child("host_balance").getValue(Integer.class);
                Integer gBal = gs.child("guest_balance").getValue(Integer.class);
                if (role.equals("host") && hBal != null) { userBalance = hBal; if (gBal != null) botBalance = gBal; }
                else if (role.equals("guest") && gBal != null) { userBalance = gBal; if (hBal != null) botBalance = hBal; }
                pot = 0; updateUI(); db.updateBalance(username, userBalance);

                String oppKey = role.equals("host") ? "guest_hand" : "host_hand";
                roomRef.child("game_state").child("cards").child(oppKey)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(@NonNull DataSnapshot s) {
                                String str = s.getValue(String.class);
                                if (str != null) { String[] ids = str.split(","); bot.setCards(new PokerCard(ids[0]), new PokerCard(ids[1]), PokerOnline.this); }

                                String winnerName = winnerRole.equals("split") ? "SPLIT POT" :
                                        winnerRole.equals(role) ? username : opponentName;
                                txtStatus.bringToFront(); txtStatus.setTranslationZ(100f);
                                showStatus(winnerName.toUpperCase() + " WINS!\n" + desc.toUpperCase(), Color.parseColor("#FFD700"));

                                handler.postDelayed(() -> {
                                    txtStatus.setVisibility(View.GONE); txtStatus.setTranslationZ(0f);
                                    if (role.equals("host")) {
                                        DatabaseReference gsRef = roomRef.child("game_state");
                                        gsRef.child("status").setValue("playing");
                                        gsRef.child("phase").setValue(0);
                                        gsRef.child("checks_this_round").setValue(0);
                                        gsRef.child("last_action").setValue("NONE");
                                        gsRef.child("winner").removeValue();
                                        gsRef.child("winner_desc").removeValue();
                                        handler.postDelayed(() -> startNewHand(), 500);
                                    } else { startNewHand(); }
                                }, 8000);
                            }
                            @Override public void onCancelled(@NonNull DatabaseError e) {}
                        });
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    // ================================================================
    //  OPPONENT FOLD
    // ================================================================

    private void handleOpponentFold() {
        if (showdownHandled) return;
        showdownHandled = true; handInProgress = false;
        stopTurnTimer(); disableAllButtons();
        me.setHighlight(false); bot.setHighlight(false);

        boolean iFolded = "FOLD".equals(me.getStatusText());
        if (iFolded) {
            showStatus("YOU FOLDED\n" + opponentName.toUpperCase() + " WINS!", Color.RED);
            botBalance += pot;
        } else {
            showStatus("OPPONENT FOLDED!\nYOU WIN!", Color.parseColor("#FFD700"));
            userBalance += pot;
            bot.setStatus("FOLD", Color.RED);
        }
        pot = 0; updateUI(); db.updateBalance(username, userBalance);

        handler.postDelayed(() -> {
            txtStatus.setVisibility(View.GONE);
            if (role.equals("host")) {
                roomRef.child("game_state").child("last_action").setValue("NONE");
                handler.postDelayed(this::startNewHand, 1000);
            } else { startNewHand(); }
        }, 5000);
    }

    // ================================================================
    //  COMMUNITY CARDS
    // ================================================================

    private void resetCommunityCardViews() {
        for (int i = 0; i < 5; i++) {
            ImageView img = (ImageView) communityCardsLayout.getChildAt(i);
            img.setImageResource(R.drawable.card_back); img.setTag(null);
        }
    }

    private void revealCommunityCardsForPhase(int phase) {
        if (phase == 1) { openCard(0); handler.postDelayed(() -> openCard(1), 700); handler.postDelayed(() -> openCard(2), 1400); }
        else if (phase == 2) { openCard(3); }
        else if (phase == 3) { openCard(4); }
    }

    private void openCard(int i) {
        if (communityCards == null || communityCards[i] == null) return;
        ImageView img = (ImageView) communityCardsLayout.getChildAt(i);
        if ("opened".equals(img.getTag())) return;
        AnimatorSet out = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.card_flip_out);
        AnimatorSet in  = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.anim.card_flip_in);
        out.setDuration(150); in.setDuration(150); out.setTarget(img);
        out.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                img.setImageResource(communityCards[i].getImageResId(PokerOnline.this));
                img.setTag("opened"); in.setTarget(img); in.start();
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
        bot.showPlayer(opponentName, "$" + botBalance);
        db.updateBalance(username, userBalance);
        String mySeat = role.equals("host") ? "0" : "1";
        roomRef.child("seats").child(mySeat).child("balance").setValue(userBalance);
    }

    private void showStatus(String msg, int color) {
        txtStatus.setText(msg); txtStatus.setTextColor(color); txtStatus.setVisibility(View.VISIBLE);
    }

    private void disableAllButtons() {
        setBtn(btnFold, false); setBtn(btnCheck, false);
        setBtn(btnCall, false); setBtn(btnBetRaise, false);
        btnAllIn.setEnabled(false); btnAllIn.setAlpha(0.4f);
    }

    private void enableButtons() {
        setBtn(btnFold, true); setBtn(btnBetRaise, true);
        btnAllIn.setEnabled(true); btnAllIn.setAlpha(1.0f);
        setBtn(btnCheck, currentHighestBet <= myCurrentBet);
        setBtn(btnCall,  currentHighestBet > myCurrentBet);
    }

    private void setBtn(Button btn, boolean enabled) {
        btn.setEnabled(enabled); btn.setAlpha(enabled ? 1.0f : 0.4f);
    }

    // ================================================================
    //  LEAVE ROOM
    // ================================================================

    private void leaveRoom() {
        if (roomRef == null) return;
        if (mainListener != null) { roomRef.removeEventListener(mainListener); mainListener = null; }
        if (cardsListener != null) { roomRef.child("game_state").child("cards").removeEventListener(cardsListener); cardsListener = null; }
        if (role.equals("host")) { roomRef.removeValue(); }
        else { roomRef.child("seats").child("1").removeValue(); roomRef.child("game_state").child("status").setValue("waiting"); }
    }

    @Override
    protected void onDestroy() { leaveRoom(); super.onDestroy(); }
}