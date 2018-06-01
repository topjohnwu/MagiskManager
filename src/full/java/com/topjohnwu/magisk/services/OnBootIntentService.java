package com.topjohnwu.magisk.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.utils.Const;
import com.topjohnwu.magisk.utils.Utils;
import com.topjohnwu.superuser.Shell;

public class OnBootIntentService extends IntentService {

    public OnBootIntentService() {
        super("OnBootIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this, Const.ID.NOTIFICATION_CHANNEL);
            builder.setSmallIcon(R.drawable.ic_magisk)
                    .setContentTitle("onBoot")
                    .setContentText("Running onBoot operations...");
            startForeground(Const.ID.ONBOOT_NOTIFICATION_ID, builder.build());
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        /* Pixel 2 (XL) devices will need to patch dtbo.img.
         * However, that is not possible if Magisk is installed by
         * patching boot image with Magisk Manager and fastboot flash
         * the boot image, since at that time we do not have root.
         * Check for dtbo status every boot time, and prompt user
         * to reboot if dtbo wasn't patched and patched by Magisk Manager.
         * */
        MagiskManager mm = Utils.getMagiskManager(this);
        mm.loadMagiskInfo();
        mm.getDefaultInstallFlags();
        if (Shell.rootAccess()) {
            Utils.patchDTBO();
        }
    }
}
