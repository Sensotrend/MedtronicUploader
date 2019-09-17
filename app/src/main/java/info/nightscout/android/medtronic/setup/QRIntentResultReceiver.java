package info.nightscout.android.medtronic.setup;

import android.support.annotation.NonNull;

import com.google.zxing.integration.android.IntentResult;

public interface QRIntentResultReceiver {
    void onQRIntentResult(@NonNull IntentResult result);
}
