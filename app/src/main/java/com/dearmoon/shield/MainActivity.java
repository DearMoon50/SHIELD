package com.dearmoon.shield;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.dearmoon.shield.services.NetworkGuardService;
import com.dearmoon.shield.services.ShieldProtectionService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView tvProtectionStatus;
    private TextView tvPermissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
    }

    private void initializeViews() {
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);

        findViewById(R.id.btnStartProtection).setOnClickListener(v -> startShieldService());
        findViewById(R.id.btnStopProtection).setOnClickListener(v -> stopShieldService());
        findViewById(R.id.btnStartVpn).setOnClickListener(v -> startVpnService());
        findViewById(R.id.btnStopVpn).setOnClickListener(v -> stopVpnService());
        findViewById(R.id.btnRequestPermissions).setOnClickListener(v -> requestNecessaryPermissions());
        findViewById(R.id.btnViewLogs).setOnClickListener(v -> viewLogs());

        // Advanced Security Feature Buttons
        findViewById(R.id.btnFileMonitoring).setOnClickListener(v -> {
            Intent intent = new Intent(this, FileAccessActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnSnapshotRecovery).setOnClickListener(v -> {
            Intent intent = new Intent(this, com.dearmoon.shield.snapshot.RecoveryActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnLockerGuard).setOnClickListener(v -> {
            Intent intent = new Intent(this, com.dearmoon.shield.lockerguard.EmergencyRecoveryActivity.class);
            startActivity(intent);
        });

        updateStatusDisplay();
    }

    private void startVpnService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, NetworkGuardService.class);
        intent.setAction(NetworkGuardService.ACTION_STOP);
        startService(intent);
        Toast.makeText(this, "Network Guard Disabled", Toast.LENGTH_SHORT).show();
        updateStatusDisplay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, NetworkGuardService.class);
            startService(intent);
            Toast.makeText(this, "Network Guard Protected", Toast.LENGTH_SHORT).show();
            updateStatusDisplay();
        }
    }

    private void startShieldService() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show();
            requestNecessaryPermissions();
            return;
        }

        Intent serviceIntent = new Intent(this, ShieldProtectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        updateStatusDisplay();
    }

    private void stopShieldService() {
        Intent serviceIntent = new Intent(this, ShieldProtectionService.class);
        stopService(serviceIntent);
        updateStatusDisplay();
    }

    private void viewLogs() {
        Intent intent = new Intent(this, LogViewerActivity.class);
        startActivity(intent);
    }

    private void updateStatusDisplay() {
        boolean isServiceRunning = isServiceRunning(ShieldProtectionService.class);
        boolean isVpnRunning = isServiceRunning(NetworkGuardService.class);

        if (isServiceRunning || isVpnRunning) {
            tvProtectionStatus.setText("System Protected");
            tvProtectionStatus.setTextColor(0xFF10B981); // Emerald 500
        } else {
            tvProtectionStatus.setText("System At Risk");
            tvProtectionStatus.setTextColor(0xFFEF4444); // Red 500
        }

        StringBuilder permStatus = new StringBuilder("Status: ");
        if (hasRequiredPermissions()) {
            permStatus.append("Fully Authorized");
        } else {
            permStatus.append("Authorization Required");
        }

        if (isVpnRunning) {
            permStatus.append(" | Network Monitor Active");
        }

        tvPermissionStatus.setText(permStatus.toString());
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null)
            return false;
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager())
                return false;
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                return false;
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusDisplay();
    }
}