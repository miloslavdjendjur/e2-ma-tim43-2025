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
import androidx.navigation.Navigation;
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
            options.setPrompt("Scan QR code");
            options.setOrientationLocked(false);
            barcodeLauncher.launch(options);
        });
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        final EditText input = new EditText(getContext());
        input.setHint("Username");
        builder.setView(input);
        builder.setTitle("Add friend");

        builder.setPositiveButton("Search", (dialog, which) -> {
            String username = input.getText().toString().trim();
            searchAndAddFriend(username);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void searchAndAddFriend(String username) {
        repo.searchUserByUsername(username).addOnSuccessListener(doc -> {
            User friend = doc.toObject(User.class);
            if(friend != null) {
                repo.addFriend(myUid, friend).addOnSuccessListener(v ->
                        Toast.makeText(getContext(), "Friend added", Toast.LENGTH_SHORT).show()
                );
            }
        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show()
        );
    }

    private void addFriendByUid(String targetUid) {
        repo.getUser(targetUid).addOnSuccessListener(doc -> {
            User friend = doc.toObject(User.class);
            if(friend != null) {
                repo.addFriend(myUid, friend).addOnSuccessListener(v ->
                        Toast.makeText(getContext(), "Friend added via QR code", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void onFriendClick(User friend) {
        Bundle bundle = new Bundle();
        bundle.putString("TARGET_UID", friend.uid);

        try {
            Navigation.findNavController(requireView())
                    .navigate(R.id.userProfileFragment, bundle);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Navigation not configured", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInviteDialog(User friend, String myAllianceId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Invite to alliance")
                .setMessage("Are you sure you want to invite " + friend.username + " to your alliance?")
                .setPositiveButton("Invite", (d, w) -> {

                    repo.getCurrentUser().addOnSuccessListener(snap -> {
                        User me = snap.toObject(User.class);
                        if (me != null) {
                            repo.getAlliance(myAllianceId).addOnSuccessListener(alliance -> {

                                repo.inviteToAlliance(friend.uid, alliance, me.username)
                                        .addOnSuccessListener(v ->
                                                Toast.makeText(getContext(), "Invite sent.", Toast.LENGTH_SHORT).show()
                                        );
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

}