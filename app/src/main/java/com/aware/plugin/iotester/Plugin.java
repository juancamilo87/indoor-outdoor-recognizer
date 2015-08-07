package com.aware.plugin.iotester;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Barometer;
import com.aware.Light;
import com.aware.Magnetometer;
import com.aware.Network;
import com.aware.Proximity;
import com.aware.Telephony;
import com.aware.WiFi;
import com.aware.providers.Barometer_Provider;
import com.aware.providers.Light_Provider;
import com.aware.providers.Magnetometer_Provider;
import com.aware.providers.Proximity_Provider;
import com.aware.providers.Telephony_Provider;
import com.aware.utils.Aware_Plugin;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.aware.plugin.iotester.Provider.IndoorOutdoor_Data;

import net.aksingh.owmjapis.CurrentWeather;
import net.aksingh.owmjapis.OpenWeatherMap;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;

public class Plugin extends Aware_Plugin{


//    public static final String ACTION_AWARE_PLUGIN_IO_RECOGNITION = "ACTION_AWARE_PLUGIN_IO_RECOGNITION";
//    public static final String ACTION_AWARE_LOCATION_TYPE_INDOOR = "ACTION_AWARE_LOCATION_TYPE_INDOOR";
//    public static final String ACTION_AWARE_LOCATION_TYPE_OUTDOOR = "ACTION_AWARE_LOCATION_TYPE_OUTDOOR";
//
//    public static final String EXTRA_LOCATION_TYPE = "location_type";
//    public static final String EXTRA_PROBABILITY = "location_type_probability";
//    public static final String EXTRA_ELAPSED_TIME = "elapsed_time";

    public static ContextProducer context_producer;
    private Context context;

    private SensorsListener sensorsListener;
    private PendingIntent pintent;
    private AlarmManager alarm;
    private Classifier classifier;
    private String location_type;
    private int location_type_probability;

    private float previous_proximity;
    private float previous_luminance;

    public class SensorsListener extends BroadcastReceiver {

        private Context sContext;


        private float pressure;
        private float mag_x;
        private float mag_y;
        private float mag_z;
        private int internetType;
        private int gsm_sig_strength;
        private int gsm_towers;
        private int wifi_ap;
        private double latitude;
        private double longitude;
        private double speed;
        private int location_accuracy;
        private int satellites;
        private float weather;
        private String partOfDay;

        private boolean barometerSensor = false;
        private boolean magnetometerSensor = false;
        private boolean networkSensor = false;
        private boolean telephonySensor = false;
        private boolean wifiSensor = false;
        private boolean gpsSensor = false;
        private boolean weatherSensor = false;
        private boolean weatherStarted = false;

        private boolean hasBarometer;
        private boolean hasMagnetometer;

        private long startGathering;
        private long waitingTime = 20*1000;

        private Handler handler;
        private Runnable calculateLocationType;
        private LocationManager locationManager;
        private LocationListener locationListener;

        public SensorsListener(){
            PackageManager manager = getPackageManager();
            hasBarometer = manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER);
            hasMagnetometer = manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS);
            barometerSensor = !hasBarometer;
            magnetometerSensor = !hasMagnetometer;

            handler = new Handler();

            locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            locationListener  = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.e("My sensors", "new gps location");
                    if(!gpsSensor&&startGathering>0)
                    {
                        if(Calendar.getInstance().getTimeInMillis()-startGathering>waitingTime*2)
                        {
                            gpsSensor = true;
                            Log.d("My sensors:", "GPS Data stored");
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            if(!weatherStarted){
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                                getWeatherData();
                                weatherStarted = true;
                            }
                            speed = location.getSpeed();
                            location_accuracy = Math.round(location.getAccuracy());
                            satellites = (int) location.getExtras().get("satellites");

                            //Toast.makeText(sContext,"Visible satellites: "+satellites,Toast.LENGTH_SHORT).show();

                            locationManager.removeUpdates(this);
                        }
                        else
                        {
                            if(!weatherStarted){
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                                getWeatherData();
                                weatherStarted = true;
                            }
                        }

                    }
                }
                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {
                }
                @Override
                public void onProviderEnabled(String s) {
                }
                @Override
                public void onProviderDisabled(String s) {
                }
            };



            calculateLocationType = new Runnable() {
                @Override
                public void run() {
                    if(allDataGathered())
                    {
                        Log.d("My sensors", "Calculating location type...");
                        calculateLocationType();
                    }
                    else
                    {
                        if(startGathering>0&&Calendar.getInstance().getTimeInMillis()-startGathering>waitingTime*3)
                        {
                            Log.e("My sensors", "Data gathering timed out, mocking data...");
                            calculateLocationType();
                            turnSensorsOff();
                        }
                        else
                        {
                            handler.postDelayed(this,2000);
                        }
                    }
                }
            };
        }

        private void resetData(){
            startGathering = 0;
            barometerSensor = !hasBarometer;
            pressure = 0;
            magnetometerSensor = !hasMagnetometer;
            mag_x = 0;
            mag_y = 0;
            mag_z = 0;
            networkSensor = false;
            internetType = 0;
            telephonySensor = false;
            gsm_sig_strength = 0;
            gsm_towers = 0;
            wifiSensor = false;
            wifi_ap = 0;
            gpsSensor = false;
            latitude = 0;
            longitude = 0;
            speed = 0;
            satellites = 0;
            weather = 0;
            partOfDay = null;
            weatherSensor = false;
            weatherStarted = false;

        }

        private boolean allDataGathered(){
            boolean result = false;
            if(barometerSensor && magnetometerSensor && wifiSensor && gpsSensor){
                //Log.d("My sensors","Start: "+ startGathering + " - Waiting: " + waitingTime + " - Time: " + Calendar.getInstance().getTimeInMillis());
                if(Calendar.getInstance().getTimeInMillis()-startGathering > waitingTime)
                {
                    if(!networkSensor){
                        //Log.d("My sensors", "Check network data manually");
                        networkSensor = true;
                        manualNetworkDataCheck();
                    }
                    if(!telephonySensor){
                        //Log.d("My sensors", "Check telephony data manually");
                        telephonySensor = true;
                        manualTelephonyDataCheck();

                    }
                    if(networkSensor&&telephonySensor&&weatherSensor)
                    {
                        result = true;
                    }
                    else
                    {
                        Log.d("My sensors","One sensor hasn't finished");
                        if(Calendar.getInstance().getTimeInMillis()-startGathering>waitingTime*2){
                            if(!networkSensor){
                                Log.d("My sensors","Network data mocked");
                                internetType = -1;
                                networkSensor = true;
                                Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);
                                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                                sContext.sendBroadcast(applySettings);
                            }
                            if(!telephonySensor){
                                Log.d("My sensors","Telephony data mocked");
                                gsm_sig_strength = -1;
                                gsm_towers = -1;
                                telephonySensor = true;
                                Aware.setSetting(sContext, Aware_Preferences.STATUS_TELEPHONY, false);
                                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                                sContext.sendBroadcast(applySettings);
                            }
                            if(!weatherSensor){
                                Log.d("My sensors","Weather data mocked");
                                weatherSensor = true;
                                weather = -1;
                                result = true;
                            }
                        }
                    }

                }
            }
            else if(!gpsSensor&&Calendar.getInstance().getTimeInMillis()-startGathering>waitingTime*2.5){
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(location!=null)
                {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    speed = -1;
                    getWeatherData();
                }
                else
                {
                    weather=-1;
                    speed=-1;
                    partOfDay = "unknown";
                }
                location_accuracy = 1000;
                satellites = 0;
                gpsSensor = true;
                locationManager.removeUpdates(locationListener);
                Log.d("My sensors:", "GPS Data mocked");
            }

            return result;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            //Toast.makeText(context, intent.toString(), Toast.LENGTH_SHORT).show();
            sContext = context;
            //Toast.makeText(context,intent.getAction(),Toast.LENGTH_SHORT).show();


            switch (intent.getAction()){
                case Barometer.ACTION_AWARE_BAROMETER:
                    processBarometerData(intent);
                    break;

                case Magnetometer.ACTION_AWARE_MAGNETOMETER:
                    processMagnetometerData(intent);
                    break;

                case Network.ACTION_AWARE_INTERNET_AVAILABLE:
                    processNetworkData(intent);
                    break;

                case Telephony.ACTION_AWARE_TELEPHONY:
                    processTelephonyData();
                    break;

                case WiFi.ACTION_AWARE_WIFI_SCAN_ENDED:
                    processWifiData();
                    break;
            }

        }

        private void processBarometerData(Intent intent){
            ContentValues extras = (ContentValues) intent.getExtras().get(Barometer.EXTRA_DATA);

            if(!barometerSensor)
            {
                if(startGathering==0)
                {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);
                    handler.postDelayed(calculateLocationType,2000);
                    startGathering = Calendar.getInstance().getTimeInMillis();
                }
                //Remove Label
                Log.d("My sensors", "Barometer Data gathered");
                barometerSensor = true;
                //Gather other data
                pressure = (float) extras.get(Barometer_Provider.Barometer_Data.AMBIENT_PRESSURE);


                //Turn sensor off
                Aware.setSetting(sContext, Aware_Preferences.STATUS_BAROMETER, false);
                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                sContext.sendBroadcast(applySettings);
            }
        }

        private void processMagnetometerData(Intent intent){
            ContentValues extras = (ContentValues) intent.getExtras().get(Magnetometer.EXTRA_DATA);


            if(!magnetometerSensor)
            {
                if(startGathering==0)
                {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);
                    handler.postDelayed(calculateLocationType,2000);
                    startGathering = Calendar.getInstance().getTimeInMillis();
                }
                //Remove Label
                Log.d("My sensors", "Magnetometer Data gathered");
                magnetometerSensor = true;
                //Gather other data
                mag_x = (float) extras.get(Magnetometer_Provider.Magnetometer_Data.VALUES_0);
                mag_y = (float) extras.get(Magnetometer_Provider.Magnetometer_Data.VALUES_1);
                mag_z = (float) extras.get(Magnetometer_Provider.Magnetometer_Data.VALUES_2);


                //Turn sensor off
                Aware.setSetting(sContext, Aware_Preferences.STATUS_MAGNETOMETER, false);
                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                sContext.sendBroadcast(applySettings);
            }
        }

        private void processNetworkData(Intent intent){

            if(!networkSensor&&startGathering>0)
            {
                Log.d("My sensors", "Network data stored");
                internetType = (int) intent.getExtras().get(Network.EXTRA_ACCESS);
                networkSensor =  true;
                //Turn sensor off
                Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);
                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                sContext.sendBroadcast(applySettings);
            }
        }

        private void processTelephonyData() {
            if(!telephonySensor&&startGathering>0)
            {

                Cursor gsm_data = getContentResolver().query(Telephony_Provider.GSM_Data.CONTENT_URI, null,null,null, Telephony_Provider.GSM_Data.TIMESTAMP + " DESC");
                if(gsm_data!=null && gsm_data.moveToFirst())
                {
                    Log.d("My sensors", "Telephony data gathered");
                    telephonySensor = true;
                    gsm_sig_strength = gsm_data.getInt(gsm_data.getColumnIndex(Telephony_Provider.GSM_Data.SIGNAL_STRENGTH));
                    getCellTowers();
                    //Turn sensor off
                    Aware.setSetting(sContext, Aware_Preferences.STATUS_TELEPHONY, false);
                    Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                    sContext.sendBroadcast(applySettings);
                    gsm_data.close();
                }

            }
        }

        private void processWifiData() {

            if(!wifiSensor) {
                //Remove Label
                if(startGathering==0)
                {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,locationListener);
                    handler.postDelayed(calculateLocationType,2000);
                    startGathering = Calendar.getInstance().getTimeInMillis();
                }

                Log.d("My sensors", "Wifi data gathered");
                wifiSensor = true;

                WifiManager wifiManager = (WifiManager) sContext.getSystemService(Context.WIFI_SERVICE);
                if(wifiManager!=null)
                {
                    wifi_ap = wifiManager.getScanResults().size();
                }
                else
                {
                    wifi_ap = 0;
                }
                Log.d("My sensors","Wifi ap: "+wifi_ap);
                Aware.setSetting(sContext, Aware_Preferences.STATUS_WIFI, false);

                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                sContext.sendBroadcast(applySettings);
                WifiManager wManager = (WifiManager)sContext.getSystemService(Context.WIFI_SERVICE);
                if(Aware.getSetting(sContext, "wifiStatus").equals("true"))
                {
                    if(wManager!=null)
                        wManager.setWifiEnabled(true);
                }
                else
                {
                    if(wManager!=null)
                        wManager.setWifiEnabled(false);
                }

            }
        }

        private void manualNetworkDataCheck(){
            Log.d("My sensors", "Network Data manually gathered");
            networkSensor = true;
            ConnectivityManager cm =
                    (ConnectivityManager)sContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            if(cm!=null)
            {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if(activeNetwork!=null)
                {
                    internetType = activeNetwork.getType();
                }
                else
                {
                    internetType = -1;
                }
            }
            else
                internetType = -1;

            //Turn sensor off
            Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);
            Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
            sContext.sendBroadcast(applySettings);
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
        private void manualTelephonyDataCheck(){
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR2) {

                Log.d("My sensors", "Telephony Data manually gathered");

                TelephonyManager tm = (TelephonyManager) sContext.getSystemService(Context.TELEPHONY_SERVICE);
                gsm_towers = 0;
                int total_strength = 0;
                int tempGsmRegisterdTowers = 0;
                if(tm!=null&&tm.getAllCellInfo()!=null)
                {
                    for (final CellInfo info : tm.getAllCellInfo()) {
                        gsm_towers++;
                        if(info.isRegistered())
                        {
                            if(info instanceof  CellInfoGsm)
                                total_strength += ((CellInfoGsm) info).getCellSignalStrength().getAsuLevel();
                            else if(info instanceof CellInfoCdma)
                                total_strength += ((CellInfoCdma) info).getCellSignalStrength().getAsuLevel();
                            else if(info instanceof CellInfoLte)
                                total_strength += ((CellInfoLte) info).getCellSignalStrength().getAsuLevel();
                            else if(info instanceof CellInfoWcdma)
                                total_strength += ((CellInfoWcdma) info).getCellSignalStrength().getAsuLevel();

                            tempGsmRegisterdTowers++;
                        }
                    }
                    if(tempGsmRegisterdTowers>0)
                    {
                        gsm_sig_strength = total_strength/tempGsmRegisterdTowers;
                    }
                    else
                    {
                        gsm_sig_strength = 0;
                    }

                }
                else
                {
                    gsm_towers = -1;
                    gsm_sig_strength = -1;
                }

                Aware.setSetting(sContext, Aware_Preferences.STATUS_TELEPHONY, false);
                Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
                sContext.sendBroadcast(applySettings);
                telephonySensor = true;
            }
            else
            {
                telephonySensor = false;
            }

        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        private void getCellTowers(){
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR1) {
                TelephonyManager tm = (TelephonyManager) sContext.getSystemService(Context.TELEPHONY_SERVICE);
                gsm_towers = 0;
                if(tm!=null&&tm.getAllCellInfo()!=null)
                {
                    for (final CellInfo info : tm.getAllCellInfo()) {
                        gsm_towers++;
                    }
                }
                else
                    gsm_towers = -1;

            }
            else {
                gsm_towers = -1;
            }
        }

        private void getWeatherData(){
            com.luckycatlabs.sunrisesunset.dto.Location location = new com.luckycatlabs.sunrisesunset.dto.Location(""+latitude,""+longitude);
            SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, Calendar.getInstance().getTimeZone());
            String sunrise = calculator.getOfficialSunriseForDate(Calendar.getInstance());
            String sunset = calculator.getOfficialSunsetForDate(Calendar.getInstance());
            String dawn = calculator.getAstronomicalSunriseForDate(Calendar.getInstance());
            String dusk = calculator.getAstronomicalSunsetForDate(Calendar.getInstance());
            String currentTime = new SimpleDateFormat("HH:mm").format(Calendar.getInstance().getTime());
