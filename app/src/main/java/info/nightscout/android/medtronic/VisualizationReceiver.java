package info.nightscout.android.medtronic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CheckResult;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ImageViewCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import info.nightscout.android.BuildConfig;
import info.nightscout.android.R;
import info.nightscout.android.medtronic.service.MasterService.Constants;

public final class VisualizationReceiver extends BroadcastReceiver {
    private static final String TAG = VisualizationReceiver.class.getSimpleName();

    private static final String ACTION_DOWNLOAD_STATUS = "info.nightscout.android.medtronic.vrDlStatus";
    private static final String ACTION_UPLOAD_STATUS = "info.nightscout.android.medtronic.vrUlStatus";
    private static final String ACTION_STATUS_EXTRA_STATUS = "vrStatus";
    private static final String EXTRA_ERROR = "vrError";

    public static final int STATUS_DOWNLOAD_CONNECTED = 1;
    public static final int STATUS_DOWNLOAD_DOWNLOADING = 2;
    public static final int STATUS_DOWNLOAD_DONE = 3;
    public static final int STATUS_DOWNLOAD_PUMP_ERROR = 4;

    @IntDef({
            STATUS_DOWNLOAD_CONNECTED,
            STATUS_DOWNLOAD_DOWNLOADING,
            STATUS_DOWNLOAD_DONE,
            STATUS_DOWNLOAD_PUMP_ERROR
    })
    @interface DownloadStatus {}

    @CheckResult
    public static Intent downloadStatus(@DownloadStatus int status) {
        final Intent intent = new Intent(ACTION_DOWNLOAD_STATUS);
        intent.putExtra(ACTION_STATUS_EXTRA_STATUS, status);
        return intent;
    }

    public static final int ERROR_NONE = 0;
    public static final int ERROR_PUMP_NOISE = 1;
    public static final int ERROR_CNL = 2;
    @IntDef({
            ERROR_NONE,
            ERROR_PUMP_NOISE,
            ERROR_CNL
    })
    @interface DownloadError {}

    @CheckResult
    public static Intent downloadStatus(@DownloadStatus int status, @DownloadError int error) {
        final Intent intent = new Intent(ACTION_DOWNLOAD_STATUS);
        intent.putExtra(ACTION_STATUS_EXTRA_STATUS, status);
        intent.putExtra(EXTRA_ERROR, error);
        return intent;
    }

    public static final int STATUS_UPLOAD_STARTED = 1;
    public static final int STATUS_UPLOAD_DONE = 2;
    public static final int STATUS_UPLOAD_NOTHING_DONE = 3;
    public static final int STATUS_UPLOAD_FAILED = 4;

    @IntDef({
            STATUS_UPLOAD_STARTED,
            STATUS_UPLOAD_DONE,
            STATUS_UPLOAD_NOTHING_DONE,
            STATUS_UPLOAD_FAILED
    })
    @interface UploadStatus {}

    @CheckResult
    public static Intent uploadStatus(@UploadStatus int status) {
        final Intent intent = new Intent(ACTION_UPLOAD_STATUS);
        intent.putExtra(ACTION_STATUS_EXTRA_STATUS, status);
        return intent;
    }

