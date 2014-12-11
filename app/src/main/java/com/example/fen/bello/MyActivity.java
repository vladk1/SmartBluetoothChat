package com.example.fen.bello;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.fen.bello.BluetoothThings.BluetoothLeService;
import com.example.fen.bello.BluetoothThings.ServiceCharacteristicActivity;
import com.example.fen.bello.datamodel.Common;
import com.example.fen.bello.datamodel.Consts;
import com.example.fen.bello.datamodel.Device;
import com.example.fen.bello.datamodel.DeviceAdapter;
import com.example.fen.bello.datamodel.Engine;
import com.example.fen.bello.datamodel.ScanRecordParser;

import java.util.Iterator;

public class MyActivity extends Activity {

    private static final int BlUETOOTH_SETTINGS_REQUEST_CODE = 100;
    public static final int SCAN_PERIOD = 10000;

    private static IntentFilter bleIntentFilter;

    private ListView devicesListView;

    private Button scanButton;
    private ProgressBar scanProgress;
    private TextView titleBarView;
    private TextView noDevicesFoundView;
    private DeviceAdapter adapter;
    private BluetoothLeService mBluetoothLeService;
    private Dialog mDialog;
    private ProgressDialog mProgressDialog;
    private boolean bleIsSupported = true;
    private float lastScale = 0.0f;

    public boolean isDisconnecting = false;



    private Button sendButton;

    // Implements callback method for service connection
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.wtf("MainActivity", "onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            startScanning();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.wtf("MainActivity", "onServiceDisconnected");
            mBluetoothLeService = null;
        }
    };
    // Implements receive methods that handle a specific intent actions from
    // mBluetoothLeService
    private final BroadcastReceiver mBluetoothLeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_DEVICE_DISCOVERED.equals(action)) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) intent
                        .getParcelableExtra(BluetoothLeService.DISCOVERED_DEVICE);
                int rssi = (int) intent.getIntExtra(BluetoothLeService.RSSI, 0);
                byte[] scanRecord = intent.getByteArrayExtra(BluetoothLeService.SCAN_RECORD);

                Device device = Engine.getInstance().addBluetoothDevice(bluetoothDevice, rssi, scanRecord);
                device.setRssi(rssi);
                device.setAdvertData(ScanRecordParser.getAdvertisements(scanRecord));
                Log.wtf("ACTION_DEVICE_DISCOVERED","device="+device.getName()+" rssi="+rssi);
