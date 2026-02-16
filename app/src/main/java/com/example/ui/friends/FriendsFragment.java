package com.example.ui.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.example.ui.friends.FriendsAdapter;

import androidx.activity.result.ActivityResultLauncher;

import java.util.List;


public class FriendsFragment extends Fragment {

    private final UserRepository repo = new UserRepository();
    private String myUid;
    private RecyclerView rvFriends;

    // Launcher za QR Skener
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() != null) {
                    // QR Kod sadrži UID ili Username korisnika
                    addFriendByUid(result.getContents());
                }
            });

    public FriendsFragment() { super(R.layout.fragment_friends); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        myUid = FirebaseAuth.getInstance().getUid();

        rvFriends = view.findViewById(R.id.rvFriends);
        Button btnSearch = view.findViewById(R.id.btnSearchUser);
        Button btnScan = view.findViewById(R.id.btnScanQr);

        FriendsAdapter adapter = new FriendsAdapter(this::onFriendClick);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFriends.setAdapter(adapter);

        repo.getFriendsQuery(myUid).addSnapshotListener((value, error) -> {
            if (value != null) {
                List<User> list = value.toObjects(User.class);
                adapter.setUsers(list);
            }
        });
        btnSearch.setOnClickListener(v -> showSearchDialog());
        btnScan.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Skeniraj QR kod prijatelja");
            options.setOrientationLocked(false);
            barcodeLauncher.launch(options);
        });
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        final EditText input = new EditText(getContext());
        input.setHint("Unesi korisničko ime");
        builder.setView(input);
        builder.setTitle("Dodaj prijatelja");

        builder.setPositiveButton("Traži", (dialog, which) -> {
            String username = input.getText().toString().trim();
            searchAndAddFriend(username);
        });
        builder.setNegativeButton("Otkaži", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void searchAndAddFriend(String username) {
        repo.searchUserByUsername(username).addOnSuccessListener(doc -> {
            User friend = doc.toObject(User.class);
            if(friend != null) {
                repo.addFriend(myUid, friend).addOnSuccessListener(v ->
                        Toast.makeText(getContext(), "Prijatelj dodat!", Toast.LENGTH_SHORT).show()
                );
            }
        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Korisnik nije pronađen.", Toast.LENGTH_SHORT).show()
        );
    }

    private void addFriendByUid(String targetUid) {
        repo.getUser(targetUid).addOnSuccessListener(doc -> {
            User friend = doc.toObject(User.class);
            if(friend != null) {
                repo.addFriend(myUid, friend).addOnSuccessListener(v ->
                        Toast.makeText(getContext(), "Prijatelj dodat preko QR-a!", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void onFriendClick(User friend) {
        repo.getCurrentUser().addOnSuccessListener(snap -> {
            User me = snap.toObject(User.class);
            if (me == null) return;

            if (me.allianceId == null || me.allianceId.isEmpty()) {
                Toast.makeText(getContext(), "Moraš prvo kreirati savez da bi pozivao ljude!", Toast.LENGTH_LONG).show();
            } else {
                showInviteDialog(friend, me.allianceId);
            }
        });
    }

    private void showInviteDialog(User friend, String myAllianceId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Pozovi u savez")
                .setMessage("Da li želiš da pozoveš " + friend.username + " u svoj savez?")
                .setPositiveButton("Pozovi", (d, w) -> {

                    repo.getCurrentUser().addOnSuccessListener(snap -> {
                        User me = snap.toObject(User.class);
                        if (me != null) {
                            repo.getAlliance(myAllianceId).addOnSuccessListener(alliance -> {

                                repo.inviteToAlliance(friend.uid, alliance, me.username)
                                        .addOnSuccessListener(v ->
                                                Toast.makeText(getContext(), "Pozivnica poslata!", Toast.LENGTH_SHORT).show()
                                        );
                            });
                        }
                    });
                })
                .setNegativeButton("Odustani", null)
                .show();
    }

}