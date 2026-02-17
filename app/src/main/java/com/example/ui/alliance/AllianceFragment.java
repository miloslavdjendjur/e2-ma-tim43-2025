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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.AllianceInvite;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.example.ui.friends.FriendsAdapter;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class AllianceFragment extends Fragment {

    private final UserRepository repo = new UserRepository();
    private String myUid;
    private User currentUser;

    // UI Elementi
    private LinearLayout layoutNoAlliance, layoutHasAlliance;
    private TextView tvAllianceName;
    private RecyclerView rvInvites, rvMembers;
    private Button btnCreateAlliance;

    // Dugmići za akcije
    private Button btnOpenChat;
    private Button btnStartMission;

    private Button btnDisband;

    // Adapteri
    private InvitesAdapter invitesAdapter;
    private FriendsAdapter membersAdapter;

    public AllianceFragment() { super(R.layout.fragment_alliance); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        myUid = FirebaseAuth.getInstance().getUid();

        // Inicijalizacija View-ova
        layoutNoAlliance = view.findViewById(R.id.layoutNoAlliance);
        layoutHasAlliance = view.findViewById(R.id.layoutHasAlliance);
        tvAllianceName = view.findViewById(R.id.tvAllianceName);
        rvInvites = view.findViewById(R.id.rvInvites);
        rvMembers = view.findViewById(R.id.rvMembers);

        btnCreateAlliance = view.findViewById(R.id.btnCreateAlliance);
        btnOpenChat = view.findViewById(R.id.btnOpenChat);
        btnStartMission = view.findViewById(R.id.btnStartMission);
        btnDisband = view.findViewById(R.id.btnDisband);

        setupAdapters();

        // Listener: Kreiraj savez
        btnCreateAlliance.setOnClickListener(v -> showCreateDialog());

        // Listener: Otvori Chat (Navigacija)
        btnOpenChat.setOnClickListener(v -> {
            if (currentUser != null && currentUser.allianceId != null) {
                try {
                    Bundle bundle = new Bundle();
                    bundle.putString("ALLIANCE_ID", currentUser.allianceId);
                    Navigation.findNavController(v).navigate(R.id.allianceChatFragment, bundle);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Greška u navigaciji", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Listener: Start Mission (Samo za vođu - Student 2 logika)
        btnStartMission.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Započinjem misiju...", Toast.LENGTH_SHORT).show();
            // Ovde Student 2 dodaje repo.startMission(...)
        });

        // Učitaj podatke
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
            if (snap == null || !snap.exists()) return;

            currentUser = snap.toObject(User.class);
            if (currentUser == null) return;

            if (currentUser.allianceId == null || currentUser.allianceId.isEmpty()) {
                showNoAllianceUI();
            } else {
                loadAllianceDetails(currentUser.allianceId);
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void showNoAllianceUI() {
        layoutNoAlliance.setVisibility(View.VISIBLE);
        layoutHasAlliance.setVisibility(View.GONE);

        // Slušamo pozivnice da bismo ažurirali listu (ALI NE ŠALJEMO NOTIFIKACIJU OVDE)
        repo.getInvitesQuery(myUid).addSnapshotListener((value, error) -> {
            if (error != null) return;

            if (value != null) {
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

            // Prikazi dugme za misiju samo ako sam ja vođa
            if (alliance.leaderUid != null && alliance.leaderUid.equals(myUid)) {
                btnStartMission.setVisibility(View.VISIBLE);
            } else {
                btnStartMission.setVisibility(View.GONE);
            }

            // U loadAllianceDetails metodi:
            if (alliance.leaderUid.equals(myUid)) {
                btnStartMission.setVisibility(View.VISIBLE);

                // Dodaj i dugme za ukidanje (pretpostavimo da si ga dodao u XML kao btnDisband)
                btnDisband.setVisibility(View.VISIBLE);
                btnDisband.setOnClickListener(v -> {
                    // Provera misije (Student 2 deo - ovde samo placeholder)
                    // if (missionActive) { Toast... "Ne može dok traje misija" return; }

                    repo.disbandAlliance(alliance.id).addOnSuccessListener(x -> {
                        checkUserStatus(); // Osveži UI, vratiće te na "Nemaš savez"
                    });
                });
            } else {
                btnDisband.setVisibility(View.GONE);
            }

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
                                .addOnSuccessListener(v -> {
                                    Toast.makeText(getContext(), "Savez kreiran!", Toast.LENGTH_SHORT).show();
                                    checkUserStatus(); // Osveži UI
                                });
                    }
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    public void acceptInvite(AllianceInvite invite) {
        repo.respondToInvite(myUid, invite, true)
                .addOnSuccessListener(v -> {
                    Toast.makeText(getContext(), "Dobrodošao u savez!", Toast.LENGTH_SHORT).show();
                    checkUserStatus(); // Osveži UI
                });
    }
}