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

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.example.fen.bello.R;
import com.example.fen.bello.datamodel.Common;
import com.example.fen.bello.datamodel.Consts;
import com.example.fen.bello.datamodel.Device;
import com.example.fen.bello.datamodel.Engine;
import com.example.fen.bello.datamodel.xml.Bit;
import com.example.fen.bello.datamodel.xml.Characteristic;
import com.example.fen.bello.datamodel.xml.Descriptor;
import com.example.fen.bello.datamodel.xml.Enumeration;
import com.example.fen.bello.datamodel.xml.Field;
import com.example.fen.bello.datamodel.xml.Service;
import com.example.fen.bello.datamodel.xml.ServiceCharacteristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

// CharacteristicActivity - displays and manages characteristic value
// It is main activity where user can read and write characteristic data
public class CharacteristicActivity extends Activity {

    final private int REFRESH_INTERVAL = 500; // miliseconds

    final private String TYPE_FLOAT = "FLOAT";
    final private String TYPE_SFLOAT = "SFLOAT";
    final private String TYPE_FLOAT_32 = "float32";
    final private String TYPE_FLOAT_64 = "float64";

    private TextView valuesText;
    private TextView valuesBytes;

    private static IntentFilter bleIntentFilter;

    private Characteristic mCharact;

    private BluetoothGattCharacteristic mBluetoothCharact;
    private BluetoothLeService mBluetoothLeService;
    private Service mService;
    private List<BluetoothGattDescriptor> mDescriptors;
    private Iterator<BluetoothGattDescriptor> iterDescriptor;
    private BluetoothGattDescriptor lastDescriptor;
    private boolean readable = false;
    private boolean writeable = false;
    private boolean notify = false;
    private boolean isRawValue = false;
    private boolean parseProblem = false;
    private int offset = 0; // in bytes
    private int currRefreshInterval = REFRESH_INTERVAL; // in seconds
    private byte[] value;