//            Log.d("My sensors","Dawn: " +dawn);
//            Log.d("My sensors","Sunrise: " +sunrise);
//            Log.d("My sensors","Sunset: " +sunset);
//            Log.d("My sensors","Dusk: " +dusk);
//            Log.d("My sensors","Current time: " + currentTime);

            SimpleDateFormat parser = new SimpleDateFormat("HH:mm");
            try{

                if(parser.parse(currentTime).before(parser.parse(dawn))){
                    partOfDay = "night";
                }
                else if(parser.parse(currentTime).before(parser.parse(sunrise)))
                {
                    partOfDay = "twilight";
                }
                else if(parser.parse(currentTime).before(parser.parse(sunset)))
                {
                    partOfDay = "day";
                }
                else if(parser.parse(currentTime).before(parser.parse(dusk)))
                {
                    partOfDay = "twilight";
                }
                else
                {
                    partOfDay = "night";
                }
            }catch (Exception e){
                partOfDay = "unknown";
            }
            Log.d("My sensors", "Part of day: " + partOfDay);

            Runnable getWeather = new Runnable() {
                @Override
                public void run() {
                    try {
                        OpenWeatherMap owm = new OpenWeatherMap("");
                        CurrentWeather cwd = owm.currentWeatherByCoordinates((float) latitude, (float) longitude);
                            if (cwd.isValid()) {
                                if (cwd.hasCloudsInstance() && cwd.hasWeatherInstance()) {
                                    weather = cwd.getCloudsInstance().getPercentageOfClouds();
                                } else {
                                    weather = -1;
                                }
                            } else {
                                weather = -1;
                            }
                    }
                    catch(Exception e){
                        Log.e("My sensors","Error gathering weather data", e);
                            weather = -1;
                    }
                        weatherSensor = true;
                }
            };

            Thread newThread = new Thread(getWeather);
            newThread.start();
        }

        private void turnSensorsOff(){
            Log.d("My sensors","Sensors turning off after location detection");
            //BAROMETER
            Aware.setSetting(sContext, Aware_Preferences.STATUS_BAROMETER, false);
//
//        //LIGHT
            //Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
//
            //MAGNETOMETER
            Aware.setSetting(sContext, Aware_Preferences.STATUS_MAGNETOMETER, false);

            //NETWORK
            Aware.setSetting(sContext, Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);

            //PROXIMITY
            //Aware.setSetting(context, Aware_Preferences.STATUS_PROXIMITY, false);

            //TELEPHONY
            Aware.setSetting(sContext, Aware_Preferences.STATUS_TELEPHONY, false);

            //WIFI
            Aware.setSetting(sContext, Aware_Preferences.STATUS_WIFI, false);

//        //GPS
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_GPS, false);
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_NETWORK, false);


            Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
            sContext.sendBroadcast(applySettings);

            WifiManager wManager = (WifiManager)sContext.getSystemService(Context.WIFI_SERVICE);
            if(Aware.getSetting(sContext, "wifiStatus").equals("true"))
            {
                if(wManager!=null)
                    wManager.setWifiEnabled(true);
            }
            else
            {
                if(wManager!=null)
                    wManager.setWifiEnabled(false);
            }
        }

        private void calculateLocationType(){
            turnSensorsOff();

            ArrayList<Attribute> attributes = new ArrayList<>();
            Attribute aPressure = new Attribute("pressure");
            Attribute aLuminance = new Attribute("luminance");
            Attribute aSpeed = new Attribute("speed");
            Attribute aLocAccuracy = new Attribute("location_accuracy");
            Attribute aMagx = new Attribute("mag_x");
            Attribute aMagy = new Attribute("mag_y");
            Attribute aMagz = new Attribute("mag_z");
            List net_typ_vals = new ArrayList(7);
            net_typ_vals.add("-1");
            net_typ_vals.add("0");
            net_typ_vals.add("1");
            net_typ_vals.add("2");
            net_typ_vals.add("3");
            net_typ_vals.add("4");
            net_typ_vals.add("5");
            Attribute aNetType = new Attribute("network_type", net_typ_vals);
            Attribute aProximity = new Attribute("proximity");
            Attribute aSigStrength = new Attribute("telephony_signal_strength");
            Attribute aCellTowers = new Attribute("telephony_cell_towers");
            Attribute aWifiAp = new Attribute("wifi_ap");
            Attribute aSatellites = new Attribute("gps_satellites");
            List part_day_vals = new ArrayList(4);
            part_day_vals.add("night");
            part_day_vals.add("twilight");
            part_day_vals.add("day");
            part_day_vals.add("unknown");
            Attribute aPartDay = new Attribute("part_of_day", part_day_vals);
            Attribute aWeather = new Attribute("cloud_percentage");
            List loc_type_vals = new ArrayList(2);
            loc_type_vals.add("Indoor");
            loc_type_vals.add("Outdoor");
            Attribute aLocType = new Attribute("location_type", loc_type_vals);

            attributes.add(aPressure);
            attributes.add(aLuminance);
            attributes.add(aSpeed);
            attributes.add(aLocAccuracy);
            attributes.add(aMagx);
            attributes.add(aMagy);
            attributes.add(aMagz);
            attributes.add(aNetType);
            attributes.add(aProximity);
            attributes.add(aSigStrength);
            attributes.add(aCellTowers);
            attributes.add(aWifiAp);
            attributes.add(aSatellites);
            attributes.add(aPartDay);
            attributes.add(aWeather);
            attributes.add(aLocType);

            Instances instances = new Instances("new_data",attributes,0);
            instances.setClass(aLocType);

            Instance values = new DenseInstance(15);
            values.setValue(attributes.get(0),pressure);
            values.setValue(attributes.get(1),previous_luminance);
            values.setValue(attributes.get(2),speed);
            values.setValue(attributes.get(3),location_accuracy);
            values.setValue(attributes.get(4),mag_x);
            values.setValue(attributes.get(5),mag_y);
            values.setValue(attributes.get(6),mag_z);
            values.setValue(attributes.get(7),internetType);
            values.setValue(attributes.get(8),previous_proximity);
            values.setValue(attributes.get(9),gsm_sig_strength);
            values.setValue(attributes.get(10),gsm_towers);
            values.setValue(attributes.get(11),wifi_ap);
            values.setValue(attributes.get(12),satellites);
            values.setValue(attributes.get(13),partOfDay);
            values.setValue(attributes.get(14),weather);

            values.setDataset(instances);

            int max = 0;
            try {
                double[] result = classifier.distributionForInstance(values);
                for(int i = 0; i < result.length; i++)
                    if(result[i] > result[max])
                        max = i;
                Aware.setSetting(sContext, "location_type", instances.classAttribute().value(max));
                location_type = instances.classAttribute().value(max);
                location_type_probability = (int) Math.round(result[max]*100);

                ContentValues data = new ContentValues();

                data.put(Provider.IndoorOutdoor_Data.TIMESTAMP, System.currentTimeMillis());
                data.put(Provider.IndoorOutdoor_Data.DEVICE_ID, Aware.getSetting(sContext, Aware_Preferences.DEVICE_ID));
                data.put(Provider.IndoorOutdoor_Data.PRESSURE, pressure);
                data.put(Provider.IndoorOutdoor_Data.LUMINANCE, previous_luminance);
                data.put(Provider.IndoorOutdoor_Data.LATITUDE, latitude);
                data.put(Provider.IndoorOutdoor_Data.LONGITUDE, longitude);
                data.put(Provider.IndoorOutdoor_Data.SPEED, speed);
                data.put(Provider.IndoorOutdoor_Data.LOCATION_ACCURACY, location_accuracy);
                data.put(Provider.IndoorOutdoor_Data.MAG_X, mag_x);
                data.put(Provider.IndoorOutdoor_Data.MAG_Y, mag_y);
                data.put(Provider.IndoorOutdoor_Data.MAG_Z, mag_z);
                data.put(Provider.IndoorOutdoor_Data.NETWORK_TYPE, internetType);
                data.put(Provider.IndoorOutdoor_Data.PROXIMITY, previous_proximity);
                data.put(Provider.IndoorOutdoor_Data.TELEPHONY_SIGNAL_STRENGTH, gsm_sig_strength);
                data.put(Provider.IndoorOutdoor_Data.TELEPHONY_CELL_TOWERS, gsm_towers);
                data.put(Provider.IndoorOutdoor_Data.WIFI_AP, wifi_ap);
                data.put(Provider.IndoorOutdoor_Data.GPS_SATELLITES, satellites);
                data.put(Provider.IndoorOutdoor_Data.PART_OF_DAY, partOfDay);
                data.put(Provider.IndoorOutdoor_Data.CLOUD_PERCENTAGE, weather);
                data.put(IndoorOutdoor_Data.LOCATION_TYPE_GUESS, location_type);
                data.put(IndoorOutdoor_Data.LOCATION_TYPE_PROBABILITY, location_type_probability);
                String realLocation = Aware.getSetting(sContext,"location");
                data.put(IndoorOutdoor_Data.LOCATION_TYPE, realLocation);


                getContentResolver().insert(IndoorOutdoor_Data.CONTENT_URI, data);





            } catch (Exception e) {
                Log.e("Result","Unable to detect location type", e);

            }


            resetData();
            //Unregister receiver
            try{
                sContext.unregisterReceiver(sensorsListener);
            }catch(Exception e){

            }
            Log.d("Results","Sensing finished");
        }

    }




    @Override
    public void onCreate() {
        super.onCreate();

        previous_proximity = -1;
        previous_luminance = -1;

        context = this;
        //Initialize our plugin's settings
        if( Aware.getSetting(this, Settings.STATUS_PLUGIN_TEMPLATE).length() == 0 ) {
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, true);
        }
        TAG = "Indoor Outdoor Recognition";


        Aware.setSetting(this, Aware_Preferences.STATUS_PROXIMITY, true);
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_PROXIMITY, 200000);
        Aware.setSetting(this, Aware_Preferences.STATUS_LIGHT, true);
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_LIGHT, 200000);
        //File modelFile = new File("res/iomodelrf.model");
        InputStream is = this.getResources().openRawResource(R.raw.random_forest);
        //InputStream is = new FileInputStream(modelFile);

        //InputStream is = Plugin.class.getClassLoader().getResourceAsStream("iomodelrf.model");
        try {

            classifier = (Classifier) SerializationHelper.read(is);
        }
        catch (Exception e) {
            e.printStackTrace();
        }


        sensorsListener = new SensorsListener();

        IntentFilter iFilter = new IntentFilter();
        iFilter.addAction(Light.ACTION_AWARE_LIGHT);
        iFilter.addAction(Proximity.ACTION_AWARE_PROXIMITY);
        registerReceiver(liProSensorReceiver, iFilter);

        IntentFilter filter = new IntentFilter();
        filter.addAction("repeatReadingLocationTester");
        registerReceiver(updateLocationReceiver, filter);

        Intent gathererIntent = new Intent();
        gathererIntent.setAction("repeatReadingLocationTester");
        Log.d("Result","Starting Alarm");
        pintent = PendingIntent.getBroadcast(this, 0, gathererIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar cal = Calendar.getInstance();
        alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pintent);
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 5 * 60 * 1000, pintent);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
//                String thisLocation = Aware.getSetting(context,"location");
//
//                if(thisLocation.equals("Indoor"))
//                {
//                    Intent context_indoor_type = new Intent();
//                    context_indoor_type.setAction(ACTION_AWARE_LOCATION_TYPE_INDOOR);
//                    context_indoor_type.putExtra(EXTRA_ELAPSED_TIME, Calendar.getInstance().getTimeInMillis() - lastChange);
//                    sendBroadcast(context_indoor_type);
//                }
//                else if(thisLocation.equals("Outdoor"))
//                {
//                    Intent context_outdoor_type = new Intent();
//                    context_outdoor_type.setAction(ACTION_AWARE_LOCATION_TYPE_OUTDOOR);
//                    context_outdoor_type.putExtra(EXTRA_ELAPSED_TIME, Calendar.getInstance().getTimeInMillis() - lastChange);
//                    sendBroadcast(context_outdoor_type);
//                }
//                Intent context_location_type = new Intent();
//                context_location_type.setAction(ACTION_AWARE_PLUGIN_IO_RECOGNITION);
//                context_location_type.putExtra(EXTRA_LOCATION_TYPE, location_type);
//                context_location_type.putExtra(EXTRA_PROBABILITY, location_type_probability);
//                sendBroadcast(context_location_type);
            }
        };
        context_producer = CONTEXT_PRODUCER;

        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{Provider.IndoorOutdoor_Data.CONTENT_URI};

        //Ask AWARE to apply your settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
        TAG = "Indoor Outdoor Recognition";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");
        if( DEBUG ) Log.d(TAG, "IO Recognition plugin running");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( DEBUG ) Log.d(TAG, "IO Recognition plugin terminated");
        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Deactivate any sensors/plugins you activated here
        //...
        alarm.cancel(pintent);
        turnSensorsOff();
        try{
            unregisterReceiver(sensorsListener);
        }catch(Exception e){

        }
        try{
            unregisterReceiver(liProSensorReceiver);
        }catch(Exception e){

        }
        unregisterReceiver(updateLocationReceiver);

        //Aware.setSetting(this, Aware_Preferences.STATUS_LIGHT, false);

        //Ask AWARE to apply your settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private void turnSensorsOff(){
        Log.d("My sensors","Sensors turning off...");
        //BAROMETER
        Aware.setSetting(this, Aware_Preferences.STATUS_BAROMETER, false);
