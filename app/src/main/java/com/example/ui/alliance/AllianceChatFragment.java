package com.example.ui.alliance;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.data.service.SpecialMissionService;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AllianceChatFragment extends Fragment {

    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;

    private ChatAdapter adapter;

    private final UserRepository repo = new UserRepository();
    private final SpecialMissionService specialMissionService = new SpecialMissionService();

    private User currentUser;
    private String allianceId;

    private ListenerRegistration chatListener;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View v = inflater.inflate(R.layout.fragment_alliance_chat, container, false);

        rvChat = v.findViewById(R.id.rvChat);
        etMessage = v.findViewById(R.id.etMessage);
        btnSend = v.findViewById(R.id.btnSend);

        adapter = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);
        rvChat.setAdapter(adapter);

        Bundle args = getArguments();
        if (args != null) {
            allianceId = args.getString("ALLIANCE_ID");
            if (allianceId == null) {
                allianceId = args.getString("allianceId");
            }
        }

        if (allianceId == null || allianceId.trim().isEmpty()) {
            Toast.makeText(getContext(), "Alliance ID missing (chat).", Toast.LENGTH_SHORT).show();
            Log.e("AllianceChat", "Missing allianceId in arguments.");
            // Disable send to avoid null path usage
            btnSend.setEnabled(false);
            return v;
        }

        // Load current user first (for username)
        repo.getCurrentUser()
                .addOnSuccessListener(snap -> {
                    if (snap == null || !snap.exists()) {
                        Toast.makeText(getContext(), "User not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentUser = snap.toObject(User.class);
                    if (currentUser == null) {
                        Toast.makeText(getContext(), "User parse error.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    listenForMessages(); // start listener only when we have user + allianceId
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());

        btnSend.setOnClickListener(v1 -> sendMessage());

        return v;
    }

    private void listenForMessages() {
        if (allianceId == null) return;

        // safety: remove old listener if any
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }

        chatListener = FirebaseFirestore.getInstance()
                .collection("alliances")
                .document(allianceId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Toast.makeText(getContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("AllianceChat", "listen error", error);
                        return;
                    }
                    if (value == null) return;

                    List<ChatMessage> list = value.toObjects(ChatMessage.class);
                    if (list == null) list = new ArrayList<>();

                    adapter.setMessages(list);

                    if (!list.isEmpty()) {
                        rvChat.scrollToPosition(list.size() - 1);
                    }
                });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        if (currentUser == null) {
            Toast.makeText(getContext(), "User not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (allianceId == null || allianceId.trim().isEmpty()) {
            Toast.makeText(getContext(), "Alliance ID missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage msg = new ChatMessage(
                currentUser.uid,
                currentUser.username,
                text,
                Timestamp.now()
        );

        FirebaseFirestore.getInstance()
                .collection("alliances")
                .document(allianceId)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener(doc -> {
                    // 7.3 hook (once per day logic inside service)
                    specialMissionService.onAllianceMessageSent(allianceId, currentUser.uid);

                    etMessage.setText("");
                    rvChat.post(() -> rvChat.smoothScrollToPosition(Math.max(0, adapter.getItemCount() - 1)));
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
    }
}
