package com.topjohnwu.magisk;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.topjohnwu.magisk.utils.CallbackHandler;
import com.topjohnwu.magisk.utils.Shell;
import com.topjohnwu.magisk.utils.Utils;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, CallbackHandler.EventListener {

    private static final String SELECTED_ITEM_ID = "SELECTED_ITEM_ID";

    public static final CallbackHandler.Event recreate = new CallbackHandler.Event();

    private final Handler mDrawerHandler = new Handler();
    private SharedPreferences prefs;

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.drawer_layout) DrawerLayout drawer;
    @BindView(R.id.nav_view) public NavigationView navigationView;

    @IdRes
    private int mSelectedId = R.id.status;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (Utils.isDarkTheme) {
            setTheme(R.style.AppTheme_dh);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                super.onDrawerSlide(drawerView, 0); // this disables the arrow @ completed tate
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                super.onDrawerSlide(drawerView, 0); // this disables the animation
            }
        };

        drawer.addDrawerListener(toggle);
        toggle.syncState();

        //noinspection ResourceType
        mSelectedId = savedInstanceState == null ? mSelectedId : savedInstanceState.getInt(SELECTED_ITEM_ID);
        navigationView.setCheckedItem(mSelectedId);

        if (savedInstanceState == null) {
            mDrawerHandler.removeCallbacksAndMessages(null);
            mDrawerHandler.postDelayed(() -> navigate(mSelectedId), 250);
        }

        navigationView.setNavigationItemSelectedListener(this);
        CallbackHandler.register(recreate, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallbackHandler.register(StatusFragment.updateCheckDone, this);
        if (StatusFragment.updateCheckDone.isTriggered) {
            onTrigger(StatusFragment.updateCheckDone);
        }
        checkHideSection();
    }

    @Override
    protected void onPause() {
        CallbackHandler.unRegister(StatusFragment.updateCheckDone, this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        CallbackHandler.unRegister(recreate, this);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SELECTED_ITEM_ID, mSelectedId);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            finish();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        mSelectedId = menuItem.getItemId();
        mDrawerHandler.removeCallbacksAndMessages(null);
        mDrawerHandler.postDelayed(() -> navigate(menuItem.getItemId()), 250);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onTrigger(CallbackHandler.Event event) {
        if (event == StatusFragment.updateCheckDone) {
            Menu menu = navigationView.getMenu();
            menu.findItem(R.id.install).setVisible(StatusFragment.remoteMagiskVersion > 0 &&
                    Shell.rootAccess());
        } else if (event == recreate) {
            recreate();
        }
    }

    private void checkHideSection() {
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.magiskhide).setVisible(StatusFragment.magiskVersion >= 8 &&
                prefs.getBoolean("magiskhide", false) && Shell.rootAccess());
        menu.findItem(R.id.modules).setVisible(StatusFragment.magiskVersion >= 4 &&
                Shell.rootAccess());
        menu.findItem(R.id.downloads).setVisible(StatusFragment.magiskVersion >= 4 &&
                Shell.rootAccess());
        menu.findItem(R.id.log).setVisible(Shell.rootAccess());
        menu.findItem(R.id.install).setVisible(Shell.rootAccess());
    }

    public void navigate(final int itemId) {
        Fragment navFragment = null;
        String tag = "";
        switch (itemId) {
            case R.id.status:
                tag = "status";
                navFragment = new StatusFragment();
                break;
            case R.id.install:
                tag = "install";
                navFragment = new InstallFragment();
                break;
            case R.id.modules:
                tag = "modules";
                navFragment = new ModulesFragment();
                break;
            case R.id.downloads:
                tag = "downloads";
                navFragment = new ReposFragment();
                break;
            case R.id.magiskhide:
                tag = "magiskhide";
                navFragment = new MagiskHideFragment();
                break;
            case R.id.log:
                tag = "log";
                navFragment = new LogFragment();
                break;
            case R.id.settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.app_about:
                startActivity(new Intent(this, AboutActivity.class));
                return;
        }

        if (navFragment != null) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
            try {
                transaction.replace(R.id.content_frame, navFragment, tag).commit();
            } catch (IllegalStateException ignored) {}
        }
    }
}