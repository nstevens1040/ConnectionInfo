package org.nanick.connectioninfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.NetworkScanRequest;
import android.telephony.PhoneStateListener;
import android.telephony.RadioAccessSpecifier;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyScanManager;
import android.util.Log;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.ObjectWriter;
//import android.support.v4.app.ActivityCompat;
//import android.support.v7.app.AppCompatActivity;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

public class ConnectionInfo {
    private final Context context;
    private boolean gpsEnabled = false;
    private boolean networkEnabled = false;
    private final LocationListener locationListener;
    //private final PhoneStateListener phoneStateListener;
    private final TelephonyManager telephonyManager;
    private final LocationManager locationManager;
    public TextView[] textViews;
    public File csv;
    public FileWriter csv_writer;
    public ConnectionInfo(Context context, TextView[] results) {
        this.csv = new File(context.getApplicationContext().getExternalFilesDir(null), Integer.valueOf((int) (System.currentTimeMillis() / 1000))+".csv");
        this.textViews = results;
        this.context = context;
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListenerImpl();
        //phoneStateListener = new PhoneStateListenerImpl(this.textViews,this.csv);
        try {
            if (this.csv.createNewFile()) {
                this.csv_writer = new FileWriter(this.csv);
                this.csv_writer.write("Mcc,Mnc,Lac,Rat,Channel,Bandwidth,Pci,Rsrp,Rsrq,Snr,CellID,eNodeB,TA,Latitude,Longitude,IPAddress,DownloadSpeed\n");
                this.csv_writer.close();
                Log.i("ConnectionInfo", "File created.");
            } else {
                Log.e("ConnectionInfo", "Failed to create file.");
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @SuppressLint("MissingPermission")
    public void start() {
        //telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            Log.e("ConnectionInfo", "No location providers enabled.");
        }
    }
    public void stop() {
        //telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        locationManager.removeUpdates(locationListener);
    }
    private class LocationListenerImpl implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                Location currentLocation = location;
                getCellularDetails();
            } else {
                Log.e("ConnectionInfo", "Location is null.");
            }
        }
        @Override
        public void onProviderDisabled(String provider) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                gpsEnabled = false;
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                networkEnabled = false;
            }
        }
        @Override
        public void onProviderEnabled(String provider) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                gpsEnabled = true;
            } else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                networkEnabled = true;
            }
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Do nothing
        }
    }
    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        Location gpsLocation = null;
        Location networkLocation = null;
        if (gpsEnabled) {
            gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (networkEnabled) {
            networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (gpsLocation == null && networkLocation == null) {
            return null;
        } else if (gpsLocation != null && networkLocation != null) {
            long gpsTime = gpsLocation.getTime();
            long networkTime = networkLocation.getTime();
            if (gpsTime > networkTime) {
                return gpsLocation;
            } else {
                return networkLocation;
            }
        } else if (gpsLocation != null) {
            return gpsLocation;
        } else {
            return networkLocation;
        }
    }
    private String getIPAddress() {
        String ha = "";
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                Enumeration<InetAddress> inetAddresses = networkInterfaces.nextElement().getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress nextElement = inetAddresses.nextElement();
                    if (!nextElement.isLoopbackAddress()) {
                        String hostAddress = nextElement.getHostAddress();
                        ha = hostAddress;
                        if(hostAddress != null && !hostAddress.matches("^10.0.0") && nextElement instanceof Inet4Address){
                            return ha;
                        }
                    }
                }
            }
            return ha;
        } catch (SocketException e2) {
            Log.e("CellInfo", "SockedException in getting local IP", e2);
            return ha;
        }
    }
    public class SpeedTestTask extends AsyncTask<Void, Void, Double> {
        String testUrl = "https://beserver.nanick.org:7777/uploads/test.bin";
        byte[] buffer = new byte[32]; //byte[] buffer = new byte[13107200];
        CellInfoObj cio;
        TextView[] textViews;
        public SpeedTestTask(CellInfoObj cellInfoObj, TextView[] tv){
            this.cio = cellInfoObj;
            this.textViews = tv;
            this.textViews[0].setText(this.cio.Rsrp+" dBm");
            if(this.cio.TA == Integer.MAX_VALUE){
                this.textViews[2].setText("N/A");
            } else {
                this.textViews[2].setText(this.cio.TA+"");
            }
            this.textViews[3].setText(this.cio.Mcc+"");
            this.textViews[4].setText(this.cio.Mnc+"");
            this.textViews[5].setText(this.cio.Lac+"");
            this.textViews[6].setText(this.cio.CellID+"");
            this.textViews[7].setText(this.cio.eNodeB+"");
            this.textViews[8].setText(this.cio.Rat+"");
            this.textViews[9].setText(this.cio.Latitude+"");
            this.textViews[10].setText(this.cio.Longitude+"");
            this.textViews[11].setText(this.cio.Channel+"");
            this.textViews[12].setText(this.cio.Bandwidth+" Mhz");
            this.textViews[13].setText(this.cio.Pci+"");
            this.textViews[14].setText(this.cio.Rsrq+" dBm");
            this.textViews[15].setText(this.cio.Cqi+"");
            this.textViews[16].setText(this.cio.IPAddress);
        }
        @Override
        protected Double doInBackground(Void... params) {
            try {
                return performSpeedTest();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(Double mbps) {
            if (mbps != null) {
                this.textViews[1].setText(new DecimalFormat("0.00").format(mbps)+" Mbps");
                StringBuilder sb = new StringBuilder();
                sb.append(this.cio.Mcc+",");
                sb.append(this.cio.Mnc+",");
                sb.append(this.cio.Lac+",");
                sb.append(this.cio.Rat+",");
                sb.append(this.cio.Channel+",");
                sb.append(this.cio.Bandwidth+",");
                sb.append(this.cio.Pci+",");
                sb.append(this.cio.Rsrp+",");
                sb.append(this.cio.Rsrq+",");
                sb.append(this.cio.Cqi+",");
                sb.append(this.cio.CellID+",");
                sb.append(this.cio.eNodeB+",");
                sb.append(this.cio.TA+",");
                sb.append(this.cio.Latitude+",");
                sb.append(this.cio.Longitude+",");
                sb.append(this.cio.IPAddress+",");
                sb.append(new DecimalFormat("0.00").format(mbps)+",\n");
                String csv_string = sb.toString();
                Log.i("CellInfo",new DecimalFormat("0.00").format(mbps)+" Mbps download");
                try {
                    Log.i("ConnectionInfo",JSONConvector.toJSON(this.cio));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                try {
                    csv_writer = new FileWriter(csv,true);
                    csv_writer.write(csv_string);
                    csv_writer.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                this.textViews[1].setText("0 Mbps");
            }
        }
        private Double performSpeedTest() throws IOException {
            Double mbps = 0.0;
            InputStream inputStream = ((HttpURLConnection) new URL(this.testUrl).openConnection()).getInputStream();
            int bytesRead;
            int totalBytesRead = 0;
            Double startTime = Double.valueOf(System.currentTimeMillis());
            while ((bytesRead = inputStream.read(this.buffer)) != -1) {
                totalBytesRead += bytesRead;
            }
            Double endTime = Double.valueOf(System.currentTimeMillis());
            Double totalSeconds = (endTime - startTime) / 1000;
            mbps = 20 / totalSeconds;
            inputStream.close();
            return mbps;
        }
    }
    public class NScanExec implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }
    public class CellInfoObj {
        public CellInfoObj(){
        }
        public int Mcc;
        public int Mnc;
        public int Lac;
        public String Rat;
        public int Channel;
        public int Bandwidth;
        public int Pci;
        public int Rsrp;
        public int Rsrq;
        public int Snr;
        public int CellID;
        public int eNodeB;
        public int TA;
        public double Latitude;
        public double Longitude;
        public double DownloadSpeed;
        public String IPAddress;
        public String Cqi;
    }
    public String parseNetworkType(int networkType){
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                return "1xRTT";
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "IDEN";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                return "EVDO_0";
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                return "EVDO_A";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                return "EVDO_B";
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "EHRPD";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPAP";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            default:
                return "Unknown";
        }
    }

    private class ScanBack extends TelephonyScanManager.NetworkScanCallback {
        @Override
        public void onResults(List<CellInfo> cellInfo) {
            for (CellInfo info : cellInfo) {
                if (info instanceof CellInfoLte) {
                    // Handle LTE cell info
                    CellInfoLte cellInfoLte = (CellInfoLte) info;
                    CellIdentityLte cellId = (CellIdentityLte)cellInfoLte.getCellIdentity();
                    CellSignalStrengthLte signalStrengthLte = cellInfoLte.getCellSignalStrength();
                    StringJoiner sj = new StringJoiner(", ");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        IntStream.of(cellId.getBands()).forEach(x -> sj.add(String.valueOf(x)));
                    }
                    //Log.i("ConnectionInfo", "bands: " + sj.toString());
                    CellInfoObj cio = new CellInfoObj();
                    cio.Cqi = sj.toString();
                    cio.Rat = "Lte";
                    cio.Lac = cellId.getTac();
                    cio.Channel = cellId.getEarfcn();
                    cio.Bandwidth = cellId.getBandwidth() / 1000;
                    cio.Pci = cellId.getPci();
                    cio.Rsrp = signalStrengthLte.getRsrp();
                    cio.Rsrq = signalStrengthLte.getRsrq();
                    cio.Snr = signalStrengthLte.getRssnr();
                    cio.CellID = cellId.getCi();
                    cio.eNodeB = Math.floorDiv(cio.CellID,256);
                    cio.TA = signalStrengthLte.getTimingAdvance();
                    cio.IPAddress = getIPAddress();
                    try {
                        Log.i("ConnectionInfo",JSONConvector.toJSON(cio));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    // Access cell info properties as needed
                }
            }
        }
        @Override
        public void onComplete() {
            Log.i("ConnectionInfo","Scan complete");
        }
        @Override
        public void onError(int error) {
            Log.e("ConnectionInfo","Scan error: "+error);
        }
    }
    @SuppressLint({"MissingPermission", "NewApi"})
    private void getCellularDetails() {
        CellInfoObj cio = new CellInfoObj();
        Location location = getLastKnownLocation();
        if(location != null){
            cio.Latitude = location.getLatitude();
            cio.Longitude = location.getLongitude();
            TelephonyManager telephonyManager = (TelephonyManager)this.context.getSystemService(Context.TELEPHONY_SERVICE);
            String networkOperator = telephonyManager.getNetworkOperator();
            cio.Mcc = Integer.parseInt(networkOperator.substring(0, 3));
            cio.Mnc = Integer.parseInt(networkOperator.substring(3));
            @SuppressLint("MissingPermission") CellInfo cellInfo = telephonyManager.getAllCellInfo().get(0);
            /*int[] bandlist = new int[]{ AccessNetworkConstants.EutranBand.BAND_1,AccessNetworkConstants.EutranBand.BAND_2,AccessNetworkConstants.EutranBand.BAND_3,AccessNetworkConstants.EutranBand.BAND_4,AccessNetworkConstants.EutranBand.BAND_5,AccessNetworkConstants.EutranBand.BAND_6,AccessNetworkConstants.EutranBand.BAND_7,AccessNetworkConstants.EutranBand.BAND_8,AccessNetworkConstants.EutranBand.BAND_9,AccessNetworkConstants.EutranBand.BAND_10,AccessNetworkConstants.EutranBand.BAND_11,AccessNetworkConstants.EutranBand.BAND_12,AccessNetworkConstants.EutranBand.BAND_13,AccessNetworkConstants.EutranBand.BAND_14,AccessNetworkConstants.EutranBand.BAND_17,AccessNetworkConstants.EutranBand.BAND_18,AccessNetworkConstants.EutranBand.BAND_19,AccessNetworkConstants.EutranBand.BAND_20,AccessNetworkConstants.EutranBand.BAND_21,AccessNetworkConstants.EutranBand.BAND_22,AccessNetworkConstants.EutranBand.BAND_23,AccessNetworkConstants.EutranBand.BAND_24,AccessNetworkConstants.EutranBand.BAND_25,AccessNetworkConstants.EutranBand.BAND_26,AccessNetworkConstants.EutranBand.BAND_27,AccessNetworkConstants.EutranBand.BAND_28,AccessNetworkConstants.EutranBand.BAND_30,AccessNetworkConstants.EutranBand.BAND_31,AccessNetworkConstants.EutranBand.BAND_33,AccessNetworkConstants.EutranBand.BAND_34,AccessNetworkConstants.EutranBand.BAND_35,AccessNetworkConstants.EutranBand.BAND_36,AccessNetworkConstants.EutranBand.BAND_37,AccessNetworkConstants.EutranBand.BAND_38,AccessNetworkConstants.EutranBand.BAND_39,AccessNetworkConstants.EutranBand.BAND_40,AccessNetworkConstants.EutranBand.BAND_41,AccessNetworkConstants.EutranBand.BAND_42,AccessNetworkConstants.EutranBand.BAND_43,AccessNetworkConstants.EutranBand.BAND_44,AccessNetworkConstants.EutranBand.BAND_45,AccessNetworkConstants.EutranBand.BAND_46,AccessNetworkConstants.EutranBand.BAND_47,AccessNetworkConstants.EutranBand.BAND_48,AccessNetworkConstants.EutranBand.BAND_49,AccessNetworkConstants.EutranBand.BAND_50,AccessNetworkConstants.EutranBand.BAND_51,AccessNetworkConstants.EutranBand.BAND_52,AccessNetworkConstants.EutranBand.BAND_53,AccessNetworkConstants.EutranBand.BAND_65,AccessNetworkConstants.EutranBand.BAND_66,AccessNetworkConstants.EutranBand.BAND_68,AccessNetworkConstants.EutranBand.BAND_70,AccessNetworkConstants.EutranBand.BAND_71,AccessNetworkConstants.EutranBand.BAND_72,AccessNetworkConstants.EutranBand.BAND_73,AccessNetworkConstants.EutranBand.BAND_74,AccessNetworkConstants.EutranBand.BAND_85,AccessNetworkConstants.EutranBand.BAND_87,AccessNetworkConstants.EutranBand.BAND_88};
            RadioAccessSpecifier[] ras = new RadioAccessSpecifier[]{ new RadioAccessSpecifier(13,bandlist,new int[]{}) };
            NetworkScanRequest nsr = new NetworkScanRequest(0,ras,0,20,false,0,new ArrayList<String>(){});
            telephonyManager.requestNetworkScan(TelephonyManager.INCLUDE_LOCATION_DATA_FINE,nsr,new NScanExec(),new ScanBack());*/
            if (cellInfo instanceof CellInfoLte) {
                CellIdentityLte cellId = (CellIdentityLte)cellInfo.getCellIdentity();
                CellSignalStrengthLte signalStrengthLte = ((CellInfoLte) cellInfo).getCellSignalStrength();
                StringJoiner sj = new StringJoiner(", ");
                IntStream.of(cellId.getBands()).forEach(x -> sj.add(String.valueOf(x)));
                //Log.i("ConnectionInfo", "bands: " + sj.toString());
                cio.Cqi = sj.toString();
                cio.Rat = parseNetworkType(telephonyManager.getNetworkType());
                cio.Lac = cellId.getTac();
                cio.Channel = cellId.getEarfcn();
                cio.Bandwidth = cellId.getBandwidth() / 1000;
                cio.Pci = cellId.getPci();
                cio.Rsrp = signalStrengthLte.getRsrp();
                cio.Rsrq = signalStrengthLte.getRsrq();
                cio.Snr = signalStrengthLte.getRssnr();
                cio.CellID = cellId.getCi();
                cio.eNodeB = Math.floorDiv(cio.CellID,256);
                cio.TA = signalStrengthLte.getTimingAdvance();
                cio.IPAddress = getIPAddress();
                SpeedTestTask test = new SpeedTestTask(cio,this.textViews);
                test.execute();
            }
        }
    }
}