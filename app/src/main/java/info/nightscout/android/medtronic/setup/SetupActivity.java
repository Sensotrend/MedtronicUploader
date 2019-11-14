package info.nightscout.android.medtronic.setup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.setup.ui.address.AddressFragment;

public class SetupActivity extends AppCompatActivity implements SetupNavigationHandler {
    private String TAG = getClass().getSimpleName();
    public static final String INTENT_RETURN = "saReturn";

    public static boolean needsStarting(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return TextUtils.isEmpty(preferences.getString(context.getString(R.string.key_nightscoutURL), "")) ||
                preferences.getString(context.getString(R.string.key_nightscoutSECRET), null) == null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, AddressFragment.newInstance())
                    .commitNow();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            final Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container);
            if (fragment instanceof QRIntentResultReceiver) {
                ((QRIntentResultReceiver) fragment).onQRIntentResult(intentResult);
            }
        }
    }

    @Override
    public void setupPartFinished(@NonNull Part part) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (part) {
            case ADDRESS:
                if (!getIntent().getBooleanExtra(INTENT_RETURN, false)) {
                    setupOtherDefaults();
                    startActivity(new Intent(this, MainActivity.class));
                }
                finish();
                break;
            default:
                Log.e(TAG, "Unexpected finished part: " + part);
                break;
        }
    }

    private void setupOtherDefaults() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit()
                .putBoolean(getString(R.string.key_EnableRESTUpload), true)
                .apply();
    }
}
