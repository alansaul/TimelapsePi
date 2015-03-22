package com.alansaul.bluepi;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Button;

import android.content.Intent;

import java.util.Set;
import android.bluetooth.BluetoothAdapter;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import static android.view.View.OnClickListener;
import android.widget.AdapterView.OnItemClickListener;

public class DeviceListActivity extends ActionBarActivity {
    TextView statusText;
    ListView devices;
    ImageView bluetoothStatusView;

    private final int DISABLED = 0;
    private final int ENABLED = 1;
    private final int ENABLING = 2;

    // EXTRA string to send on to mainactivity
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothAdapter BTAdaptor;
    private ArrayAdapter<String> devicesArrayAdaptor;

    // Handle changes in the bluetooth state
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        updateStatusView.obtainMessage(DISABLED).sendToTarget();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        updateStatusView.obtainMessage(ENABLING).sendToTarget();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        updateStatusView.obtainMessage(ENABLED).sendToTarget();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        updateStatusView.obtainMessage(ENABLING).sendToTarget();
                        break;
                }
            }
        }
    };

    public final Handler updateStatusView = new Handler(){
        @Override
        public void handleMessage(Message msg){
            if (msg.what == ENABLED){
                int enabledColor = android.graphics.Color.GREEN;
                bluetoothStatusView.setBackgroundColor(enabledColor);
            }
            else if (msg.what == DISABLED){
                int disableColor = android.graphics.Color.RED;
                bluetoothStatusView.setBackgroundColor(disableColor);
            }
            else if (msg.what == ENABLING){
                int enablingColor = android.graphics.Color.YELLOW;
                bluetoothStatusView.setBackgroundColor(enablingColor);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_list);
    }

    @Override
    public void onResume(){
        super.onResume();
        BTAdaptor = BluetoothAdapter.getDefaultAdapter();

        statusText = (TextView) findViewById(R.id.statusText);
        statusText.setTextSize(15);

        devicesArrayAdaptor = new ArrayAdapter<String>(this, R.layout.device_name);

        devices = (ListView) findViewById(R.id.BT_listView);
        devices.setAdapter(devicesArrayAdaptor);
        //Setup click listener for selecting a device in the list
        devices.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                statusText.setText("Connecting...");

                // Get the MAC address (last 17 characters)
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                Intent i = new Intent(DeviceListActivity.this, MainActivity.class);
                i.putExtra(EXTRA_DEVICE_ADDRESS, address);
                startActivity(i);
            }
        });

        Button findDevicesBtn = (Button) findViewById(R.id.find_devices);
        Switch bluetoothSwitch = (Switch) findViewById(R.id.bluetoothSwitch);
        bluetoothStatusView = (ImageView) findViewById(R.id.bluetoothStatusView);

        //Register an event to be made on action change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        boolean isEnabled = BTAdaptor.isEnabled();
        bluetoothSwitch.setChecked(isEnabled);

        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    BTAdaptor.enable();
                }
                else {
                    BTAdaptor.disable();
                }
            }
        });

        //Setup click listener for "find devices"
        findDevicesBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                findDevices();
            }
        });
        findDevices();
    }

    private void findDevices(){
        checkBTState();
        statusText.setText(" ");
        devicesArrayAdaptor.clear();

        Set<BluetoothDevice> pairedDevices = BTAdaptor.getBondedDevices();
        if (pairedDevices.size() > 0){
            for (BluetoothDevice device : pairedDevices) {
                devicesArrayAdaptor.add(device.getName() + "\n address" + device.getAddress());
            }
        }
        else{
            statusText.setText("No paired devices found");
        }
    }

    private void checkBTState(){
        BTAdaptor = BluetoothAdapter.getDefaultAdapter();

        if (BTAdaptor == null){
            Toast.makeText(getBaseContext(), "Device does not have bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (!BTAdaptor.isEnabled()) {
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBTIntent, 1);
            }

        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(mReceiver);
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
}
