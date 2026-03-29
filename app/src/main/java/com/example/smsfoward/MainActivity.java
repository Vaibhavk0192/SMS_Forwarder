package com.example.smsfoward;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "SmsForwardPrefs";
    private static final String KEY_RULES_JSON = "forwarding_rules_json";

    private RecyclerView rvRules;
    private RulesAdapter adapter;
    private List<ForwardRule> ruleList = new ArrayList<>();
    
    private MaterialCardView cardSystemStatus;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private ImageView ivStatusIcon;
    private Button btnGrantAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvRules = findViewById(R.id.rvRules);
        cardSystemStatus = findViewById(R.id.cardSystemStatus);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusDesc = findViewById(R.id.tvStatusDesc);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        btnGrantAction = findViewById(R.id.btnGrantAction);
        
        ExtendedFloatingActionButton fabAddRule = findViewById(R.id.fabAddRule);

        rvRules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RulesAdapter();
        rvRules.setAdapter(adapter);

        authenticateUser();

        fabAddRule.setOnClickListener(v -> showAddRuleDialog());
        btnGrantAction.setOnClickListener(v -> requestPermissions());
    }

    private void authenticateUser() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    finish();
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    loadRules();
                    updateSystemStatus();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                }
            });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Authentication Required")
                    .setSubtitle("Authenticate to view and edit forwarding rules")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        } else {
            loadRules();
            updateSystemStatus();
        }
    }

    private void updateSystemStatus() {
        if (checkPermissions()) {
            cardSystemStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primaryLightColor));
            tvStatusTitle.setText("System Active");
            tvStatusDesc.setText("App is monitoring incoming SMS rules.");
            ivStatusIcon.setImageResource(android.R.drawable.presence_online);
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.primaryColor));
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.primaryColor));
            tvStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.primaryColor));
            btnGrantAction.setVisibility(View.GONE);
        } else {
            cardSystemStatus.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light));
            tvStatusTitle.setText("Action Required");
            tvStatusDesc.setText("Permissions missing! App cannot forward SMS.");
            ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
            ivStatusIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            tvStatusDesc.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            btnGrantAction.setVisibility(View.VISIBLE);
        }
    }

    private void showAddRuleDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_rule, null);
        TextInputEditText etSender = dialogView.findViewById(R.id.etSenderName);
        TextInputEditText etReceiver = dialogView.findViewById(R.id.etForwardNumber);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnAdd = dialogView.findViewById(R.id.btnAdd);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnAdd.setOnClickListener(v -> {
            String sender = etSender.getText().toString().trim();
            String receiver = etReceiver.getText().toString().trim();
            if (!sender.isEmpty() && isValidIndianNumber(receiver)) {
                addRule(sender, receiver);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void addRule(String sender, String receiver) {
        if (!receiver.startsWith("+")) {
            if (receiver.startsWith("91") && receiver.length() == 12) {
                receiver = "+" + receiver;
            } else if (receiver.length() == 10) {
                receiver = "+91" + receiver;
            }
        }
        ruleList.add(new ForwardRule(sender, receiver));
        saveRules();
        adapter.notifyDataSetChanged();
    }

    private void loadRules() {
        SharedPreferences prefs = SecurityUtils.getEncryptedPrefs(this);
        String rulesJson = prefs.getString(KEY_RULES_JSON, "[]");
        try {
            JSONArray array = new JSONArray(rulesJson);
            ruleList.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ruleList.add(new ForwardRule(obj.getString("sender"), obj.getString("receiver")));
            }
            adapter.notifyDataSetChanged();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveRules() {
        JSONArray array = new JSONArray();
        try {
            for (ForwardRule rule : ruleList) {
                JSONObject obj = new JSONObject();
                obj.put("sender", rule.getSender());
                obj.put("receiver", rule.getReceiver());
                array.put(obj);
            }
            SharedPreferences.Editor editor = SecurityUtils.getEncryptedPrefs(this).edit();
            editor.putString(KEY_RULES_JSON, array.toString());
            editor.apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidIndianNumber(String number) {
        String regex = "^(\\+91[\\-\\s]?)?[6-9]\\d{9}$";
        return number.matches(regex) || (number.length() == 10 && number.matches("^[6-9]\\d{9}$"));
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateSystemStatus();
    }

    private class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forward_rule, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ForwardRule rule = ruleList.get(position);
            holder.tvFrom.setText(rule.getSender());
            holder.tvTo.setText("Forwarding to " + rule.getReceiver());
            holder.btnDelete.setOnClickListener(v -> {
                ruleList.remove(position);
                saveRules();
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return ruleList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFrom, tvTo;
            View btnDelete;
            ViewHolder(View itemView) {
                super(itemView);
                tvFrom = itemView.findViewById(R.id.tvRuleFrom);
                tvTo = itemView.findViewById(R.id.tvRuleTo);
                btnDelete = itemView.findViewById(R.id.btnDeleteRule);
            }
        }
    }
}