    public static IntentFilter createReceiverFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION_CNL_COMMS_ACTIVE);
        intentFilter.addAction(Constants.ACTION_CNL_COMMS_READY);
        intentFilter.addAction(Constants.ACTION_READ_OVERDUE);

        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(Constants.ACTION_NO_USB_PERMISSION);

        intentFilter.addAction(ACTION_DOWNLOAD_STATUS);
        intentFilter.addAction(ACTION_UPLOAD_STATUS);
        return intentFilter;
    }

    @NonNull private final ImageView devicePump;
    @NonNull private final ImageView deviceMeter;
    @NonNull private final ImageView deviceIcon;
    @NonNull private final ImageView signal;
    @NonNull private final LevelListDrawable signalDrawable;
    @NonNull private final ImageView cloud;
    @NonNull private final Drawable deviceDrawable;
    @NonNull private final Drawable deviceDrawableStatic;
    @NonNull private final ImageView checkMark;

    public VisualizationReceiver(
            @NonNull ImageView devicePump,
            @NonNull ImageView deviceMeter,
            @NonNull ImageView deviceIcon,
            @NonNull ImageView signal,
            @NonNull ImageView cloud,
            @NonNull ImageView checkMark
    ) {
        this.devicePump = devicePump;
        this.deviceMeter = deviceMeter;
        this.deviceIcon = deviceIcon;
        this.signal = signal;
        this.cloud = cloud;
        this.checkMark = checkMark;

        final Context context = devicePump.getContext();

        final ColorStateList stateList = new ColorStateList(
                new int[][] {
                        {android.R.attr.state_enabled, android.R.attr.state_selected},
                        {android.R.attr.state_enabled},
                        {-android.R.attr.state_enabled, android.R.attr.state_selected},
                        {-android.R.attr.state_enabled}
                },
                new int [] {
                        ContextCompat.getColor(context, R.color.warning_icon),
                        ContextCompat.getColor(context, R.color.enabled_icon),
                        ContextCompat.getColor(context, R.color.error_icon),
                        ContextCompat.getColor(context, R.color.disabled_icon)
                }
        );

        ImageViewCompat.setImageTintList(devicePump, stateList);
        ImageViewCompat.setImageTintList(deviceMeter, stateList);
        ImageViewCompat.setImageTintList(cloud, stateList);

        devicePump.setEnabled(false);
        deviceMeter.setEnabled(false);

        deviceDrawable = deviceIcon.getDrawable();
        deviceDrawableStatic = ContextCompat.getDrawable(context, R.drawable.ic_device);
        deviceIcon.setImageDrawable(deviceDrawableStatic);

        signalDrawable = (LevelListDrawable) signal.getDrawable();
        mUpdateSignalRunnable.stop();

        updateState();
    }

    private abstract static class HandlerRunnable implements Runnable {
        private final Handler mHandler;
        private boolean isRunning;

        HandlerRunnable(Handler handler) {
            mHandler = handler;
        }

        Handler getHandler() {
            return mHandler;
        }

        boolean isRunning() {
            return isRunning;
        }

        void start() {
            if (!isRunning) {
                isRunning = true;
                mHandler.post(this);
            }
        }

        void stop() {
            mHandler.removeCallbacks(this);
            isRunning = false;
        }
    }


    private boolean mIsHostResumed;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final HandlerRunnable mUpdateSignalRunnable = new HandlerRunnable(mHandler) {
        private int current = 0;

        @Override
        void start() {
            current = 0;
            super.start();
            signal.setVisibility(View.VISIBLE);
        }

        @Override
        void stop() {
            super.stop();
            signal.setVisibility(View.INVISIBLE);
        }

        @Override
        public void run() {
            current = (current + 1) % 4;
            signalDrawable.setLevel(current);
            if (mIsHostResumed) {
                if (mState.isUploading || current != 0) {
                    getHandler().postDelayed(this, 250);
                } else {
                    signal.setVisibility(View.INVISIBLE);
                }
            }
        }
    };

    private static class State {
        private static final String BUNDLE_KEY = "vrs";

        boolean hasMeter;
        boolean hasPump;
        boolean isPumpErrorAWarning;
        boolean hasPumpError;
        boolean hasCNLError;
        boolean isReading;
        boolean _isReadAnimating;
        boolean isUploading;
        boolean uploadError;
        boolean uploadDone;

        @NonNull
        @Override
        public String toString() {
            if (BuildConfig.DEBUG) {
                return "State{" +
                        "hasMeter=" + hasMeter +
                        ", hasPump=" + hasPump +
                        ", isPumpErrorAWarning=" + isPumpErrorAWarning +
                        ", hasPumpError=" + hasPumpError +
                        ", hasCNLError=" + hasCNLError +
                        ", isReading=" + isReading +
                        ", _isReadAnimating=" + _isReadAnimating +
                        ", isUploading=" + isUploading +
                        ", uploadError=" + uploadError +
                        ", uploadDone=" + uploadDone +
                        '}';
            } else {
                return super.toString();
            }
        }

        void saveInstanceState(@NonNull Bundle out) {
            out.putBooleanArray(BUNDLE_KEY, new boolean[] {
                    hasMeter,
                    hasPump,
                    isPumpErrorAWarning,
                    hasPumpError,
                    hasCNLError,
                    isReading,
                    _isReadAnimating,
                    isUploading,
                    uploadError,
                    uploadDone
            });
        }

        @SuppressWarnings("UnusedAssignment")
        void restoreInstanceState(@NonNull Bundle in) {
            final boolean[] arr = in.getBooleanArray(BUNDLE_KEY);
            if (arr == null) {
                return;
            }
            int i = 0;
            hasMeter = arr[i++];
            hasPump = arr[i++];
            isPumpErrorAWarning = arr[i++];
            hasPumpError = arr[i++];
            hasCNLError = arr[i++];
            isReading = arr[i++];
            _isReadAnimating = arr[i++];
            isUploading = arr[i++];
            uploadError = arr[i++];
            uploadDone = arr[i++];
        }
    }

    private final State mState = new State();

    public void saveInstanceState(@NonNull Bundle out) {
        mState.saveInstanceState(out);
    }

    public void restoreInstanceState(@NonNull Bundle in) {
        mState.restoreInstanceState(in);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            Log.w(TAG, "Missing action");
            return;
        }
        Log.d(TAG, "Got event: " + action);
        switch (action) {
            case Constants.ACTION_CNL_COMMS_READY:
                // "Just ready. Nothing special."
                break;

            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                // Attached, connection pending?
                break;

            case Constants.ACTION_READ_OVERDUE:
                // Not read in a while?
                break;

            case Constants.ACTION_CNL_COMMS_ACTIVE:
                // Reading started. Might not be able to connect, though.
                break;

            case Constants.ACTION_NO_USB_PERMISSION:
                // Read did start but failed.
                mState.hasMeter = false;
                mState.hasPump = false;
                mState.isReading = false;
                break;

            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                // Detached.
                mState.hasMeter = false;
                mState.hasPump = false;
                mState.hasPumpError = false;
                break;

            case ACTION_DOWNLOAD_STATUS: {
                final int status = intent.getIntExtra(ACTION_STATUS_EXTRA_STATUS, -1);
                final int error = intent.getIntExtra(EXTRA_ERROR, ERROR_NONE);
                Log.d(TAG, "Status: " + status);
                switch (status) {
                    case STATUS_DOWNLOAD_CONNECTED:
                        mState.hasMeter = true;
                        mState.hasPumpError = false;
                        mState.isPumpErrorAWarning = false;
                        mState.hasCNLError = false;
                        mState.uploadDone = false;
                        break;

                    case STATUS_DOWNLOAD_DOWNLOADING:
                        mState.hasPump = true;
                        mState.isReading = true;
                        break;

                    case STATUS_DOWNLOAD_DONE:
                        mState.isReading = false;
                        if (error == ERROR_PUMP_NOISE) {
                            mState.isPumpErrorAWarning = true;
                            mState.hasPumpError = true;
                        } else if (error == ERROR_CNL) {
                            mState.hasCNLError = true;
                        } else if (error == ERROR_NONE) {
                            mState.isPumpErrorAWarning = false;
                            mState.hasCNLError = false;
                        }
                        break;

                    case STATUS_DOWNLOAD_PUMP_ERROR:
                        mState.hasPumpError = true;
                        break;

                    default:
                        Log.e(TAG, "Invalid status: " + status);
                        break;
                }
                break;
            }

            case ACTION_UPLOAD_STATUS: {
                final int status = intent.getIntExtra(ACTION_STATUS_EXTRA_STATUS, -1);
                Log.d(TAG, "Status: " + status);
                switch (status) {
                    case STATUS_UPLOAD_STARTED:
                        mState.uploadError = false;
                        mState.isUploading = true;
                        mState.uploadDone = false;
                        break;

                    case STATUS_UPLOAD_DONE:
                        mState.isUploading = false;
                        mState.uploadDone = true;
                        break;

                    case STATUS_UPLOAD_NOTHING_DONE:
                        mState.isUploading = false;
                        mState.uploadDone = false;
                        break;

                    case STATUS_UPLOAD_FAILED:
                        mState.isUploading = false;
                        mState.uploadError = true;
                        break;

                    default:
                        Log.e(TAG, "Invalid status: " + status);
                        break;
                }
                break;
            }

            default:
                Log.w(TAG, "Skipped event: " + action);
                return;
        }

        updateState();
    }

    public void onResume() {
        mIsHostResumed = true;
        updateState();
    }

    public void onPause() {
        mIsHostResumed = false;
        mUpdateSignalRunnable.stop();
    }

    private void updateState() {
        devicePump.setEnabled(mState.hasPump);
        deviceMeter.setEnabled(mState.hasMeter);
        if (mState.hasPumpError) {
            if (mState.isPumpErrorAWarning) {
                devicePump.setEnabled(true);
            } else {
                devicePump.setEnabled(false);
            }
            devicePump.setSelected(true);
        } else {
            devicePump.setEnabled(mState.hasPump);
            devicePump.setSelected(false);
        }
        deviceMeter.setSelected(mState.hasCNLError);

        if (mState.isReading && !mState._isReadAnimating) {
            deviceIcon.setImageDrawable(deviceDrawable);
            mState._isReadAnimating = true;
            AnimationHelper.start(deviceDrawable);
        }
        else if (!mState.isReading && mState._isReadAnimating) {
            AnimationHelper.stop(deviceDrawable);
            mState._isReadAnimating = false;
            deviceIcon.setImageDrawable(deviceDrawableStatic);
        }

        cloud.setSelected(mState.uploadError);

        if (mState.isUploading && !mUpdateSignalRunnable.isRunning()) {
            mUpdateSignalRunnable.start();
        }

        checkMark.setVisibility(mState.uploadDone ? View.VISIBLE : View.GONE);
    }


    private static class AnimationHelper {
        // TODO: Support old devices.
        static void start(ImageView iv) {
            final Drawable drawable = iv.getDrawable();
            start(drawable);
        }

        static void start(Drawable drawable) {
            if (drawable instanceof Animatable) {
                Animatable animatable = (Animatable) drawable;
                animatable.start();
            }
        }

        static void stop(ImageView iv) {
            final Drawable drawable = iv.getDrawable();
            stop(drawable);
        }

        static void stop(Drawable drawable) {
            if (drawable instanceof Animatable) {
                Animatable animatable = (Animatable) drawable;
                animatable.stop();
            }
        }
    }
}
