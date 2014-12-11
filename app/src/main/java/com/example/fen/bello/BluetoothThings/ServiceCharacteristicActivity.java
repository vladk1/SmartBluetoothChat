/*
 * Bluegiga’s Bluetooth Smart Android SW for Bluegiga BLE modules
 * Contact: support@bluegiga.com.
 *
 * This is free software distributed under the terms of the MIT license reproduced below.
 *
 * Copyright (c) 2013, Bluegiga Technologies
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files ("Software")
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF 
 * ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A  PARTICULAR PURPOSE.
 */
package com.example.fen.bello.BluetoothThings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;

import com.example.fen.bello.R;
import com.example.fen.bello.datamodel.Consts;
import com.example.fen.bello.datamodel.Device;
import com.example.fen.bello.datamodel.Engine;
import com.example.fen.bello.datamodel.ServiceCharacteristicAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;


// ServiceCharacteristicActivity - displays all discovered services on BLE device and characteristics related to
// It uses TreeView component where first level shows services and second level shows characteristics related to service
@SuppressLint("UseSparseArrays")
public class ServiceCharacteristicActivity extends Activity  {

    private static IntentFilter bleIntentFilter;

//    private TreeViewList treeView;
//    private TreeStateManager<Integer> manager = null;

    private ServiceCharacteristicAdapter serviceCharacteristicAdapter;
    //    private TreeBuilder<Integer> treeBuilder = new TreeBuilder<Integer>(manager);
    private BluetoothLeService mBluetoothLeService;

    private Device device;

    // Implements receive methods that handle a specific intent actions from
    // mBluetoothLeService When ACTION_GATT_DISCONNECTED action is received and
    // device address equals current device, activity closes When
    // ACTION_GATT_SERVICES_DISCOVERED action is received, whole tree adapter is
    // rebuild
    private final BroadcastReceiver mBluetoothLeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            String deviceAddress = intent.getExtras().getString(BluetoothLeService.DEVICE_ADDRESS);
            if (action.equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)) {
                if (deviceAddress.equals(device.getAddress())) {
                    finish();
                }
            } else if (action.equals(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)) {
                if (deviceAddress.equals(device.getAddress())) {
                    displayServices();
                }
            }
        }
    };

    // Implements callback method for service connection
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    private ListView characteristicList;

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String address = getIntent().getExtras().getString(Consts.DEVICE_ADDRESS);
        device = Engine.getInstance().getDevice(address);

        registerReceiver(mBluetoothLeReceiver, getGattUpdateIntentFilter());

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        setContentView(R.layout.characteristic_my);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar);


        characteristicList = (ListView) findViewById(R.id.charecteristic_list);
        characteristicList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mViewClickListener.onCharacteristicClicked(position);
            }
        });

        displayServices();
    }

    // Clears and fills tree adapter with services discovered on device
    private void displayServices() {
        int index = 0;
//        treeBuilder.clear();
        ArrayList<BluetoothGattService> services = (ArrayList<BluetoothGattService>) device.getBluetoothGatt()
                .getServices();

        Collections.sort(services, new Comparator<BluetoothGattService>() {

            @Override
            public int compare(BluetoothGattService lhs, BluetoothGattService rhs) {
                return lhs.getUuid().compareTo(rhs.getUuid());
            }

        });

        try {
            Log.wtf("ServiceCharacteristicActivity smth cool", "index = " + Integer.valueOf(index));
        } catch (Exception ex) {
            Log.wtf("ServiceCharacteristicActivity error", "smth fucked");
        }

        HashMap<Integer, UUID> uuidMap = new HashMap<Integer, UUID>();
        HashMap<Integer, BluetoothGattService> servicesMap = new HashMap<Integer, BluetoothGattService>();

        for (BluetoothGattService service : services) {

            uuidMap.put(Integer.valueOf(index), service.getUuid());
            servicesMap.put(Integer.valueOf(index), service);

            index++;
        }

        serviceCharacteristicAdapter = new ServiceCharacteristicAdapter(this, device, uuidMap, servicesMap);
        characteristicList.setAdapter(serviceCharacteristicAdapter);
    }


    // Returns intent filter for receiving specific action from
    // mBluetoothLeService:
    // - BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED - services were
    // discovered
    // - BluetoothLeService.ACTION_GATT_DISCONNECTED - device disconnected
    // This method is used when registerReceiver method is called
    private static IntentFilter getGattUpdateIntentFilter() {
        if (bleIntentFilter == null) {
            bleIntentFilter = new IntentFilter();
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        }
        return bleIntentFilter;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBluetoothLeReceiver, getGattUpdateIntentFilter());
        if (!device.isConnected()) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBluetoothLeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }


    private ViewClickListener mViewClickListener;

    public interface ViewClickListener {
        void onCharacteristicClicked(int position);
    }

    public void setViewClickListener (ViewClickListener viewClickListener) {
        mViewClickListener = viewClickListener;
    }

}
