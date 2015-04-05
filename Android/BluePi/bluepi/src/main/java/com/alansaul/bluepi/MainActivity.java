package com.alansaul.bluepi;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.os.Handler;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.UUID;

public class MainActivity extends Activity {
    public static final String PREFS_NAME = "BluePiPrefs";

    public static final int SETTINGS_REQUEST = 1;

    TextView settingsText;
    TextView infoText;
    TextView detailsText;

    int filmDurationMinutes;
    int footageSeconds;
    int percent;
    int length;
    float shutterspeed;
    int interval;
    int delay;

    final int port = 1; //Bluetooth SSP port
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private final String TAG="MAIN_THREAD";

    private final String REQUEST = "REQUEST";
    private final String MOVE = "MOVE";
    private final String CAMERA = "CAMERA";

    //private final String LEFT = "LEFT";
    //private final String RIGHT = "RIGHT";
    private final String START = "START";
    private final String SHUTDOWN = "SHUTDOWN";
    private final String SETTINGS_CHANGE = "SETTINGS_CHANGE";
    private final String STOP = "STOP";
    private final String INFO = "INFO";
    private final String IMAGE = "IMAGE";
    private final String[] EMPTY_DATA = {};
    private String[] SETTINGS_DATA;

    // SPP UUID service - this should work for most devices
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Seems to work with lightblue
    //private static final UUID BTMODULEUUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66"); //Seems to connect to the mac directly

    // String for MAC address
    private static String address;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    public final int UPDATE_TEXT=0;
    public final int UPDATE_IMAGE=1;
    public final int DISCONNECTED=0;
    public final int CONNECTED=1;
    public final int CONNECTING=2;
    public File currentImage = null;
    private ImageView latestView;
    private ImageView statusView;

