package com.topjohnwu.magisk.utils;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.topjohnwu.magisk.Global;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.receivers.DownloadReceiver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

public class Utils {

    public static boolean isDownloading = false;

    public static boolean itemExist(String path) {
        return itemExist(true, path);
    }

    public static boolean itemExist(boolean root, String path) {
        String command = "if [ -e " + path + " ]; then echo true; else echo false; fi";
        List<String> ret;
        if (Shell.rootAccess() && root) {
            ret = Shell.su(command);
            return isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(0));
        } else {
            return new File(path).exists();
        }
    }

    public static boolean commandExists(String s) {
        String command = "if [ -z $(which " + s + ") ]; then echo false; else echo true; fi";
        List<String> ret = Shell.sh(command);
        return isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(0));
    }

    public static boolean createFile(String path) {
        String folder = path.substring(0, path.lastIndexOf('/'));
        String command = "mkdir -p " + folder + " 2>/dev/null; touch " + path + " 2>/dev/null; if [ -f \"" + path + "\" ]; then echo true; else echo false; fi";
        List<String> ret = Shell.su(command);
        return isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(0));
    }

    public static boolean removeItem(String path) {
        String command = "rm -rf " + path + " 2>/dev/null; if [ -e " + path + " ]; then echo false; else echo true; fi";
        List<String> ret = Shell.su(command);
        return isValidShellResponse(ret) && Boolean.parseBoolean(ret.get(0));
    }

    public static List<String> getModList(String path) {
        List<String> ret;
        String command = "find " + path + " -type d -maxdepth 1 ! -name \"*.core\" ! -name \"*lost+found\" ! -name \"*magisk\"";
        ret = Shell.su(command);
        return ret;
    }

    public static List<String> readFile(String path) {
        List<String> ret;
        String command = "cat " + path;
        if (Shell.rootAccess()) {
            ret = Shell.su(command);
        } else {
            ret = Shell.sh(command);
        }
        return ret;
    }

    public static String readFirstLine(String path) {
        List<String> ret;
        String command = "head -1 " + path;
        if (Shell.rootAccess()) {
            ret = Shell.su(command);
        } else {
            ret = Shell.sh(command);
        }
        return isValidShellResponse(ret) ? ret.get(0) : null;
    }

    public static void dlAndReceive(Context context, DownloadReceiver receiver, String link, String filename) {
        if (isDownloading) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            return;
        }

        File file = new File(Environment.getExternalStorageDirectory() + "/MagiskManager/" + filename);

        if ((!file.getParentFile().exists() && !file.getParentFile().mkdirs()) || (file.exists() && !file.delete())) {
            Toast.makeText(context, R.string.permissionNotGranted, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(context, context.getString(R.string.downloading_toast, filename), Toast.LENGTH_LONG).show();
        isDownloading = true;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
        request.setDestinationUri(Uri.fromFile(file));

        receiver.setDownloadID(downloadManager.enqueue(request));
        receiver.setFilename(filename);
        context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public static String getLegalFilename(CharSequence filename) {
        return filename.toString().replace(" ", "_").replace("'", "").replace("\"", "")
                .replace("$", "").replace("`", "").replace("(", "").replace(")", "")
                .replace("#", "").replace("@", "").replace("*", "");
    }

    public static String detectBootImage() {
        String[] commands = {
                "for PARTITION in kern-a KERN-A android_boot ANDROID_BOOT kernel KERNEL boot BOOT lnx LNX; do",
                "BOOTIMAGE=`readlink /dev/block/by-name/$PARTITION || readlink /dev/block/platform/*/by-name/$PARTITION || readlink /dev/block/platform/*/*/by-name/$PARTITION`",
                "if [ ! -z \"$BOOTIMAGE\" ]; then break; fi",
                "done",
                "echo \"${BOOTIMAGE##*/}\""
        };
        List<String> ret = Shell.su(commands);
        if (isValidShellResponse(ret))
            return ret.get(0);
        return null;
    }

    public static AlertDialog.Builder getAlertDialogBuilder(Context context) {
        if (Global.Configs.isDarkTheme) {
            return new AlertDialog.Builder(context, R.style.AlertDialog_dh);
        } else {
            return new AlertDialog.Builder(context);
        }
    }

    public static boolean lowercaseContains(CharSequence string, CharSequence nonNullLowercaseSearch) {
        return !TextUtils.isEmpty(string) && string.toString().toLowerCase().contains(nonNullLowercaseSearch);
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

}