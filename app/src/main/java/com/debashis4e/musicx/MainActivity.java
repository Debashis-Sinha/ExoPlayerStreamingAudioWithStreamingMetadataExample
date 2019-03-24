package com.debashis4e.musicx;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import wseemann.media.FFmpegMediaMetadataRetriever;

public class MainActivity extends AppCompatActivity {

    private String nowPlaying;
    private ImageView playStopBtn;
    private boolean mBound = false;
    private MusicService musicService;
    private TextView radioStationNowPlaying;
    private View onlineLayout, offlineLayout;
    String streamUrl = "http://radio.bongonet.net:8000";
    private static final int READ_PHONE_STATE_REQUEST_CODE = 22;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            MusicService.MusicBinder mServiceBinder = (MusicService.MusicBinder) iBinder;
            musicService = mServiceBinder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            System.exit(0);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        onlineLayout = findViewById(R.id.onlineLayout);
        offlineLayout = findViewById(R.id.offlineLayout);
        playStopBtn = findViewById(R.id.playStopBtn);
        radioStationNowPlaying = findViewById(R.id.radioStationNowPlaying);

        processPhoneListenerPermission();
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
                if (tm != null) {
                    switch (tm.getCallState()) {
                        case TelephonyManager.CALL_STATE_RINGING:
                            if (musicService.isPlaying()) {
                                musicService.stop();
                                playStopBtn.setImageResource(R.drawable.ic_play);
                            }
                            break;
                    }
                }

                if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                        showOnlineLayout();
                    } else if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.DISCONNECTED) {
                        showOfflineLayout();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PHONE_STATE");
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(broadcastReceiver, filter);

        Thread t = new Thread() {
            public void run() {
                try {
                    while (!isInterrupted()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                reloadShoutCastInfo();
                            }
                        });
                        Thread.sleep(20000);
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        t.start();
    }

    private void reloadShoutCastInfo() {
        if(isNetworkAvailable()) {
            AsyncTaskRunner runner = new AsyncTaskRunner();
            runner.execute();
        }
    }

    private void showOfflineLayout() {
        onlineLayout.setVisibility(View.GONE);
        offlineLayout.setVisibility(View.VISIBLE);
    }

    private void showOnlineLayout() {
        offlineLayout.setVisibility(View.GONE);
        onlineLayout.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(MainActivity.this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(serviceConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void playStop(View view) {
        if (musicService.isPlaying()) {
            musicService.stop();
            playStopBtn.setImageResource(R.drawable.ic_play);
        } else {
            musicService.play(streamUrl);
            playStopBtn.setImageResource(R.drawable.ic_pause);
        }
    }

    private void processPhoneListenerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == READ_PHONE_STATE_REQUEST_CODE) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(getApplicationContext(), "Permission not granted.\nWe can't pause music when phone ringing.", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;
        if (cm != null) {
            networkInfo = cm.getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }

    private class AsyncTaskRunner extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
            mmr.setDataSource(streamUrl);
            nowPlaying = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ICY_METADATA).replaceAll("StreamTitle", "").replaceAll("[=,';]+", "");
            mmr.release();
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            radioStationNowPlaying.setText(nowPlaying);
        }
    }
}