//                if (device.getName().equals("bekonz2")) {
//
//
//                }

                ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
            } else if (intent.getAction().equals(BluetoothLeService.ACTION_STOP_SCAN)) {
                setScanningStatus(Engine.getInstance().getDevices().size() > 0);
                setScanningProgress(false);
            } else if (intent.getAction().equals(BluetoothLeService.ACTION_GATT_CONNECTED)) {
                refreshViewOnUiThread();
                startServiceCharacteristicAtivity(intent.getStringExtra(BluetoothLeService.DEVICE_ADDRESS));
            } else if (intent.getAction().equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)
                    || intent.getAction().equals(BluetoothLeService.ACTION_READ_REMOTE_RSSI)
                    || intent.getAction().equals(BluetoothLeService.ACTION_GATT_CONNECTION_STATE_ERROR)) {
                refreshViewOnUiThread();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_my);
        setContentView(R.layout.activity_my);
        sendButton = (Button) findViewById(R.id.send_button);

        Log.wtf("MainActivity", "onCreate");



    // Check if Bluetooth Low Energy technology is supported on device
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            bleIsSupported = false;
            Log.wtf("MainActivity", "Bluetooth Low Energy technology is not supported on device");
        }

        Engine.getInstance().init(this.getApplicationContext());
        Log.wtf("MainActivity", "Engine.getInstance()");

        adapter = new DeviceAdapter(this, Engine.getInstance().getDevices());
        Log.wtf("MainActivity", "adapter");

        configureDeviceGrid();

        // Check if Bluetooth module is enabled
        Log.wtf("MainActivity", "checkBluetoothAdapter");
        checkBluetoothAdapter();

        Log.wtf("MainActivity", "registerForContextMenu");
        registerForContextMenu(devicesListView);

        configureScanButton();
        Log.wtf("MainActivity", "configureScanButton");
    }


    // Configures grid view for showing devices list
    private void configureDeviceGrid() {
        devicesListView = (ListView) findViewById(R.id.deviceGrid);

        devicesListView.setAdapter(adapter);

        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.wtf("MainActivity", "click disconnecting="+isDisconnecting);
                if (!isDisconnecting) {

                    Device elem = Engine.getInstance().getDevices().get(position);
                    if (elem.isConnected()) {
                        Toast.makeText(getApplicationContext(), "connected...", Toast.LENGTH_SHORT).show();
                        startServiceCharacteristicAtivity(elem.getAddress());
                    } else {
                        Toast.makeText(getApplicationContext(), "connecting...", Toast.LENGTH_SHORT).show();
                        mBluetoothLeService.connect(elem);
                        view.setBackgroundColor(Color.YELLOW);
                        Log.wtf("MainActivity", "mBluetoothLeService.connect");
                    }
                } else {
                    isDisconnecting = false;
                }
            }
        });

        devicesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.wtf("MainActivity", "long click");
                Device elem = Engine.getInstance().getDevices().get(position);
                if (elem.isConnected()) {
                    Log.wtf("MainActivity", "disconnect");
                    Toast.makeText(getApplicationContext(), "disconnecting...", Toast.LENGTH_SHORT).show();
                    isDisconnecting = true;
                    mBluetoothLeService.disconnect(elem);
//                    startServiceCharacteristicAtivity(elem.getAddress());
                } else {
                    Toast.makeText(getApplicationContext(), "disconnected...", Toast.LENGTH_SHORT).show();
                }
                view.setBackgroundColor(Color.WHITE);
