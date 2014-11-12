package com.cs4720.lightdroid;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


public class WebServiceConsumer extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_service_consumer);

        SeekBar seekBar = (SeekBar) findViewById(R.id.intensityBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                sendPostInternal();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new SoundMeter(), 0, 50);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.web_service_consumer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void sendPostInternal() {
        EditText text = (EditText) findViewById(R.id.ipAddr);
        String ipAddr = text.getText().toString();

        new SendData(-1.0).execute(ipAddr);
    }

    public void sendPost (View view) {
        EditText text = (EditText) findViewById(R.id.ipAddr);
        String ipAddr = text.getText().toString();

        new SendData(-1.0).execute(ipAddr);

    }

    class SendData extends AsyncTask<String, Void, String> {
        private double micIntensity;
        public SendData(double micIntensity) {
            this.micIntensity = micIntensity;
        }
        @Override
        protected String doInBackground(String... urls) {
            try{
                String ipAddr = urls[0];
                HttpClient httpclient = new DefaultHttpClient();
                if (ipAddr.equals("")) {
                    Toast toast = Toast.makeText(getApplicationContext(), "IP Address not Valid", Toast.LENGTH_LONG);
                    return null;
                }
                HttpPost httppost = new HttpPost("http://" + ipAddr);

                JSONObject jsonMain = new JSONObject();
                JSONObject jsonLight = new JSONObject();
                try{
                    SeekBar intensityBar = (SeekBar) findViewById(R.id.intensityBar);
                    double intensity = (double)intensityBar.getProgress()/100.0;

                    int redValue = Integer.parseInt(((EditText)findViewById(R.id.redValue)).getText().toString());
                    int greenValue = Integer.parseInt(((EditText)findViewById(R.id.greenValue)).getText().toString());
                    int blueValue = Integer.parseInt(((EditText)findViewById(R.id.blueValue)).getText().toString());
                    if(redValue > 255 || redValue<0 || greenValue > 255 || greenValue<0 || blueValue > 255 || blueValue<0) {
                        Toast toast = Toast.makeText(getApplicationContext(), "Colors need to be between 0 and 255", Toast.LENGTH_LONG);
                        return null;
                    }
                    jsonLight.put("lightId", 1);
                    jsonLight.put("red", redValue);
                    jsonLight.put("green", greenValue);
                    jsonLight.put("blue", blueValue);
                    if(micIntensity == -1) {
                        jsonLight.put("intensity", intensity);
                    }
                    else {
                        jsonLight.put("intensity", micIntensity);
                    }

                    JSONArray lightsList = new JSONArray();
                    lightsList.put(jsonLight);

                    jsonMain.put("lights", lightsList);
                    Switch propagateSwitch = (Switch)findViewById(R.id.propagateSwitch);

                    jsonMain.put("propagate", propagateSwitch.isChecked());
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }

                try {
                    // Add your data
                    StringEntity se = new StringEntity( jsonMain.toString());
                    se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));


                    httppost.setEntity(se);

                    return httpclient.execute(httppost).toString();

                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            catch (Exception e) {
                return null;
            }
            return null;
        }
    }

    public class SoundMeter extends TimerTask {

        private AudioRecord ar = null;
        private int minSize;
        public double reference = 0.0002;

        public void start() {
            minSize= AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000,AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,minSize);
            ar.startRecording();
        }

        public void run() {
            double total = 0;
            start();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            double average = getAmplitude();
            stop();
            double pressure = average / 51805.5336; //the value 51805.5336 can be derived from asuming that x=32767=0.6325 Pa and x=1 = 0.00002 Pa (the reference value)
            double db = (20 * Math.log10(pressure / reference));

            db = (db - 50) / 25;

            if (db != Double.POSITIVE_INFINITY || db != Double.NEGATIVE_INFINITY) {
                EditText text = (EditText) findViewById(R.id.ipAddr);
                String ipAddr = text.getText().toString();
                new SendData(db).execute(ipAddr);
            }
        }
        public void stop() {
            if (ar != null) {
                ar.stop();
            }
        }

        public double getAmplitude() {
            short[] buffer = new short[minSize];
            ar.read(buffer, 0, minSize);
            int max = 0;
            for (short s : buffer)
            {
                if (Math.abs(s) > max)
                {
                    max = Math.abs(s);
                }
            }
            return max;
        }

    }
}
