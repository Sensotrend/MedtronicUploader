package info.nightscout.android.medtronic.setup;

import android.support.annotation.NonNull;

public interface SetupNavigationHandler {
    enum Part {
        ADDRESS,
    }

    void setupPartFinished(@NonNull Part part);
}
