package com.topjohnwu.magisk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.topjohnwu.magisk.asyncs.CheckUpdates;
import com.topjohnwu.magisk.asyncs.LoadModules;
import com.topjohnwu.magisk.asyncs.ParallelTask;
import com.topjohnwu.magisk.asyncs.UpdateRepos;
import com.topjohnwu.magisk.components.Activity;
import com.topjohnwu.magisk.services.UpdateCheckService;
import com.topjohnwu.magisk.utils.Const;
import com.topjohnwu.magisk.utils.Utils;
import com.topjohnwu.superuser.Shell;

public class SplashActivity extends Activity {

    @Override
    public int getDarkTheme() {
        return -1;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MagiskManager mm = getMagiskManager();

        mm.loadMagiskInfo();
        mm.getDefaultInstallFlags();
        Utils.loadPrefs();

        // Dynamic detect all locales
        new LoadLocale().exec();

        // Create notification channel on Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(Const.ID.NOTIFICATION_CHANNEL,
                    getString(R.string.magisk_updates), NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        LoadModules loadModuleTask = new LoadModules();

        if (Utils.checkNetworkStatus()) {

            // Fire update check
            new CheckUpdates().exec();

            // Add repo update check
            loadModuleTask.setCallBack(() -> new UpdateRepos(false).exec());
        }

        // Magisk working as expected
        if (Shell.rootAccess() && mm.magiskVersionCode > 0) {

            // Add update checking service
            if (Const.UPDATE_SERVICE_VER > mm.prefs.getInt(Const.Key.UPDATE_SERVICE_VER, -1)) {
                ComponentName service = new ComponentName(this, UpdateCheckService.class);
                JobInfo info = new JobInfo.Builder(Const.ID.UPDATE_SERVICE_ID, service)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setPeriodic(8 * 60 * 60 * 1000)
                        .build();
                ((JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(info);
            }

            // Fire asynctasks
            loadModuleTask.exec();

            // Check dtbo status
            Utils.patchDTBO();
        }

        // Write back default values
        mm.writeConfig();

        mm.hasInit = true;

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Const.Key.OPEN_SECTION, getIntent().getStringExtra(Const.Key.OPEN_SECTION));
        intent.putExtra(Const.Key.INTENT_PERM, getIntent().getStringExtra(Const.Key.INTENT_PERM));
        startActivity(intent);
        finish();
    }

    static class LoadLocale extends ParallelTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            MagiskManager.get().locales = Utils.getAvailableLocale();
            return null;
        }
        @Override
        protected void onPostExecute(Void aVoid) {
            MagiskManager.get().localeDone.publish();
        }
    }
}
