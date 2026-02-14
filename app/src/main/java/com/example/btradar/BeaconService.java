package com.example.btradar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class BeaconService extends Service {
    private static final String TAG = "BeaconService";
    private static final String CHANNEL_ID = "BeaconServiceChannel";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private Vibrator vibrator;
    private String targetMac;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<Integer> rssiSamples = new ArrayList<>();
    private static final int MAX_SAMPLES = 240;

    private boolean isScanning = false;
    long lastSample = 0;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (result.getDevice().getAddress().equals(targetMac)) {
                handleBeaconFound(result.getRssi());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed with error: " + errorCode);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BT Radar Running")
                .setContentText("Scanning for beacon...")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build();

        startForeground(1, notification);

        SharedPreferences sharedPrefs = getSharedPreferences("BTRadarPrefs", MODE_PRIVATE);
        targetMac = sharedPrefs.getString("target_mac", null);

        if (targetMac != null && !isScanning) {
            startScanning();
        }

        return START_STICKY;
    }

    private void startScanning() {
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            return;
        }

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceAddress(targetMac).build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            isScanning = true;
            Log.d(TAG, "Started scanning for: " + targetMac);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: Missing permissions for scanning", e);
        }
    }

    private void handleBeaconFound(int rssi) {

if (System.currentTimeMillis()> lastSample+200) {
    lastSample = System.currentTimeMillis();
    rssiSamples.add(rssi);
    if (rssiSamples.size() > MAX_SAMPLES) {
        rssiSamples.remove(0);
    }

    if (rssiSamples.size() < 2) {
        return;
    }
}

    double mean = calculateMean(rssiSamples);
    double stdDev = calculateStdDev(rssiSamples, mean);

    if (stdDev == 0) {
        return;
    }

    double zScore = (rssi - mean) / stdDev;
    int intensity = mapZScoreToIntensity(zScore);
    //Log.d(TAG, "Beacon found! RSSI: " + rssi + " Intensity: " + intensity + " nsamples:" + rssiSamples.size());

    if (intensity > 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long[] timings = {40,60}; // Start immediately (0ms delay), vibrate for 100ms, then off for 500ms
            int[] amplitudes = {intensity,0};
            VibrationEffect effect = VibrationEffect.createWaveform(timings,amplitudes,0);
            vibrator.cancel();
            vibrator.vibrate(effect);
        } else {
            vibrator.vibrate(200);
        }
    }

    }

    private double calculateMean(List<Integer> samples) {
        double sum = 0;
        for (int sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    private double calculateStdDev(List<Integer> samples, double mean) {
        double standardDeviation = 0;
        for (int sample : samples) {
            standardDeviation += Math.pow(sample - mean, 2);
        }
        return Math.sqrt(standardDeviation / samples.size());
    }

    private int mapZScoreToIntensity(double zScore) {
        double minZ = 0.2;
        double maxZ = 1.75;

        if (zScore <= minZ) return 0;
        if (zScore >= maxZ) return 255;

        double normalized = (zScore - minZ) / (maxZ - minZ);
        return (int) (normalized * 254) + 1;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Beacon Scanner Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothLeScanner != null && isScanning) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                vibrator.cancel();
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to stop scan", e);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