//
//        //LIGHT
        //Aware.setSetting(context, Aware_Preferences.STATUS_LIGHT, false);
//
        //MAGNETOMETER
        Aware.setSetting(this, Aware_Preferences.STATUS_MAGNETOMETER, false);

        //NETWORK
        Aware.setSetting(this, Aware_Preferences.STATUS_NETWORK_TRAFFIC, false);

        //PROXIMITY
        //Aware.setSetting(context, Aware_Preferences.STATUS_PROXIMITY, false);

        //TELEPHONY
        Aware.setSetting(this, Aware_Preferences.STATUS_TELEPHONY, false);

        //WIFI
        Aware.setSetting(this, Aware_Preferences.STATUS_WIFI, false);

//        //GPS
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_GPS, false);
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_NETWORK, false);


        Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
        this.sendBroadcast(applySettings);

        WifiManager wManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        if(Aware.getSetting(this, "wifiStatus").equals("true"))
        {
            if(wManager!=null)
                wManager.setWifiEnabled(true);
        }
        else
        {
            if(wManager!=null)
                wManager.setWifiEnabled(false);
        }
    }

    private void startSensing(){
        Log.d("Result","Register receiver");
        registerReceiver();
        turnSensorsOn();
    }

    private void turnSensorsOn(){
        Log.d("My sensors","Sensors turning on...");

//        //BAROMETER
        Aware.setSetting(this, Aware_Preferences.STATUS_BAROMETER, true);
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_BAROMETER, 0);
//
//        //LIGHT

