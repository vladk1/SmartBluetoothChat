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
package com.example.fen.bello.datamodel;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.example.fen.bello.BluetoothThings.BluetoothLeService;
import com.example.fen.bello.BluetoothThings.CharacteristicActivity;
import com.example.fen.bello.BluetoothThings.ServiceCharacteristicActivity;
import com.example.fen.bello.R;
import com.example.fen.bello.datamodel.xml.Characteristic;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


//ServiceCharacteristicAdapter - used to build up TreeView component in ServiceCharacteristicActivity
@SuppressLint("UseSparseArrays")
public class ServiceCharacteristicAdapter extends BaseAdapter implements ServiceCharacteristicActivity.ViewClickListener {

    private final Device device;
    private final HashMap<Integer, BluetoothGattService> services;
    private final HashMap<Integer, UUID> uuidMap;
    private Context context;

    private HashMap<Integer, BluetoothGattCharacteristic> characteristics = new HashMap<Integer, BluetoothGattCharacteristic>();

    public ServiceCharacteristicAdapter(Context context, Device device,
                                        HashMap<Integer, UUID> uuidMap,
                                        HashMap<Integer, BluetoothGattService> services) {
        this.context = context;
        this.device = device;
        this.services = services;
        this.uuidMap = uuidMap;

        ServiceCharacteristicActivity main = (ServiceCharacteristicActivity) context;
        main.setViewClickListener(this);
    }

    @Override
    public int getCount() {
        return services.size();
    }

    @Override
    public Object getItem(int arg0) {
        return services.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {

        LinearLayout viewLayout = (LinearLayout) view;


        if (viewLayout == null) {

            LayoutInflater vi;
            vi = LayoutInflater.from(context);
            viewLayout = (LinearLayout) vi.inflate(R.layout.list_item_characteristic, null);

        }

//        final TextView serviceNameView = (TextView) viewLayout.findViewById(R.id.serviceName);
//        final TextView serviceUuidView = (TextView) viewLayout.findViewById(R.id.serviceUuid);
        final TextView charactNameView = (TextView) viewLayout.findViewById(R.id.characteristicName);
        final TextView charactUuidView = (TextView) viewLayout.findViewById(R.id.characteristicUuid);
        final TextView charactPropertiesView = (TextView) viewLayout.findViewById(R.id.characteristicProperties);

//        UUID uuidMap = services.get(treeNodeInfo.getId()).getUuid();

        UUID uuid = uuidMap.get(position);

        // sort services by uuids
        BluetoothGattService service = services.get(position);
        List<BluetoothGattCharacteristic> characteristicsList = service.getCharacteristics();
        Collections.sort(characteristicsList, new Comparator<BluetoothGattCharacteristic>() {

            @Override
            public int compare(BluetoothGattCharacteristic lhs, BluetoothGattCharacteristic rhs) {
                return lhs.getUuid().compareTo(rhs.getUuid());
            }
        });

        int charactIndex = 0;
        for (BluetoothGattCharacteristic charact : characteristicsList) {
            characteristics.put(charactIndex, charact);
            charactIndex++;
        }


//        UUID uuidMap = characteristics.get(treeNodeInfo.getId()).getUuid();
        Characteristic charact = Engine.getInstance().getCharacteristic(uuid);
        if (charact != null) {
            charactNameView.setText(charact.getName().trim());
        } else {
            charactNameView.setText("unknown characteristic");
        }
        charactUuidView.setText(Common.getUuidText(uuid));


        BluetoothGattCharacteristic bluetoothCharact = this.characteristics.get(position);
        charactPropertiesView.setText("properties_big_case" + " "
                + Common.getProperties(context, bluetoothCharact.getProperties()));

        return viewLayout;
    }




        // Creates content view for advertise data of BLE device
    private TableRow createAdvertiseView(final Device blueetoothDevice, String label, String data) {

        TableRow tableRow = new TableRow(context);
        tableRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tableRow.setPadding(0, 0, 0, 0);

        TextView labelView = new TextView(context);

        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT, 1.0f);

        labelView.setLayoutParams(params);
        labelView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        labelView.setTextColor(context.getResources().getColor(R.color.BluegigaWhite));
        labelView.setTextSize(14);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);

        labelView.setText(label + ":");

        TableRow.LayoutParams dataParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT, 1.0f);

        final TextView dataText = new TextView(context);
        dataText.setLayoutParams(dataParams);
        dataText.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        dataText.setTextColor(context.getResources().getColor(R.color.BluegigaWhite));
        dataText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dataText.setSingleLine(true);
        dataText.setEllipsize(TextUtils.TruncateAt.END);

        dataText.setText(data);

        ViewTreeObserver vto = dataText.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Layout layout = dataText.getLayout();
                if (layout.getEllipsisCount(0) > 0) {
                    blueetoothDevice.setAdvertDetails(true);
                }
            }
        });

        tableRow.addView(labelView);
        tableRow.addView(dataText);
        return tableRow;
    }

    @Override
    public void onCharacteristicClicked(int position) {
        Engine.getInstance().setLastCharacteristic(characteristics.get(position));
        Intent intent = new Intent(context, CharacteristicActivity.class);
        intent.putExtra(BluetoothLeService.DEVICE_ADDRESS, device.getAddress());
        context.startActivity(intent);
    }
}
