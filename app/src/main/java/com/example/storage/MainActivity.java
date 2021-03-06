package com.example.storage;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.storage.data.Dataframe;
import com.example.storage.data.Measurement;
import com.example.storage.data.Measurements;
import com.example.storage.databinding.ActivityMainBinding;
import com.example.storage.utils.FileUtils;
import com.example.storage.utils.JsonConverter;
import com.example.storage.utils.ZipUtils;

import org.apache.commons.validator.routines.InetAddressValidator;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private final InetAddressValidator mInetAddressValidator = InetAddressValidator.getInstance();
    private final int PERMISSION_FINE_LOCATION = 99;
    private SharedPreferences mSharedPreferences;
    private Intent mBacklogIntent;
    private ActivityMainBinding mBinding;
    private Timer mViewTimer;

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_FINE_LOCATION);
        }

        mSharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        String packageName = this.getPackageName();

        Intent sensorIntent = new Intent(getApplicationContext(), SensorService.class);
        boolean sensorsRunning = isServiceRunning(SensorService.class);
        boolean backlogRunning = isServiceRunning(BacklogService.class);

        mBacklogIntent = new Intent(getApplicationContext(), BacklogService.class);

        if (sensorsRunning) {
            mBinding.session.setText(mSharedPreferences.getString("SESSION", ""));
            mBinding.switchBtn.setChecked(true);
            mBinding.switchBtn.setEnabled(true);
            mBinding.sendBtn.setEnabled(false);
            disableEditText(mBinding.session);
        }

        if (backlogRunning) {
            mBinding.server.setText(mSharedPreferences.getString("SERVER", String.valueOf(R.string.default_ip)));
            mBinding.sendBtn.setChecked(true);
            mBinding.sendBtn.setEnabled(true);
            mBinding.switchBtn.setEnabled(false);
            disableEditText(mBinding.server);
        } else {
            String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
            mBinding.sendBtn.setEnabled(mInetAddressValidator.isValid(server) || server.contains("192.168.1."));
        }

        mBinding.switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                stopService(mBacklogIntent);
                disableEditText(mBinding.session);
                mBinding.sendBtn.setEnabled(false);

                @SuppressLint("SimpleDateFormat") DateFormat isoDate = new SimpleDateFormat(FileUtils.ISO_DATE);
                isoDate.setTimeZone(TimeZone.getTimeZone("UTC"));
                String time = isoDate.format(new Date());

                sensorIntent.putExtra("SESSION", String.valueOf(mBinding.session.getText()));
                sensorIntent.putExtra("START", time);
                mSharedPreferences.edit()
                        .putString("SESSION", String.valueOf(mBinding.session.getText()))
                        .putString("START", time)
                        .apply();


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (powerManager.isIgnoringBatteryOptimizations(this.getPackageName())) {
                        sensorIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    } else {
                        sensorIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        sensorIntent.setData(Uri.parse("package:" + packageName));
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(sensorIntent);
                } else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    startService(sensorIntent);
                }
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                stopService(sensorIntent);

                try {
                    List<Measurement> data = Measurements.sFirstArray ? Measurements.DATA_1.subList(0, Measurements.sCurrIdx) :
                            Measurements.DATA_2.subList(0, Measurements.sCurrIdx);

                    if (data.size() > 0) {
                        final Dataframe d = new Dataframe(
                                Build.BRAND,
                                Build.MANUFACTURER,
                                Build.MODEL,
                                Build.ID,
                                Build.VERSION.RELEASE,
                                mSharedPreferences.getString("SESSION", ""),
                                mSharedPreferences.getString("START", ""),
                                data
                        );
                        final byte[] msg = ZipUtils.compress(JsonConverter.convert(d));
                        FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, getApplicationContext());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Measurements.DATA_1.clear();
                Measurements.DATA_2.clear();

                enableEditText(mBinding.session);
                mBinding.sendBtn.setEnabled(true);
            }
        });

        mBinding.sendBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                stopService(sensorIntent);
                mBinding.switchBtn.setEnabled(false);
                disableEditText(mBinding.server);

                String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
                mBacklogIntent.putExtra("SERVER", server);
                mSharedPreferences.edit()
                        .putString("SERVER", server)
                        .apply();

                Measurements.sFinishedSend = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (powerManager.isIgnoringBatteryOptimizations(this.getPackageName())) {
                        mBacklogIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    } else {
                        mBacklogIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        mBacklogIntent.setData(Uri.parse("package:" + packageName));
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(mBacklogIntent);
                } else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    startService(mBacklogIntent);
                }
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                stopService(mBacklogIntent);
                enableEditText(mBinding.server);
                mBinding.switchBtn.setEnabled(true);
            }
        });

        mViewTimer = new Timer();
        scheduleUITimer();

        mBinding.session.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Unused */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mBinding.switchBtn.setEnabled(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });

        mBinding.server.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Unused */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 7) {
                    String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
                    mBinding.sendBtn.setEnabled(mInetAddressValidator.isValid(server) || server.contains("192.168.1."));
                }
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });
    }

    @Override
    protected void onPause() {
        if (mViewTimer != null)
            mViewTimer.cancel();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mViewTimer = new Timer();
        scheduleUITimer();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_FINE_LOCATION) {
            if (!(grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "This app requires permission to be granted in order to work properly", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * Check if the service is running
     *
     * @param serviceClass the class of the Service
     * @return true if the service is running otherwise false
     */
    public boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void enableEditText(EditText editText) {
        editText.setFocusableInTouchMode(true);
        editText.setEnabled(true);
        editText.setCursorVisible(true);
    }

    private void disableEditText(EditText editText) {
        editText.setFocusable(false);
        editText.setEnabled(false);
        editText.setCursorVisible(false);
    }

    /**
     * Helper function to kick off screen updates to slightly slower than screen refresh
     * <p>
     * Approximate timing is impossible and assigning faster than refresh updates crashes eventually
     * due to vsync inconsistencies.
     */
    private void scheduleUITimer() {
        mViewTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Reflect connection changes even if not measuring
                runOnUiThread(() -> {
                    mBinding.backlogLabel.setText(MessageFormat.format("Backlog files: {0}", FileUtils.list(getApplicationContext()).size()));
                    mBinding.connectionLabel.
                            setText((Measurements.sBacklogHasConnection) ?
                                    getString(R.string.connection_succeeded) :
                                    getString(R.string.connection_failed));

                    if (Measurements.sFinishedSend)
                        mBinding.sendBtn.setChecked(false);

                    if (mBinding.switchBtn.isChecked()) {
                        if (Measurements.sCurrIdx > 1) { // Current index to write to, read one back
                            if (Measurements.sFirstArray) {
                                updateUI(Measurements.DATA_1.get(Measurements.sCurrIdx - 1));
                            } else
                                updateUI(Measurements.DATA_2.get(Measurements.sCurrIdx - 1));
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    /**
     * Updates on screen information with recent values
     */
    private void updateUI(Measurement m) {
        if (m.getTime() == null) return;

        mBinding.tvLat.setText(String.valueOf(m.getLat()));
        mBinding.tvLon.setText(String.valueOf(m.getLon()));
        mBinding.tvAccuracy.setText(String.valueOf(m.getAcc()));
        mBinding.tvAltitude.setText(String.valueOf(m.getAlt()));
        mBinding.tvSpeed.setText(String.valueOf(m.getMs()));
        mBinding.time.setText(m.getTime().replace('T', ' ').replace('Z', ' '));
        mBinding.zValue.setText((String.valueOf(m.getZ())));
        mBinding.zFiltered.setText(String.valueOf(Math.max(m.getFz(), 0.0)));
        mBinding.std.setText(String.valueOf(m.getRms()));
        mBinding.edr.setText(String.format(Locale.US, "%s %.6f", "EDR:", m.getEdrRms()));
    }
}
