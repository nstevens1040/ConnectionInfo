package org.nanick.connectioninfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
//import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    public TextView[] textViews;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] permissions = new String[]{Manifest.permission.INTERNET,Manifest.permission.ACCESS_NETWORK_STATE,Manifest.permission.READ_PHONE_STATE,Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.WRITE_EXTERNAL_STORAGE};
        while(
                ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||ActivityCompat.checkSelfPermission(this, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_GRANTED || (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ){
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
        }
        setContentView(R.layout.activity_main);
        textViews = new TextView[]{
                (TextView) findViewById(R.id.rsrp),
                (TextView) findViewById(R.id.downloadspeed),
                (TextView) findViewById(R.id.ta),
                (TextView) findViewById(R.id.mcc),
                (TextView) findViewById(R.id.mnc),
                (TextView) findViewById(R.id.lac),
                (TextView) findViewById(R.id.cellid),
                (TextView) findViewById(R.id.enodeb),
                (TextView) findViewById(R.id.rat),
                (TextView) findViewById(R.id.latitude),
                (TextView) findViewById(R.id.longitude),
                (TextView) findViewById(R.id.channel),
                (TextView) findViewById(R.id.bandwidth),
                (TextView) findViewById(R.id.pci),
                (TextView) findViewById(R.id.rsrq),
                (TextView) findViewById(R.id.cqi),
                (TextView) findViewById(R.id.ipaddress)
        };
        ConnectionInfo clg = new ConnectionInfo(MainActivity.this,this.textViews);
        clg.start();
    }
}