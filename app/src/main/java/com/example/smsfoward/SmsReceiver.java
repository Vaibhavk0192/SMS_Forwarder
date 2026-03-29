package com.example.smsfoward;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String KEY_RULES_JSON = "forwarding_rules_json";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) return;

            StringBuilder fullMessageBody = new StringBuilder();
            String incomingSender = messages[0].getDisplayOriginatingAddress();
            for (SmsMessage msg : messages) {
                if (msg != null) {
                    fullMessageBody.append(msg.getMessageBody());
                }
            }
            String finalBody = fullMessageBody.toString();

            Log.d(TAG, "New SMS received from " + incomingSender);

            // Use Encrypted SharedPreferences to load rules securely
            SharedPreferences prefs = SecurityUtils.getEncryptedPrefs(context);
            String rulesJson = prefs.getString(KEY_RULES_JSON, "[]");
            
            try {
                JSONArray rulesArray = new JSONArray(rulesJson);
                for (int i = 0; i < rulesArray.length(); i++) {
                    JSONObject rule = rulesArray.getJSONObject(i);
                    String targetSender = rule.getString("sender").trim();
                    String forwardToNumber = rule.getString("receiver").trim();

                    if (incomingSender != null && !targetSender.isEmpty() && 
                        incomingSender.toLowerCase().contains(targetSender.toLowerCase())) {
                        
                        forwardSms(context, incomingSender, finalBody, forwardToNumber);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSON error reading rules", e);
            }
        }
    }

    private void forwardSms(Context context, String sender, String messageBody, String forwardToNumber) {
        try {
            SmsManager smsManager = context.getSystemService(SmsManager.class);
            if (smsManager != null) {
                String forwardText = "📩 Fwd from " + sender + ":\n" + messageBody + "\n\n✨ App created by Vaibhav 🚀";
                ArrayList<String> parts = smsManager.divideMessage(forwardText);
                if (parts.size() > 1) {
                    smsManager.sendMultipartTextMessage(forwardToNumber, null, parts, null, null);
                } else {
                    smsManager.sendTextMessage(forwardToNumber, null, forwardText, null, null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Forwarding failed", e);
        }
    }
}
