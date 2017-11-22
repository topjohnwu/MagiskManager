package com.topjohnwu.magisk.utils;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.SplashActivity;
import com.topjohnwu.magisk.components.SnackbarMaker;
import com.topjohnwu.magisk.receivers.DownloadReceiver;

import java.io.File;
import java.io.FileInputStream; // checkFileDigest
import java.io.FileNotFoundException; // checkFileDigest
import java.io.IOException;
import java.io.IOException; // checkFileDigest
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import java.security.MessageDigest; // checkFileDigest
import java.security.NoSuchAlgorithmException; // checkFileDigest

public class Utils {

    public static boolean isDownloading = false;

    public static boolean itemExist(String path) {
        String command = "[ -e " + path + " ] && echo true || echo false";
        List<String> ret = Shell.su(command);
        return isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(0));
    }

    public static void createFile(String path) {
        String folder = path.substring(0, path.lastIndexOf('/'));
        String command = "mkdir -p " + folder + " 2>/dev/null; touch " + path + " 2>/dev/null;";
        Shell.su_raw(command);
    }

    public static void removeItem(String path) {
        String command = "rm -rf " + path + " 2>/dev/null";
        Shell.su_raw(command);
    }

    public static List<String> readFile(String path) {
        String command = "cat " + path + " | sed '$a\\ ' | sed '$d'";
        return Shell.su(command);
    }

    public static boolean checkFileDigest(File file, String digestType, String digest) {
        if ( (file == null) || (digest == null) || (digestType == null) || !file.exists() || digest.trim().isEmpty() )
            return false;

        MessageDigest fileDigest; // Tested with "MD5" and "SHA1"
        try {
            fileDigest = MessageDigest.getInstance(digestType);
        } catch (NoSuchAlgorithmException nsae) { return false; }

        FileInputStream stream;
        try {
            stream = new FileInputStream(file);
        } catch (FileNotFoundException fnfe) { return false; }

        fileDigest.reset();

        byte[] readbuffer = new byte[32 * 1024];

        boolean readDone=false;
        try {
            int nread = 0;
            while ( (nread = stream.read(readbuffer)) != -1 ) {
                fileDigest.update(readbuffer, 0, nread);
            }
            readDone=true;
        } catch (IOException ioe) { }

        try {
            stream.close();
        } catch (IOException ioe) { return false; }

        if (!readDone)
            return false;

        byte[] b = fileDigest.digest();

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            sb.append(Integer.toString(((b[i] & 0xff) | 0x100), 16).substring(1));
        }

        return sb.toString().equalsIgnoreCase(digest.trim());
    }

    public static void dlAndReceive(Context context, DownloadReceiver receiver, String link, String filename) {
    {
        dlAndReceive(context, receiver, link, filename, null, null);
    }

    public static void dlAndReceive(Context context, DownloadReceiver receiver, String link, String filename, String digestType, String digest) {
        if (isDownloading)
            return;

        runWithPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, () -> {
            File file = new File(Const.EXTERNAL_PATH, filename);

            if (checkFileDigest(filename, digestType, digest))
            {
                Uri uri = Uri.parse(link);
                receiver.onDownloadDone(uri);
                return;
            }

            if ((!file.getParentFile().exists() && !file.getParentFile().mkdirs())
                    || (file.exists() && !file.delete())) {
                Toast.makeText(context, R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(context, context.getString(R.string.downloading_toast, filename), Toast.LENGTH_LONG).show();
            isDownloading = true;

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            if (link != null) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
                request.setDestinationUri(Uri.fromFile(file));
                receiver.setDownloadID(downloadManager.enqueue(request));
            }
            receiver.setFilename(filename);
            context.getApplicationContext().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        });
    }

    public static String getLegalFilename(CharSequence filename) {
        return filename.toString().replace(" ", "_").replace("'", "").replace("\"", "")
                .replace("$", "").replace("`", "").replace("(", "").replace(")", "")
                .replace("#", "").replace("@", "").replace("*", "");
    }

    public static boolean isValidShellResponse(List<String> list) {
        if (list != null && list.size() != 0) {
            // Check if all empty
            for (String res : list) {
                if (!TextUtils.isEmpty(res)) return true;
            }
        }
        return false;
    }

    public static int getPrefsInt(SharedPreferences prefs, String key, int def) {
        return Integer.parseInt(prefs.getString(key, String.valueOf(def)));
    }

    public static int getPrefsInt(SharedPreferences prefs, String key) {
        return getPrefsInt(prefs, key, 0);
    }

    public static MagiskManager getMagiskManager(Context context) {
        return (MagiskManager) context.getApplicationContext();
    }

    public static String getNameFromUri(Context context, Uri uri) {
        String name = null;
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null) {
                int nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    c.moveToFirst();
                    name = c.getString(nameIndex);
                }
            }
        }
        if (name == null) {
            int idx = uri.getPath().lastIndexOf('/');
            name = uri.getPath().substring(idx + 1);
        }
        return name;
    }

    public static void showUriSnack(Activity activity, Uri uri) {
        SnackbarMaker.make(activity, activity.getString(R.string.internal_storage,
                "/MagiskManager/" + Utils.getNameFromUri(activity, uri)),
                Snackbar.LENGTH_LONG)
                .setAction(R.string.ok, (v)->{}).show();
    }

    public static boolean checkNetworkStatus() {
        ConnectivityManager manager = (ConnectivityManager)
                MagiskManager.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    public static String getLocaleString(Locale locale, @StringRes int id) {
        Context context = MagiskManager.get();
        Configuration config = context.getResources().getConfiguration();
        config.setLocale(locale);
        Context localizedContext = context.createConfigurationContext(config);
        return localizedContext.getString(id);
    }

    public static List<Locale> getAvailableLocale() {
        List<Locale> locales = new ArrayList<>();
        HashSet<String> set = new HashSet<>();
        Locale locale;

        @StringRes int compareId = R.string.download_file_error;

        // Add default locale
        locales.add(Locale.ENGLISH);
        set.add(getLocaleString(Locale.ENGLISH, compareId));

        // Add some special locales
        locales.add(Locale.TAIWAN);
        set.add(getLocaleString(Locale.TAIWAN, compareId));
        locale = new Locale("pt", "BR");
        locales.add(locale);
        set.add(getLocaleString(locale, compareId));

        // Other locales
        for (String s : MagiskManager.get().getAssets().getLocales()) {
            locale = Locale.forLanguageTag(s);
            if (set.add(getLocaleString(locale, compareId))) {
                locales.add(locale);
            }
        }

        Collections.sort(locales, (l1, l2) -> l1.getDisplayName(l1).compareTo(l2.getDisplayName(l2)));

        return locales;
    }

    public static void runWithPermission(Context context, String permission, Runnable callback) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            // Passed in context should be an activity if not granted, need to show dialog!
            Utils.getMagiskManager(context).setPermissionGrantCallback(callback);
            if (!(context instanceof com.topjohnwu.magisk.components.Activity)) {
                // Start activity to show dialog
                Intent intent = new Intent(context, SplashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Const.Key.INTENT_PERM, permission);
                context.startActivity(intent);
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[] { permission }, 0);
            }

        } else {
            callback.run();
        }
    }

    public static File getDatabasePath(Context context, String dbName) {
        return new File(context.getFilesDir().getParent() + "/databases", dbName);
    }

    public static AssetManager getAssets(String apk) {
        try {
            AssetManager asset = AssetManager.class.newInstance();
            AssetManager.class.getMethod("addAssetPath", String.class).invoke(asset, apk);
            return asset;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int inToOut(InputStream in, OutputStream out) throws IOException {
        int read, total = 0;
        byte buffer[] = new byte[4096];
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            total += read;
        }
        out.flush();
        return total;
    }

    public static void patchDTBO() {
        if (MagiskManager.get().magiskVersionCode >= 1446) {
            List<String> ret = Shell.su("patch_dtbo_image && echo true || echo false");
            if (Utils.isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(ret.size() - 1))) {
                ShowUI.dtboPatchedNotification();
            }
        }
    }

    public static int dpInPx(int dp) {
        Context context = MagiskManager.get();
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5);
    }
}
