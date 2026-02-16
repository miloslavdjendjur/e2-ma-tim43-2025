package com.example.ui.alliance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.data.model.ChatMessage;
import com.example.myapplication.R;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<ChatMessage> messages = new ArrayList<>();
    private String myUid = FirebaseAuth.getInstance().getUid();

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        // Formatiranje vremena (HH:mm)
        String time = "";
        if(msg.timestamp != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            time = sdf.format(msg.timestamp.toDate());
        }

        if (msg.senderUid != null && msg.senderUid.equals(myUid)) {
            // MOJA PORUKA -> Desno
            holder.layoutSent.setVisibility(View.VISIBLE);
            holder.layoutReceived.setVisibility(View.GONE);

            holder.tvMessageSent.setText(msg.messageText);
            holder.tvTimeSent.setText(time);
        } else {
            // TUĐA PORUKA -> Levo
            holder.layoutSent.setVisibility(View.GONE);
            holder.layoutReceived.setVisibility(View.VISIBLE);

            holder.tvSenderName.setText(msg.senderName);
            holder.tvMessageReceived.setText(msg.messageText);
            holder.tvTimeReceived.setText(time);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutSent, layoutReceived;
        TextView tvMessageSent, tvTimeSent;
        TextView tvSenderName, tvMessageReceived, tvTimeReceived;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);

            tvMessageSent = itemView.findViewById(R.id.tvMessageSent);
            tvTimeSent = itemView.findViewById(R.id.tvTimeSent);

            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageReceived = itemView.findViewById(R.id.tvMessageReceived);
            tvTimeReceived = itemView.findViewById(R.id.tvTimeReceived);
        }
    }
}