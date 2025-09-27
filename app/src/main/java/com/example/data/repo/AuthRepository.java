package com.example.data.repo;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public Task<AuthResult> register(String email, String pass) {
        return auth.createUserWithEmailAndPassword(email, pass);
    }
    public Task<AuthResult> login(String email, String pass) {
        return auth.signInWithEmailAndPassword(email, pass);
    }
    public void logout() { auth.signOut(); }
    public FirebaseUser current() { return auth.getCurrentUser(); }
}
