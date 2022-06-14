package com.example.storage;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private final InetAddressValidator mInetAddressValidator = InetAddressValidator.getInstance();
    private final int PERMISSION_FINE_LOCATION = 99;
    private int mApproxRefresh;
    private SharedPreferences mSharedPreferences;
    private Intent mBacklogIntent;
    private ActivityMainBinding mBinding;
    private Timer mViewTimer;

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

        Intent sensorIntent = new Intent(this, SensorService.class);
        boolean sensorsRunning = isServiceRunning(SensorService.class);

        mBacklogIntent = new Intent(this, BacklogService.class);
        if (!sensorsRunning) {
            if (FileUtils.list(getApplicationContext()).size() > 0) {
                tryEnableBacklogs();
            }
        } else {
            mBinding.server.setText(mSharedPreferences.getString("SERVER", String.valueOf(R.string.default_ip)));
            mBinding.session.setText(mSharedPreferences.getString("SESSION", ""));
            mBinding.switchBtn.setChecked(true);
            mBinding.switchBtn.setEnabled(true);
            disableEditText(mBinding.session);
            disableEditText(mBinding.server);
        }

        mBinding.switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                disableEditText(mBinding.session);
                disableEditText(mBinding.server);

                stopService(mBacklogIntent);

                String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
                sensorIntent.putExtra("SESSION", String.valueOf(mBinding.session.getText()));
                sensorIntent.putExtra("SERVER", server);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(sensorIntent);
                } else {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    startService(sensorIntent);
                }

                mSharedPreferences.edit()
                        .putString("SESSION", String.valueOf(mBinding.session.getText()))
                        .putString("SERVER", server)
                        .apply();
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                stopService(sensorIntent);

                try {
                    List<Measurement> data = Measurements.firstArray ? Measurements.sData1.subList(0, Measurements.currIdx) :
                            Measurements.sData2.subList(0, Measurements.currIdx);

                    if (data.size() > 0) {
                        final Dataframe d = new Dataframe(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.ID, Build.VERSION.RELEASE, String.valueOf(mBinding.session.getText()), data);
                        final byte[] msg = ZipUtils.compress(JsonConverter.convert(d));
                        FileUtils.write(d.getData().get(d.getData().size() - 1).getTime(), msg, getApplicationContext());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Measurements.sData1.clear();
                Measurements.sData2.clear();

                if (FileUtils.list(this).size() > 0)
                    tryEnableBacklogs();

                enableEditText(mBinding.session);
                enableEditText(mBinding.server);
            }
        });

        mApproxRefresh = 1000;
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        //     mApproxRefresh = (int) (1000 / this.getDisplay().getRefreshRate()) + 1;
        // } else {
        //     // Assume 60fps ish, rounded up
        //     mApproxRefresh = 17;
        // }

        mViewTimer = new Timer();
        scheduleUITimer();

        mBinding.session.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Unused */ }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String server = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
                mBinding.switchBtn.setEnabled(s.length() > 0 && mInetAddressValidator.isValid(server));
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

                    if (mInetAddressValidator.isValid(server)) {
                        mBacklogIntent.putExtra("SERVER", server);
                        startService(mBacklogIntent);

                        mBinding.switchBtn.setEnabled(mBinding.session.getText().length() > 0);
                    }
                } else {
                    mBinding.switchBtn.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) { /* Unused */ }
        });
    }

    @Override
    protected void onStop() {
        stopService(mBacklogIntent);

        super.onStop();
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
     * Check if the service is Running
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

    private void tryEnableBacklogs() {
        String defaultTxt = String.valueOf(mBinding.server.getText()).replaceAll(" ", "");
        if (defaultTxt.length() >= 7 && mInetAddressValidator.isValid(defaultTxt)) {
            mBacklogIntent.putExtra("SERVER", defaultTxt);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(mBacklogIntent);
            } else {
                startService(mBacklogIntent);
            }
        }
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
                runOnUiThread(() -> mBinding.connectionLabel.
                        setText((Measurements.sensorHasConnection || Measurements.backlogHasConnection) ?
                                getString(R.string.connection_succeeded) :
                                getString(R.string.connection_failed)));

                if (mBinding.switchBtn.isChecked()) {
                    runOnUiThread(() -> {
                        if (Measurements.currIdx > 1) { // Current index to write to, read one back
                            if (Measurements.firstArray) {
                                updateUI(Measurements.sData1.get(Measurements.currIdx - 1));
                            } else
                                updateUI(Measurements.sData2.get(Measurements.currIdx - 1));
                        }
                    });
                }
            }
        }, 0, mApproxRefresh);
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
    }
}
