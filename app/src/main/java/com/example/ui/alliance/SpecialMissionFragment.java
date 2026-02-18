package com.example.ui.alliance;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.data.model.User;
import com.example.data.repo.UserRepository;
import com.example.data.service.SpecialMissionService;
import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class SpecialMissionFragment extends Fragment {

    private static final String ARG_ALLIANCE_ID = "ALLIANCE_ID";

    private TextView tvCountdown, tvBossHp, tvStatus;
    private Button btnDebugReward;
    private ProgressBar pbBossHp;
    private RecyclerView rvContrib;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final UserRepository userRepo = new UserRepository();

    private MemberContributionAdapter adapter;

    private String allianceId;
    private CountDownTimer timer;

    public SpecialMissionFragment() {
        super(R.layout.fragment_special_mission);
    }

    public static SpecialMissionFragment newInstance(String allianceId) {
        SpecialMissionFragment f = new SpecialMissionFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ALLIANCE_ID, allianceId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        allianceId = (getArguments() != null) ? getArguments().getString(ARG_ALLIANCE_ID) : null;

        tvCountdown = view.findViewById(R.id.tvCountdown);
        tvBossHp = view.findViewById(R.id.tvBossHp);
        tvStatus = view.findViewById(R.id.tvStatus);
        pbBossHp = view.findViewById(R.id.pbBossHp);
        rvContrib = view.findViewById(R.id.rvContributions);
        btnDebugReward = view.findViewById(R.id.btnDebugReward);

        adapter = new MemberContributionAdapter();
        rvContrib.setLayoutManager(new LinearLayoutManager(getContext()));
        rvContrib.setAdapter(adapter);

        if (allianceId == null || allianceId.isEmpty()) {
            tvStatus.setText("No alliance ID.");
            return;
        }

        listenMission();
        loadProgressOnceThenListen();

        btnDebugReward.setOnClickListener(v -> {
            new SpecialMissionService()
                    .debugTriggerRewards(allianceId)
                    .addOnSuccessListener(x ->
                            Toast.makeText(getContext(), "Rewards triggered.", Toast.LENGTH_SHORT).show()
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });
    }

    private DocumentReference missionRef() {
        return db.collection("alliances")
                .document(allianceId)
                .collection("special_mission")
                .document("current");
    }

    private void listenMission() {
        missionRef().addSnapshotListener((snap, err) -> {
            if (err != null) {
                tvStatus.setText("Error: " + err.getMessage());
                return;
            }
            if (snap == null || !snap.exists()) {
                tvStatus.setText("Special mission not started.");
                tvBossHp.setText("Boss HP: -- / --");
                pbBossHp.setProgress(0);
                cancelTimer();
                tvCountdown.setText("Time left: --:--:--");
                return;
            }

            Boolean active = snap.getBoolean("active");
            Boolean defeated = snap.getBoolean("defeated");
            Long total = snap.getLong("bossHpTotal");
            Long left = snap.getLong("bossHpRemaining");
            Timestamp endAt = snap.getTimestamp("endAt");

            long hpTotal = total != null ? total : 0L;
            long hpLeft = left != null ? left : 0L;

            tvBossHp.setText("Boss HP: " + hpLeft + " / " + hpTotal);

            // progress percent (0..100)
            int pct;
            if (hpTotal <= 0) pct = 0;
            else pct = (int) Math.round((hpLeft * 100.0) / hpTotal);

            pbBossHp.setMax(100);
            pbBossHp.setProgress(Math.max(0, Math.min(100, pct)));

            if (defeated != null && defeated) {
                tvStatus.setText("The boss has been defeated.");
            } else if (active != null && active) {
                tvStatus.setText("Mission is active.");
            } else {
                tvStatus.setText("Mission inactive");
            }

            if (endAt != null) startOrUpdateTimer(endAt.toDate());
            else {
                cancelTimer();
                tvCountdown.setText("Time left: --:--:--");
            }
        });
    }

    private void loadProgressOnceThenListen() {
        missionRef().collection("progress")
                .addSnapshotListener((qs, err) -> {
                    if (err != null) {
                        tvStatus.setText("Error (progress): " + err.getMessage());
                        return;
                    }
                    if (qs == null) return;

                    // prvo skupimo hpDealt po uid
                    HashMap<String, Long> hpByUid = new HashMap<>();
                    for (QueryDocumentSnapshot d : qs) {
                        long hp = computeHpDealtFromProgressDoc(d);
                        hpByUid.put(d.getId(), hp);
                    }

                    // onda dohvatimo imena korisnika preko UserRepository (simple)
                    List<MemberContributionAdapter.Row> rows = new ArrayList<>();
                    if (hpByUid.isEmpty()) {
                        adapter.setData(rows);
                        return;
                    }

                    // async: fetch users one by one
                    // (za tim od par članova je ok; ako hoćeš optimizaciju posle, uradimo batch lookup)
                    final int[] pending = {hpByUid.size()};
                    for (String uid : hpByUid.keySet()) {
                        long hp = hpByUid.get(uid) != null ? hpByUid.get(uid) : 0L;

                        userRepo.getUser(uid).addOnSuccessListener(snap -> {
                            String name = uid;
                            if (snap != null && snap.exists()) {
                                User u = snap.toObject(User.class);
                                if (u != null) {
                                    if (u.username != null && !u.username.isEmpty()) name = u.username;
                                }
                            }
                            rows.add(new MemberContributionAdapter.Row(uid, name, hp));

                            pending[0]--;
                            if (pending[0] == 0) adapter.setData(rows);
                        }).addOnFailureListener(e -> {
                            rows.add(new MemberContributionAdapter.Row(uid, uid, hp));
                            pending[0]--;
                            if (pending[0] == 0) adapter.setData(rows);
                        });
                    }
                });
    }

    /**
     * Računamo damage tačno po pravilima:
     * purchaseCount * 2
     * bossHitCount * 2
     * easyTaskCount * 1   (easyTaskCount su "units" već)
     * otherTaskCount * 4
     * messageDaysCount * 4
     */
    private long computeHpDealtFromProgressDoc(QueryDocumentSnapshot d) {
        Long purchase = d.getLong("purchaseCount");
        Long bossHits = d.getLong("bossHitCount");
        Long easyUnits = d.getLong("easyTaskCount");
        Long other = d.getLong("otherTaskCount");
        Long msgDays = d.getLong("messageDaysCount");

        long p = purchase != null ? purchase : 0L;
        long b = bossHits != null ? bossHits : 0L;
        long e = easyUnits != null ? easyUnits : 0L;
        long o = other != null ? other : 0L;
        long m = msgDays != null ? msgDays : 0L;

        return p * 2L + b * 2L + e * 1L + o * 4L + m * 4L;
    }

    private void startOrUpdateTimer(@NonNull Date endDate) {
        cancelTimer();
        long msLeft = endDate.getTime() - System.currentTimeMillis();
        if (msLeft <= 0) {
            tvCountdown.setText("Preostalo: 00:00:00");
            return;
        }

        timer = new CountDownTimer(msLeft, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                tvCountdown.setText("Preostalo: " + formatMs(millisUntilFinished));
            }

            @Override public void onFinish() {
                tvCountdown.setText("Preostalo: 00:00:00");
            }
        };
        timer.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private String formatMs(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

}
