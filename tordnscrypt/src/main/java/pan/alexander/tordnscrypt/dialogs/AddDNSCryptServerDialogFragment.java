/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.DnsServerFeatures;
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.DnsServerItem;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;

import javax.inject.Inject;
import javax.inject.Named;

public class AddDNSCryptServerDialogFragment extends ExtendedDialogFragment {

    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;

    private EditText etOwnServerName;
    private EditText etOwnServerDescription;
    private EditText etOwnServerSDNS;

    private OnServerAddedListener onServerAddedListener;

    public static AddDNSCryptServerDialogFragment getInstance() {
        return new AddDNSCryptServerDialogFragment();
    }

    public interface OnServerAddedListener {
        void onServerAdded(DnsServerItem dnsServerItem);
    }

    public void setOnServerAddListener(OnServerAddedListener onServerAddedListener) {
        this.onServerAddedListener = onServerAddedListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.add_custom_server_title)
                .setView(R.layout.add_own_server)
                .setPositiveButton(R.string.ok, (dialog, which) -> {

                    if (etOwnServerName != null
                            && etOwnServerDescription != null
                            && etOwnServerSDNS != null) {

                        if (!saveOwnDNSCryptServer(getActivity()) && isAdded()) {
                            DialogFragment dialogFragment = NotificationDialogFragment.newInstance(R.string.add_custom_server_error);
                            dialogFragment.show(getParentFragmentManager(), "add_custom_server_error");
                        }
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return builder;
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();

        if (dialog != null) {
            etOwnServerName = dialog.findViewById(R.id.etOwnServerName);
            etOwnServerDescription = dialog.findViewById(R.id.etOwnServerDescription);
            etOwnServerSDNS = dialog.findViewById(R.id.etOwnServerSDNS);
        }
    }

    private boolean saveOwnDNSCryptServer(Context context) {
        logi("Save Own DNSCrypt server");

        String etOwnServerNameText = etOwnServerName.getText().toString().trim();
        String etOwnServerDescriptionText = etOwnServerDescription.getText().toString().trim();
        String etOwnServerSDNSText = etOwnServerSDNS.getText().toString().trim().replace("sdns://", "");

        if (etOwnServerNameText.isEmpty() || etOwnServerSDNSText.length() < 8) {
            return false;
        }

        if (etOwnServerDescriptionText.isEmpty()) {
            etOwnServerDescriptionText = etOwnServerNameText;
        }

        try {
            Base64.decode(etOwnServerSDNSText.substring(0, 7).getBytes(), 16);
        } catch (Exception e) {
            logw("Trying to add wrong DNSCrypt server "
                    + etOwnServerNameText + " " + etOwnServerDescriptionText
                    + " " + etOwnServerSDNSText);
            return false;
        }

        try {
            DnsServerFeatures features = new DnsServerFeatures(context, defaultPreferences.get());
            DnsServerItem item = new DnsServerItem(etOwnServerNameText, etOwnServerDescriptionText, etOwnServerSDNSText, features);
            item.setOwnServer(true);

            if (onServerAddedListener != null) {
                onServerAddedListener.onServerAdded(item);
            }
        } catch (Exception e) {
            logw("Trying to add wrong DNSCrypt server " + e.getMessage() + " "
                    + etOwnServerNameText + " " + etOwnServerDescriptionText
                    + " " + etOwnServerSDNSText);
            return false;
        }

        return true;
    }
}
