package com.example.btradar;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class MainActivity extends Activity {

    private EditText macInput;
    private TextView statusText;
    private SharedPreferences sharedPrefs;

    private static final int REQUEST_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        macInput = findViewById(R.id.macInput);
        statusText = findViewById(R.id.statusText);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnStop = findViewById(R.id.btnStop);

        sharedPrefs = getSharedPreferences("BTRadarPrefs", MODE_PRIVATE);
        String savedMac = sharedPrefs.getString("target_mac", "");
        macInput.setText(savedMac);

        btnSave.setOnClickListener(v -> {
            String mac = macInput.getText().toString().trim().toUpperCase();
            if (isValidMac(mac)) {
                sharedPrefs.edit().putString("target_mac", mac).apply();
                checkPermissionsAndStartService();
            } else {
                Toast.makeText(this, "Invalid MAC format", Toast.LENGTH_SHORT).show();
            }
        });

        btnStop.setOnClickListener(v -> {
            Intent stopIntent = new Intent(this, BeaconService.class);
            stopService(stopIntent);
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(1); //stop vibration
            vibrator.cancel(); //cancel any looping stim

            statusText.setText("Service: Stopped");
        });
    }

    private boolean isValidMac(String mac) {
        return mac.matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    }

    private void checkPermissionsAndStartService() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                };
            } else {
                permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
                };
            }
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        } else {
            startService();
        }
    }

    private void startService() {
        Intent intent = new Intent(this, BeaconService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateStatus();
    }

    private void updateStatus() {
        statusText.setText("Service: Running (Target: " + sharedPrefs.getString("target_mac", "None") + ")");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startService();
            } else {
                Toast.makeText(this, "Permissions required for BLE scanning", Toast.LENGTH_LONG).show();
            }
        }
    }
}
