package com.topjohnwu.magisk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by jLynx on 8/08/2016.
 */

public class BR extends BroadcastReceiver {

    public static final String TAG = "Magisk";

    private String suPATH;
    private String xbinPATH;

    private Switch rootSwitch, selinuxSwitch;
    private TextView rootStatus, selinuxStatus, safetyNet, permissive;

    Context contextG;


    @Override
    public void onReceive(Context context, Intent intent) {
        contextG = context;
        Log.v(TAG, "this is shown: " + intent.getAction());

        if(intent.getAction().equals("com.topjohnwu.magisk.ENABLE"))
        {
            Log.i(TAG,"Root ENABLED!");
            Toast.makeText(contextG, "Root ENABLED!",  Toast.LENGTH_LONG).show();
            setRoot(true);
        }
        else if(intent.getAction().equals("com.topjohnwu.magisk.DISABLE"))
        {
            Log.i(TAG,"Root DISABLED!");
            Toast.makeText(contextG, "Root DISABLED!",  Toast.LENGTH_LONG).show();
            setRoot(false);
        }
    }

    private void setRoot(boolean root)
    {
        boolean rooted = true;

        File phh = new File("/magisk/phh/su");
        File supersu = new File("/su/bin/su");

        if(!supersu.exists()) {
            if(!phh.exists()) {
                rooted = false;
            } else {
                suPATH = "/magisk/phh/su";
                xbinPATH = "/magisk/phh/xbin";
            }
        } else {
            suPATH = "/su/bin/su";
            xbinPATH = "/su/xbin";
        }

        if(rooted) {
//            (new callSU()).execute();

            if(root) {
//                (new callSU()).execute("mount -o bind " + xbinPATH + " /system/xbin");
                String[] ans = test("mount -o bind " + xbinPATH + " /system/xbin");
                Log.d(TAG, ans[0] + " - " + ans[1]);
//                Toast.makeText(getApplicationContext(), "Root Enabled", Toast.LENGTH_SHORT).show();
            }
            else {
//                (new callSU()).execute("umount /system/xbin");
                String[] ans = test("umount /system/xbin");
                Log.d(TAG, ans[0] + " - " + ans[1]);
                Log.i(TAG,"Root DISABLED2!");
//                Toast.makeText(getApplicationContext(), "Root Disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String[] test(String test)
    {
        String[] results = new String[2];
        try {
            Process su = Runtime.getRuntime().exec(suPATH);
            DataOutputStream out = new DataOutputStream(su.getOutputStream());
            DataInputStream in = new DataInputStream(su.getInputStream());
            out.writeBytes(test + "\n");
            out.flush();
            out.writeBytes("if [ -z $(which su) ]; then echo 0; else echo 1; fi;\n");
            out.flush();
            results[0] = in.readLine();
            out.writeBytes("getenforce\n");
            out.flush();
            results[1] = in.readLine();
            out.writeBytes("exit\n");
            out.flush();
        } catch (IOException e) { e.printStackTrace(); }
        return results;
    }
}
