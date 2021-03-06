package com.ai.mobility.mobilityai;

import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.error.VolleyError;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.Logging;

import org.json.JSONObject;

import java.util.Calendar;

import bolts.Task;

public class DeviceInfoActivity extends AppCompatActivity implements ServiceConnection {
    private final String TAG = "MobilityAI";

    private TextView m_batteryPercentage, m_devName, m_macAddr, m_lastSync, m_batteryText;
    private Button m_startButton, m_stopButton, m_reassignButton;
    private ProgressBar m_batteryCircle, m_syncProgressBar, m_loadingBar;

    private MetaMotionService m_service;

    private String m_macAddress;
    private BtleService.LocalBinder m_serviceBinder;

    private final static String ELAPSEDTIMEZERO = "0";

    AlertDialog.Builder builder;
    AlertDialog m_dialog;

    private SingletonRequestQueue m_rqueue;
    private CStringRequest m_updateReq;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        m_rqueue = SingletonRequestQueue.getInstance(this);
        m_updateReq = null;

        builder = new AlertDialog.Builder(this);

        initializeDialogBuilder();
        m_dialog = builder.create();
        findViews();
        setOnClickListeners();
        hideAllElements(); //Hide all elements until service is connected

        m_macAddress = getIntent().getStringExtra("EXTRA_MAC_ADDR");

        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) { }

    private void hideAllElements() {
        m_batteryPercentage.setVisibility(View.INVISIBLE);
        m_devName.setVisibility(View.INVISIBLE);
        m_macAddr.setVisibility(View.INVISIBLE);
        m_lastSync.setVisibility(View.INVISIBLE);
        m_batteryText.setVisibility(View.INVISIBLE);
        m_startButton.setVisibility(View.INVISIBLE);
        m_stopButton.setVisibility(View.INVISIBLE);
        m_reassignButton.setVisibility(View.INVISIBLE);
        m_batteryCircle.setVisibility(View.INVISIBLE);
        m_syncProgressBar.setVisibility(View.INVISIBLE);

        //Show loading bar
        m_loadingBar.setVisibility(View.VISIBLE);
    }

    private void showAllElements() {
        m_batteryPercentage.setVisibility(View.VISIBLE);
        m_devName.setVisibility(View.VISIBLE);
        m_macAddr.setVisibility(View.VISIBLE);
        m_lastSync.setVisibility(View.VISIBLE);
        m_batteryText.setVisibility(View.VISIBLE);
        m_startButton.setVisibility(View.VISIBLE);
        m_stopButton.setVisibility(View.VISIBLE);
        m_reassignButton.setVisibility(View.VISIBLE);
        m_batteryCircle.setVisibility(View.VISIBLE);
        m_syncProgressBar.setVisibility(View.VISIBLE);

        //Hide loading bar
        m_loadingBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        m_serviceBinder = (BtleService.LocalBinder) service;

        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(m_macAddress);

        m_service = new MetaMotionService(m_serviceBinder.getMetaWearBoard(remoteDevice));

        //Don't need to deserialize here because we're only taking a battery level reading
        m_service.connectBoard().continueWithTask(task -> {
            if(task.isFaulted()) {
                Log.i("MobilityAI", "Failed to configure app", task.getError());
                m_service.disconnectBoard();
                return null;
            } else {
                Log.i("MobilityAI", "App Configured, connected: " + m_macAddress);

                return m_service.getBoard().readBatteryLevelAsync().continueWithTask(batteryLevelTask -> {
                    int batteryVal = batteryLevelTask.getResult().intValue();
                    m_batteryPercentage.setText(batteryVal + "%");
                  
                    ObjectAnimator animation = ObjectAnimator.ofInt(m_batteryCircle, "progress", batteryVal);
                    animation.setDuration(3000); // in milliseconds
                    animation.setInterpolator(new DecelerateInterpolator());
                    animation.start();

                    showAllElements();
                    return null;
                }, Task.UI_THREAD_EXECUTOR);
            }
        }, Task.UI_THREAD_EXECUTOR).continueWithTask(task -> {
            m_service.disconnectBoard();
            return null;
        });

        //Populate Views
        findViews();

        //Populate activity fields and set battery progress bar
        populateFields();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) { }


    /**
     * Populate fields with intent extras passed in from caller activity
     */
    private void populateFields() {
        String name = getIntent().getStringExtra("EXTRA_FNAME")
                + " " + getIntent().getStringExtra("EXTRA_LNAME");
        String macAddr = "MAC Address: " + getIntent().getStringExtra("EXTRA_MAC_ADDR");
        int rssi = getIntent().getIntExtra("EXTRA_RSSI", -100);
        String lastSync = "Last Sync: " + getIntent().getStringExtra("EXTRA_LAST_SYNC");

        setTitle(name);
        m_macAddr.setText(macAddr);
        m_lastSync.setText(lastSync);
    }

    /**
     * Initializes all the views on the page
     */
    private void findViews() {
        m_batteryPercentage = findViewById(R.id.batteryPercentage);
        m_devName = findViewById(R.id.devName);
        m_macAddr = findViewById(R.id.devMacAddr);
        m_lastSync = findViewById(R.id.devLastSync);
        m_batteryCircle = findViewById(R.id.progressBar);
        m_syncProgressBar = findViewById(R.id.syncProgress);
        m_startButton = findViewById(R.id.startButton);
        m_stopButton = findViewById(R.id.stopButton);
        m_reassignButton = findViewById(R.id.reassignButton);
        m_batteryText = findViewById(R.id.batteryIdentifier);
        m_loadingBar = findViewById(R.id.loadingCircle);


    }

    private void setOnClickListeners() {
        m_reassignButton.setOnClickListener(l -> {
            m_dialog.show();
        });

        m_startButton.setOnClickListener(l -> {
            //Start logging
            m_service.connectBoard().continueWithTask(task -> {
                if(task.isFaulted()) {
                    Log.i("MobilityAI", "Failed to configure app", task.getError());
                    m_service.disconnectBoard();
                    return null;
                } else {
                    Log.i("MobilityAI", "App Configured, connected: " + m_macAddress);

                    m_service.getLogging().stop();
                    m_service.getBoard().tearDown();

                    //Reconfigure sensors
                    m_service.configureAccelerometer();
                    m_service.configureGyroscope();
                    m_service.configureStepCounter();

                    Task<Route> result = m_service.configureStepCounterLogging(this);
                    result = result.continueWithTask(t -> { return m_service.configureGyroscopeLogging(this);     });
                    result = result.continueWithTask(t -> { return m_service.configureAccelerometerLogging(this); });
                    result.continueWith(t -> {
                        m_service.startSensors();
                        //Serialize board state before logging starts
                        m_service.serializeBoard(this.getFilesDir());

                        //Overwrite previous log entries if they exist
                        m_service.getLogging().start(true);

                        Log.i(TAG, "Data collection started for " + m_service.getBoard().getMacAddress());
                        Toast.makeText(getApplicationContext(), "Started Data Collection", Toast.LENGTH_SHORT).show();
                        return null;
                    });
                }

                m_service.disconnectBoard();
                return null;
            });

        });

        m_stopButton.setOnClickListener(l -> {
            //Stop logging
            m_service.connectBoard().continueWithTask(task -> {
                if(task.isFaulted()) {
                    Log.i("MobilityAI", "Failed to configure app", task.getError());

                    m_service.disconnectBoard();
                    return null;
                } else {
                    Log.i("MobilityAI", "App Configured, connected: " + m_macAddress);

                    m_service.stopSensors();
                    m_service.stopLogging();
                    m_service.getLogging().clearEntries();
                    m_service.getBoard().tearDown();
                    m_service.disconnectBoard();
                }
                return null;
            });
        });
    }

    /**
     * Sync button handler to connect, sync data, and restart logging
     */
    private void setSyncButtonOnClickHandler() {
        m_startButton.setOnClickListener((View v) -> {
            //TODO: Implement in next commit
        });
    }

    /**
     * Create the dialog builder
     */
    private void initializeDialogBuilder() {
        builder.setTitle("Enter a new Patient ID");

        final EditText userInput = new EditText(this);
        userInput.setHint("Patient ID");


        userInput.setInputType(InputType.TYPE_CLASS_TEXT);

        builder.setView(userInput);

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //user clicked OK
                String ui = userInput.getText().toString();
                if(!ui.equals("")) {
                    //Make web request to update patient
                    int patientId = Integer.parseInt(ui);

                    //If successful, then update the title on the activity
                    Response.Listener<String> listener = new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            updateWithNewPatient(response);
                        }
                    };

                    Response.ErrorListener el = new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(getApplicationContext(), error.toString(), Toast.LENGTH_LONG);
                        }
                    };

                    m_rqueue.addToRequestQueue(WebRequest.getInstance().assignNewPatient(getApplicationContext(), listener, el, m_macAddress, patientId));
                }
                Log.i(TAG, "UI: " + userInput.getText().toString());
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //User clicked Cancel
            }
        });
    }

    private void updateWithNewPatient(String response) {
        if(m_updateReq != null)  {
            if(m_updateReq.getStatusCode() == 200) {
                //Update name
                try {
                    JSONObject patient = new JSONObject(m_updateReq.getResponse());
                    this.setTitle(patient.getString("FirstName") + " " + patient.getString("LastName"));
                } catch (Exception e) {
                    Log.i(TAG, e.toString());
                    Toast.makeText(this, "An error occured with updating the patient", Toast.LENGTH_SHORT);
                }
            } else {
                Toast.makeText(this, "Invalid User ID entered", Toast.LENGTH_SHORT);
            }
        }
    }
}
