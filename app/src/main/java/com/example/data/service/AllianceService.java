package com.example.data.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.data.model.Alliance;
import com.example.data.model.AllianceInvite;
import com.example.data.model.ChatMessage;
import com.example.data.model.User;
import com.example.myapplication.R; // Proveri da li je R klasa dobro importovana
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AllianceService extends Service {

    private static final String TAG = "AllianceService";
    private static final String CHANNEL_ID = "alliance_channel";

    // Listeneri za bazu
    private ListenerRegistration invitesListener;
    private ListenerRegistration chatListener;
    private ListenerRegistration userListener;
    private ListenerRegistration allianceListener; // Za vođu (da zna kad neko uđe)

    // Pamtimo trenutni savez da bismo znali promene
    private String currentAllianceId = null;
    private int currentMemberCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Servis kreiran");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Servis pokrenut");
        startListening();
        // START_STICKY: Ako sistem ubije servis zbog memorije, pokušaće da ga ponovo pokrene
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Servis ugašen, čistim listenere");
        removeAllListeners();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void removeAllListeners() {
        if (invitesListener != null) invitesListener.remove();
        if (chatListener != null) chatListener.remove();
        if (userListener != null) userListener.remove();
        if (allianceListener != null) allianceListener.remove();
    }

    private void startListening() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) {
            Log.e(TAG, "startListening: Nema ulogovanog korisnika");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. SLUŠANJE POZIVNICA (Invites)
        if (invitesListener != null) invitesListener.remove();
        invitesListener = db.collection("users").document(myUid)
                .collection("alliance_invites")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                AllianceInvite invite = dc.getDocument().toObject(AllianceInvite.class);
                                // Dodajemo ID dokumenta u objekat (trebaće nam za brisanje/prihvatanje)
                                invite.id = dc.getDocument().getId();

                                Log.d(TAG, "Nova pozivnica od: " + invite.inviterName);
                                showInviteNotification(invite);
                            }
                        }
                    }
                });

        // 2. SLUŠANJE KORISNIKA (User Status)
        // Ovo nam treba da znamo da li je ušao u savez, izašao, ili promenio savez
        if (userListener != null) userListener.remove();
        userListener = db.collection("users").document(myUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    User user = snapshot.toObject(User.class);
                    if (user == null) return;

                    String newAllianceId = user.allianceId;

                    // Provera da li se savez promenio
                    if (newAllianceId != null && !newAllianceId.equals(currentAllianceId)) {
                        Log.d(TAG, "Promena saveza detektovana: " + newAllianceId);
                        currentAllianceId = newAllianceId;
                        startAllianceListeners(newAllianceId, myUid);
                    } else if (newAllianceId == null && currentAllianceId != null) {
                        Log.d(TAG, "Korisnik je napustio savez");
                        currentAllianceId = null;
                        stopAllianceListeners();
                    }
                });
    }

    // Pokreće listenere vezane za konkretan savez (Chat + Status članova)
    private void startAllianceListeners(String allianceId, String myUid) {
        stopAllianceListeners(); // Prvo očisti stare ako postoje
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // A) CHAT LISTENER
        chatListener = db.collection("alliances").document(allianceId)
                .collection("messages")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    for (DocumentChange dc : value.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessage msg = dc.getDocument().toObject(ChatMessage.class);
                            // Ne šalji notifikaciju za moje poruke
                            if (msg != null && msg.senderUid != null && !msg.senderUid.equals(myUid)) {
                                showChatNotification("Nova poruka (" + msg.senderName + ")", msg.messageText);
                            }
                        }
                    }
                });

        // B) ALLIANCE DETAILS LISTENER (Za vođu)
        allianceListener = db.collection("alliances").document(allianceId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    Alliance alliance = snapshot.toObject(Alliance.class);
                    if (alliance == null) return;

                    // Ako sam ja vođa
                    if (alliance.leaderUid != null && alliance.leaderUid.equals(myUid)) {
                        int newCount = (alliance.members != null) ? alliance.members.size() : 0;

                        // Ako se broj članova povećao (neko je prihvatio poziv)
                        if (newCount > currentMemberCount && currentMemberCount > 0) {
                            showBasicNotification("Novi član saveza!", "Neko je prihvatio tvoj poziv.");
                        }
                        currentMemberCount = newCount;
                    }
                });
    }

    private void stopAllianceListeners() {
        if (chatListener != null) chatListener.remove();
        if (allianceListener != null) allianceListener.remove();
        currentMemberCount = 0;
    }

    // --- METODE ZA PRIKAZ NOTIFIKACIJA ---

    // 1. Notifikacija za POZIV (Sa dugmićima Prihvati/Odbij)
    private void showInviteNotification(AllianceInvite invite) {
        int notificationId = (int) System.currentTimeMillis();

        // Intent za Prihvati
        Intent acceptIntent = new Intent(this, NotificationReceiver.class);
        acceptIntent.setAction("ACTION_ACCEPT");
        acceptIntent.putExtra("inviteId", invite.id);
        acceptIntent.putExtra("allianceId", invite.allianceId);
        acceptIntent.putExtra("notificationId", notificationId);
        PendingIntent acceptPending = PendingIntent.getBroadcast(this, notificationId, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent za Odbij
        Intent declineIntent = new Intent(this, NotificationReceiver.class);
        declineIntent.setAction("ACTION_DECLINE");
        declineIntent.putExtra("inviteId", invite.id);
        declineIntent.putExtra("allianceId", invite.allianceId);
        declineIntent.putExtra("notificationId", notificationId);
        PendingIntent declinePending = PendingIntent.getBroadcast(this, notificationId + 1, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Poziv u savez: " + invite.allianceName)
                .setContentText("Pozvao te je: " + invite.inviterName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true) // Ne može se skloniti swajpovanjem (Specifikacija 7.1)
                .addAction(R.drawable.ic_launcher_foreground, "Prihvati", acceptPending)
                .addAction(R.drawable.ic_launcher_foreground, "Odbij", declinePending);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId, builder.build());
    }

    // 2. Notifikacija za CHAT
    private void showChatNotification(String title, String message) {
        showBasicNotification(title, message);
    }

    // 3. Generička notifikacija (koristi se za Chat i za Vođu)
    private void showBasicNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alliance Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Sve notifikacije vezane za savez");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}