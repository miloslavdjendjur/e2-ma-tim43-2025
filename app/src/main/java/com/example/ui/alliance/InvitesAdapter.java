package com.example.ui.alliance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.data.model.AllianceInvite;
import com.example.myapplication.R;
import java.util.ArrayList;
import java.util.List;

public class InvitesAdapter extends RecyclerView.Adapter<InvitesAdapter.ViewHolder> {
    private List<AllianceInvite> invites = new ArrayList<>();
    private final OnInviteAction listener;

    public interface OnInviteAction {
        void onAccept(AllianceInvite invite);
    }

    public InvitesAdapter(OnInviteAction listener) { this.listener = listener; }

    public void setInvites(List<AllianceInvite> invites) {
        this.invites = invites;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AllianceInvite invite = invites.get(position);
        holder.tvName.setText("Poziv od: " + invite.inviterName + " (Savez: " + invite.allianceName + ")");
        holder.btnAction.setVisibility(View.VISIBLE);
        holder.btnAction.setText("Prihvati");
        holder.btnAction.setOnClickListener(v -> listener.onAccept(invite));
    }

    @Override public int getItemCount() { return invites.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        Button btnAction;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvItemName);
            btnAction = itemView.findViewById(R.id.btnItemAction);
        }
    }
}