/*
 *
 * Copyright (C) 2010 Colibria AS
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.colibria.android.sipservice;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import com.colibria.android.sipservice.sip.Address;
import com.colibria.android.sipservice.sip.URI;

import java.net.InetSocketAddress;

/**
 * @author Sebastian Dehne
 */
public class SipServiceSettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    // below names must match with the 'key' attributes from the preferences.xml file
    private static final String PREF_KEY_DISPLAYNAME = "prefDisplayName";
    private static final String PREF_KEY_SIP_URI = "prefSipUri";
    private static final String PREF_KEY_TEL_URI = "prefTelUri";
    private static final String PREF_KEY_CONFERENCE_FACTORY_URI = "prefConferenceFacUri";
    private static final String PREF_KEY_USERNAME = "prefUsername";
    private static final String PREF_KEY_PASSWORD = "prefPassword";
    private static final String PREF_KEY_PROXY_HOST = "prefSipProxyHost";
    private static final String PREF_KEY_PROXY_PORT = "prefSipProxyPort";
    private static final String PREF_KEY_SERVICE_ENABLED = "prefEnableService";
    private static final String PREF_KEY_SERVICE_STATUS = "prefServiceStatus";
    private static final String PREF_KEY_SERVICE_VERSION = "prefVersion";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // inflate layout
        addPreferencesFromResource(R.layout.preferences);

        // start listening for preference changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // ensure service instance is up
        startService(new Intent("com.colibria.android.sipservice.SipService"));
    }

    private void bindToServiceIfPossible() {
        final int waitMax = 500;
        long started = System.currentTimeMillis();
        while ((System.currentTimeMillis() - started) < waitMax) {
            if (SipService.sInstance == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    //noop
                }
            } else
                break;
        }
        if (SipService.sInstance != null)
            SipService.sInstance.setActiveInstance(this);
    }

    private void unbindFromConversationIfNeeded() {
        SipService.sInstance.setActiveInstance(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // we want to receive status updates from now on
        bindToServiceIfPossible();

        // set the summary field on screen for all preferences
        updateAllEntries();

        Preference p = findPreference(PREF_KEY_SERVICE_VERSION);
        p.setSummary(SipService.VERSION_STRING);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // we don't need any further status updates from the service
        unbindFromConversationIfNeeded();
    }

    /**
     * Called by the SipService to notify that something has changed.
     * This only works because we run the GUI in the same process/jvm as the
     * SipService.
     */
    public void stateChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateAllEntries();
            }
        });
    }

    private void updateAllEntries() {
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        Preference p;
        boolean serviceEnabled = sp.getBoolean(PREF_KEY_SERVICE_ENABLED, false);

        // sip enable/disable
        CheckBoxPreference cp = (CheckBoxPreference) findPreference(PREF_KEY_SERVICE_ENABLED);
        cp.setChecked(serviceEnabled);

        // sip status
        p = findPreference(PREF_KEY_SERVICE_STATUS);
        p.setSummary(sp.getString(PREF_KEY_SERVICE_STATUS, getCurrentStateAsString()));

        // settings...

        p = findPreference(PREF_KEY_DISPLAYNAME);
        p.setSummary(sp.getString(PREF_KEY_DISPLAYNAME, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_SIP_URI);
        p.setSummary(sp.getString(PREF_KEY_SIP_URI, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_TEL_URI);
        p.setSummary(sp.getString(PREF_KEY_TEL_URI, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_CONFERENCE_FACTORY_URI);
        p.setSummary(sp.getString(PREF_KEY_CONFERENCE_FACTORY_URI, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_USERNAME);
        p.setSummary(sp.getString(PREF_KEY_USERNAME, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_PASSWORD);
        if (sp.getString(PREF_KEY_PASSWORD, "").length() > 0)
            p.setSummary("********");
        else
            p.setSummary("");
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_PROXY_HOST);
        p.setSummary(sp.getString(PREF_KEY_PROXY_HOST, ""));
        p.setEnabled(!serviceEnabled);

        p = findPreference(PREF_KEY_PROXY_PORT);
        p.setSummary(sp.getString(PREF_KEY_PROXY_PORT, ""));
        p.setEnabled(!serviceEnabled);
    }

    private String validate() {
        SharedPreferences p = getPreferenceScreen().getSharedPreferences();

        String tmp = p.getString(PREF_KEY_DISPLAYNAME, null);
        if (tmp == null || tmp.length() == 0)
            return getResources().getString(R.string.config_invalid_display_name);


        String uri = p.getString(PREF_KEY_SIP_URI, "");
        URI result = URI.fromString(uri);
        if (result == null || result.getType() == URI.Type.tel) {
            return getResources().getString(R.string.config_invalid_sip_uri);
        }
        uri = p.getString(PREF_KEY_TEL_URI, "");
        result = URI.fromString(uri);
        if (result == null || result.getType() != URI.Type.tel) {
            return getResources().getString(R.string.config_invalid_tel_uri);
        }

        uri = p.getString(PREF_KEY_CONFERENCE_FACTORY_URI, "");
        result = URI.fromString(uri);
        if (result == null) {
            return getResources().getString(R.string.config_invalid_conf_fac);
        }

        tmp = p.getString(PREF_KEY_PROXY_HOST, null);
        if (tmp == null || tmp.length() == 0)
            return getResources().getString(R.string.config_invalid_proxy_host);

        int port;
        try {
            port = Integer.parseInt(p.getString(PREF_KEY_PROXY_PORT, "-1"));
        } catch (NumberFormatException e) {
            return getResources().getString(R.string.config_invalid_proxy_port);
        }
        if (port < 0 || port > 65536) {
            return getResources().getString(R.string.config_invalid_proxy_port);
        }

        if ((tmp = p.getString(PREF_KEY_USERNAME, null)) == null || tmp.length() == 0) {
            return getResources().getString(R.string.config_invalid_auth_username);
        }

        if ((tmp = p.getString(PREF_KEY_PASSWORD, null)) == null || tmp.length() == 0) {
            return getResources().getString(R.string.config_invalid_auth_password);
        }

        return null;
    }

    private String getCurrentStateAsString() {
        String validation = validate();
        if (validation != null) {
            return String.format(getResources().getString(R.string.service_status_unconfigured_error), validation);
        } else {
            SipService sipService = SipService.sInstance;
            if (sipService != null) {
                switch (sipService.getCurrentState()) {
                    case SipService.SIP_SERVICE_STATE_UNCONFIGURED:
                        return getResources().getString(R.string.service_status_unconfigured);
                    case SipService.SIP_SERVICE_STATE_DISABLED:
                        return getResources().getString(R.string.service_status_disabled);
                    case SipService.SIP_SERVICE_STATE_CONNECTING:
                        return getResources().getString(R.string.service_status_connecting);
                    case SipService.SIP_SERVICE_STATE_CONNECTED:
                        return getResources().getString(R.string.service_status_connected);
                    case SipService.SIP_SERVICE_STATE_REGISTERED:
                        return getResources().getString(R.string.service_status_registered);
                    default:
                        break;
                }
            }
            return getResources().getString(android.R.string.untitled);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEY_SERVICE_ENABLED.equals(key)) {
            if (sharedPreferences.getBoolean(PREF_KEY_SERVICE_ENABLED, false)) {
                if (validate() == null) {
                    // start sip-service now
                    InetSocketAddress proxyAddress = new InetSocketAddress(
                            sharedPreferences.getString(PREF_KEY_PROXY_HOST, null),
                            Integer.parseInt(sharedPreferences.getString(PREF_KEY_PROXY_PORT, null))
                    );
                    Address localUser = Address.fromString(sharedPreferences.getString(PREF_KEY_DISPLAYNAME, null) + "<" + sharedPreferences.getString(PREF_KEY_SIP_URI, null) + ">");
                    SipService.sInstance.stayConnected(
                            proxyAddress,
                            localUser,
                            sharedPreferences.getString(PREF_KEY_CONFERENCE_FACTORY_URI, ""),
                            sharedPreferences.getString(PREF_KEY_USERNAME, null),
                            sharedPreferences.getString(PREF_KEY_PASSWORD, null)

                    );
                }
            } else {
                // stop sip-service now
                if (SipService.sInstance != null)
                    SipService.sInstance.giveUp();
            }
        }

        updateAllEntries();
    }
}
