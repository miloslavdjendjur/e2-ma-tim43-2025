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
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AllianceService extends Service {

    private static final String TAG = "AllianceService";
    private static final String CHANNEL_ID = "alliance_channel";

    private ListenerRegistration invitesListener;
    private ListenerRegistration chatListener;
    private ListenerRegistration userListener;
    private ListenerRegistration allianceListener;

    private String currentAllianceId = null;
    private int currentMemberCount = 0;

    // Novo polje za filtriranje starih poruka
    private long serviceStartTime;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTime = System.currentTimeMillis();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startListening();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        if (myUid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (invitesListener != null) invitesListener.remove();
        invitesListener = db.collection("users").document(myUid)
                .collection("alliance_invites")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                AllianceInvite invite = dc.getDocument().toObject(AllianceInvite.class);
                                invite.id = dc.getDocument().getId();
                                showInviteNotification(invite);
                            }
                        }
                    }
                });

        if (userListener != null) userListener.remove();
        userListener = db.collection("users").document(myUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    User user = snapshot.toObject(User.class);
                    if (user == null) return;

                    String newAllianceId = user.allianceId;

                    if (newAllianceId != null && !newAllianceId.equals(currentAllianceId)) {
                        currentAllianceId = newAllianceId;
                        startAllianceListeners(newAllianceId, myUid);
                    } else if (newAllianceId == null && currentAllianceId != null) {
                        currentAllianceId = null;
                        stopAllianceListeners();
                    }
                });
    }

    private void startAllianceListeners(String allianceId, String myUid) {
        stopAllianceListeners();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        chatListener = db.collection("alliances").document(allianceId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    for (DocumentChange dc : value.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessage msg = dc.getDocument().toObject(ChatMessage.class);

                            if (msg != null && msg.senderUid != null && !msg.senderUid.equals(myUid)) {
                                // Provera vremena: Prikazi samo ako je poruka novija od startovanja servisa
                                if (msg.timestamp != null && msg.timestamp.toDate().getTime() > serviceStartTime) {
                                    showChatNotification("Nova poruka (" + msg.senderName + ")", msg.messageText);
                                }
                            }
                        }
                    }
                });

        allianceListener = db.collection("alliances").document(allianceId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    Alliance alliance = snapshot.toObject(Alliance.class);
                    if (alliance == null) return;

                    if (alliance.leaderUid != null && alliance.leaderUid.equals(myUid)) {
                        int newCount = (alliance.members != null) ? alliance.members.size() : 0;

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

    private void showInviteNotification(AllianceInvite invite) {
        int notificationId = (int) System.currentTimeMillis();

        Intent acceptIntent = new Intent(this, NotificationReceiver.class);
        acceptIntent.setAction("ACTION_ACCEPT");
        acceptIntent.putExtra("inviteId", invite.id);
        acceptIntent.putExtra("allianceId", invite.allianceId);
        acceptIntent.putExtra("notificationId", notificationId);
        PendingIntent acceptPending = PendingIntent.getBroadcast(this, notificationId, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
                .setOngoing(true)
                .addAction(R.drawable.ic_launcher_foreground, "Prihvati", acceptPending)
                .addAction(R.drawable.ic_launcher_foreground, "Odbij", declinePending);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId, builder.build());
    }

    private void showChatNotification(String title, String message) {
        showBasicNotification(title, message);
    }

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