//
//        //MAGNETOMETER
        Aware.setSetting(this, Aware_Preferences.STATUS_MAGNETOMETER, true);
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_MAGNETOMETER, 0);
//
//        //NETWORK
        Aware.setSetting(this, Aware_Preferences.STATUS_NETWORK_TRAFFIC, true);
//
//        //PROXIMITY
//
//        //TELEPHONY
        Aware.setSetting(this, Aware_Preferences.STATUS_TELEPHONY, true);
//
//        //WIFI
        WifiManager wManager = (WifiManager)this.getSystemService(Context.WIFI_SERVICE);
        if(wManager!=null) {
            if (wManager.isWifiEnabled()) {
                Aware.setSetting(this, "wifiStatus", "true");
            } else {
                Aware.setSetting(this, "wifiStatus", "false");
            }
            wManager.setWifiEnabled(true);
        }
        Aware.setSetting(this, Aware_Preferences.STATUS_WIFI, true);
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_WIFI, 60);

//        //GPS
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_GPS, true);
//        Aware.setSetting(sContext, Aware_Preferences.STATUS_LOCATION_NETWORK, false);
//        Aware.setSetting(sContext, Aware_Preferences.FREQUENCY_LOCATION_GPS, 0);
//        Aware.setSetting(sContext, Aware_Preferences.MIN_LOCATION_GPS_ACCURACY, 0);

        Intent applySettings = new Intent(Aware.ACTION_AWARE_REFRESH);
        this.sendBroadcast(applySettings);
    }

    private void registerReceiver(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Barometer.ACTION_AWARE_BAROMETER);

        //registerReceiver(sensorsListener,intentFilter);

        //IntentFilter lightFilter = new IntentFilter();
        //intentFilter.addAction(Light.ACTION_AWARE_LIGHT);
        //registerReceiver(sensorsListener,lightFilter);

        //IntentFilter magFilter = new IntentFilter();
        intentFilter.addAction(Magnetometer.ACTION_AWARE_MAGNETOMETER);
        //registerReceiver(sensorsListener,magFilter);

        //IntentFilter netFilter = new IntentFilter();
        intentFilter.addAction(Network.ACTION_AWARE_INTERNET_AVAILABLE);

        //intentFilter.addAction(Proximity.ACTION_AWARE_PROXIMITY);

        intentFilter.addAction(Telephony.ACTION_AWARE_TELEPHONY);

        intentFilter.addAction(WiFi.ACTION_AWARE_WIFI_SCAN_ENDED);

        //intentFilter.addAction(Locations.ACTION_AWARE_LOCATIONS);

        registerReceiver(sensorsListener, intentFilter);
    }


    private UpdateLocation updateLocationReceiver = new UpdateLocation();
    public class UpdateLocation extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals("repeatReadingLocationTester") ) {
                Log.d("Results","Starting sensing");
                startSensing();
            }

        }
    }

    private LightProximityReceiver liProSensorReceiver = new LightProximityReceiver();
    public class LightProximityReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            ContentValues extras;
            switch (intent.getAction()) {
                case Light.ACTION_AWARE_LIGHT:
                    extras = (ContentValues) intent.getExtras().get(Light.EXTRA_DATA);
                    previous_luminance = (float) extras.get(Light_Provider.Light_Data.LIGHT_LUX);
                    break;

                case Proximity.ACTION_AWARE_PROXIMITY:
                    extras = (ContentValues) intent.getExtras().get(Proximity.EXTRA_DATA);
                    previous_proximity = (float) extras.get(Proximity_Provider.Proximity_Data.PROXIMITY);
                    break;
            }
        }
    }

}
