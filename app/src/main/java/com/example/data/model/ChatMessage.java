package com.example.data.model;

import com.google.firebase.Timestamp;
import java.util.Date;

public class ChatMessage {
    public String messageId;
    public String senderUid;
    public String senderName;
    public String messageText;
    public Timestamp timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderUid, String senderName, String messageText, Timestamp timestamp) {
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.messageText = messageText;
        this.timestamp = timestamp;
    }
}