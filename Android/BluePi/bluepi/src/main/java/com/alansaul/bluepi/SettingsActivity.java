package com.alansaul.bluepi;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;

import java.util.ArrayList;
import java.util.Map;


public class SettingsActivity extends ActionBarActivity {
    private final String TAG="SETTINGS_THREAD";

    ArrayList<ShutterPair> shutterspeedsAL;
    Map<String, Float> shutterspeedsHash;

    NumberPicker shutterspeedPicker;
    NumberPicker durationPicker;
    NumberPicker secondsPicker;
    NumberPicker percentPicker;
    NumberPicker lengthPicker;

    int footageSeconds; //How many seconds of footage we want
    int filmingDurationMinutes; //How many seconds of footage we want
    int percentComplete;
    int barLengthCM;
    float shutterspeed;
    int interval;
    int delay;

    public static final String INTERVAL = "INTERVAL";
    public static final String DELAY = "DELAY";
    public static final String FILMDURATION = "FILMDURATION";
    public static final String FOOTAGESECONDS = "FOOTAGESECONDS";
    public static final String SHUTTERSPEED= "SHUTTERSPEED";
    public static final String PERCENT = "PERCENT";
    public static final String LENGTH = "LENGTH";
    public static final String PREFS_NAME = "BluePiPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Make shutterspeeds
        //FIXME: Import Guava and use BiMap later
        /*
        shutterspeedsHash = new HashMap<String, Float>();
        shutterspeedsHash.put("1/500", 1/500f);
        shutterspeedsHash.put("1/250", 1/250f);
        shutterspeedsHash.put("1/2", 1/2f);
        shutterspeedsHash.put("1\"", 1f);
        shutterspeedsHash.put("5\"", 5f);
        shutterspeedsHash.put("30\"", 5f);
        */

