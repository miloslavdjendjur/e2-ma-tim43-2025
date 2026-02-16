package com.example.data.model;

import java.util.List;

public class Alliance {
    public String id;
    public String name;
    public String leaderUid;
    public List<String> members; // Lista UID-ova članova

    public Alliance() {}

    public Alliance(String id, String name, String leaderUid, List<String> members) {
        this.id = id;
        this.name = name;
        this.leaderUid = leaderUid;
        this.members = members;
    }
}