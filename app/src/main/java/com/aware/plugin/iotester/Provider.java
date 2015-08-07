package com.aware.plugin.iotester;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

public class Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 9;

    /**
     * Provider authority: com.aware.plugin.ambient_noise.provider.ambient_noise
     */
    public static String AUTHORITY = "com.aware.plugin.iotester.provider.general_data";

    public static final int IO_DATA = 1;
    public static final int IO_DATA_ID = 2;


    public static final String DATABASE_NAME = Environment.getExternalStorageDirectory() + "/AWARE/plugin_iogather.db";

    public static final String[] DATABASE_TABLES = {
            "plugin_iogather"
    };

    public static final String[] TABLES_FIELDS = {
            IndoorOutdoor_Data._ID + " integer primary key autoincrement," +
                    IndoorOutdoor_Data.TIMESTAMP + " real default -1," +
                    IndoorOutdoor_Data.DEVICE_ID + " text default ''," +
                    IndoorOutdoor_Data.PRESSURE + " real default -1," +
                    IndoorOutdoor_Data.LUMINANCE + " real default -1," +
                    IndoorOutdoor_Data.LATITUDE + " real default 0," +
                    IndoorOutdoor_Data.LONGITUDE + " real default 0," +
                    IndoorOutdoor_Data.SPEED + " real default -1," +
                    IndoorOutdoor_Data.LOCATION_ACCURACY + " integer default -1," +
                    IndoorOutdoor_Data.MAG_X + " real default -1," +
                    IndoorOutdoor_Data.MAG_Y + " real default -1," +
                    IndoorOutdoor_Data.MAG_Z + " real default -1," +
                    IndoorOutdoor_Data.NETWORK_TYPE + " real default -1," +
                    IndoorOutdoor_Data.PROXIMITY + " real default -1," +
                    IndoorOutdoor_Data.TELEPHONY_SIGNAL_STRENGTH + " real default -1," +
                    IndoorOutdoor_Data.TELEPHONY_CELL_TOWERS + " integer default -1," +
                    IndoorOutdoor_Data.WIFI_AP + " integer default 0," +
                    IndoorOutdoor_Data.GPS_SATELLITES + " integer default -1," +
                    //Time as long??
                    IndoorOutdoor_Data.PART_OF_DAY + " text default ''," +
                    //Determine data type
                    IndoorOutdoor_Data.CLOUD_PERCENTAGE + " real default -1," +
                    IndoorOutdoor_Data.LOCATION_TYPE_GUESS + " text default ''," +
                    IndoorOutdoor_Data.LOCATION_TYPE_PROBABILITY + " integer default ''," +
                    IndoorOutdoor_Data.LOCATION_TYPE + " text default ''," +
                    "UNIQUE("+ IndoorOutdoor_Data.TIMESTAMP+","+ IndoorOutdoor_Data.DEVICE_ID+")"
    };

    public static final class IndoorOutdoor_Data implements BaseColumns {
        private IndoorOutdoor_Data(){};

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/plugin_iogather");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.iotester";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.iotester";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String PRESSURE = "pressure";
        public static final String PRESSURE_ACCURACY = "pressure_accuracy";
        public static final String LUMINANCE = "luminance";
        public static final String LUMINANCE_ACCURACY = "luminance_accuracy";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String SPEED = "speed";
        public static final String ALTITUDE = "altitude";
        public static final String LOCATION_ACCURACY = "location_accuracy";
        public static final String MAG_X = "mag_x";
        public static final String MAG_Y = "mag_y";
        public static final String MAG_Z = "mag_z";
        public static final String MAG_ACCURACY = "mag_accuracy";
        public static final String NETWORK_TYPE = "network_type";
        public static final String PROXIMITY = "proximity";
        public static final String PROXIMITY_ACCURACY = "proximity_accuracy";
        public static final String TELEPHONY_SIGNAL_STRENGTH = "telephony_signal_strength";
        public static final String TELEPHONY_CELL_TOWERS = "telephony_cell_towers";
        public static final String WIFI_AP = "wifi_ap";
        public static final String GPS_SATELLITES = "gps_satellites";
        public static final String PART_OF_DAY = "part_of_day";
        public static final String CLOUD_PERCENTAGE = "cloud_percentage";
        public static final String LOCATION_TYPE_GUESS = "location_type_guess";
        public static final String LOCATION_TYPE_PROBABILITY = "location_type_probability";
        public static final String LOCATION_TYPE = "location_type";
    }

    private static UriMatcher URIMatcher;
    private static HashMap<String, String> databaseMap;
    private static DatabaseHelper databaseHelper;
    private static SQLiteDatabase database;

    @Override
    public boolean onCreate() {

        AUTHORITY = getContext().getPackageName() + ".provider.general_data";

        URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], IO_DATA);
        URIMatcher.addURI(AUTHORITY, DATABASE_TABLES[0]+"/#", IO_DATA_ID);

        databaseMap = new HashMap<String, String>();
        databaseMap.put(IndoorOutdoor_Data._ID, IndoorOutdoor_Data._ID);
        databaseMap.put(IndoorOutdoor_Data.TIMESTAMP, IndoorOutdoor_Data.TIMESTAMP);
        databaseMap.put(IndoorOutdoor_Data.DEVICE_ID, IndoorOutdoor_Data.DEVICE_ID);
        databaseMap.put(IndoorOutdoor_Data.PRESSURE, IndoorOutdoor_Data.PRESSURE);
        databaseMap.put(IndoorOutdoor_Data.PRESSURE_ACCURACY, IndoorOutdoor_Data.PRESSURE_ACCURACY);
        databaseMap.put(IndoorOutdoor_Data.LUMINANCE, IndoorOutdoor_Data.LUMINANCE);
        databaseMap.put(IndoorOutdoor_Data.LUMINANCE_ACCURACY, IndoorOutdoor_Data.LUMINANCE_ACCURACY);
        databaseMap.put(IndoorOutdoor_Data.LATITUDE, IndoorOutdoor_Data.LATITUDE);
        databaseMap.put(IndoorOutdoor_Data.LONGITUDE, IndoorOutdoor_Data.LONGITUDE);
        databaseMap.put(IndoorOutdoor_Data.SPEED, IndoorOutdoor_Data.SPEED);
        databaseMap.put(IndoorOutdoor_Data.ALTITUDE, IndoorOutdoor_Data.ALTITUDE);
        databaseMap.put(IndoorOutdoor_Data.LOCATION_ACCURACY, IndoorOutdoor_Data.LOCATION_ACCURACY);
        databaseMap.put(IndoorOutdoor_Data.MAG_X, IndoorOutdoor_Data.MAG_X);
        databaseMap.put(IndoorOutdoor_Data.MAG_Y, IndoorOutdoor_Data.MAG_Y);
        databaseMap.put(IndoorOutdoor_Data.MAG_Z, IndoorOutdoor_Data.MAG_Z);
        databaseMap.put(IndoorOutdoor_Data.MAG_ACCURACY, IndoorOutdoor_Data.MAG_ACCURACY);
        databaseMap.put(IndoorOutdoor_Data.NETWORK_TYPE, IndoorOutdoor_Data.NETWORK_TYPE);
        databaseMap.put(IndoorOutdoor_Data.PROXIMITY, IndoorOutdoor_Data.PROXIMITY);
        databaseMap.put(IndoorOutdoor_Data.PROXIMITY_ACCURACY, IndoorOutdoor_Data.PROXIMITY_ACCURACY);
        databaseMap.put(IndoorOutdoor_Data.TELEPHONY_SIGNAL_STRENGTH, IndoorOutdoor_Data.TELEPHONY_SIGNAL_STRENGTH);
        databaseMap.put(IndoorOutdoor_Data.TELEPHONY_CELL_TOWERS, IndoorOutdoor_Data.TELEPHONY_CELL_TOWERS);
        databaseMap.put(IndoorOutdoor_Data.WIFI_AP, IndoorOutdoor_Data.WIFI_AP);
        databaseMap.put(IndoorOutdoor_Data.GPS_SATELLITES, IndoorOutdoor_Data.GPS_SATELLITES);
        databaseMap.put(IndoorOutdoor_Data.PART_OF_DAY, IndoorOutdoor_Data.PART_OF_DAY);
        databaseMap.put(IndoorOutdoor_Data.CLOUD_PERCENTAGE, IndoorOutdoor_Data.CLOUD_PERCENTAGE);
        databaseMap.put(IndoorOutdoor_Data.LOCATION_TYPE_GUESS, IndoorOutdoor_Data.LOCATION_TYPE_GUESS);
        databaseMap.put(IndoorOutdoor_Data.LOCATION_TYPE_PROBABILITY, IndoorOutdoor_Data.LOCATION_TYPE_PROBABILITY);
        databaseMap.put(IndoorOutdoor_Data.LOCATION_TYPE, IndoorOutdoor_Data.LOCATION_TYPE);


        return true;
    }

    private boolean initializeDB() {
        if (databaseHelper == null) {
            databaseHelper = new DatabaseHelper( getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS );
        }
        if( databaseHelper != null && ( database == null || ! database.isOpen() )) {
            database = databaseHelper.getWritableDatabase();
        }
        return( database != null && databaseHelper != null);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case IO_DATA:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (URIMatcher.match(uri)) {
            case IO_DATA:
                return IndoorOutdoor_Data.CONTENT_TYPE;
            case IO_DATA_ID:
                return IndoorOutdoor_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY, "Database unavailable...");
            return null;
        }

        ContentValues values = (initialValues != null) ? new ContentValues(
                initialValues) : new ContentValues();

        switch (URIMatcher.match(uri)) {
            case IO_DATA:
                long data_id = database.insert(DATABASE_TABLES[0], IndoorOutdoor_Data.DEVICE_ID, values);

                if (data_id > 0) {
                    Uri new_uri = ContentUris.withAppendedId(
                            IndoorOutdoor_Data.CONTENT_URI,
                            data_id);
                    getContext().getContentResolver().notifyChange(new_uri,
                            null);
                    return new_uri;
                }
                throw new SQLException("Failed to insert row into " + uri);
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

        if( ! initializeDB() ) {
            Log.w(AUTHORITY, "Database unavailable...");
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URIMatcher.match(uri)) {
            case IO_DATA:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(databaseMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());

            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        if( ! initializeDB() ) {
            Log.w(AUTHORITY, "Database unavailable...");
            return 0;
        }

        int count = 0;
        switch (URIMatcher.match(uri)) {
            case IO_DATA:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:

                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}