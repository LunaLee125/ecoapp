package com.example.ecobluetoothconnection;

// CODE BASED ON: https://medium.com/swlh/create-custom-android-app-to-control-arduino-board-using-bluetooth-ff878e998aa8

// imported packages
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    // variables needed to create bluetooth connection
    private String deviceName = null;
    private String deviceAddress;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;

    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bluetooth UI Initialization
        final Button buttonConnect = findViewById(R.id.buttonConnect);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        final ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        final TextView textViewInfo = findViewById(R.id.textViewInfo);

        // Controller UI Initialization
        final SeekBar seekBarL = findViewById(R.id.seekBarL);
        final SeekBar seekBarR = findViewById(R.id.seekBarR);
        final SeekBar seekBarCT = findViewById(R.id.seekBarCT);
        final SeekBar seekBarC = findViewById(R.id.seekBarC);

        final TextView toChange = findViewById(R.id.toChange);

        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName != null) {
            // Get the device address to make BT Connection
            deviceAddress = getIntent().getStringExtra("deviceAddress");
            // Show progress and connection status
            toolbar.setSubtitle("Connecting to " + deviceName + "...");
            progressBar.setVisibility(View.VISIBLE);
            buttonConnect.setEnabled(false);

            //When "deviceName" is found the code will call a new thread to create a bluetooth connection to the selected device
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            createConnectThread = new CreateConnectThread(bluetoothAdapter, deviceAddress);
            createConnectThread.start();
        }

        //GUI handler
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTING_STATUS:
                        switch (msg.arg1) {
                            case 1:
                                toolbar.setSubtitle("Connected to " + deviceName);
                                progressBar.setVisibility(View.GONE);
                                buttonConnect.setEnabled(true);
                                break;
                            case -1:
                                toolbar.setSubtitle("Device fails to connect");
                                progressBar.setVisibility(View.GONE);
                                buttonConnect.setEnabled(true);

                                break;
                        }
                        break;
                }
            }
        };

/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ < Functionality code > ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

        // Select Bluetooth Device
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                startActivity(intent);
            }
        });

        toChange.setText("       Serial Monitor");

        // seek bar that control left thruster
        seekBarL.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // When the seek bar is changed, transmit identifier + value to bluetooth
            @Override
            public void onProgressChanged(SeekBar seekBar,  int progress,  boolean fromUser)
            {
                connectedThread.write("LT" + progress + " ");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBarL) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBarL) {}
        });

        // seek bar that control right thruster
        seekBarR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // When the seek bar is changed, transmit identifier + value to bluetooth
            @Override
            public void onProgressChanged(SeekBar seekBarR,  int progress,  boolean fromUser)
            {
                connectedThread.write("RT" + progress + " ");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBarR) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBarR) {}
        });

        // seek bar that controls the tilt of the conveyor belt
        seekBarCT.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // When the seek bar is changed, transmit identifier + value to bluetooth
            @Override
            public void onProgressChanged(SeekBar seekBar,  int progress,  boolean fromUser)
            {
                Log.i("change", "change");
                connectedThread.write("CT" + progress + " ");
                Log.i("change", "change");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBarCT) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBarCT) {}
        });

        // seek bar that controls the conveyor belt rotation
        seekBarC.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // When the seek bar is changed, transmit identifier + value to bluetooth
            @Override
            public void onProgressChanged(SeekBar seekBarCO,  int progress,  boolean fromUser)
            {
                connectedThread.write("CB" + progress + " ");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBarCO) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBarCO) {}
        });

    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            //Use a temporary object that is later assigned to mmSocket because mmSocket is final.

            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);

            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,new String[] { Manifest.permission.BLUETOOTH_SCAN },
                        1);
                return;
            }
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                Log.e("Status", "Device connected");
                handler.obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }

        public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                               int[] grantResults) {
            switch (requestCode) {
                case 1:
                    // If request is cancelled, the result arrays are empty.
                    if (grantResults.length > 0 &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        Log.i("MainActivity","Permission approved");
                    }  else {
                        Log.i("MainActivity","Error getting permission");
                    }
                    return;
            }

        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        final TextView toChange = findViewById(R.id.toChange);
        String left = "";
        String right = "";

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }




        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;

                    if (buffer[bytes] == '\n'){
                        readMessage = new String(buffer,0,bytes);


                        handler.obtainMessage(MESSAGE_READ,readMessage).sendToTarget();
                        bytes = 0;

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                if(readMessage.charAt(0)== 'r'){
                                    if(!readMessage.substring(1).equals(right)) {
                                        right = readMessage.substring(1);
                                        toChange.setText("      Right side " + right + "\n      Left side " + left);
                                    }

                                }
                                if(readMessage.charAt(0) == 'l'){
                                    if(!readMessage.substring(1).equals(left)) {
                                        left = readMessage.substring(1);
                                        toChange.setText("      Right side " + right + "\n      Left side " + left);
                                    }
                                }


                            }
                        });
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error","Unable to send message",e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null){
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }
}
