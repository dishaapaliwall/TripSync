package com.yay.tripsync;

import com.google.firebase.Timestamp;

public class Message {
    private String senderId;
    private String senderName;
    private String text;
    private String tripId;
    private Timestamp timestamp;
    private String messageId; // For optimistic UI deduplication
 // For optimistic UI deduplication

    public Message() {
        // Required for Firestore
        // Required for Firestore
    }

    public Message(String senderId, String senderName, String text, String tripId, Timestamp timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.tripId = tripId;
        this.timestamp = timestamp;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return messageId != null && messageId.equals(message.messageId);
    }

    @Override
    public int hashCode() {
        return messageId != null ? messageId.hashCode() : 0;
    }
}
