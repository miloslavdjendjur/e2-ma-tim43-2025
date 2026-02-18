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
import com.example.data.service.SpecialMissionService;
import com.example.myapplication.R;
import com.example.ui.friends.FriendsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class AllianceFragment extends Fragment {

    private final UserRepository repo = new UserRepository();
    private final SpecialMissionService specialMissionService = new SpecialMissionService();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String myUid;
    private User currentUser;

    // UI Elementi
    private LinearLayout layoutNoAlliance, layoutHasAlliance;
    private TextView tvAllianceName;
    private RecyclerView rvInvites, rvMembers;
    private Button btnCreateAlliance;
    private Button btnOpenChat;
    private Button btnStartMission;
    private Button btnViewMission;

    // Dugmići za akcije
//    private Button btnOpenChat;
//    private Button btnStartMission;

    private Button btnDisband;

    // Adapteri
    private InvitesAdapter invitesAdapter;
    private FriendsAdapter membersAdapter;

    private boolean isLeader = false;
    private boolean missionActive = false;

    private ListenerRegistration missionListener;

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
        btnViewMission = view.findViewById(R.id.btnViewMission);
        btnDisband = view.findViewById(R.id.btnDisband);

        setupAdapters();

        // default (dok se ne učita state)
        if (btnStartMission != null) btnStartMission.setVisibility(View.GONE);
        if (btnViewMission != null) btnViewMission.setVisibility(View.GONE);


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

        btnStartMission.setOnClickListener(v -> {
            if (currentUser == null || currentUser.allianceId == null) return;

            String allianceId = currentUser.allianceId;

            specialMissionService.startMissionLeaderOnly(allianceId)
                    .addOnSuccessListener(x -> {
                        // listener će sam da prebaci dugmad na "view"
                        Toast.makeText(getContext(), "Specijalna misija je pokrenuta.", Toast.LENGTH_SHORT).show();

                        // ako želiš odmah da otvoriš ekran:
                        Bundle b = new Bundle();
                        b.putString("ALLIANCE_ID", allianceId);
                        Navigation.findNavController(v).navigate(R.id.specialMissionFragment, b);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });

        btnViewMission.setOnClickListener(v -> {
            if (currentUser == null || currentUser.allianceId == null) return;

            Bundle b = new Bundle();
            b.putString("ALLIANCE_ID", currentUser.allianceId);
            Navigation.findNavController(v).navigate(R.id.specialMissionFragment, b);
        });

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
                detachMissionListener();
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

        if (btnStartMission != null) btnStartMission.setVisibility(View.GONE);
        if (btnViewMission != null) btnViewMission.setVisibility(View.GONE);

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

        // Resetujemo stanja dok učitavamo
        isLeader = false;
        missionActive = false; // Pretpostavimo da nije aktivna dok listener ne javi drugačije
        applyMissionButtons();

        repo.getAlliance(allianceId).addOnSuccessListener(alliance -> {
            if (alliance == null) return;

            tvAllianceName.setText(alliance.name);

            // Provera da li sam ja vođa
            isLeader = alliance.leaderUid != null && alliance.leaderUid.equals(myUid);

            if (isLeader) {
                // --- Prikaz za VOĐU ---
                btnStartMission.setVisibility(View.VISIBLE);
                btnDisband.setVisibility(View.VISIBLE);

                // Logika za dugme UKINI SAVEZ
                btnDisband.setOnClickListener(v -> {
                    // 1. Sigurnosna provera: Ne može se ukinuti ako traje misija
                    if (missionActive) {
                        Toast.makeText(getContext(), "Ne možete ukinuti savez dok traje specijalna misija!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 2. Onemogući dugme da se ne klikne dvaput
                    btnDisband.setEnabled(false);

                    // 3. Poziv repozitorijuma
                    repo.disbandAlliance(allianceId)
                            .addOnSuccessListener(x -> {
                                Toast.makeText(getContext(), "Savez je uspešno ukinut.", Toast.LENGTH_SHORT).show();
                                // Ovo će osvežiti status korisnika i vratiti ga na ekran "Nemaš savez"
                                checkUserStatus();
                            })
                            .addOnFailureListener(e -> {
                                btnDisband.setEnabled(true); // Vrati dugme ako pukne
                                Toast.makeText(getContext(), "Greška pri brisanju: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                });

            } else {
                // --- Prikaz za ČLANA ---
                btnStartMission.setVisibility(View.GONE);
                btnDisband.setVisibility(View.GONE);
            }

            // Učitaj podatke o članovima za listu
            fetchMembersData(alliance.members);

            // Ažuriraj dugmad za misiju (Start/View) na osnovu toga da li sam vođa i da li misija traje
            applyMissionButtons();

            // Zakači listener da pratiš stanje misije uživo
            attachMissionListener(allianceId);

        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Greška pri učitavanju saveza: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    private void attachMissionListener(@NonNull String allianceId) {
        detachMissionListener();

        DocumentReference missionRef = db.collection("alliances")
                .document(allianceId)
                .collection("special_mission")
                .document("current");

        missionListener = missionRef.addSnapshotListener((snap, err) -> {
            if (err != null) {
                Toast.makeText(getContext(), "PERMISSION_DENIED: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            boolean activeNow = false;
            if (snap != null && snap.exists()) {
                Boolean active = snap.getBoolean("active");
                Boolean defeated = snap.getBoolean("defeated");
                activeNow = (active != null && active) && !(defeated != null && defeated);
            }

            missionActive = activeNow;
            applyMissionButtons();
        });
    }

    private void detachMissionListener() {
        if (missionListener != null) {
            missionListener.remove();
            missionListener = null;
        }
    }

    /**
     * RULES:
     * - Ako misija JE aktivna: prikazi btnViewMission svima, sakrij btnStartMission
     * - Ako misija NIJE aktivna: sakrij btnViewMission, btnStartMission vidi samo leader
     */
    private void applyMissionButtons() {
        if (btnStartMission == null || btnViewMission == null) return;

        if (missionActive) {
            btnViewMission.setVisibility(View.VISIBLE);
            btnStartMission.setVisibility(View.GONE);
        } else {
            btnViewMission.setVisibility(View.GONE);
            btnStartMission.setVisibility(isLeader ? View.VISIBLE : View.GONE);
        }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachMissionListener();
    }
}
