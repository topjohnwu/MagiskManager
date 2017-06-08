package com.topjohnwu.magisk.utils;

import android.support.v4.app.FragmentActivity;

public abstract class SafetyNetHelper {

    protected FragmentActivity mActivity;

    public SafetyNetHelper(FragmentActivity activity) {
    }

    // Entry point to start test
    public void requestTest() {
    }



    // Callback function to save the results
    public abstract void handleResults(Result result);

    public static class Result {
        public boolean failed = true;
        public String errmsg;
        public boolean ctsProfile = false;
        public boolean basicIntegrity = false;
    }
}
