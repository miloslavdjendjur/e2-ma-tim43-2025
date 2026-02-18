package com.example.data.service;

import com.example.data.model.boss.AttackResult;
import com.example.data.model.boss.Boss;
import com.example.data.model.boss.FightResult;
import com.example.data.model.Task;
import com.example.data.model.equipment.type.ClothesType;
import com.example.data.model.equipment.type.WeaponType;
import com.example.data.repo.BossRepository;
import com.example.data.repo.EquipmentRepository;
import com.example.data.repo.TaskRepository;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class BossService {

    private final BossRepository bossRepo = new BossRepository();
    private final TaskRepository taskRepo = new TaskRepository();
    private final EquipmentRepository equipmentRepo = new EquipmentRepository();
    private final Random random = new Random();

    // ---- Quota keys (based on BASE tiers) ----
    private static final String QUOTA_VE_NORMAL = "VE_NORMAL";                   // diff 1 + imp 1
    private static final String QUOTA_EASY_IMPORTANT = "EASY_IMPORTANT";         // diff 3 + imp 3
    private static final String QUOTA_HARD_EXT_IMPORTANT = "HARD_EXT_IMPORTANT"; // diff 7 + imp 10
    private static final String QUOTA_EXTREMELY_HARD = "EXTREMELY_HARD";         // diff 20
    private static final String QUOTA_SPECIAL = "SPECIAL";                       // imp 100

    // ------------------ FORMULE ------------------

    public long calculateMaxHp(int bossLevel) {
        if (bossLevel <= 1) return 200;
        long hp = 200;
        for (int i = 2; i <= bossLevel; i++) {
            hp = (hp * 5) / 2; // *2.5
        }
        return hp;
    }

    public int calculateBaseCoinReward(int bossLevel) {
        if (bossLevel <= 1) return 200;
        long coins = 200;
        for (int i = 2; i <= bossLevel; i++) {
            coins = (coins * 120) / 100; // +20%
        }
        return (coins > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) coins;
    }

    // ------------------ PRE-SPAWN (ON LEVEL-UP) ------------------

    /**
     * Creates a boss doc if missing / wrong / finished, so it exists BEFORE user clicks fight.
     * Safe to call multiple times.
     */
    public com.google.android.gms.tasks.Task<Void> preSpawnBossIfNeeded(int bossLevelToFight) {
        TaskCompletionSource<Void> tcs = new TaskCompletionSource<>();
        final int lvl = Math.max(1, bossLevelToFight);

        bossRepo.getCurrentBoss()
                .addOnSuccessListener(doc -> {
                    Boss current = null;
                    if (doc != null && doc.exists()) current = doc.toObject(Boss.class);

                    boolean okToKeep = current != null
                            && !current.isDefeated()
                            && current.getLevel() == lvl
                            && !"ESCAPED".equalsIgnoreCase(current.getStatus())
                            && !"DEFEATED".equalsIgnoreCase(current.getStatus());

                    if (okToKeep) {
                        tcs.setResult(null);
                        return;
                    }

                    long maxHp = calculateMaxHp(lvl);
                    Boss boss = new Boss(lvl, maxHp);
                    // attacksLeft is decided when battle starts
                    boss.setAttacksLeft(0);
                    boss.setStatus("ACTIVE");

                    bossRepo.saveBoss(boss)
                            .addOnSuccessListener(v -> tcs.setResult(null))
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    // ------------------ BOSS RESPWAN / START BATTLE ------------------

    public com.google.android.gms.tasks.Task<Boss>  getBossForBattle(int expectedBossLevel, int attacksForThisBattle) {
        TaskCompletionSource<Boss> tcs = new TaskCompletionSource<>();

        bossRepo.getCurrentBoss()
                .addOnSuccessListener(doc -> {
                    Boss current = null;
                    if (doc != null && doc.exists()) current = doc.toObject(Boss.class);

                    Boss bossToFight;
                    if (current != null && !current.isDefeated() && current.getLevel() == expectedBossLevel) {
                        bossToFight = current;
                        bossToFight.setStatus("ACTIVE");
                    } else {
                        long maxHp = calculateMaxHp(expectedBossLevel);
                        bossToFight = new Boss(expectedBossLevel, maxHp);
                    }


                    bossToFight.setAttacksLeft(Math.max(1, attacksForThisBattle));

                    bossRepo.saveBoss(bossToFight)
                            .addOnSuccessListener(v -> tcs.setResult(bossToFight))
                            .addOnFailureListener(tcs::setException);
                })
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    // ------------------ SUCCESS RATE (ETAPA + KVOTE) ------------------

    private static class Attempt {
        String idKey;
        Timestamp when;
        String status;
        String quotaKey;
        String periodKey;
    }

    public com.google.android.gms.tasks.Task<Double> calculateTaskSuccessRate(Timestamp lastLevelUp) {
        TaskCompletionSource<Double> tcs = new TaskCompletionSource<>();

        taskRepo.getTasks(tasks -> {
            if (tasks == null || tasks.isEmpty()) {
                tcs.setResult(0.0);
                return;
            }

            List<Attempt> attempts = new ArrayList<>();

            for (Task t : tasks) {
                String quotaKey = quotaKeyForTask(t);

                if (Task.TYPE_SINGLE.equals(t.getType())) {
                    Timestamp exec = t.getExecutionTime();
                    if (!isInStage(exec, lastLevelUp)) continue;

                    String st = (t.getStatus() != null) ? t.getStatus() : Task.STATUS_ACTIVE;
                    if (Task.STATUS_PAUSED.equals(st) || Task.STATUS_CANCELED.equals(st)) continue;

                    Attempt a = new Attempt();
                    a.idKey = "S|" + safe(t.getId());
                    a.when = (exec != null) ? exec : Timestamp.now();
                    a.status = st;
                    a.quotaKey = quotaKey;
                    a.periodKey = computePeriodKey(quotaKey, dateKeyFromTimestamp(a.when));
                    attempts.add(a);
                } else if (Task.TYPE_RECURRING.equals(t.getType())) {
                    Map<String, String> occ = t.getOccurrenceStatuses();
                    if (occ == null || occ.isEmpty()) continue;

                    for (Map.Entry<String, String> e : occ.entrySet()) {
                        String dateKey = e.getKey();
                        Timestamp occTs = timestampEndOfDay(dateKey);
                        if (!isInStage(occTs, lastLevelUp)) continue;

                        String st = (e.getValue() != null) ? e.getValue() : Task.STATUS_ACTIVE;
                        if (Task.STATUS_PAUSED.equals(st) || Task.STATUS_CANCELED.equals(st)) continue;

                        Attempt a = new Attempt();
                        a.idKey = "R|" + safe(t.getId()) + "|" + dateKey;
                        a.when = occTs;
                        a.status = st;
                        a.quotaKey = quotaKey;
                        a.periodKey = computePeriodKey(quotaKey, dateKey);
                        attempts.add(a);
                    }
                }
            }

            if (attempts.isEmpty()) {
                tcs.setResult(0.0);
                return;
            }

            List<Attempt> filtered = applyQuotaFiltering(attempts);

            if (filtered.isEmpty()) {
                tcs.setResult(0.0);
                return;
            }

            int total = filtered.size();
            int done = 0;
            for (Attempt a : filtered) {
                if (Task.STATUS_DONE.equals(a.status)) done++;
            }

            double rate = (double) done / (double) total * 100.0;
            tcs.setResult(rate);
        });

        return tcs.getTask();
    }

    private List<Attempt> applyQuotaFiltering(List<Attempt> attempts) {
        attempts.sort(Comparator
                .comparing((Attempt a) -> a.when.toDate())
                .thenComparing(a -> a.idKey));

        List<Attempt> out = new ArrayList<>();
        java.util.HashMap<String, Integer> counter = new java.util.HashMap<>();

        for (Attempt a : attempts) {
            if (a.quotaKey == null) {
                out.add(a);
                continue;
            }
            int limit = quotaLimitForKey(a.quotaKey);

            String bucket = a.quotaKey + "|" + a.periodKey;
            int curr = counter.containsKey(bucket) ? counter.get(bucket) : 0;

            if (curr < limit) {
                out.add(a);
                counter.put(bucket, curr + 1);
            }
        }

        return out;
    }

    private boolean isInStage(Timestamp eventTime, Timestamp lastLevelUp) {
        if (lastLevelUp == null) return true;
        if (eventTime == null) return false;
        return eventTime.compareTo(lastLevelUp) >= 0;
    }

    private String safe(String s) { return (s == null) ? "" : s; }

    // ------------------ QUOTA (FIX: uses BASE XP tiers) ------------------

    private String quotaKeyForTask(Task t) {
        if (t == null) return null;

        int baseImp = t.getBaseImportanceXp();
        int baseDiff = t.getBaseDifficultyXp();

        if (baseImp == 100) return QUOTA_SPECIAL;
        if (baseDiff == 20) return QUOTA_EXTREMELY_HARD;

        if (baseDiff == 1 && baseImp == 1) return QUOTA_VE_NORMAL;
        if (baseDiff == 3 && baseImp == 3) return QUOTA_EASY_IMPORTANT;
        if (baseDiff == 7 && baseImp == 10) return QUOTA_HARD_EXT_IMPORTANT;

        return null;
    }

    private int quotaLimitForKey(String quotaKey) {
        switch (quotaKey) {
            case QUOTA_VE_NORMAL:
            case QUOTA_EASY_IMPORTANT:
                return 5; // daily
            case QUOTA_HARD_EXT_IMPORTANT:
                return 2; // daily
            case QUOTA_EXTREMELY_HARD:
            case QUOTA_SPECIAL:
                return 1; // weekly / monthly
            default:
                return Integer.MAX_VALUE;
        }
    }

    private boolean isDailyQuota(String quotaKey) {
        return QUOTA_VE_NORMAL.equals(quotaKey)
                || QUOTA_EASY_IMPORTANT.equals(quotaKey)
                || QUOTA_HARD_EXT_IMPORTANT.equals(quotaKey);
    }

    private boolean isWeeklyQuota(String quotaKey) {
        return QUOTA_EXTREMELY_HARD.equals(quotaKey);
    }

    private boolean isMonthlyQuota(String quotaKey) {
        return QUOTA_SPECIAL.equals(quotaKey);
    }

    private String computePeriodKey(String quotaKey, String dateKey) {
        if (quotaKey == null) return dateKey;
        if (isDailyQuota(quotaKey)) return dateKey;
        if (isWeeklyQuota(quotaKey)) return weekKeyFromDateKey(dateKey);
        if (isMonthlyQuota(quotaKey)) return monthKeyFromDateKey(dateKey);
        return dateKey;
    }

    private String dateKeyFromTimestamp(Timestamp ts) {
        if (ts == null) return "1970-01-01";
        Calendar c = Calendar.getInstance();
        c.setTime(ts.toDate());
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
    }

    private String weekKeyFromDateKey(String dateKey) {
        try {
            String[] parts = dateKey.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);

            Calendar c = Calendar.getInstance();
            c.setFirstDayOfWeek(Calendar.MONDAY);
            c.setMinimalDaysInFirstWeek(4);
            c.set(y, m, d, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);

            int week = c.get(Calendar.WEEK_OF_YEAR);
            int weekYear = c.getWeekYear();
            return String.format(Locale.US, "%04d-W%02d", weekYear, week);
        } catch (Exception e) {
            return dateKey;
        }
    }

    private String monthKeyFromDateKey(String dateKey) {
        if (dateKey != null && dateKey.length() >= 7) return dateKey.substring(0, 7);
        return dateKey;
    }

    private Timestamp timestampEndOfDay(String dateKey) {
        try {
            String[] parts = dateKey.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]) - 1;
            int d = Integer.parseInt(parts[2]);

            Calendar c = Calendar.getInstance();
            c.set(y, m, d, 23, 59, 59);
            c.set(Calendar.MILLISECOND, 999);
            return new Timestamp(c.getTime());
        } catch (Exception e) {
            return Timestamp.now();
        }
    }

    // ------------------ FIGHT ------------------

    /**
     * Backward-compatible: if UI already supplies randomValue.
     */
    @Deprecated
    public com.google.android.gms.tasks.Task<Boolean> performAttack(Boss boss, int damage, double successRate, int randomValue) {
        TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();

        boolean hit = randomValue < successRate;

        if (hit) {
            long newHp = Math.max(0, boss.getCurrentHp() - (long) damage);
            boss.setCurrentHp(newHp);
            if (newHp == 0) {
                boss.setStatus("DEFEATED");
            }
        }

        boss.setAttacksLeft(Math.max(0, boss.getAttacksLeft() - 1));
        if (!boss.isDefeated() && boss.getAttacksLeft() <= 0) {
            boss.setStatus("ESCAPED");
        }

        bossRepo.saveBoss(boss)
                .addOnSuccessListener(v -> tcs.setResult(hit))
                .addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    public com.google.android.gms.tasks.Task<FightResult> resolveBattleRewards(Boss boss) {
        TaskCompletionSource<FightResult> tcs = new TaskCompletionSource<>();

        boolean victory = boss.isDefeated();
        long maxHp = boss.getMaxHp();
        long currentHp = boss.getCurrentHp();
        int baseCoins = calculateBaseCoinReward(boss.getLevel());

        double rewardMultiplier;
        double dropChanceMultiplier;

        if (victory) {
            rewardMultiplier = 1.0;
            dropChanceMultiplier = 1.0;
        } else if (currentHp <= (maxHp / 2)) {
            rewardMultiplier = 0.5;
            dropChanceMultiplier = 0.5;
        } else {
            rewardMultiplier = 0.0;
            dropChanceMultiplier = 0.0;
        }

        int finalCoins = (int) Math.round(baseCoins * rewardMultiplier);

        boolean dropSuccess = false;
        if (rewardMultiplier > 0) {
            int chance = (int) Math.round(20.0 * dropChanceMultiplier);
            int roll = random.nextInt(101); // 0..100
            if (roll < chance) dropSuccess = true;
        }

        final int coinsToAdd = finalCoins;
        final boolean hasDrop = dropSuccess;

        equipmentRepo.addCoins(coinsToAdd).continueWithTask(task -> {
            if (!hasDrop) {
                return Tasks.forResult(new FightResult(victory, coinsToAdd, null, false,
                        maxHp, currentHp, rewardMultiplier, dropChanceMultiplier));
            }

            boolean isWeapon = random.nextInt(100) < 5;

            if (isWeapon) {
                WeaponType wType = random.nextBoolean() ? WeaponType.SWORD : WeaponType.BOW;
                return equipmentRepo.upsertWeapon(wType, 0, 0.0002)
                        .continueWith(t -> new FightResult(victory, coinsToAdd, wType.name(), true,
                                maxHp, currentHp, rewardMultiplier, dropChanceMultiplier));
            } else {
                int cRoll = random.nextInt(3);
                ClothesType cType = (cRoll == 0) ? ClothesType.GLOVES : (cRoll == 1) ? ClothesType.SHIELD : ClothesType.BOOTS;

                // Spec:
                //  - Gloves: +10% PP
                //  - Shield: +10% max HP
                //  - Boots:  +40% chance for ONE extra attack in the next fight (per pair)
                // NOTE: value is stored as a percentage. UI/Battle start logic should interpret
                // BOOTS value as "extra attack chance %" (not a flat stat like PP/HP).
                int value = (cType == ClothesType.BOOTS) ? 40 : 10;


                return equipmentRepo.equipClothes(cType, value)
                        .continueWith(t -> new FightResult(victory, coinsToAdd, cType.name(), false,
                                maxHp, currentHp, rewardMultiplier, dropChanceMultiplier));
            }
        }).addOnSuccessListener(tcs::setResult).addOnFailureListener(tcs::setException);

        return tcs.getTask();
    }

    /**
     * Spec: Boots grant a % chance to gain ONE extra attack for the next fight.
     */
    public int computeAttacksForBattle(int baseAttacks, Integer bootsChancePercent) {
        int attacks = Math.max(1, baseAttacks);
        if (bootsChancePercent == null || bootsChancePercent <= 0) return attacks;
        int roll = random.nextInt(100); // 0..99
        if (roll < bootsChancePercent) attacks += 1;
        return attacks;
    }


    public BossRepository getRepo() { return bossRepo; }
}