    public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)){
                Log.d(TAG, "Connection made");
            }
            else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)){
                    Log.d(TAG, "Connection made");
                }
        }
    };

    public final Handler updateStatusText = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if (msg.what == UPDATE_TEXT){
                infoText.setText((String)msg.obj);
            }
        }
    };

    public final Handler updateStatusView = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if (msg.what == CONNECTED){
                int connectedColor = android.graphics.Color.GREEN;
                statusView.setBackgroundColor(connectedColor);
            }
            else if (msg.what == DISCONNECTED){
                int disconnectedColor = android.graphics.Color.RED;
                statusView.setBackgroundColor(disconnectedColor);
            }
            else if (msg.what == CONNECTING){
                int connectingColor= android.graphics.Color.YELLOW;
                statusView.setBackgroundColor(connectingColor);
            }
        }
    };

    public final Handler updateDetailsText = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if (msg.what == UPDATE_TEXT){
                detailsText.setText((String)msg.obj);
            }
        }
    };

    public final Handler latestViewHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            Log.d(TAG, "Setting image");
            Log.d(TAG, (String)msg.obj);
            if (msg.what == UPDATE_IMAGE){
                Bitmap bmp = BitmapFactory.decodeFile((String)msg.obj);
                latestView.setImageBitmap(bmp);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get timelapse settings
        Intent settingsRequestIntent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivityForResult(settingsRequestIntent, SETTINGS_REQUEST);
    }

    @Override
    public void onResume() {
        super.onResume();

        btAdapter = BluetoothAdapter.getDefaultAdapter();       // get Bluetooth adapter
        checkBTState(btAdapter);

        getApplicationContext().registerReceiver(mReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));

        getApplicationContext().registerReceiver(mReceiver,
                new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));

        Intent intent = getIntent();
        address = intent.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        //address = "C8:BC:C8:CE:7C:58";
        System.out.println(address);

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        Log.d(TAG, "Starting connect thread");
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        Log.d(TAG, "Connect thread started");

        File outputDir = getCacheDir(); // context being the Activity pointer
        try {
            currentImage = File.createTempFile("prefix", "extension", outputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        infoText = (TextView) findViewById(R.id.info_text);
        infoText.setTextSize(15);
        infoText.setText("Connecting to: " + address);

        Button startBtn = (Button) findViewById(R.id.startBtn);
        Button shutdownBtn = (Button) findViewById(R.id.shutdownBtn);
        Button stopBtn = (Button) findViewById(R.id.stopBtn);
        Button detailsBtn = (Button) findViewById(R.id.cameraDetailBtn);
        Button imageBtn = (Button) findViewById(R.id.imageBtn);
        Button settingsBtn = (Button) findViewById(R.id.settingsBtn);
        detailsText = (TextView) findViewById(R.id.cameraDetails);
        detailsText.setText("No Details yet");
        latestView = (ImageView) findViewById(R.id.latestView);
        statusView = (ImageView) findViewById(R.id.statusView);

        updateStatusView.obtainMessage(DISCONNECTED).sendToTarget();

        //Setup click listener for "find devices"
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(REQUEST, SETTINGS_CHANGE, SETTINGS_DATA);
                sendCommand(REQUEST, START, EMPTY_DATA);
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(REQUEST, STOP, EMPTY_DATA);
            }
        });

        shutdownBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(REQUEST, SHUTDOWN, EMPTY_DATA);
            }
        });

        detailsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(CAMERA, INFO, EMPTY_DATA);
            }
        });

        imageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(CAMERA, IMAGE, EMPTY_DATA);
            }
        });
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Opening settings");
                Intent settingsChangeIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(settingsChangeIntent, SETTINGS_REQUEST);
            }
        });

        settingsText = (TextView) findViewById(R.id.settingsView);
        settingsText.setTextSize(15);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case SETTINGS_REQUEST : {
                if (resultCode == Activity.RESULT_OK){
                    Log.d(TAG, "Got some settings");
                    filmDurationMinutes = data.getIntExtra(SettingsActivity.FILMDURATION, 31);
                    footageSeconds = data.getIntExtra(SettingsActivity.FOOTAGESECONDS, 21);
                    percent = data.getIntExtra(SettingsActivity.PERCENT, 1);
                    length = data.getIntExtra(SettingsActivity.LENGTH, 151);
                    shutterspeed = data.getFloatExtra(SettingsActivity.SHUTTERSPEED, 5f);
                    interval = data.getIntExtra(SettingsActivity.INTERVAL, 3333);
                    delay = data.getIntExtra(SettingsActivity.DELAY, 400);

                    SETTINGS_DATA = new String[3];
                    SETTINGS_DATA[0] = Integer.toString(delay);
                    SETTINGS_DATA[1] = Integer.toString(interval);
                    SETTINGS_DATA[2] = Float.toString(shutterspeed);

                    settingsText.setText("Minutes: " + filmDurationMinutes + "\nPercent: " + percent + "\nLength: " + length + "\nInterval: " + interval + "\nDelay: " + delay);
                }
                break;
                }
        }

    }

    private void sendCommand(String command, String action, String[] data){
        //Take a command and action and send it through bluetooth to the server
        Log.d(TAG, "Sending command");
        if (mConnectedThread != null && mConnectedThread.streamReady){
            //Form command
            updateStatusText.obtainMessage(UPDATE_TEXT, "Requesting " + command + " " + action).sendToTarget();
            String fullCommand = command + "#" + action;
            if (data.length > 0){
                for (int i=0; i<data.length; i++){
                    fullCommand += '#' + data[i];
                }
            }
            Log.d(TAG, fullCommand);
            mConnectedThread.write(fullCommand.getBytes());
        }else{
            Log.d(TAG, "Command send failed");
            infoText.setText("Command send failed");
        }

    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        //if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    @Override
    public void onPause()
    {
        super.onPause();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
    }

    private void checkBTState(BluetoothAdapter btAdapter) {
        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "Device does not support bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
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
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String TAG="CONNECT_THREAD";

        public ConnectThread(BluetoothDevice device){
            Log.d(TAG, "At beginning of connect thread");
            mmDevice = device;
            mmSocket = null;
            //List uuids
            ParcelUuid[] uuids = mmDevice.getUuids();
            for (ParcelUuid uuid : uuids) {
                System.out.println(uuid.getUuid());
            }
            try {
                Log.d(TAG, "Connecting through standard method");
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(BTMODULEUUID);

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mmSocket != null){
                Log.d(TAG, "Socket created successfully");
            }
        }

        @Override
        public void run() {
            updateStatusView.obtainMessage(CONNECTING).sendToTarget();
            Log.d(TAG, "Connecting in thread");
            btAdapter.cancelDiscovery();
            try{
                Log.d(TAG, "Connecting");
                mmSocket.connect();
                Log.d(TAG, "Connected normally");
                connected(mmSocket, mmDevice);
            } catch (IOException e) {
                e.printStackTrace();
                //Sometimes the socket doesn't connect properly, this backup way sometimes works so try this, if not give up
                try {
                    Log.d(TAG, "Failed to connect with existing socket, trying fallback socket connection");
                    mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, port);
                    mmSocket.connect();
                    connected(mmSocket, mmDevice);
                } catch (InvocationTargetException | NoSuchMethodException | IOException | IllegalAccessException e1) {
                    e1.printStackTrace();
                    Log.d(TAG, "Still failing to connect, closing");
                    updateStatusText.obtainMessage(UPDATE_TEXT, "Failed to connect").sendToTarget();
                    try {
                        mmSocket.close();
                    } catch (IOException e2) {
                        Log.d(TAG, "Eep failed to close!");
                    }
                }
            }
        }

        public void cancel(){
            try {
                mmSocket.close();
                Log.d(TAG, "Socket closed");
            }catch (IOException e){
                System.out.println(e);
                Log.d(TAG, "Failed to close connect thread!");
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final String TAG = "CONNECTED_THREAD";
        private final BluetoothSocket socket;
        private final InputStream mmInput;
        private final OutputStream mmOutput;
        private boolean stop = false;
        public boolean streamReady = false;

        public ConnectedThread(BluetoothSocket socket){
            this.socket = socket;

            Log.d(TAG, "Connected, getting input output streams");
            //Get input and output streams
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Failed to get input or output stream");
            }

            mmInput = tmpIn;
            mmOutput = tmpOut;

            streamReady = true;
        }

        public void run() {
            updateStatusText.obtainMessage(UPDATE_TEXT, "Connected").sendToTarget();
            updateStatusView.obtainMessage(CONNECTED).sendToTarget();
            byte[] buffer = new byte[1024]; //Buffer to hold the bytes
            int bytes; //Number of bytes received

            Log.d(TAG, "Reading");
            while (!stop) {
                try {
                    // Read from the InputStream
                    bytes = mmInput.read(buffer);
                    Log.d(TAG, "Received data");
                    parseCommand(new String(Arrays.copyOfRange(buffer, 0, bytes)));
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Error reading, cancelling");
                    cancel();
                }
            }
        }

        public void parseCommand(String command) throws IOException {
            Log.d(TAG, String.format("Got command: %s", command));
            String[] commands = command.split("#");
            for (int i=0; i < commands.length; i++) {
                Log.d(TAG, commands[i]);
            }
            if (commands.length == 0){
                return;
            }
            else if (commands[0].equals(CAMERA) && commands[1].equals(IMAGE)){
                handleImageDownload(commands);
            }
            else if (commands[0].equals(CAMERA) && commands[1].equals(INFO)){
                handleDetails(commands);
            }
            else{
                Log.d(TAG, "Expecting something else, or maybe not");
            }
        }

        private void handleDetails(String[] commands) throws IOException{
            Log.d(TAG, "Handling info download");
            updateStatusText.obtainMessage(UPDATE_TEXT, "Received details").sendToTarget();
            // Parse the details and set the correct forms
            String details = commands[2];
            updateDetailsText.obtainMessage(UPDATE_TEXT, details).sendToTarget();
        }

        private void handleImageDownload(String[] commands) throws IOException{
            updateStatusText.obtainMessage(UPDATE_TEXT, "Receiving image").sendToTarget();
            Log.d(TAG, "Expecting an image!");
            //Get filesize, then read in with 1024 bytes at a time into a file
            int fileSize = Integer.parseInt(commands[2]);
            Log.d(TAG, String.format("Filesize %d", fileSize));
            //Might not be threadsafe?
            OutputStream outFile = new FileOutputStream(currentImage);
            long bytesReceived = 0;
            byte[] buff = new byte[1024];
            Log.d(TAG, String.format("Opened file %s", currentImage.getAbsolutePath()));
            while (bytesReceived < fileSize) {
                int bytes = mmInput.read(buff);
                if (bytes > 0) {
                    bytesReceived += bytes;
                    outFile.write(buff, 0, bytes);
                }
                Log.d(TAG, String.format("Got %d bytes so far", bytesReceived));
            }
            outFile.flush();
            Log.d(TAG, String.format("Received all %d bytes", bytesReceived));
            latestViewHandler.obtainMessage(UPDATE_IMAGE, currentImage.getAbsolutePath()).sendToTarget();
            updateStatusText.obtainMessage(UPDATE_TEXT, "Image received").sendToTarget();
        }

        public void write(byte[] bytes){
            Log.d(TAG, "Writing");
            Log.d(TAG, String.valueOf(bytes.length));
            try{
                mmOutput.write(bytes);
                mmOutput.flush();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Failed to write bytes");
            }

        }

        public void cancel(){
            streamReady = false;
            updateStatusText.obtainMessage(UPDATE_TEXT, "Disconnected").sendToTarget();
            updateStatusView.obtainMessage(DISCONNECTED).sendToTarget();
            try{
                socket.close();
                Log.d(TAG, "Socket closed");
            }catch (IOException e){
                System.out.println(e);
                Log.d(TAG, "Failed to close connected thread!");
            }
            stopThread();
        }

        public synchronized void stopThread(){
            stop=true;
        }


    }
}
