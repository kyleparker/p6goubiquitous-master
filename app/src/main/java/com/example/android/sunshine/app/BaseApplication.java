package com.example.android.sunshine.app;

import android.app.Application;
import android.content.Intent;

import com.example.android.sunshine.app.service.WearReceiverService;

/**
 * Created by kyleparker on 12/3/2015.
 */
public class BaseApplication extends Application {

    public void onCreate() {
        super.onCreate();

        Intent intent = new Intent(this, WearReceiverService.class);
        startService(intent);
    }
}
