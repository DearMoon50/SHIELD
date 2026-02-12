package com.dearmoon.shield.services;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.dearmoon.shield.MainActivity;
import com.dearmoon.shield.data.NetworkEvent;
import com.dearmoon.shield.data.TelemetryStorage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class NetworkGuardService extends VpnService {
    private static final String TAG = "NetworkGuardService";
    public static final String ACTION_STOP = "com.dearmoon.shield.STOP";

    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private TelemetryStorage storage;
    private volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        storage = new TelemetryStorage(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRunning();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            vpnThread = new Thread(this::runVpnLoop, "VpnThread");
            vpnThread.start();
        }
        return START_NOT_STICKY;
    }

    private void stopRunning() {
        isRunning = false;
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        closeVpnInterface();
    }

    private void runVpnLoop() {
        try {
            vpnInterface = establishVpnInterface();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                isRunning = false;
                return;
            }

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            ByteBuffer packet = ByteBuffer.allocate(32767);

            while (isRunning && !Thread.interrupted()) {
                int length = in.read(packet.array());
                if (length > 0) {
                    packet.limit(length);
                    analyzePacket(packet);
                    packet.clear();

                    // Pass-through
                    out.write(packet.array(), 0, length);
                }
            }
        } catch (Exception e) {
            if (isRunning) {
                Log.e(TAG, "VPN loop error", e);
            }
        } finally {
            closeVpnInterface();
            isRunning = false;
        }
    }

    private ParcelFileDescriptor establishVpnInterface() {
        Builder builder = new Builder();
        builder.addAddress("10.0.0.2", 32);
        builder.addRoute("0.0.0.0", 0);
        builder.setSession("NetworkGuard");

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        builder.setConfigureIntent(pendingIntent);

        return builder.establish();
    }

    private void analyzePacket(ByteBuffer packet) {
        if (packet.remaining() < 20)
            return;

        byte versionAndIHL = packet.get(0);
        int version = (versionAndIHL >> 4) & 0x0F;
        if (version != 4)
            return; // IPv4 only

        int protocol = packet.get(9) & 0xFF;
        String protoName = protocol == 6 ? "TCP" : protocol == 17 ? "UDP" : "OTHER";

        byte[] destIpBytes = new byte[4];
        packet.position(16);
        packet.get(destIpBytes);
        String destIp = String.format("%d.%d.%d.%d",
                destIpBytes[0] & 0xFF, destIpBytes[1] & 0xFF,
                destIpBytes[2] & 0xFF, destIpBytes[3] & 0xFF);

        int destPort = 0;
        if (packet.remaining() >= 24) {
            destPort = packet.getShort(22) & 0xFFFF;
        }

        NetworkEvent netEvent = new NetworkEvent(
                destIp, destPort, protoName, packet.remaining(), 0, android.os.Process.myUid());
        storage.store(netEvent);
    }

    private void closeVpnInterface() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing VPN interface", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopRunning();
        super.onDestroy();
    }
}
