package com.dearmoon.shield.snapshot;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.dearmoon.shield.R;

public class RecoveryActivity extends AppCompatActivity {
    private RestoreEngine restoreEngine;
    private SnapshotManager snapshotManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recovery);

        restoreEngine = new RestoreEngine(this);
        snapshotManager = new SnapshotManager(this);

        TextView tvStatus = findViewById(R.id.tvRecoveryStatus);
        TextView tvSnapshotInfo = findViewById(R.id.tvSnapshotInfo);
        Button btnCreateSnapshot = findViewById(R.id.btnCreateSnapshot);
        Button btnRestore = findViewById(R.id.btnStartRestore);
        Button btnCancel = findViewById(R.id.btnCancelRestore);

        updateSnapshotInfo(tvSnapshotInfo);

        long attackId = snapshotManager.getActiveAttackId();
        
        if (attackId > 0) {
            tvStatus.setText("⚠️ ATTACK DETECTED!\nReady to restore affected files.");
        } else {
            tvStatus.setText("No active attack.\nYou can manually restore changed files.");
        }
        
        btnCreateSnapshot.setOnClickListener(v -> {
            btnCreateSnapshot.setEnabled(false);
            tvStatus.setText("Creating snapshot...");
            
            new Thread(() -> {
                String[] dirs = getMonitoredDirectories();
                snapshotManager.createBaselineSnapshot(dirs);
                
                try { Thread.sleep(2000); } catch (Exception e) {}
                
                runOnUiThread(() -> {
                    updateSnapshotInfo(tvSnapshotInfo);
                    tvStatus.setText("Snapshot created successfully!");
                    btnCreateSnapshot.setEnabled(true);
                });
            }).start();
        });
        
        btnRestore.setOnClickListener(v -> {
            long lastSnapshotTime = getSharedPreferences("ShieldPrefs", MODE_PRIVATE)
                .getLong("last_snapshot_time", 0);
            
            if (lastSnapshotTime == 0) {
                tvStatus.setText("No snapshot available.\nCreate a snapshot first.");
                return;
            }
            
            long restoreId = attackId > 0 ? attackId : snapshotManager.getActiveAttackId();
            
            if (restoreId <= 0) {
                tvStatus.setText("No attack detected.\nAll files are safe.");
                return;
            }
            
            btnRestore.setEnabled(false);
            tvStatus.setText("Restoring files...");
            
            new Thread(() -> {
                if (attackId > 0) {
                    snapshotManager.stopAttackTracking();
                }
                
                RestoreEngine.RestoreResult result = restoreEngine.restoreFromAttack(restoreId);
                
                runOnUiThread(() -> {
                    if (result.noChanges) {
                        tvStatus.setText("No changes detected.\nAll files are safe.");
                    } else if (result.failedCount > 0) {
                        tvStatus.setText("Restore complete!\n" +
                            "Restored: " + result.restoredCount + "\n" +
                            "Failed: " + result.failedCount);
                    } else {
                        tvStatus.setText("Restore complete!\n" +
                            result.restoredCount + " files restored successfully.");
                    }
                    btnRestore.setEnabled(true);
                });
            }).start();
        });

        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void updateSnapshotInfo(TextView tvSnapshotInfo) {
        long lastSnapshotTime = getSharedPreferences("ShieldPrefs", MODE_PRIVATE)
            .getLong("last_snapshot_time", 0);
        
        if (lastSnapshotTime > 0) {
            long minutesAgo = (System.currentTimeMillis() - lastSnapshotTime) / 60000;
            if (minutesAgo < 1) {
                tvSnapshotInfo.setText("Last snapshot: Just now");
            } else {
                tvSnapshotInfo.setText("Last snapshot: " + minutesAgo + " minutes ago");
            }
        } else {
            tvSnapshotInfo.setText("No snapshot created yet");
        }
    }
    
    private String[] getMonitoredDirectories() {
        java.util.List<String> dirs = new java.util.ArrayList<>();
        java.io.File externalStorage = android.os.Environment.getExternalStorageDirectory();
        if (externalStorage != null && externalStorage.exists()) {
            dirs.add(new java.io.File(externalStorage, "Documents").getAbsolutePath());
            dirs.add(new java.io.File(externalStorage, "Download").getAbsolutePath());
            dirs.add(new java.io.File(externalStorage, "Pictures").getAbsolutePath());
            dirs.add(new java.io.File(externalStorage, "DCIM").getAbsolutePath());
        }
        return dirs.toArray(new String[0]);
    }
}
