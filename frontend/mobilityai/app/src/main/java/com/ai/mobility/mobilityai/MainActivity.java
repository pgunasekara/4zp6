package com.ai.mobility.mobilityai;

import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.error.VolleyError;
import com.android.volley.request.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.Logging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {
    //Metawear classes
    private BtleService.LocalBinder m_serviceBinder;

    private MetaWearBoards m_boards = MetaWearBoards.getInstance();

    private static final String TAG = "MobilityAI";
    private static final int REQUEST_ENABLE_BT = 1, PERMISSION_REQUEST_COARSE_LOCATION= 2;

    //RecylerView
    private RecyclerView m_bleList;
    private MetaMotionDeviceAdapter m_adapter;
    private ArrayList<MetaMotionDevice> m_deviceList;

    private ImageButton m_refreshButton;

    //Bluetooth Scanning
    private BluetoothAdapter m_bluetoothAdapter;
    private BluetoothLeScanner m_bluetoothLeScanner;
    private boolean m_isScanning = false;
    private Handler m_handler;
    private ScanCallback m_leScanCallback;
    private HashSet<ParcelUuid> m_filterServiceUuids;
    private BleScannerFragment.ScannerCommunicationBus commBus= null;
    private boolean m_isScanReady;
    private ArrayList<BluetoothDevice> m_devices;
    private static final long SCAN_PERIOD = 10000L;

    final Calendar cal = Calendar.getInstance();

    //TODO: Remove once the server side changes are made
    private Random r = new Random();
    private Button tmpBtn, tmpBtn2, tmpBtn3;

    private SingletonRequestQueue m_rqueue;
    private HashMap<String, CStringRequest> m_deviceRequests;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        m_rqueue = SingletonRequestQueue.getInstance(this);
        m_deviceRequests = new HashMap<>();

        //Temporary buttons to start and stop logging manually
        tmpBtn = findViewById(R.id.tmpBtn);
        tmpBtn.setOnClickListener(l -> {
            for(MetaMotionDevice d : m_deviceList) {
                //Connect to board
                Toast.makeText(this, "Stop/Collect Data", Toast.LENGTH_SHORT).show();
                Task<Route> currTask = connectToBoard(d);

                //Deserialize, set environment, stop sensors, get logging data
                currTask = currTask.continueWithTask(task -> {
                    Log.i(TAG, "Starting data collection");
                    return collectData(d);
                }).continueWithTask(task -> {
                    m_boards.getBoard(d.getMacAddr()).disconnectBoard();
                    Toast.makeText(this, "Done data collection/disconnected", Toast.LENGTH_SHORT).show();
                    return null;
                });

                //TODO: Handle multiple boards
                break;
            }
        });

        tmpBtn3 = findViewById(R.id.tmpBtn3);
        tmpBtn3.setOnClickListener(l -> {
            Toast.makeText(this, "Start Data Collection", Toast.LENGTH_SHORT).show();
            connectToBoard(m_deviceList.get(0)).continueWithTask(task -> {
                return startDataCollection(m_deviceList.get(0));
            }).continueWithTask(task -> {
                m_boards.getBoard(m_deviceList.get(0).getMacAddr()).disconnectBoard();
                Toast.makeText(this, "Start Data Collection - Disconnected", Toast.LENGTH_SHORT).show();
                return null;
            });

        });

        initialize();

        setUpBluetoothScanner();

        // Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        stopBleScan();
        super.onDestroy();

        //Disconnected any connected boards
        for(MetaMotionDevice d : m_deviceList) {
            MetaMotionService m = m_boards.getBoard(d.getMacAddr());
            if(m != null) {
                if (m.getBoard().isConnected())
                    m.disconnectBoard();
            }
        }

        // Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                startBleScan();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_syncall) {
            //Start syncing
            startSyncing();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        m_serviceBinder = (BtleService.LocalBinder) service;

        Log.i(TAG, "Service Connected");

//        retrieveBoard();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        //Asks the user to grant the proper permissions required for BLE scanning (Location)
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION:
                if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    //TODO: Handle if permission denied
                } else {
                    m_isScanReady = true;
                    startBleScan();
                }
                break;
        }
    }


    /**
     * Starts a BLE scan to get all nearby MetaWear devices
     */
    private void startBleScan() {
        m_adapter.clear();
        m_deviceList.clear();
        m_isScanning = true;

        Log.i(TAG, "Started Scan");
        m_refreshButton.setImageResource(R.drawable.ic_close_black_24dp); //Change icon to X

        m_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Delayed Stopped Scan");
                if(m_isScanning)
                    stopBleScan();
            }
        }, SCAN_PERIOD);

        cal.add(Calendar.DATE, -1);

        //When a device is found, add it to the device list
        m_leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if(result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                    boolean valid = true;
                    for(ParcelUuid it : result.getScanRecord().getServiceUuids()) {
                        valid &= m_filterServiceUuids.contains(it);
                    }

                    if(valid) {
                        m_handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "Found: " + result.getDevice().getAddress());
                                //if(result.getDevice().getAddress().equals("C1:D9:A9:77:09:72")) {
                                    //Only make web request if device does not already exist in list of devices
                                    if(!deviceExists(result.getDevice().getAddress())) {
                                        //Make request to get device information
                                        Response.Listener ls = new Response.Listener<String>() {
                                            @Override
                                            public void onResponse(String response) {
                                                Log.i(TAG, response);

                                                addNewDevice(response, result.getDevice().getAddress(), result.getRssi());
                                            }
                                        };

                                        Response.ErrorListener el = new Response.ErrorListener() {
                                            @Override
                                            public void onErrorResponse(VolleyError error) {
                                                addNewDevice(null, result.getDevice().getAddress(), result.getRssi());
                                            }
                                        };

                                        CStringRequest req = WebRequest.getInstance().getDeviceInfo(getApplicationContext(), ls, el, result.getDevice().getAddress());
                                        m_deviceRequests.put(result.getDevice().getAddress(), req);

                                        m_rqueue.addToRequestQueue(req);
                                    }
                                //}
                            }
                        });
                    }
                }

            super.onScanResult(callbackType, result);
            }
        };

        m_bluetoothLeScanner.startScan(m_leScanCallback);
    }

    private void stopBleScan() {
        Log.i(TAG, "Stop Scan");
        if(m_isScanning) {
            m_bluetoothLeScanner.stopScan(m_leScanCallback);
        }

        m_isScanning = false;
        m_refreshButton.setImageResource(R.drawable.ic_refresh_black_24dp); //Change back to the refresh icon so that the user knows they can scan again
    }

    private void setUpBluetoothScanner() {
        //Enabling BLE
        //Get the Bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        m_bluetoothAdapter = bluetoothManager.getAdapter();
        m_bluetoothLeScanner = m_bluetoothAdapter.getBluetoothLeScanner();

        //Enable bluetooth if not already enabled
        if(m_bluetoothAdapter == null || !m_bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            m_isScanReady = true;
        }
        Log.i(TAG, "m_isScanReady = "+m_isScanReady);

        UUID[] filterUuids= new UUID[] {UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")};//commBus.getFilterServiceUuids();
        m_filterServiceUuids = new HashSet<>();
        for(UUID uuid : filterUuids) {
            m_filterServiceUuids.add(new ParcelUuid(uuid));
        }

        if(m_isScanReady)   startBleScan();
    }

    /**
     * Performs task of connecting to a new board given a MetaMotionDevice object for that device
     * @param d MetaMotionDevice of object to connect
     * @return Async connect task
     */
    private Task<Route> connectToBoard(MetaMotionDevice d) {
        final BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothDevice remoteDevice = btManager.getAdapter().getRemoteDevice(d.getMacAddr());

        MetaWearBoard b = m_serviceBinder.getMetaWearBoard(remoteDevice);
        m_boards.enrollNewBoard(d.getMacAddr(), b); //Enroll the new board into the MetaWearBoards singleton
        MetaMotionService m = m_boards.getBoard(d.getMacAddr()); //Retrieve the newly enrolled board

        m.deserializeBoard(this.getFilesDir()); //Deserialize if device was already in use

        return m.connectBoard().continueWithTask(task -> {
            d.setService(m);

            //Update Battery
            return m.getBoard().readBatteryLevelAsync().continueWithTask(task1 -> {
                if(task1.isFaulted()) {
                    Log.i(TAG, "Battery Read Faulted on " + d.getMacAddr());
                } else {
                    int batteryVal = task1.getResult().intValue();
                    View v = m_bleList.getLayoutManager().findViewByPosition(0);
                    TextView batteryPercentage = v.findViewById(R.id.devBatteryLevelPercentage);
                    batteryPercentage.setText(batteryVal + "%");

                    Log.i(TAG, "Battery Level: " + batteryVal);
                    //Hide other elements
                    ImageView profileImg = v.findViewById(R.id.userImage);
                    TextView initials = v.findViewById(R.id.initials);
                    profileImg.setVisibility(View.INVISIBLE);
                    initials.setVisibility(View.INVISIBLE);

                    ProgressBar batteryCircle = v.findViewById(R.id.devBatteryLevel);
                    batteryCircle.setVisibility(View.VISIBLE);

                    ObjectAnimator animation = ObjectAnimator.ofInt(
                            batteryCircle, "progress", batteryVal);
                    animation.setDuration(3000); // in milliseconds
                    animation.setInterpolator(new DecelerateInterpolator());
                    animation.start();
                }
                return null;
            }, Task.UI_THREAD_EXECUTOR);
        });
    }

    /**
     * Stops logging, and collects data from a given device, then tears down all routes/data on the board
     * @param d Device to collect data
     * @return Async download task from the logger
     */
    private Task<Route> collectData(MetaMotionDevice d) {
        MetaMotionService m = m_boards.getBoard(d.getMacAddr());

        m.stopSensors();
        m.stopLogging();

        m.setEnvironment(this); //Restore the environment that was previously saved

        //Start Downloading data
        Logging log = m.getLogging();
        View v = m_bleList.getLayoutManager().findViewByPosition(0);
        ProgressBar syncProgress = v.findViewById(R.id.devSyncProgress);
        TextView syncProgressText = v.findViewById(R.id.syncProgressText);
        TextView lastSync = v.findViewById(R.id.devLastSync);

        syncProgressText.setVisibility(View.VISIBLE);

        Log.i(TAG, "Starting log download");



        return log.downloadAsync(100, (long nEntriesLeft, long totalEntries) -> {
            syncProgress.setProgress((int)totalEntries - (int)nEntriesLeft);
            syncProgress.setMax((int)totalEntries);
            Log.i(TAG, "Download: "  + ((int)totalEntries - (int)nEntriesLeft));
        }).continueWithTask(task -> {
            if (task.isFaulted()) {
                Toast.makeText(this, "Failed to download log file.", Toast.LENGTH_LONG).show();
                Log.i(TAG, "Failed to download log file.");
            } else {
                Log.i(TAG, "Log downloaded successfully");
                //Clear Log
                log.clearEntries();

                Log.i(TAG, "Log Location: " + this.getFilesDir().getAbsolutePath());

                //Upload files
                String filePath = this.getFilesDir().toString();
                m_rqueue.addToRequestQueue(WebRequest.getInstance().uploadSensorData(
                        this,
                        m.getPatientId(),
                        filePath,
                        m.getAccelerometerFileName(),
                        m.getGyroscopeFileName()
                ));

                //Upload step counts
                m_rqueue.addToRequestQueue(WebRequest
                        .getInstance()
                        .uploadStepCount(
                                this,
                                m.getPatientId(),
                                filePath,
                                m.getStepCountFileName()
                        )
                );

                //Update Last Sync
                lastSync.setText("Last Sync: " + Calendar.getInstance().getTime().toString());

                syncProgressText.setText("Sync Complete");
                syncProgress.setProgress(0);
            }

            //Clear board
            m.getBoard().tearDown();
            return null;
        });
    }

    /**
     * Starts data collection on the board once it's been cleared
     * @param d Device to collect data
     * @return Configure async tasks for gyroscope and accelerometer
     */
    private Task<Route> startDataCollection(MetaMotionDevice d) {
        MetaMotionService m = m_boards.getBoard(d.getMacAddr());

        //Stop logging and reset board in case it's already running
        m.getLogging().stop();
        m.getBoard().tearDown();

        //Reconfigure sensors
        m.configureAccelerometer();
        m.configureGyroscope();
        m.configureStepCounter();

        //Configure logging environments
        Task<Route> result = m.configureStepCounterLogging(this);
        result = result.continueWithTask(task -> { return m.configureGyroscopeLogging(this);     });
        result = result.continueWithTask(task -> { return m.configureAccelerometerLogging(this); });
        return result.continueWith(task -> {
            m.startSensors();
            //Serialize board state before logging starts
            m.serializeBoard(this.getFilesDir());

            //Overwrite previous log entries if they exist
            m.getLogging().start(true);

            Log.i(TAG, "Data collection started for " + d.getMacAddr());

            m.disconnectBoard();

            return null;
        });
    }

    /**
     * Initializes the views on the activity
     */
    private void initialize() {
        m_refreshButton = (ImageButton) findViewById(R.id.refreshButton);
        m_bleList = (RecyclerView) findViewById(R.id.bleList);
        m_bleList.setLayoutManager(new LinearLayoutManager(this));
        m_deviceList = new ArrayList<>();
        m_adapter = new MetaMotionDeviceAdapter(this,
                m_deviceList,
                new MetaMotionDeviceAdapter.OnItemClickListener() {
                    @Override
                    public void onItemClick(MetaMotionDevice device) {
                        //Open a new intent
                        Intent intent = new Intent(getBaseContext(), DeviceInfoActivity.class);
                        intent.putExtra("EXTRA_MAC_ADDR", device.getMacAddr());
                        intent.putExtra("EXTRA_RSSI", device.getRssi());
                        intent.putExtra("EXTRA_FNAME", device.getFirstName());
                        intent.putExtra("EXTRA_LNAME", device.getLastName());
                        intent.putExtra("EXTRA_LAST_SYNC", device.getLastSync());
                        startActivity(intent);
                    }
                });

        setButtonListeners();

        m_bleList.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
        m_bleList.setAdapter(m_adapter);

        m_handler = new Handler();
    }

    /**
     * Set listeners for BLE scan button
     */
    private void setButtonListeners() {
        m_refreshButton.setOnClickListener(l -> {
            if(m_isScanning) {
                stopBleScan();
            } else {
                if(m_isScanReady)
                    startBleScan();
            }
        });
    }

    /**
     * Task to start collecting data from all devices
     */
    private void startSyncing() {
        //Collect existing data from device
        for(MetaMotionDevice d : m_deviceList) {
            //Connect to board
            Task<Route> currTask = connectToBoard(d);

            //Deserialize, set environment, stop sensors, get logging data
            currTask = currTask.continueWithTask(task -> {
                Log.i(TAG, "startSyncing(): Getting Data");
                return collectData(d);
            });

            //Restart logging
            currTask = currTask.continueWithTask(task -> { return startDataCollection(d); });

            currTask.continueWith(task -> {
                String msg = "Data Syncing completed on " + d.getMacAddr();
                Toast.makeText(this, (String)msg, Toast.LENGTH_LONG).show();
                m_boards.getBoard(d.getMacAddr()).disconnectBoard();
                return null;
            });

            break;
        }
    }

    /**
     * Add a new device to the device list after getting the device details from the server
     * @param response Response containing first name, last name, and last synced date
     * @param macAddr Mac address of new device to be added
     * @param rssi Signal strength of new device to be added
     */
    private void addNewDevice(String response, String macAddr, int rssi) {
        String firstName = "Unregistered Device";
        String lastName = "";
        String lastSync = "Never";
        int patientId = -1;

        CStringRequest req = m_deviceRequests.get(macAddr);

        if(req != null && req.getStatusCode() == 200 && response != null) {
            try {
                JSONObject res = new JSONObject(response);
                firstName = res.getString("firstName");
                lastName = res.getString("lastName");
                lastSync = res.getString("lastSync");
                patientId = res.getInt("patientID");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        m_adapter.update(new MetaMotionDevice(firstName, lastName, patientId, macAddr, 50, lastSync, rssi));
    }

    /**
     * Check if device was already found
     * @param address MAC address of newly found device
     * @return If the device was already found, return true, else return false
     */
    private boolean deviceExists(String address) {
        for(MetaMotionDevice d : m_deviceList) {
            if(d.getMacAddr().equals(address)) {
                return true;
            }
        }

        return false;
    }
}
