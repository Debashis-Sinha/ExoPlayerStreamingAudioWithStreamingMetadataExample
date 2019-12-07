package com.debashis4e.musicx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import wseemann.media.FFmpegMediaMetadataRetriever;

import static com.debashis4e.musicx.Config.STREAMING_URL;

public class MainActivity extends AppCompatActivity {

    private TextView nowPlaying;
    private ImageView playStop;
    private BroadcastReceiver broadcastReceiver;
    private String nowPlayingData = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playStop = findViewById(R.id.playStopBtn);
        nowPlaying = findViewById(R.id.radioStationNowPlaying);
        setIsPlaying(false);
        processPhoneListenerPermission();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
                if (tm != null) {
                    if (tm.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
                        if (getIsPlaying()) {
                            stop();
                        }
                        System.exit(0);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PHONE_STATE");
        registerReceiver(broadcastReceiver, filter);
        loadNowPlaying();
        playStop.setOnClickListener(view -> {
            if (isNetworkAvailable()) {
                if (getIsPlaying()) {
                    stop();
                } else {
                    play();
                }
            } else {
                Toast.makeText(getApplicationContext(), "No internet", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadNowPlaying() {
        Thread t = new Thread() {
            public void run() {
                try {
                    while (!isInterrupted()) {
                        runOnUiThread(() -> reloadShoutCastInfo());
                        Thread.sleep(20000);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };
        t.start();
    }

    private void reloadShoutCastInfo() {
        if (isNetworkAvailable()) {
            AsyncTaskRunner runner = new AsyncTaskRunner();
            runner.execute();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class AsyncTaskRunner extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
            mmr.setDataSource(STREAMING_URL);
            nowPlayingData = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ICY_METADATA).replaceAll("StreamTitle", "").replaceAll("[=,';]+", "");
            mmr.release();
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            nowPlaying.setText(nowPlayingData);
        }
    }

    private void processPhoneListenerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 121);
        }
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (cm != null) {
            networkInfo = cm.getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 121) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(getApplicationContext(), "Permission not granted.\nWe can't pause music when phone ringing.", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, id) -> {
                    if (getIsPlaying()) {
                        stop();
                    }
                    System.exit(0);
                })
                .setNegativeButton("No", (dialog, id) -> dialog.cancel());
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void setIsPlaying(boolean status) {
        SharedPreferences.Editor editor = getApplicationContext().getSharedPreferences("isPlaying", MODE_PRIVATE).edit();
        editor.putBoolean("isPlaying", status);
        editor.apply();
    }

    private boolean getIsPlaying() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("isPlaying", MODE_PRIVATE);
        return prefs.getBoolean("isPlaying", false);
    }

    private void play() {
        setIsPlaying(true);
        Intent servicePlayIntent = new Intent(this, MyService.class);
        servicePlayIntent.putExtra("playStop", "play");
        startService(servicePlayIntent);
        playStop.setImageResource(R.drawable.ic_pause);
        Toast.makeText(getApplicationContext(), "Loading ...", Toast.LENGTH_LONG).show();
    }

    private void stop() {
        setIsPlaying(false);
        Intent serviceStopIntent = new Intent(this, MyService.class);
        serviceStopIntent.putExtra("playStop", "stop");
        startService(serviceStopIntent);
        playStop.setImageResource(R.drawable.ic_play);
        Toast.makeText(getApplicationContext(), "Stop music...", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(getApplicationContext(), MyService.class));
        unregisterReceiver(broadcastReceiver);
        super.onDestroy();
    }
}
