package com.example.data.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.example.data.model.AllianceInvite;
import com.example.data.repo.UserRepository;
import com.google.firebase.auth.FirebaseAuth;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final BroadcastReceiver.PendingResult pendingResult = goAsync();

        String action = intent.getAction();
        String inviteId = intent.getStringExtra("inviteId");
        String allianceId = intent.getStringExtra("allianceId");
        int notificationId = intent.getIntExtra("notificationId", 0);
        String myUid = FirebaseAuth.getInstance().getUid();

        Log.d(TAG, "onReceive: Akcija=" + action);

        if (myUid == null || inviteId == null || allianceId == null) {
            Log.e(TAG, "Greška: Nedostaju podaci ili korisnik nije ulogovan.");
            pendingResult.finish();
            return;
        }

        UserRepository repo = new UserRepository();
        AllianceInvite inviteData = new AllianceInvite();
        inviteData.id = inviteId;
        inviteData.allianceId = allianceId;

        if ("ACTION_ACCEPT".equals(action)) {
            Toast.makeText(context, "Prihvatam poziv...", Toast.LENGTH_SHORT).show();

            repo.respondToInvite(myUid, inviteData, true)
                    .addOnSuccessListener(v -> {
                        Toast.makeText(context, "Uspešno si ušao u savez!", Toast.LENGTH_LONG).show();
                        dismissNotification(context, notificationId);
                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        pendingResult.finish();
                    });

        } else if ("ACTION_DECLINE".equals(action)) {
            Toast.makeText(context, "Odbijam poziv...", Toast.LENGTH_SHORT).show();

            repo.respondToInvite(myUid, inviteData, false)
                    .addOnSuccessListener(v -> {
                        Toast.makeText(context, "Poziv odbijen.", Toast.LENGTH_SHORT).show();
                        dismissNotification(context, notificationId);
                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        pendingResult.finish();
                    });
        } else {
            // Nepoznata akcija
            pendingResult.finish();
        }
    }

    private void dismissNotification(Context context, int notificationId) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId);
        } catch (SecurityException e) {
        }
    }
}