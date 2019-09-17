package info.nightscout.android.medtronic.setup.ui.address;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;

class AddressViewModel extends ViewModel {
    private final MutableLiveData<String> m_address = new MutableLiveData<>();
    private final MutableLiveData<String> m_secret = new MutableLiveData<>();
    private final MutableLiveData<Boolean> m_canContinue = new MutableLiveData<>();

    MutableLiveData<String> getAddress() {
        return m_address;
    }

    MutableLiveData<String> getSecret() {
        return m_secret;
    }

    MutableLiveData<Boolean> getCanContinue() {
        return m_canContinue;
    }
}
