package info.nightscout.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import info.nightscout.android.medtronic.MainActivity;
import info.nightscout.android.medtronic.setup.SetupActivity;

/**
 * Created by lgoedhart on 18/06/2016.
 */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent;
        if (SetupActivity.needsStarting(this)) {
            intent = new Intent(this, SetupActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
