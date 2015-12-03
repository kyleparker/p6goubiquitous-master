package com.example.android.sunshine.app.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

/**
 * Created by kyleparker on 12/2/2015.
 */
public class WearForecaseUpdateService extends IntentService {
    private static final String TAG = WearForecaseUpdateService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    private String mNodeId;
    private static String mTodayHigh = "";
    private static String mTodayLow = "";
    private static int mTodayWeatherId;
    private static int mTomorrowWeatherId;

    /**
     * Sets an identifier for this class' background thread
     */
    public WearForecaseUpdateService() {
        super("WearForecaseUpdateService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();

        if (extras == null) {
            return;
        }

        mTodayHigh = extras.getString(SunshineSyncAdapter.EXTRA_HIGH_TEMP);
        mTodayLow = extras.getString(SunshineSyncAdapter.EXTRA_LOW_TEMP);
        mTodayWeatherId = extras.getInt(SunshineSyncAdapter.EXTRA_WEATHER_ID);
        mTomorrowWeatherId = extras.getInt(SunshineSyncAdapter.EXTRA_TOMORROW_WEATHER_ID);
        mTomorrowWeatherId = extras.getInt(SunshineSyncAdapter.EXTRA_TOMORROW_WEATHER_ID);

        Log.e(TAG, "here");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.e(TAG, "onConnected");

//                        updateWeather();
                        retrieveDeviceNode();
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.e(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.e(TAG, "onConnectionFailed: " + result);
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    private void retrieveDeviceNode() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult result) {
                List<Node> nodes = result.getNodes();
                if (nodes.size() > 0) {
                    mNodeId = nodes.get(0).getId();
                    updateWeather();
                }
            }
        });
    }

    // Create a data map and put data in it
    private void updateWeather() {
        Log.e("***> node", mNodeId + "");

        if (!TextUtils.isEmpty(mNodeId)) {
            DataMap config = new DataMap();

            config.putString(SunshineSyncAdapter.EXTRA_HIGH_TEMP, mTodayHigh);
            config.putString(SunshineSyncAdapter.EXTRA_LOW_TEMP, mTodayLow);
            config.putInt(SunshineSyncAdapter.EXTRA_WEATHER_ID, mTodayWeatherId);
            config.putInt(SunshineSyncAdapter.EXTRA_TOMORROW_WEATHER_ID, mTomorrowWeatherId);
            byte[] rawData = config.toByteArray();

            Wearable.MessageApi.sendMessage(mGoogleApiClient, mNodeId, "/wear_data", rawData)
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                    Log.d(TAG, "Sent watch face data:" + sendMessageResult.getStatus());
                }
            });
        }

//        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/wear_data");
//        putDataMapReq.getDataMap().putLong(SunshineSyncAdapter.EXTRA_TIMESTAMP, System.currentTimeMillis());
//        putDataMapReq.getDataMap().putString(SunshineSyncAdapter.EXTRA_HIGH_TEMP, mTodayHigh);
//        putDataMapReq.getDataMap().putString(SunshineSyncAdapter.EXTRA_LOW_TEMP, mTodayLow);
//        putDataMapReq.getDataMap().putInt(SunshineSyncAdapter.EXTRA_WEATHER_ID, mTodayWeatherId);
//        putDataMapReq.getDataMap().putInt(SunshineSyncAdapter.EXTRA_TOMORROW_WEATHER_ID, mTomorrowWeatherId);
//
//        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
//        PendingResult<DataApi.DataItemResult> pendingResult =
//                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
//
//        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//            @Override
//            public void onResult(final DataApi.DataItemResult result) {
//                if (result.getStatus().isSuccess()) {
//                    Log.e(TAG, "Data item set: " + result.getDataItem().getUri());
//
//                    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
//                        mGoogleApiClient.disconnect();
//                    }
//                }
//            }
//        });
    }
}
