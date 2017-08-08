package com.topjohnwu.magisk;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import com.topjohnwu.magisk.components.Activity;
import com.topjohnwu.magisk.database.SuDatabaseHelper;
import com.topjohnwu.magisk.utils.Logger;
import com.topjohnwu.magisk.utils.Shell;
import com.topjohnwu.magisk.utils.Topic;
import com.topjohnwu.magisk.utils.Utils;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SettingsActivity extends Activity implements Topic.Subscriber {

    @BindView(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getApplicationContext().isDarkTheme) {
            setTheme(R.style.AppTheme_Transparent_Dark);
        }

        setContentView(R.layout.activity_settings);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(view -> finish());

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.settings);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating();

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.container, new SettingsFragment()).commit();
        }

    }

    @Override
    public void onTopicPublished(Topic topic) {
        recreate();
    }

    @Override
    public Topic[] getSubscription() {
        return new Topic[] { getApplicationContext().reloadActivity };
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener,
            Topic.Subscriber {

        private SharedPreferences prefs;
        private PreferenceScreen prefScreen;

        private ListPreference suAccess, autoRes, suNotification, requestTimeout, multiuserMode, namespaceMode;
        private MagiskManager magiskManager;
        private PreferenceCategory generalCatagory;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.app_settings);
            prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefScreen = getPreferenceScreen();
            magiskManager = Utils.getMagiskManager(getActivity());

            generalCatagory = (PreferenceCategory) findPreference("general");
            PreferenceCategory magiskCategory = (PreferenceCategory) findPreference("magisk");
            PreferenceCategory suCategory = (PreferenceCategory) findPreference("superuser");
            PreferenceCategory developer = (PreferenceCategory) findPreference("developer");

            suAccess = (ListPreference) findPreference("su_access");
            autoRes = (ListPreference) findPreference("su_auto_response");
            requestTimeout = (ListPreference) findPreference("su_request_timeout");
            suNotification = (ListPreference) findPreference("su_notification");
            multiuserMode = (ListPreference) findPreference("multiuser_mode");
            namespaceMode = (ListPreference) findPreference("mnt_ns");
            SwitchPreference reauth = (SwitchPreference) findPreference("su_reauth");

            setSummary();

            // Disable dangerous settings in user mode if selected owner manage
            if (getActivity().getApplicationInfo().uid > 99999) {
                prefScreen.removePreference(magiskCategory);
                prefScreen.removePreference(suCategory);
            }

            // Remove re-authentication option on Android O, it will not work
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                suCategory.removePreference(reauth);
            }

            findPreference("clear").setOnPreferenceClickListener((pref) -> {
                Utils.clearRepoCache(getActivity());
                return true;
            });

            if (!BuildConfig.DEBUG) {
                prefScreen.removePreference(developer);
            }

            if (!Shell.rootAccess()) {
                prefScreen.removePreference(magiskCategory);
                prefScreen.removePreference(suCategory);
            } else {
                if (!magiskManager.isSuClient) {
                    prefScreen.removePreference(suCategory);
                }
                if (magiskManager.magiskVersionCode < 1300) {
                    prefScreen.removePreference(magiskCategory);
                }
            }
            
            // Remove request timeout if Auto response is set to 'grant'
            if (autoRes.getValue() == 2) {
                prefScreen.removePreference(requestTimeout);
            }
        }

        private void setLocalePreference(ListPreference lp) {
            boolean isNew = lp == null;
            if (isNew) {
                lp = new ListPreference(getActivity());
            }
            CharSequence[] entries = new CharSequence[magiskManager.locales.size() + 1];
            CharSequence[] entryValues = new CharSequence[magiskManager.locales.size() + 1];
            entries[0] = getString(R.string.system_default);
            entryValues[0] = "";
            int i = 1;
            for (Locale locale : magiskManager.locales) {
                entries[i] = locale.getDisplayName(locale);
                entryValues[i++] = locale.toLanguageTag();
            }
            lp.setEntries(entries);
            lp.setEntryValues(entryValues);
            lp.setTitle(R.string.language);
            lp.setKey("locale");
            lp.setSummary(MagiskManager.locale.getDisplayName(MagiskManager.locale));
            if (isNew) {
                generalCatagory.addPreference(lp);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            prefs.registerOnSharedPreferenceChangeListener(this);
            subscribeTopics();
        }

        @Override
        public void onPause() {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            unsubscribeTopics();
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Logger.dev("Settings: Prefs change " + key);
            boolean enabled;

            switch (key) {
                case "dark_theme":
                    enabled = prefs.getBoolean("dark_theme", false);
                    if (magiskManager.isDarkTheme != enabled) {
                        magiskManager.isDarkTheme = enabled;
                        magiskManager.reloadActivity.publish(false);
                    }
                    break;
                case "disable":
                    enabled = prefs.getBoolean("disable", false);
                    if (enabled) {
                        Utils.createFile(Shell.getShell(magiskManager), MagiskManager.MAGISK_DISABLE_FILE);
                    } else {
                        Utils.removeItem(Shell.getShell(magiskManager), MagiskManager.MAGISK_DISABLE_FILE);
                    }
                    Toast.makeText(getActivity(), R.string.settings_reboot_toast, Toast.LENGTH_LONG).show();
                    break;
                case "magiskhide":
                    enabled = prefs.getBoolean("magiskhide", false);
                    if (enabled) {
                        Utils.enableMagiskHide(Shell.getShell(magiskManager));
                    } else {
                        Utils.disableMagiskHide(Shell.getShell(magiskManager));
                    }
                    break;
                case "hosts":
                    enabled = prefs.getBoolean("hosts", false);
                    if (enabled) {
                        Shell.getShell(magiskManager).su_raw(
                                "cp -af /system/etc/hosts /magisk/.core/hosts",
                                "mount -o bind /magisk/.core/hosts /system/etc/hosts");
                    } else {
                        Shell.getShell(magiskManager).su_raw(
                                "umount -l /system/etc/hosts",
                                "rm -f /magisk/.core/hosts");
                    }
                    break;
                case "su_access":
                    magiskManager.suAccessState = Utils.getPrefsInt(prefs, "su_access", 3);
                    magiskManager.suDB.setSettings(SuDatabaseHelper.ROOT_ACCESS, magiskManager.suAccessState);
                    break;
                case "multiuser_mode":
                    magiskManager.multiuserMode = Utils.getPrefsInt(prefs, "multiuser_mode", 0);
                    magiskManager.suDB.setSettings(SuDatabaseHelper.MULTIUSER_MODE, magiskManager.multiuserMode);
                    break;
                case "mnt_ns":
                    magiskManager.suNamespaceMode = Utils.getPrefsInt(prefs, "mnt_ns", 1);
                    magiskManager.suDB.setSettings(SuDatabaseHelper.MNT_NS, magiskManager.suNamespaceMode);
                    break;
                case "su_request_timeout":
                    magiskManager.suRequestTimeout = Utils.getPrefsInt(prefs, "su_request_timeout", 10);
                    break;
                case "su_auto_response":
                    magiskManager.suResponseType = Utils.getPrefsInt(prefs, "su_auto_response", 0);
                    break;
                case "su_notification":
                    magiskManager.suNotificationType = Utils.getPrefsInt(prefs, "su_notification", 1);
                    break;
                case "developer_logging":
                    MagiskManager.devLogging = prefs.getBoolean("developer_logging", false);
                    break;
                case "shell_logging":
                    MagiskManager.shellLogging = prefs.getBoolean("shell_logging", false);
                    break;
                case "locale":
                    magiskManager.setLocale();
                    magiskManager.reloadActivity.publish(false);
                    break;
            }
            setSummary();
        }

        private void setSummary() {
            suAccess.setSummary(getResources()
                    .getStringArray(R.array.su_access)[magiskManager.suAccessState]);
            autoRes.setSummary(getResources()
                    .getStringArray(R.array.auto_response)[magiskManager.suResponseType]);
            suNotification.setSummary(getResources()
                    .getStringArray(R.array.su_notification)[magiskManager.suNotificationType]);
            requestTimeout.setSummary(
                    getString(R.string.request_timeout_summary, prefs.getString("su_request_timeout", "10")));
            multiuserMode.setSummary(getResources()
                    .getStringArray(R.array.multiuser_summary)[magiskManager.multiuserMode]);
            namespaceMode.setSummary(getResources()
                    .getStringArray(R.array.namespace_summary)[magiskManager.suNamespaceMode]);
        }

        @Override
        public void onTopicPublished(Topic topic) {
            setLocalePreference((ListPreference) findPreference("locale"));
        }

        @Override
        public Topic[] getSubscription() {
            return new Topic[] { magiskManager.localeDone };
        }
    }

}
