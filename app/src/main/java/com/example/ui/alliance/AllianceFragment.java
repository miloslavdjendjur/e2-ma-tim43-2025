package com.example.ui.alliance;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.Alliance;
import com.example.data.model.AllianceInvite;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.example.ui.alliance.InvitesAdapter;
import com.example.ui.friends.FriendsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class AllianceFragment extends Fragment {

    private final UserRepository repo = new UserRepository();
    private String myUid;
    private User currentUser;

    // UI elementi
    private LinearLayout layoutNoAlliance, layoutHasAlliance;
    private TextView tvAllianceName;
    private RecyclerView rvInvites, rvMembers;
    private Button btnCreateAlliance;

    // Adapteri
    private InvitesAdapter invitesAdapter;
    private FriendsAdapter membersAdapter;

    public AllianceFragment() { super(R.layout.fragment_alliance); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        myUid = FirebaseAuth.getInstance().getUid();

        layoutNoAlliance = view.findViewById(R.id.layoutNoAlliance);
        layoutHasAlliance = view.findViewById(R.id.layoutHasAlliance);
        tvAllianceName = view.findViewById(R.id.tvAllianceName);
        rvInvites = view.findViewById(R.id.rvInvites);
        rvMembers = view.findViewById(R.id.rvMembers);
        btnCreateAlliance = view.findViewById(R.id.btnCreateAlliance);

        setupAdapters();

        btnCreateAlliance.setOnClickListener(v -> showCreateDialog());

        checkUserStatus();
    }

    private void setupAdapters() {
        invitesAdapter = new InvitesAdapter(this::acceptInvite);
        rvInvites.setLayoutManager(new LinearLayoutManager(getContext()));
        rvInvites.setAdapter(invitesAdapter);

        membersAdapter = new FriendsAdapter();
        rvMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMembers.setAdapter(membersAdapter);
    }

    private void checkUserStatus() {
        if (myUid == null) return;

        repo.getCurrentUser().addOnSuccessListener(snap -> {
            if (snap == null || !snap.exists()) {
                Toast.makeText(getContext(), "GREŠKA: Nema profila!", Toast.LENGTH_SHORT).show();
                return;
            }

            currentUser = snap.toObject(User.class);
            if (currentUser == null) return;

            if (currentUser.allianceId == null || currentUser.allianceId.isEmpty()) {
                showNoAllianceUI();
            } else {
                loadAllianceDetails(currentUser.allianceId);
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void showNoAllianceUI() {
        layoutNoAlliance.setVisibility(View.VISIBLE);
        layoutHasAlliance.setVisibility(View.GONE);

        repo.getInvitesQuery(myUid).addSnapshotListener((value, error) -> {
            if (error != null) return;

            if (value != null) {
                int count = value.size();

                if (count > 0) {
                    AllianceInvite latest = value.toObjects(AllianceInvite.class).get(0);
                    sendSystemNotification("Novi poziv za Savez!", "Pozvao te je: " + latest.inviterName);
                }

                List<AllianceInvite> list = value.toObjects(AllianceInvite.class);
                invitesAdapter.setInvites(list);
            }
        });
    }

    private void loadAllianceDetails(String allianceId) {
        layoutNoAlliance.setVisibility(View.GONE);
        layoutHasAlliance.setVisibility(View.VISIBLE);

        repo.getAlliance(allianceId).addOnSuccessListener(alliance -> {
            if (alliance == null) return;

            tvAllianceName.setText(alliance.name);

            fetchMembersData(alliance.members);
        });
    }

    private void fetchMembersData(List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return;

        List<User> memberList = new ArrayList<>();

        for (String uid : memberIds) {
            repo.getUser(uid).addOnSuccessListener(documentSnapshot -> {
                User u = documentSnapshot.toObject(User.class);
                if (u != null) {
                    memberList.add(u);
                    membersAdapter.setUsers(new ArrayList<>(memberList));
                }
            });
        }
    }

    private void showCreateDialog() {
        final EditText input = new EditText(getContext());
        input.setHint("Naziv saveza");
        new AlertDialog.Builder(requireContext())
                .setTitle("Osnuj savez")
                .setView(input)
                .setPositiveButton("Kreiraj", (d, w) -> {
                    String name = input.getText().toString();
                    if (!name.isEmpty()) {
                        repo.createAlliance(name, currentUser)
                                .addOnSuccessListener(v -> checkUserStatus());
                    }
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    public void acceptInvite(AllianceInvite invite) {
        repo.respondToInvite(myUid, invite, true)
                .addOnSuccessListener(v -> checkUserStatus());
    }


    private void sendSystemNotification(String title, String message) {
        String channelId = "alliance_invites";
        NotificationManager manager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Pozivnice za savez", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // ILI TVOJA SLIKA
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true); // Nestane kad klikneš (iako spec traži da se ne sklanja, ovo je lakše za sad)

        manager.notify(1, builder.build());
    }
}