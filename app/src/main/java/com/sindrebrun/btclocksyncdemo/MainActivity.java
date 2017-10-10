package com.sindrebrun.btclocksyncdemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.sindrebrun.btclocksyncdemo.bluetooth.BluetoothSPPHandler;
import com.sindrebrun.btclocksyncdemo.bluetooth.BluetoothSPPService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BtClockSyncDemo";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;


    private String mConnectedDeviceName = null;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothSPPService mChatService = null;

    private TextView mConnectedDeviceView;
    private Button mConnectToDevice;
    private Button mMakeDiscoverable;
    private Button mSyncClocks;
    private TextView mDelayTextView;
    private TextView mStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        mConnectedDeviceView = (TextView) findViewById(R.id.connectedToName);
        mConnectToDevice = (Button) findViewById(R.id.button_connect);
        mMakeDiscoverable = (Button) findViewById(R.id.button_discoverable);
        mSyncClocks = (Button) findViewById(R.id.button_sync);
        mDelayTextView = (TextView) findViewById(R.id.delayTextView);
        mStatusText = (TextView) findViewById(R.id.statusText);
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothSPPService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    public void onScanButtonCLicked() {
        // Launch the DeviceListActivity to see devices and do scan
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
    }


    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothSPPService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");


        mConnectToDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            }
        });

        mMakeDiscoverable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ensureDiscoverable();
            }
        });

        mSyncClocks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sync_count = 0;
                delays = new ArrayList<>();
                logLines = new ArrayList<>();
                syncInProgress = true;
                sendSyncMessage();
            }
        });
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }


    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    long request_time;
    int sync_count = 0;
    List<Long> delays = new ArrayList<>();
    List<String> logLines = new ArrayList<>();
    boolean syncInProgress = false;

    private void sendSyncMessage() {

        if (mChatService.getState() != BluetoothSPPService.STATE_CONNECTED) {
            Toast.makeText(this, "You're not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Sending sync request");
        request_time = System.currentTimeMillis();
        mChatService.write("get_time".getBytes());
        sync_count++;
    }

    private void replyToTimeRequest() {
        long response_time = System.currentTimeMillis();
        mChatService.write(("time:" + Long.toString(response_time)).getBytes());
    }


    private void calculateOffset(String message) {
        long current_time = System.currentTimeMillis();
        long rtt = current_time - request_time;

        long expected_response = request_time + (rtt / 2);
        long response = Long.valueOf(message);

        long deviance = expected_response - response;
        delays.add(deviance);
        logLines.add(sync_count + "," + request_time + "," + current_time + "," + response);
        //mDelayTextView.setText(Long.toString(deviance));
    }

    private void handleIncomingMessage(String message){
        if (message.equalsIgnoreCase("get_time")) {
            replyToTimeRequest();
        } else if (isTimeResponse(message) && syncInProgress) {
            calculateOffset(message.subSequence(5, message.length()).toString());

            if (sync_count < 100) {
                sendSyncMessage();
            }

            if (sync_count == 100) {
                calculateAverage();
            }
        }
    }

    private void calculateAverage() {
        syncInProgress = false;
        double average = 0;

        Collections.sort(delays);
        delays = delays.subList(10,90);

        Long sum = 0L;
        for(Long e: delays){
            sum += e;
        }

        average = (double)sum / (double)delays.size();

        //writeSyncLogToFile();

        mDelayTextView.setText("After " + Integer.toString(sync_count) + "syncs, the average delay is " + Double.toString(average));
    }

    private boolean isTimeResponse(String message) {
        return message.substring(0, 4).equalsIgnoreCase("time");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    //connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "User did not enable bluetooth",
                            Toast.LENGTH_SHORT).show();
                    this.finish();
                }
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothSPPHandler.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSPPService.STATE_CONNECTED:
                            // Set connected to status
                            setStatus("Connected to: " + mConnectedDeviceName);
                            mConnectedDeviceView.setText(mConnectedDeviceName);
                            break;
                        case BluetoothSPPService.STATE_CONNECTING:
                            setStatus("Connecting");
                            break;
                        case BluetoothSPPService.STATE_LISTEN:
                        case BluetoothSPPService.STATE_NONE:
                            setStatus("Not connected");
                            break;
                    }
                    break;
                case BluetoothSPPHandler.MESSAGE_WRITE:
                    // THIS IS TRIGGERED AFTER A MESSAGE IS SENT

                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    Log.d(TAG, "Sent: " + writeMessage);
                    break;
                case BluetoothSPPHandler.MESSAGE_READ:
                    // THIS CODE IS RUN WHEN A MESSAGE IS RECEIVED

                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Log.d(TAG, "Received: " + readMessage);
                    handleIncomingMessage(readMessage);
                    break;
                case BluetoothSPPHandler.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(BluetoothSPPHandler.DEVICE_NAME);
                    if (null != MainActivity.this) {
                        Toast.makeText(MainActivity.this, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        mConnectedDeviceView.setText(mConnectedDeviceName);
                    }
                    break;
                case BluetoothSPPHandler.MESSAGE_TOAST:
                    if (null != MainActivity.this) {
                        Toast.makeText(MainActivity.this, msg.getData().getString(BluetoothSPPHandler.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    private void setStatus(CharSequence subTitle) {
        mStatusText.setText(subTitle);
    }

}
