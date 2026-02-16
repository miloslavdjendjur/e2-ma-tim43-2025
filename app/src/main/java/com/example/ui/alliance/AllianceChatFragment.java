package com.example.ui.alliance;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.ChatMessage;
import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

public class AllianceChatFragment extends Fragment {

    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;

    private ChatAdapter adapter;
    private String allianceId;
    private User currentUser;
    private UserRepository repo = new UserRepository();

    public AllianceChatFragment() { super(R.layout.fragment_alliance_chat); }

    // Ovako primamo ID saveza iz AllianceFragment-a
    public static AllianceChatFragment newInstance(String allianceId) {
        AllianceChatFragment fragment = new AllianceChatFragment();
        Bundle args = new Bundle();
        args.putString("ALLIANCE_ID", allianceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            allianceId = getArguments().getString("ALLIANCE_ID");
        }

        rvChat = view.findViewById(R.id.rvChat);
        etMessage = view.findViewById(R.id.etMessage);
        btnSend = view.findViewById(R.id.btnSend);

        // Setup Adaptera
        adapter = new ChatAdapter();
        LinearLayoutManager manager = new LinearLayoutManager(getContext());
        manager.setStackFromEnd(true); // Poruke kreću od dna
        rvChat.setLayoutManager(manager);
        rvChat.setAdapter(adapter);

        // Učitaj ko sam ja (treba mi ime za slanje)
        repo.getCurrentUser().addOnSuccessListener(snap -> {
            currentUser = snap.toObject(User.class);
            listenForMessages(); // Tek kad znam ko sam, palim chat
        });

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || currentUser == null || allianceId == null) return;

        // Kreiranje poruke
        ChatMessage msg = new ChatMessage(
                currentUser.uid,
                currentUser.username,
                text,
                Timestamp.now()
        );

        // Slanje u pod-kolekciju "messages" unutar saveza
        FirebaseFirestore.getInstance()
                .collection("alliances").document(allianceId)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener(v -> {
                    etMessage.setText("");
                    rvChat.smoothScrollToPosition(adapter.getItemCount());
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Greška pri slanju", Toast.LENGTH_SHORT).show());
    }

    private void listenForMessages() {
        if (allianceId == null) return;

        FirebaseFirestore.getInstance()
                .collection("alliances").document(allianceId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING) // Hronološki
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        List<ChatMessage> list = value.toObjects(ChatMessage.class);
                        adapter.setMessages(list);
                        // Skroluj na dno kad stigne nova poruka
                        if (!list.isEmpty()) {
                            rvChat.scrollToPosition(list.size() - 1);
                        }
                    }
                });
    }
}