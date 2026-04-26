package com.yay.tripsync;
public class Message {
    public String text;
    public boolean isSentByMe;

    public Message(String text, boolean isSentByMe) {
        this.text = text;
        this.isSentByMe = isSentByMe;
    }
}