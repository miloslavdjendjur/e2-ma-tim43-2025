package com.example.data.model;

public class User {
    public String uid;
    public String email;
    public String username;      // ne menja se
    public int avatarIndex;      // 0..4
    public boolean active;       // mora biti true posle aktivacije
    public int level;            // 1 start
    public String title;         // početna titula (npr. "Novajlija")
    public long xp;              // 0 start
    public long pp;              // 0 start (posle prelaska 1. nivoa dobija 40 PP)
    public long coins;           // 0 start
    public int badges;           // 0 start
    public String qrId;          // npr. uid
    public com.google.firebase.Timestamp createdAt;
    public com.google.firebase.Timestamp lastLogin;

    public User() {}
    public User(String uid, String email, String username, int avatarIndex) {
        this.uid = uid; this.email = email; this.username = username; this.avatarIndex = avatarIndex;
        this.active = false; this.level = 1; this.title = "Novajlija";
        this.xp = 0; this.pp = 0; this.coins = 0; this.badges = 0; this.qrId = uid;
    }
}
