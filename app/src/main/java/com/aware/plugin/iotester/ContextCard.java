package com.aware.plugin.iotester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Barometer;
import com.aware.Light;
import com.aware.Magnetometer;
import com.aware.Proximity;
import com.aware.WiFi;
import com.aware.ui.Stream_UI;
import com.aware.utils.IContextCard;

public class ContextCard implements IContextCard {

    //Set how often your card needs to refresh if the stream is visible (in milliseconds)
    private int refresh_interval = 1 * 1000; //1 second = 1000 milliseconds

    //DEMO: we are demo'ing a counter incrementing in real-time

    private Handler uiRefresher = new Handler(Looper.getMainLooper());
    private Runnable uiChanger = new Runnable() {
        @Override
        public void run() {

            //Modify card's content here once it's initialized
            if( card != null ) {

                String  location = Aware.getSetting(sContext,"location");
                if(location.equals("Indoor"))
                {
                    button.setChecked(true);
                }
                else
                {
                    button.setChecked(false);
                }
                String location_guess = Aware.getSetting(sContext,"location_type");
                txt_location.setText(location_guess);
                //DEMO display the counter value
                //counter_txt.setText(""+counter);
            }

            //Reset timer and schedule the next card refresh
            uiRefresher.postDelayed(uiChanger, refresh_interval);
        }
    };

    //Empty constructor used to instantiate this card
    public ContextCard(){};

    //You may use sContext on uiChanger to do queries to databases, etc.
    private Context sContext;

    //Declare here all the UI elements you'll be accessing
    private View card;
    private ToggleButton button;
    private TextView txt_location;

    //Used to load your context card
    private LayoutInflater sInflater;

    @Override
    public View getContextCard(Context context) {
        sContext = context;

        //Tell Android that you'll monitor the stream statuses
        IntentFilter filter = new IntentFilter();
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_OPEN);
        filter.addAction(Stream_UI.ACTION_AWARE_STREAM_CLOSED);
        context.registerReceiver(streamObs, filter);

        //Load card information to memory
        sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        card = sInflater.inflate(R.layout.card, null);
        //Log.d(Plugin.TAG,"GetContextCard");

        //Initialize UI elements from the card
        txt_location = (TextView) card.findViewById(R.id.txt_location);
        button = (ToggleButton) card.findViewById(R.id.toggleIndoor);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean on = ((ToggleButton) view).isChecked();

                if (on) {
                    // Enable vibrate
                    Aware.setSetting(sContext,"location", "Indoor");
                } else {
                    Aware.setSetting(sContext,"location", "Outdoor");
                    // Disable vibrate
                }
            }
        });


        //Begin refresh cycle
        uiRefresher.postDelayed(uiChanger, refresh_interval);

        //Return the card to AWARE/apps
        return card;
    }





    //This is a BroadcastReceiver that keeps track of stream status. Used to stop the refresh when user leaves the stream and restart again otherwise
    private StreamObs streamObs = new StreamObs();
    public class StreamObs extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_OPEN) ) {
                //start refreshing when user enters the stream
                uiRefresher.postDelayed(uiChanger, refresh_interval);

                //DEMO only, reset the counter every time the user opens the stream
            }
            if( intent.getAction().equals(Stream_UI.ACTION_AWARE_STREAM_CLOSED) ) {
                //stop refreshing when user leaves the stream
                uiRefresher.removeCallbacks(uiChanger);
                uiRefresher.removeCallbacksAndMessages(null);
            }
        }
    }


}
