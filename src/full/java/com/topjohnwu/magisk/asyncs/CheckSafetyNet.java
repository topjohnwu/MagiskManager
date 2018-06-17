package com.topjohnwu.magisk.asyncs;

import android.app.Activity;

import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.utils.Const;
import com.topjohnwu.magisk.utils.ISafetyNetHelper;
import com.topjohnwu.magisk.utils.WebService;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import dalvik.system.DexClassLoader;

public class CheckSafetyNet extends ParallelTask<Void, Void, Exception> {

    public static final File dexPath =
            new File(MagiskManager.get().getFilesDir().getParent() + "/snet", "snet.apk");
    private ISafetyNetHelper helper;

    public CheckSafetyNet(Activity activity) {
        super(activity);
    }

    private void dlSnet() throws Exception {
        Shell.Sync.sh("rm -rf " + dexPath.getParent());
        dexPath.getParentFile().mkdir();
        HttpURLConnection conn = WebService.request(Const.Url.SNET_URL, null);
        try (
                OutputStream out = new BufferedOutputStream(new FileOutputStream(dexPath));
                InputStream in = new BufferedInputStream(conn.getInputStream())) {
            ShellUtils.pump(in, out);
        } finally {
            conn.disconnect();
        }
    }

    private void dyload() throws Exception {
        DexClassLoader loader = new DexClassLoader(dexPath.getPath(), dexPath.getParent(),
                null, ISafetyNetHelper.class.getClassLoader());
        Class<?> clazz = loader.loadClass("com.topjohnwu.snet.SafetyNetHelper");
        helper = (ISafetyNetHelper) clazz.getConstructors()[0]
                .newInstance(getActivity(), (ISafetyNetHelper.Callback)
                        code -> MagiskManager.get().safetyNetDone.publish(false, code));
        if (helper.getVersion() != Const.SNET_VER) {
            throw new Exception();
        }
    }

    @Override
    protected Exception doInBackground(Void... voids) {
        try {
            try {
                dyload();
            } catch (Exception e) {
                // If dynamic load failed, try re-downloading and reload
                dlSnet();
                dyload();
            }
        } catch (Exception e) {
            return e;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Exception e) {
        if (e == null) {
            helper.attest();
        } else {
            e.printStackTrace();
            MagiskManager.get().safetyNetDone.publish(false, -1);
        }
        super.onPostExecute(e);
    }
}
