/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

// NOTE: Attempted to use the DataAPI and onDataChanged event {@link DataLayerListenerService} but the communication
// was not reliable. The onDataChanged event would fire (eventually) but it would take anywhere from 5-10 minutes, sometimes
// requiring the watchface to be changed.
/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = SunshineWatchFace.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface CONDENSED_TYPEFACE = Typeface.create("sans-serif-condensed", Typeface.NORMAL);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            MessageApi.MessageListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        private static final String EXTRA_HIGH_TEMP = "HIGH_TEMP";
        private static final String EXTRA_LOW_TEMP = "LOW_TEMP";
        private static final String EXTRA_WEATHER_ID = "WEATHER_ID";
        private static final String EXTRA_TOMORROW_WEATHER_ID = "TOMORROW_WEATHER_ID";

        private static final String COLON_STRING = ":";
        private static final int MSG_UPDATE_TIME = 0;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        private boolean mRegisteredTimeZoneReceiver = false;

        private Resources mResources;
        private Date mDate;
        private SimpleDateFormat mDayOfWeekFormat;
        private java.text.DateFormat mDateFormat;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mAmbient;
        private boolean mIsRound = false;
        private boolean mRetrieveWeather = true;

        private Paint mHourPaint;
        private Paint mColonPaint;
        private Paint mMinutePaint;
        private Paint mAmPmPaint;
        private Paint mWeekdayPaint;
        private Paint mDatePaint;
        private Paint mHighTempPaint;
        private Paint mLowTempPaint;
        private Paint mLaterPaint;

        private float mColonWidth;
        private float mXOffset;
        private float mXTempOffset;
        private float mYOffset;
        private float mLineHeight;
        private float mHalfLineHeight;
        private float mXPadding;

        private Calendar mCalendar;

        private String mHighTemp = "-";
        private String mLowTemp = "-";
        private String mAmString;
        private String mPmString;
        private String mTomorrowString;
        private String mNodeId;

        private int mWeatherId = R.drawable.ic_weather_sunny;
        private int mTomorrowWeatherId = R.drawable.ic_weather_sunny_tomorrow;
        private int mWeatherBackground = R.color.default_bg;
        private int mSecondaryText = R.color.secondary_text;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
            }
        };

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.START)
                    .setStatusBarGravity(Gravity.TOP | Gravity.END)
                    .setShowSystemUiTime(false)
                    .build());

            mResources = SunshineWatchFace.this.getResources();

            mYOffset = mResources.getDimension(R.dimen.y_offset);
            mXTempOffset = mResources.getDimension(R.dimen.x_temp_offset);
            mLineHeight = mResources.getDimension(R.dimen.line_height);
            mHalfLineHeight = mResources.getDimension(R.dimen.half_line_height);
            mXPadding = mResources.getDimension(R.dimen.x_padding);

            mAmString = mResources.getString(R.string.content_am);
            mPmString = mResources.getString(R.string.content_pm);
            mTomorrowString = mResources.getString(R.string.content_tomorrow);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);

            mDateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);

            // Notify the phone that the watchface was set - need to get the current weather info
            mRetrieveWeather = true;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.MessageApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.e("***> apply insets", "here");
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            mIsRound = insets.isRound();
            initPaint();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;

                    mHourPaint.setAntiAlias(antiAlias);
                    mColonPaint.setAntiAlias(antiAlias);
                    mMinutePaint.setAntiAlias(antiAlias);
                    mAmPmPaint.setAntiAlias(antiAlias);
                    mWeekdayPaint.setAntiAlias(antiAlias);
                    mDatePaint.setAntiAlias(antiAlias);
                    mHighTempPaint.setAntiAlias(antiAlias);
                    mLowTempPaint.setAntiAlias(antiAlias);
                    mLaterPaint.setAntiAlias(antiAlias);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);

            // Draw the background.
            canvas.drawColor(getResources().getColor(R.color.bg_top));

            int height = canvas.getHeight();
            int width = canvas.getWidth();

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }

            canvas.drawText(hourString, x, mYOffset, mHourPaint);

            x += mHourPaint.measureText(hourString);

            // Draw first colon (between hour and minute).
            canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);

            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString) + mXPadding;

            // In interactive mode, draw a second colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM
            canvas.drawText(getAmPmString(mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            x += mAmPmPaint.measureText(getAmPmString(mCalendar.get(Calendar.AM_PM))) + (mXPadding * 2);

            // Day of week
            canvas.drawText(mDayOfWeekFormat.format(mDate), x, mYOffset - mHalfLineHeight, mDatePaint);

            // Add the date to the string
            canvas.drawText(mDateFormat.format(mDate), x, mYOffset, mDatePaint);

            // Only render steps if there is no peek card, so they do not bleed into each other
            // in ambient mode.
            if (getPeekCardPosition().isEmpty()) {
                // Background for weather
                Paint paint = new Paint();
                paint.setColor(getResources().getColor(mWeatherBackground));
                canvas.drawRect(0, mYOffset + mLineHeight, width, height, paint);

                // Setup additional padding and offsets for round versus square watches
                float tempLineHeight = mIsRound ? mLineHeight * 2 : mLineHeight;
                float tempHalfLineHeight = mIsRound ? mHalfLineHeight * 2 : mHalfLineHeight;
                float padding = mIsRound ? mXPadding : 0;
                float tomorrowWeatherPadding = mIsRound ? mHalfLineHeight * 4 : mHalfLineHeight * 3;
                float tempOffset = mDatePaint.measureText(mHighTemp);

                // Weather indicator
                Bitmap b = BitmapFactory.decodeResource(getResources(), mWeatherId);
                float bitmapXOffset = mIsRound ? mXTempOffset + b.getWidth(): b.getWidth();

                canvas.drawBitmap(b, mIsRound ? mXTempOffset : 0, mYOffset + tempLineHeight, null);

                // Temperature
                canvas.drawText(mHighTemp, bitmapXOffset, mYOffset + tempHalfLineHeight + (b.getHeight() / 2) + padding, mHighTempPaint);
                canvas.drawText(mLowTemp, bitmapXOffset, mYOffset + tempLineHeight + padding + (b.getHeight() / 2) + tempOffset, mLowTempPaint);

                // Tomorrow
                canvas.drawText(mTomorrowString, bitmapXOffset + mXTempOffset + padding,
                        mYOffset + tempHalfLineHeight + (b.getHeight() / 2), mLaterPaint);

                Bitmap tomorrowIcon = BitmapFactory.decodeResource(getResources(), mTomorrowWeatherId);
                canvas.drawBitmap(tomorrowIcon, bitmapXOffset + mXTempOffset + (mXPadding * 2),
                        mYOffset + tomorrowWeatherPadding, null);
            }
        }

        private Paint createTextPaint(int color) {
            return createTextPaint(color, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int color, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }

        private int getWeatherIcon(int weatherId, boolean today) {
            // Set the weather icons to the appropriate resource
            if (weatherId >= 200 && weatherId <= 232) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_lightning : R.drawable.ic_weather_lightning_tomorrow;
            } else if ((weatherId >= 300 && weatherId <= 321) || weatherId == 500) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_rainy : R.drawable.ic_weather_rainy_tomorrow;
            } else if (weatherId >= 501 && weatherId <= 504) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_pouring : R.drawable.ic_weather_pouring_tomorrow;
            } else if (weatherId == 511) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_snowy : R.drawable.ic_weather_snowy_tomorrow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_pouring : R.drawable.ic_weather_pouring_tomorrow;
            } else if (weatherId >= 600 && weatherId <= 622) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_snowy : R.drawable.ic_weather_snowy_tomorrow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                if (today) {
                    mWeatherBackground = R.color.cloudy_bg;
                    mSecondaryText = R.color.cloudy_secondary_text;
                }
                return today ? R.drawable.ic_weather_fog : R.drawable.ic_weather_fog_tomorrow;
            } else if (weatherId == 761 || weatherId == 781) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_lightning : R.drawable.ic_weather_lightning_tomorrow;
            } else if (weatherId == 800) {
                if (today) {
                    mWeatherBackground = R.color.sunny_bg;
                    mSecondaryText = R.color.sunny_secondary_text;
                }
                return today ? R.drawable.ic_weather_sunny : R.drawable.ic_weather_sunny_tomorrow;
            } else if (weatherId == 801) {
                if (today) {
                    mWeatherBackground = R.color.cloudy_bg;
                    mSecondaryText = R.color.cloudy_secondary_text;
                }
                return today ? R.drawable.ic_weather_partlycloudy : R.drawable.ic_weather_partlycloudy_tomorrow;
            } else if (weatherId >= 802 && weatherId <= 804) {
                if (today) {
                    mWeatherBackground = R.color.cloudy_bg;
                    mSecondaryText = R.color.cloudy_secondary_text;
                }
                return today ? R.drawable.ic_weather_cloudy : R.drawable.ic_weather_cloudy_tomorrow;
            } else if (weatherId == 905) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_windy : R.drawable.ic_weather_windy_tomorrow;
            } else if (weatherId == 906) {
                if (today) {
                    mWeatherBackground = R.color.rainy_bg;
                    mSecondaryText = R.color.rainy_secondary_text;
                }
                return today ? R.drawable.ic_weather_hail : R.drawable.ic_weather_hail_tomorrow;
            }

            return today ? R.drawable.ic_weather_sunny : R.drawable.ic_weather_sunny_tomorrow;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void initPaint() {
            mXOffset = mResources.getDimension(mIsRound
                    ? R.dimen.x_offset_round : R.dimen.x_offset);
            float textSize = mResources.getDimension(mIsRound
                    ? R.dimen.text_size_round : R.dimen.text_size);
            float smallTextSize = mResources.getDimension(mIsRound
                    ? R.dimen.text_small_size_round : R.dimen.text_small_size);
            float xSmallTextSize = mResources.getDimension(mIsRound
                    ? R.dimen.text_xsmall_size_round : R.dimen.text_xsmall_size);
            float mediumTextSize = mResources.getDimension(mIsRound
                    ? R.dimen.text_medium_size_round : R.dimen.text_medium_size);

            mHourPaint = createTextPaint(mResources.getColor(R.color.time_text), CONDENSED_TYPEFACE);
            mColonPaint = createTextPaint(mResources.getColor(mSecondaryText));
            mMinutePaint = createTextPaint(mResources.getColor(R.color.time_text), CONDENSED_TYPEFACE);
            mAmPmPaint = createTextPaint(mResources.getColor(mSecondaryText), CONDENSED_TYPEFACE);
            mWeekdayPaint = createTextPaint(mResources.getColor(mSecondaryText), CONDENSED_TYPEFACE);
            mDatePaint = createTextPaint(mResources.getColor(mSecondaryText), CONDENSED_TYPEFACE);
            mHighTempPaint = createTextPaint(mResources.getColor(R.color.temperature_text));
            mLowTempPaint = createTextPaint(mResources.getColor(R.color.temperature_text));
            mLaterPaint = createTextPaint(mResources.getColor(R.color.secondary_text));

            mHourPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(smallTextSize);
            mWeekdayPaint.setTextSize(smallTextSize);
            mDatePaint.setTextSize(smallTextSize);
            mHighTempPaint.setTextSize(mediumTextSize);
            mLowTempPaint.setTextSize(mediumTextSize);
            mLaterPaint.setTextSize(xSmallTextSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        /**
         * Notify the connected device that the watchface was set - used to retrieve the current weather info
         */
        private void notifyPhone() {
            Log.e("***> node", mNodeId + "");

            if (!TextUtils.isEmpty(mNodeId)) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mNodeId, "/watchface_data", null)
                        .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                Log.d(TAG, "Sent phone message:" + sendMessageResult.getStatus());
                            }
                        });
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void retrieveDeviceNode() {
            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                        @Override
                        public void onResult(NodeApi.GetConnectedNodesResult result) {
                            List<Node> nodes = result.getNodes();
                            if (nodes.size() > 0) {
                                mNodeId = nodes.get(0).getId();
                                notifyPhone();
                            }
                        }
                    });
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            Log.e(TAG, "onConnected: " + connectionHint);
            Wearable.MessageApi.addListener(mGoogleApiClient, Engine.this);

            if (mRetrieveWeather) {
                mRetrieveWeather = false;
                retrieveDeviceNode();
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Log.e(TAG, "onConnectionSuspended: " + cause);
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed: " + result);
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            Log.e("***> onMessageReceived", "here");
            Log.e("***> onMessageReceived", messageEvent.getPath());

            if (messageEvent.getPath().equals("/wear_data")) {
                byte[] rawData = messageEvent.getData();
                DataMap dataMap = DataMap.fromByteArray(rawData);
                mHighTemp = dataMap.getString(EXTRA_HIGH_TEMP);
                mLowTemp = dataMap.getString(EXTRA_LOW_TEMP);
                mWeatherId = getWeatherIcon(800, true);
                mTomorrowWeatherId = getWeatherIcon(dataMap.getInt(EXTRA_TOMORROW_WEATHER_ID), false);

                initPaint();
                invalidate();
            }
        }
    }
}