        shutterspeedsAL= new ArrayList<ShutterPair>();
        shutterspeedsAL.add(new ShutterPair("1/500", 1/500f));
        shutterspeedsAL.add(new ShutterPair("1/250", 1/250f));
        shutterspeedsAL.add(new ShutterPair("1/2", 1/2f));
        shutterspeedsAL.add(new ShutterPair("1\"", 1f));
        shutterspeedsAL.add(new ShutterPair("5\"", 5f));
        shutterspeedsAL.add(new ShutterPair("30\"", 30f));

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        footageSeconds = settings.getInt("footageSeconds", 20);
        filmingDurationMinutes = settings.getInt("filmDurationMinutes", 30);
        shutterspeed = settings.getFloat("shutterspeed", 1.0f);
        percentComplete = settings.getInt("percentComplete", 0);
        barLengthCM = settings.getInt("barLengthCM", 150);
        interval = settings.getInt("interval", 3333);
        delay = settings.getInt("delay", 400);
    }

    @Override
    public void onResume() {
        super.onResume();
        secondsPicker = (NumberPicker) findViewById(R.id.secondsPicker);
        durationPicker = (NumberPicker) findViewById(R.id.durationPicker);
        shutterspeedPicker= (NumberPicker) findViewById(R.id.shutterspeedPicker);
        percentPicker = (NumberPicker) findViewById(R.id.percentPicker);
        lengthPicker = (NumberPicker) findViewById(R.id.lengthPicker);

        secondsPicker.setMinValue(1);
        secondsPicker.setMaxValue(60);

        durationPicker.setMinValue(1);
        durationPicker.setMaxValue(5000);

        //String[] shutterKeys = (String[]) shutterspeedsHash.keySet().toArray();
        String[] shutterKeys = new String[shutterspeedsAL.size()];
        for (int i=0; i < shutterspeedsAL.size(); i++){
            shutterKeys[i] = shutterspeedsAL.get(i).key();
        }

        shutterspeedPicker.setMaxValue(shutterKeys.length-1);
        shutterspeedPicker.setMinValue(0);
        shutterspeedPicker.setDisplayedValues(shutterKeys);

        percentPicker.setMinValue(0);
        percentPicker.setMaxValue(100);

        lengthPicker.setMinValue(10);
        lengthPicker.setMaxValue(300);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        filmingDurationMinutes = settings.getInt("filmingDurationMinutes", 60);
        footageSeconds = settings.getInt("footageSeconds", 60);
        shutterspeed = settings.getFloat("shutterspeed", 1.0f);
        percentComplete = settings.getInt("percentComplete", 0);
        barLengthCM = settings.getInt("barLengthCM", 150);


        //FIXME: Lookup shutter index from float
        int oldShutterIndex = 2;

        secondsPicker.setValue(footageSeconds);
        durationPicker.setValue(filmingDurationMinutes);
        shutterspeedPicker.setValue(oldShutterIndex);
        percentPicker.setValue(percentComplete);
        lengthPicker.setValue(barLengthCM);

        Button updateBtn = (Button) findViewById(R.id.updateBtn);

        updateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                returnSettings();
            }
        });
    }

    public void onBackPressed(){
        returnSettings();
        super.onBackPressed();
    }

    protected void returnSettings(){
        Settings settings = calculateRequiredSettings();

        Intent resultIntent = new Intent();
        resultIntent.putExtra(FILMDURATION, filmingDurationMinutes);
        resultIntent.putExtra(FOOTAGESECONDS, footageSeconds);
        resultIntent.putExtra(SHUTTERSPEED, shutterspeed);
        resultIntent.putExtra(PERCENT, percentComplete);
        resultIntent.putExtra(LENGTH, barLengthCM);
        resultIntent.putExtra(INTERVAL, interval);
        resultIntent.putExtra(DELAY, delay);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private Settings calculateRequiredSettings(){
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        filmingDurationMinutes = settings.getInt("filmingDurationMinutes", 30);
        footageSeconds = settings.getInt("footageSeconds", 20);
        shutterspeed = settings.getFloat("shutterspeed", 1.0f);
        percentComplete = settings.getInt("percentComplete", 0);
        barLengthCM = settings.getInt("barLengthCM", 150);
        interval = settings.getInt("interval", 3333);
        delay = settings.getInt("delay", 400);

        Log.d(TAG, Integer.toString(filmingDurationMinutes));
        Log.d(TAG, Integer.toString(footageSeconds));
        Log.d(TAG, Float.toString(shutterspeed));
        Log.d(TAG, Integer.toString(percentComplete));
        Log.d(TAG, Integer.toString(barLengthCM));

        int framerate = 24; // Normal framerate
        // Say it takes 0.5 seconds to respond to a request to take a photo
        double responseTime = 0.5;

        int numPhotos = footageSeconds * framerate;
        double secondsRequiredPerPhoto = shutterspeed + responseTime;
        double totalCaptureTimeSeconds = secondsRequiredPerPhoto * numPhotos;
        double timeLeft = filmingDurationMinutes*60 - totalCaptureTimeSeconds;

        int RPM = 15;
        double circumference= 3; //in CM
        double CMPerMinute = RPM*circumference;
        double CMLeft = barLengthCM*((100-percentComplete)/100);
        double movingSecondsRequired = (CMLeft/CMPerMinute)*60;

        double secondsGivenPerPhoto = (filmingDurationMinutes * 60 - movingSecondsRequired);

        //Sanity check
        Log.d(TAG, "Calculating interval");
        Log.d(TAG, "Seconds required per photo: " + secondsRequiredPerPhoto);
        Log.d(TAG, "Overall photos to take: " + numPhotos);
        Log.d(TAG, "Number of footageMinutes just to take photos " + (numPhotos * secondsRequiredPerPhoto)/60);
        Log.d(TAG, "timeLeft filmDurationMinutes " + Double.toString(timeLeft / 60));
        Log.d(TAG, "Moving filmDurationMinutes required: " + Double.toString(movingSecondsRequired/60));
        Log.d(TAG, "Enough time to atleast take photos " + (timeLeft > movingSecondsRequired));

        if (timeLeft > movingSecondsRequired) {
            delay = (int) Math.round(movingSecondsRequired * 1000 / numPhotos); //In ms
            interval = (int) Math.round(secondsGivenPerPhoto * 1000 / numPhotos); //In ms
            Log.d(TAG, "Interval and delay changed");
        }

        Log.d(TAG, "Interval: " + Integer.toString(interval));
        Log.d(TAG, "Delay: " + Integer.toString(delay));

        Settings s = new Settings();
        s.barLength = barLengthCM;
        s.footageSeconds = footageSeconds;
        s.filmingDurationMinutes = filmingDurationMinutes;
        s.shutterspeed = shutterspeed;
        s.percentageComplete = percentComplete;
        s.delay = delay;
        s.interval = interval;
        return s;
    }

    protected void saveSettings(){
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        footageSeconds = secondsPicker.getValue();
        percentComplete = percentPicker.getValue();
        barLengthCM = lengthPicker.getValue();
        filmingDurationMinutes = durationPicker.getValue();
        int shutterspeedIndex = shutterspeedPicker.getValue();

        shutterspeed = shutterspeedsAL.get(shutterspeedIndex).value();

        if (footageSeconds > 0) {
            editor.putInt("footageSeconds", footageSeconds);
        }
        if ((percentComplete >= 0) && (percentComplete <= 100)){
            editor.putInt("percentComplete", percentComplete);
        }
        if (barLengthCM >= 10){
            editor.putInt("barLengthCM", barLengthCM);
        }

        editor.putFloat("shutterspeed", shutterspeed);
        editor.putInt("filmingDurationMinutes", filmingDurationMinutes);

        Log.d(TAG, "Shutterspeed set to: " + Float.toString(shutterspeed));
        //Commit the settings
        editor.commit();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
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