    private Device mDevice;
    // Implements callback method for service connection
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            // If characteristic is readable, call read
            // characteristic method on mBluetoothLeService
            if (readable) {
                Log.wtf("CharacteristicActivity","characteristic is readable");
                mBluetoothLeService.readCharacteristic(mDevice, mBluetoothCharact);
            } else { // Another case prepare empty data and show UI
                if (!isRawValue) {
                    Log.wtf("CharacteristicActivity","Another case prepare empty data and show UI");
                    prepareValueData();
                }
            }
            // If characteristic is notify, set notification on it
            if (notify) {
                Log.wtf("CharacteristicActivity","characteristic is notify");
                setNotification();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    // Implements receive methods that handle a specific intent actions from
    // mBluetoothLeService When ACTION_GATT_DISCONNECTED action is received and
    // device address equals current device, activity closes When
    // ACTION_DATA_AVAILABLE action is received, refresh ativity UI When
    // ACTION_DATA_WRITE action is received, and characteristic uuid equals
    // current activity, show appropriate toast message
    private final BroadcastReceiver mBluetoothLeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.wtf("CharactersisticActivity","mBluetoothLeReceiver");
            final String action = intent.getAction();
            if (action.equals(BluetoothLeService.ACTION_DATA_AVAILABLE)) {

                String uuidCharact = intent.getExtras().getString(BluetoothLeService.UUID_CHARACTERISTIC);
                // If time from last update was elapsed then UI is updated and
                // timer is reset
                    if (uuidCharact.equals(mBluetoothCharact.getUuid().toString())) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                offset = 0;
                                value = mBluetoothCharact.getValue();
                                String byteMesg = bytesToHex(value); // Arrays.toString(value);
                                String stringMesg = new String(value);
                                Log.wtf("CharacteristicActivity getting","byte="+byteMesg+" string="+stringMesg);
                                updateValuesViews(byteMesg, stringMesg);
                            }
                        });
                    }

            } else if (action.equals(BluetoothLeService.ACTION_DATA_WRITE)
                    && intent.getExtras().getString(BluetoothLeService.UUID_CHARACTERISTIC).equals(
                            mBluetoothCharact.getUuid().toString())) {

                Log.wtf("CharactersisticActivity","ACTION_DATA_WRITE");

                final int status = intent.getIntExtra(BluetoothLeService.GATT_STATUS, 0);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Toast.makeText(CharacteristicActivity.this, getText(R.string.characteristic_write_success),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CharacteristicActivity.this, getText(R.string.characteristic_write_fail),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            } else if (intent.getAction().equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)) {
                String deviceAddress = intent.getExtras().getString(BluetoothLeService.DEVICE_ADDRESS);
                if (deviceAddress.equals(mDevice.getAddress())) {
                    finish();
                }
            } else if (intent.getAction().equals(BluetoothLeService.ACTION_DESCRIPTOR_WRITE)) {
                UUID descriptorUuid = (UUID) intent.getExtras().get(BluetoothLeService.UUID_DESCRIPTOR);
                if (Common.equalsUUID(descriptorUuid, lastDescriptor.getUuid())) {
                    writeNextDescriptor();
                }
            }
        }
    };

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_characteristic);

        TextView serviceNameView = (TextView) findViewById(R.id.serviceName);
        TextView charactNameView = (TextView) findViewById(R.id.characteristicName);
        TextView charactUuidView = (TextView) findViewById(R.id.uuid);
        TextView charactPropertiesName = (TextView) findViewById(R.id.properties);
        valuesText = (TextView) findViewById(R.id.valuesText);
        valuesBytes = (TextView) findViewById(R.id.valuesBytes);


        String address = getIntent().getExtras().getString(BluetoothLeService.DEVICE_ADDRESS);
        mDevice = Engine.getInstance().getDevice(address);
        mBluetoothCharact = Engine.getInstance().getLastCharacteristic();
        mCharact = Engine.getInstance().getCharacteristic(mBluetoothCharact.getUuid());
        mService = Engine.getInstance().getService(mBluetoothCharact.getService().getUuid());
        mDescriptors = new ArrayList<BluetoothGattDescriptor>();

        setProperties();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        configureWriteable();

        if (mService != null) {
            serviceNameView.setText(mService.getName().trim());
        } else {
            serviceNameView.setText(getText(R.string.unknown_service));
        }

        if (mCharact != null) {
            charactNameView.setText(mCharact.getName());
        } else {
            charactNameView.setText(getText(R.string.unknown_characteristic));
        }

        charactUuidView.setText(getText(R.string.uuid) + " 0x"
                + Common.convert128to16UUID(mBluetoothCharact.getUuid().toString()));

        charactPropertiesName.setText(Common.getProperties(this, mBluetoothCharact.getProperties()));
    }

    private void updateValuesViews(String newBytes, String newText) {
        String curBytes = valuesBytes.getText().toString();
        String curText = valuesText.getText().toString();
        valuesBytes.setText(curBytes + "\n" + newBytes);
        valuesText.setText(curText + newText);
    }

    // Sets property members for characteristics
    private void setProperties() {

        if (Common.isSetProperty(Common.PropertyType.READ, mBluetoothCharact.getProperties())) {
            Log.wtf("CharacteristicActivity","setProperties readable");
            readable = true;
        }
        if (Common.isSetProperty(Common.PropertyType.WRITE, mBluetoothCharact.getProperties())
                || Common.isSetProperty(Common.PropertyType.WRITE_NO_RESPONSE, mBluetoothCharact.getProperties())) {
            Log.wtf("CharacteristicActivity","setProperties writeable");
            writeable = true;
        }
        if (Common.isSetProperty(Common.PropertyType.NOTIFY, mBluetoothCharact.getProperties())
                || Common.isSetProperty(Common.PropertyType.INDICATE, mBluetoothCharact.getProperties())) {
            notify = true;
        }
        if (mCharact == null || mCharact.getFields() == null) {
            isRawValue = true;
        }
    }

    // Configures characteristic if it is writeable
    private void configureWriteable() {

        if (writeable) {
            Button sendButton = (Button) findViewById(R.id.send_button);
            sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.wtf("CharactersisticActivity","send button is clicked");
                    EditText inputText = (EditText) findViewById(R.id.input_text);
                    String value = inputText.getText().toString();

                    writeValueToCharacteristic(value);
                }
            });

        }
    }

    private void writeValueToCharacteristic(String value) {

//        mBluetoothCharact.setValue(value.getBytes());
//        Log.wtf("CharactersisticActivity","writeValueToCharacteristic = "+value);

            byte endingByte = 0x0A;

//          String byteMesg = bytesToHex(value);

//        if (isRawValue || parseProblem) {

//            String hex = bytesToHex(value.getBytes());
//            byte newValue[] = hexToByteArray(hex);
           byte newValue[]= value.getBytes();

            byte[] data = new byte[newValue.length+1];
            for (int i =0; i<newValue.length; i++) {
                data[i] = newValue[i];
            }
            data[newValue.length] = endingByte;

            Log.wtf("CharactersisticActivity","writeValueToCharacteristic = "+newValue);
            mBluetoothCharact.setValue(data);
//        } else {
//            Log.wtf("CharactersisticActivity","writeValueToCharacteristic = "+value);
//            mBluetoothCharact.setValue(value);
//        }
        Log.wtf("CharactersisticActivity","writeCharacteristic ");


        boolean smth = mBluetoothLeService.writeCharacteristic(mDevice, mBluetoothCharact);
//        boolean smth = mBluetoothLeService.writeDescriptor(mDevice, mBluetoothCharact);
        Log.wtf("CharactersisticActivity","writeCharacteristic ="+smth);

    }


    // Sets notification on characteristic data changes
    protected void setNotification() {
        mBluetoothLeService.setCharacteristicNotification(mDevice, mBluetoothCharact, true);
        // mBluetoothLeService.readCharacteristic(mDevice, mBluetoothCharact);

        ArrayList<Descriptor> descriptors = getCharacteristicDescriptors();

        if (descriptors != null) {
            for (BluetoothGattDescriptor blDescriptor : mBluetoothCharact.getDescriptors()) {
                if (isDescriptorAvailable(descriptors, blDescriptor)) {
                    mDescriptors.add(blDescriptor);
                }
            }
        } else {
            mDescriptors = new ArrayList<BluetoothGattDescriptor>(mBluetoothCharact.getDescriptors());
        }

        iterDescriptor = mDescriptors.iterator();
        writeNextDescriptor();
    }


    // Gets all characteristic descriptors
    private ArrayList<Descriptor> getCharacteristicDescriptors() {
        Log.wtf("CharacteristicActivity","getCharacteristicDescriptors");
        if (mService == null && mCharact == null) {
            return null;
        }
        ArrayList<Descriptor> descriptors = new ArrayList<Descriptor>();

        for (ServiceCharacteristic charact : mService.getCharacteristics()) {
            if (charact.getType().equals(mCharact.getType())) {
                for (Descriptor descriptor : charact.getDescriptors()) {
                    descriptors.add(Engine.getInstance().getDescriptorByType(descriptor.getType()));
                }
            }
        }
        return descriptors;
    }

    // Checks if given descriptor is available in this characteristic
    private boolean isDescriptorAvailable(ArrayList<Descriptor> descriptors, BluetoothGattDescriptor blDescriptor) {
        for (Descriptor descriptor : descriptors) {
            if (Common.equalsUUID(descriptor.getUuid(), blDescriptor.getUuid())) {
                return true;
            }
        }
        return false;
    }

    // Writes next descriptor in order to enable notification or indication
    protected void writeNextDescriptor() {
        Log.wtf("CharacteristicActivity","writeNextDescriptor");
        if (iterDescriptor.hasNext()) {
            lastDescriptor = iterDescriptor.next();

            if (lastDescriptor.getCharacteristic() == mBluetoothCharact) {

                Log.wtf("CharacteristicActivity","last descriptor is mBluetoothCharact");

                lastDescriptor.setValue(Common.isSetProperty(Common.PropertyType.NOTIFY, mBluetoothCharact
                        .getProperties()) ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                mBluetoothLeService.writeDescriptor(mDevice, lastDescriptor);
                // new
                // mBluetoothLeService.readDescriptor(mDevice, lastDescriptor);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBluetoothLeReceiver, getGattUpdateIntentFilter());
        if (!mDevice.isConnected()) {
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
        if (notify) {
            mBluetoothLeService.setCharacteristicNotification(mDevice, mBluetoothCharact, false);
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    // Returns intent filter for receiving specific action from
    // mBluetoothLeService:
    // - BluetoothLeService.ACTION_DATA_AVAILABLE - new data is available
    // - BluetoothLeService.ACTION_DATA_WRITE - data wrote
    // - BluetoothLeService.ACTION_DESCRIPTOR_WRITE - descriptor wrote
    // - BluetoothLeService.ACTION_GATT_DISCONNECTED - device disconnected
    // This method is used when registerReceiver method is called
    private static IntentFilter getGattUpdateIntentFilter() {
        if (bleIntentFilter == null) {
            bleIntentFilter = new IntentFilter();
            bleIntentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
            bleIntentFilter.addAction(BluetoothLeService.ACTION_DESCRIPTOR_WRITE);
        }
        return bleIntentFilter;
    }


    // Initializes byte array with empty characteristic content
    private void prepareValueData() {
        int size = characteristicSize();
        if (size != 0) {
            value = new byte[size];
        }
    }

    // Returns characteristic size in bytes
    private int characteristicSize() {
        int size = 0;
        for (Field field : mCharact.getFields()) {
            size += fieldSize(field);
        }
        return size;
    }

    // Returns only one field size in bytes
    private int fieldSize(Field field) {

        String format = field.getFormat();
        if (format != null) {
            return Engine.getInstance().getFormat(format);
        } else if (field.getReferenceFields().size() > 0) {
            int subFieldsSize = 0;
            for (Field subField : field.getReferenceFields()) {
                subFieldsSize += fieldSize(subField);
            }
            return subFieldsSize;
        } else {
            return 0;
        }
    }

    // Checks if field is present based on it's requirements and bitfield
    // settings
    private boolean isFieldPresent(Field field) {
        if (parseProblem) {
            return true;
        }
        if (field.getRequirement() == null || field.getRequirement().equals(Consts.REQUIREMENT_MANDATORY)) {
            return true;
        } else {
            for (Field bitField : getBitFields()) {
                for (Bit bit : bitField.getBitfield().getBits()) {
                    for (Enumeration enumeration : bit.getEnumerations()) {
                        if (enumeration.getRequires() != null
                                && field.getRequirement().equals(enumeration.getRequires())) {
                            boolean fieldPresent = checkRequirement(bitField, enumeration, bit);
                            return fieldPresent;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Checks requirement on exactly given bitfield, enumeration and bit
    private boolean checkRequirement(Field bitField, Enumeration enumeration, Bit bit) {
        int formatLength = Engine.getInstance().getFormat(bitField.getFormat());
        int off = getFieldOffset(bitField);
        int val = readInt(off, formatLength);
        int enumVal = readEnumInt(bit.getIndex(), bit.getSize(), val);
        return (enumVal == enumeration.getKey() ? true : false);
    }


    // Converts string given in hexadecimal system to byte array
    private byte[] hexToByteArray(String hex) {
        byte byteArr[] = new byte[hex.length() / 2];
        for (int i = 0; i < byteArr.length; i++) {
            int temp = Integer.parseInt(hex.substring(i * 2, (i * 2) + 2), 16);
            byteArr[i] = (byte) (temp & 0xFF);
        }
        return byteArr;
    }

    public static String bytesToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a) {
            sb.append(String.format("%02x", b & 0xff));
            sb.append(' ');
        }
        return sb.toString();
    }

    // Converts string given in decimal system to byte array
    private byte[] decToByteArray(String dec) {
        if (dec.length() == 0) {
            return new byte[] {};
        }
        String decArray[] = dec.split(" ");
        byte byteArr[] = new byte[decArray.length];

        for (int i = 0; i < decArray.length; i++) {
            try {
                byteArr[i] = (byte) (Integer.parseInt(decArray[i]));
            } catch (NumberFormatException e) {
                return new byte[] { 0 };
            }
        }
        return byteArr;
    }

    // Converts int to byte array
    private byte[] intToByteArray(int newVal, int formatLength) {
        byte val[] = new byte[formatLength];
        for (int i = 0; i < formatLength; i++) {
            val[i] = (byte) (newVal & 0xff);
            newVal >>= 8;
        }
        return val;
    }

    // Checks if decimal input value is valid
    private boolean isDecValueValid(String decValue) {
        char value[] = decValue.toCharArray();
        int valLength = value.length;
        boolean valid = false;
        if (decValue.length() < 4) {
            valid = true;
        } else {
            valid = value[valLength - 1] == ' ' || value[valLength - 2] == ' ' || value[valLength - 3] == ' '
                    || value[valLength - 4] == ' ';
        }
        return valid;
    }

    // Reads integer value for given offset and field size
    private int readInt(int offset, int size) {
        int val = 0;
        for (int i = 0; i < size; i++) {
            val <<= 8;
            val |= value[offset + i];
        }
        return val;
    }

    // Reads next enumeration value for given enum length
    private int readNextEnum(int formatLength) {
        int result = 0;
        for (int i = 0; i < formatLength; i++) {
            result |= value[offset];
            if (i < formatLength - 1) {
                result <<= 8;
            }
        }
        offset += formatLength;
        return result;
    }

    // Reads next value for given format
    private String readNextValue(String format) {
        if (value == null) {
            return "";
        }

        int formatLength = Engine.getInstance().getFormat(format);

        String result = "";
        // If field length equals 0 then reads from offset to end of
        // characteristic data
        if (formatLength == 0) {
            result = new String(Arrays.copyOfRange(value, offset, value.length));
            offset += value.length;
        } else {
            // If format type is kind of float type then reads float value
            // else reads value as integer
            if (format.equals(TYPE_SFLOAT) || format.equals(TYPE_FLOAT) || format.equals(TYPE_FLOAT_32)
                    || format.equals(TYPE_FLOAT_64)) {
                double fValue = readFloat(format, formatLength);
                result = String.valueOf(fValue);
            } else {
                for (int i = offset; i < offset + formatLength; i++) {
                    result += (int) (value[i] & 0xff);
                }
            }
            offset += formatLength;
        }
        return result;
    }

    // Reads float value for given format
    private double readFloat(String format, int formatLength) {
        double result = 0.0;
        if (format.equals(TYPE_SFLOAT)) {
            result = Common.readSfloat(value, offset, formatLength - 1);
        } else if (format.equals(TYPE_FLOAT)) {
            result = Common.readFloat(value, offset, formatLength - 1);
        } else if (format.equals(TYPE_FLOAT_32)) {
            result = Common.readFloat32(value, offset, formatLength);
        } else if (format.equals(TYPE_FLOAT_64)) {
            result = Common.readFloat64(value, offset, formatLength);
        }
        return result;
    }

    // Reads enum for given value
    private int readEnumInt(int index, int size, int val) {
        int result = 0;
        for (int i = 0; i < size; i++) {
            result <<= 8;
            result |= ((val >> (index + i)) & 0x1);
        }
        return result;
    }

    // Sets value from offset position
    private void setValue(int off, byte[] val) {
        for (int i = off; i < val.length; i++) {
            value[i] = val[i];
        }
    }

    // Gets field offset in bytes
    private int getFieldOffset(Field searchField) {
        foundField = false;
        int off = 0;
        for (Field field : mCharact.getFields()) {
            off += getOffset(field, searchField);
        }
        foundField = true;

        return off;
    }

    private boolean foundField = false;

    // Gets field offset when field has references to other fields
    private int getOffset(Field field, Field searchField) {
        int off = 0;
        if (field == searchField) {
            foundField = true;
            return off;
        }
        if (!foundField && isFieldPresent(field)) {
            if (field.getReferenceFields().size() > 0) {
                for (Field subField : field.getReferenceFields()) {
                    off += getOffset(subField, searchField);
                }
            } else {
                if (field.getFormat() != null) {
                    off += Engine.getInstance().getFormat(field.getFormat());
                }
            }
        }
        return off;
    }

    // Gets all bit fields for this characteristic
    private ArrayList<Field> getBitFields() {
        ArrayList<Field> bitFields = new ArrayList<Field>();
        for (Field field : mCharact.getFields()) {
            bitFields.addAll(getBitField(field));
        }
        return bitFields;
    }

    // Gets bit field when field has references to other fields
    private ArrayList<Field> getBitField(Field field) {
        ArrayList<Field> bitFields = new ArrayList<Field>();
        if (field.getBitfield() != null) {
            bitFields.add(field);
        } else if (field.getReferenceFields().size() > 0) {
            for (Field subField : field.getReferenceFields()) {
                bitFields.addAll(getBitField(subField));
            }
        }
        return bitFields;
    }


    // Converts pixels to 'dp' unit
    private int convertPxToDp(int sizeInPx) {
        float scale = getResources().getDisplayMetrics().density;
        int sizeInDp = (int) (sizeInPx * scale + 0.5f);
        return sizeInDp;
    }

    class WriteCharacteristic implements OnEditorActionListener {

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
//                writeValueToCharacteristic();
                return true;
            }
            return false;
        }
    }
}