//                else {
//                    mBluetoothLeService.connect(elem);
//                    Log.wtf("MainActivity", "mBluetoothLeService.connect");
//                }
                return false;
            }
        });


    }

    // Configures scan button
    private void configureScanButton() {
        scanButton = (Button) findViewById(R.id.send_button);
        scanButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startScanning();
            }
        });
    }


    // If Bluetooth is not supported on device, the application is closed
    // in other case method enable Bluetooth
    private void checkBluetoothAdapter() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.wtf("device", "is " + bluetoothAdapter.getName());
        if (bluetoothAdapter == null) {
            bluetoothNotSupported();
        } else if (!bluetoothAdapter.isEnabled()) {
            Log.wtf("device", "bluetoothEnable ");
            bluetoothEnable();
        } else {
            Log.wtf("device", "connectService ");
            connectService();
        }
    }

    private void connectService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    // Displays dialog with information that phone doesn't support Bluetooth
    private void bluetoothNotSupported() {

        Context context = getApplicationContext();
        CharSequence text = "Bluetooth not supported!";
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
        Log.wtf("bluetoothNotSupported", "saad");
    }

    // Starts ServiceCharacteristicActivity activity with DEVICE_ADDRESS extras
    private void startServiceCharacteristicAtivity(String deviceAddress) {
        Intent myIntent = new Intent(MyActivity.this, ServiceCharacteristicActivity.class);
        myIntent.putExtra(Consts.DEVICE_ADDRESS, deviceAddress);
        startActivity(myIntent);
    }

    // Displays scanning status in UI and starts scanning for new BLE devices
    private void startScanning() {
        Log.wtf("startScanning", "boom");
        setScanningProgress(true);
        setScanningStatus(true);
        // Connected devices are not deleted from list
        Engine.getInstance().clearDeviceList(true);
        // For each connected device read rssi
        Iterator<Device> device = Engine.getInstance().getDevices().iterator();
        while (device.hasNext()) {
            mBluetoothLeService.readRemoteRssi(device.next());
            Log.wtf("scanning", "got next device");
        }
        ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
        // Starts a scan for Bluetooth LE devices for SCAN_PERIOD miliseconds
        Log.wtf("mBluetoothLeService", "mBluetoothLeService is "+mBluetoothLeService);
        mBluetoothLeService.startScanning(SCAN_PERIOD);
        Log.wtf("sstart scanning", "mBluetoothLeService");

        registerReceiver(mBluetoothLeReceiver, getGattUpdateIntentFilter());
    }

    private void setScanningStatus(boolean foundDevices) {
        if (foundDevices) {
            Log.wtf("setScanningStatus", "foundDevices");
        } else {
            Log.wtf("setScanningStatus", "devices not found");
        }
    }
    //bekonz2

    private void setScanningProgress(boolean isScanning) {
        if (isScanning) {
            Log.wtf("is scanning", "booom");
        } else {
            Log.wtf("is not scanning", "booom");
        }
    }

    // Returns intent filter for receiving specific action from
    // mBluetoothLeService:
    // - BluetoothLeService.ACTION_DEVICE_DISCOVERED - new
    // device was discovered
    // - BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED - services were
    // discovered
    // - BluetoothLeService.ACTION_STOP_SCAN - scanning finished -
    // BluetoothLeService.ACTION_GATT_CONNECTED - device connected
    // - BluetoothLeService.ACTION_GATT_DISCONNECTED - device disconnected -
    // BluetoothLeService.ACTION_READ_REMOTE_RSSI - device rssi was read
    // This method is used when registerReceiver method is called
    private static IntentFilter getGattUpdateIntentFilter() {
        if (bleIntentFilter == null) {
            bleIntentFilter = new IntentFilter();
            bleIntentFilter.addAction(BluetoothLeService.ACTION_DEVICE_DISCOVERED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_STOP_SCAN);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_READ_REMOTE_RSSI);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTION_STATE_ERROR);
        }
        return bleIntentFilter;
    }

    private void refreshViewOnUiThread() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
            }
        });
    }

    // Displays dialog and request user to enable Bluetooth
    private void bluetoothEnable() {

        Context context = getApplicationContext();
        CharSequence text = "bluetoothEnable!";
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BlUETOOTH_SETTINGS_REQUEST_CODE) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (!bluetoothAdapter.isEnabled() && mDialog != null) {
                mDialog.show();
            } else {
                connectService();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bleIsSupported) {
            registerReceiver(mBluetoothLeReceiver, getGattUpdateIntentFilter());
            if (mBluetoothLeService != null) {
                Log.wtf("onResume set scanning", "mBluetoothLeService != null");
                setScanningProgress(mBluetoothLeService.isScanning());
            }
            Log.wtf("onResume ", "mBluetoothLeService is null");
            ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
        }

        configureFontScale();
    }

    // Configures number of shown advertisement types
    private void configureFontScale() {

        float scale = getResources().getConfiguration().fontScale;
        if (lastScale != scale) {
            lastScale = scale;
            if (lastScale == Common.FONT_SCALE_LARGE) {
                Device.MAX_EXTRA_DATA = 2;
            } else if (lastScale == Common.FONT_SCALE_XLARGE) {
                Device.MAX_EXTRA_DATA = 1;
            } else {
                Device.MAX_EXTRA_DATA = 3;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bleIsSupported) {
            unregisterReceiver(mBluetoothLeReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothLeService != null) {
            mBluetoothLeService.close();
        }
        Engine.getInstance().close();
        if (bleIsSupported && mBluetoothLeService != null) {
            unbindService(mServiceConnection);
        }
        mBluetoothLeService = null;
    }




    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((mProgressDialog != null) && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mBluetoothLeService.close();
                ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onBackPressed() {

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mBluetoothLeService.close();
            ((DeviceAdapter) devicesListView.getAdapter()).notifyDataSetChanged();
        } else {
            super.onBackPressed(); // allows standard use of backbutton for page
            // 1
        }
    }

    // Gets advertisement data line by line
    private String prepareAdvertisementText(Device device) {
        String advertisementData = "";
        for (String data : device.getAdvertData()) {
            advertisementData += data + "\n";
        }
        return advertisementData;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my, menu);
        return super.onCreateOptionsMenu(menu);
    }

}