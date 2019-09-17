package info.nightscout.android.medtronic.setup.ui.address;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.net.MalformedURLException;
import java.net.URL;

import info.nightscout.android.R;
import info.nightscout.android.medtronic.setup.QRIntentResultReceiver;
import info.nightscout.android.medtronic.setup.SetupNavigationHandler;

public class AddressFragment extends Fragment implements QRIntentResultReceiver {
    private static abstract class AbstractTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    // minSdk requirement not met if using Objects.
    @SuppressWarnings({"EqualsReplaceableByObjectsCall", "BooleanMethodIsAlwaysInverted"})
    private static boolean isEqual(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public static AddressFragment newInstance() {
        return new AddressFragment();
    }

    private AddressViewModel mViewModel;

    private SetupNavigationHandler m_handler;
    private boolean m_bubbling;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof SetupNavigationHandler) {
            m_handler = (SetupNavigationHandler) context;
        } else {
            throw new IllegalArgumentException("Context must implement SetupNavigationHandler");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.setup_fragment, container, false);
        root.findViewById(R.id.scan_qr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // IntentIntegrator uses deprecated android.app.Fragment and cannot be used.
                // Pass activity and expect it to be able to redirect the result back here.
                final IntentIntegrator integrator = new IntentIntegrator(requireActivity());
                integrator.initiateScan();
            }
        });

        return root;
    }

    private void updateViewModelFromPreferences() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        mViewModel.getAddress().setValue(preferences.getString(getString(R.string.key_nightscoutURL), ""));
        mViewModel.getSecret().setValue(preferences.getString(getString(R.string.key_nightscoutSECRET), ""));
        updateContinue();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(AddressViewModel.class);

        final View root = getView();
        if (root == null) {
            Log.e(TAG, "Missing view on create");
            return;
        }

        final TextView address = root.findViewById(R.id.address);
        final TextView secret = root.findViewById(R.id.secret);
        final View continueButton = root.findViewById(R.id.continue_button);

        mViewModel.getAddress().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                if (!m_bubbling) {
                    address.setText(s);
                }
                updateContinue();
            }
        });
        mViewModel.getSecret().observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                if (!m_bubbling) {
                    secret.setText(s);
                }
                updateContinue();
            }
        });
        mViewModel.getCanContinue().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean aBoolean) {
                continueButton.setEnabled(Boolean.TRUE.equals(aBoolean));
            }
        });

        updateViewModelFromPreferences();

        address.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!isEqual(mViewModel.getAddress().getValue(), s.toString())) {
                    m_bubbling = true;
                    mViewModel.getAddress().setValue(s.toString());
                    m_bubbling = false;
                }
            }
        });
        secret.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!isEqual(mViewModel.getSecret().getValue(), s.toString())) {
                    m_bubbling = true;
                    mViewModel.getSecret().setValue(s.toString());
                    m_bubbling = false;
                }
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String address = mViewModel.getAddress().getValue();
                if (address != null) {
                    saveSettings(address, mViewModel.getSecret().getValue());
                }
            }
        });
    }

    private final String TAG = getClass().getSimpleName();

    @Override
    public void onQRIntentResult(@NonNull IntentResult result) {
        // ** This code is mainly from SettingsFragment **
        JsonParser json = new JsonParser();
        String resultContents = result.getContents() == null ? "" : result.getContents();

        JsonElement jsonElement = json.parse(resultContents);
        if (jsonElement != null && jsonElement.isJsonObject()) {
            jsonElement = (jsonElement.getAsJsonObject()).get("rest");
            if (jsonElement != null && jsonElement.isJsonObject()) {
                jsonElement = (jsonElement.getAsJsonObject()).get("endpoint");
                if (jsonElement != null && jsonElement.isJsonArray() && jsonElement.getAsJsonArray().size() > 0) {
                    String endpoint = jsonElement.getAsJsonArray().get(0).getAsString();

                    try {
                        URL uri = new URL(endpoint);

                        StringBuilder url = new StringBuilder(uri.getProtocol())
                                .append("://").append(uri.getHost());
                        if (uri.getPort() > -1)
                            url.append(":").append(uri.getPort());

                        saveSettings(url.toString(), uri.getUserInfo());

                    } catch (MalformedURLException e) {
                        Log.w(TAG, e.getMessage());
                    }

                }
            }
        }
    }

    private void saveSettings(@NonNull String url, @Nullable String secret) {
        if (secret == null) {
            secret = "";
        }
        Log.d(TAG, "endpoint: " + url + " secret is " + secret.length() + " chars long");
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        preferences
                .edit()
                .putString(getString(R.string.key_nightscoutURL), url)
                .putString(getString(R.string.key_nightscoutSECRET), secret)
                .apply();

        updateViewModelFromPreferences();
        m_handler.setupPartFinished(SetupNavigationHandler.Part.ADDRESS);
    }

    private void updateContinue() {
        if (mViewModel != null) {
            mViewModel.getCanContinue().setValue(
                    !TextUtils.isEmpty(mViewModel.getAddress().getValue()) &&
                    !TextUtils.isEmpty(mViewModel.getSecret().getValue())
            );
        }
    }
}
