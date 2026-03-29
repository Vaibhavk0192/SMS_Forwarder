package com.example.smsfoward;

public class ForwardRule {
    private String sender;
    private String receiver;

    public ForwardRule(String sender, String receiver) {
        this.sender = sender;
        this.receiver = receiver;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }
}
