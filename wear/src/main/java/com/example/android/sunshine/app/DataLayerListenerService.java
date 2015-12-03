package com.example.android.sunshine.app;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

// NOTE: NOT USED! Attempted to use the listener, but it would not respond in a timely manner (5-15 minute delay)
/**
 * Created by kyleparker on 12/2/2015.
 */
public class DataLayerListenerService extends WearableListenerService {
    private static final String TAG = DataLayerListenerService.class.getSimpleName();

    private static final String EXTRA_TIMESTAMP = "TIMESTAMP";
    private static final String EXTRA_METRIC = "METRIC";
    private static final String EXTRA_HIGH_TEMP = "HIGH_TEMP";
    private static final String EXTRA_LOW_TEMP = "LOW_TEMP";
    private static final String EXTRA_WEATHER_ID = "WEATHER_ID";
    private static final String EXTRA_TOMORROW_WEATHER_ID = "TOMORROW_WEATHER_ID";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.e("***> wear data", "here");

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult = googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(googleApiClient);
        results.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                Log.e("***> count", dataItems.getCount() + "");
                if (dataItems.getCount() > 0) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItems.get(0));

                    // Retrieve the values from the data map
//                    String todayHigh = dataMapItem.getDataMap().getString(EXTRA_HIGH_TEMP);
//                    String todayLow = dataMapItem.getDataMap().getString(EXTRA_LOW_TEMP);
//                    boolean isMetric = dataMapItem.getDataMap().getBoolean(EXTRA_METRIC);
//                    long timestamp = dataMapItem.getDataMap().getLong(EXTRA_TIMESTAMP);
//                    Log.e("***> todayHigh", todayHigh + "");
//                    Log.e("***> todayLow", todayLow + "");
//                    Log.e("***> timestamp", timestamp + "");
//
//                    // TODO: send Broadcast for updates to the watchface
//                    Intent broadcastIntent = new Intent();
//                    broadcastIntent.setAction("WEATHER_UPDATED");
//                    sendBroadcast(broadcastIntent);
                }

                dataItems.release();
            }
        });
    }
}
