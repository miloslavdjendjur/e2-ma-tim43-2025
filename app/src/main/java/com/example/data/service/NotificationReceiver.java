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
        String action = intent.getAction();
        String inviteId = intent.getStringExtra("inviteId");
        String allianceId = intent.getStringExtra("allianceId");
        int notificationId = intent.getIntExtra("notificationId", 0);
        String myUid = FirebaseAuth.getInstance().getUid();

        Log.d(TAG, "onReceive: Akcija=" + action + ", InviteID=" + inviteId + ", AllianceID=" + allianceId);

        // 1. Provera podataka - Ako nešto fali, ne radi ništa da ne pukne app
        if (myUid == null) {
            Log.e(TAG, "Greška: Korisnik nije ulogovan.");
            return;
        }
        if (inviteId == null || allianceId == null) {
            Log.e(TAG, "Greška: Nedostaju podaci u intentu (inviteId ili allianceId je null).");
            Toast.makeText(context, "Greška: Neispravan poziv.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Skloni notifikaciju
        try {
            NotificationManagerCompat.from(context).cancel(notificationId);
        } catch (SecurityException e) {
            // Ignoriši ako fali dozvola, nije kritično
        }

        UserRepository repo = new UserRepository();
        AllianceInvite inviteData = new AllianceInvite();
        inviteData.id = inviteId;
        inviteData.allianceId = allianceId;

        // "goAsync()" drži Receiver u životu
        final PendingResult pendingResult = goAsync();

        try {
            if ("ACTION_ACCEPT".equals(action)) {
                Toast.makeText(context, "Obrada: Prihvatam...", Toast.LENGTH_SHORT).show();

                repo.respondToInvite(myUid, inviteData, true)
                        .addOnSuccessListener(v -> {
                            Log.d(TAG, "Uspešno prihvaćeno!");
                            Toast.makeText(context, "Uspešno si ušao u savez!", Toast.LENGTH_LONG).show();
                            pendingResult.finish();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Greška pri prihvatanju: ", e);
                            Toast.makeText(context, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            pendingResult.finish();
                        });

            } else if ("ACTION_DECLINE".equals(action)) {
                Toast.makeText(context, "Obrada: Odbijam...", Toast.LENGTH_SHORT).show();

                repo.respondToInvite(myUid, inviteData, false)
                        .addOnSuccessListener(v -> {
                            Log.d(TAG, "Uspešno odbijeno!");
                            Toast.makeText(context, "Poziv odbijen.", Toast.LENGTH_SHORT).show();
                            pendingResult.finish();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Greška pri odbijanju: ", e);
                            Toast.makeText(context, "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            pendingResult.finish();
                        });
            } else {
                // Nepoznata akcija, samo završi
                pendingResult.finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "KRITIČNA GREŠKA u Receiveru: ", e);
            Toast.makeText(context, "Došlo je do greške u aplikaciji.", Toast.LENGTH_SHORT).show();
            pendingResult.finish();
        }
    }
}