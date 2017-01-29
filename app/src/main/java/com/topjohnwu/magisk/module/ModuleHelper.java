package com.topjohnwu.magisk.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.topjohnwu.magisk.Global;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.utils.Async;
import com.topjohnwu.magisk.utils.Logger;
import com.topjohnwu.magisk.utils.Utils;
import com.topjohnwu.magisk.utils.ValueSortedMap;
import com.topjohnwu.magisk.utils.WebService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModuleHelper {

    private static final String MAGISK_PATH = "/magisk";

    private static final int GSON_DB_VER = 1;
    private static final String ETAG_KEY = "ETag";
    private static final String VERSION_KEY = "version";
    private static final String REPO_KEY = "repomap";
    private static final String FILE_KEY = "RepoMap";

    public static void createModuleMap() {
        Logger.dev("ModuleHelper: Loading modules");

        Global.Data.moduleMap.clear();

        for (String path : Utils.getModList(MAGISK_PATH)) {
            Logger.dev("ModuleHelper: Adding modules from " + path);
            Module module;
            try {
                module = new Module(path);
                Global.Data.moduleMap.put(module.getId(), module);
            } catch (BaseModule.CacheModException ignored) {}
        }

        Logger.dev("ModuleHelper: Data load done");
    }

    public static void createRepoMap(Context context) {
        Logger.dev("ModuleHelper: Loading repos");

        SharedPreferences prefs = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE);

        Global.Data.repoMap.clear();

        Gson gson = new Gson();
        String jsonString;

        int cachedVersion = prefs.getInt(VERSION_KEY, 0);
        if (cachedVersion != GSON_DB_VER) {
            // Ignore incompatible cached database
            jsonString = null;
        } else {
            jsonString = prefs.getString(REPO_KEY, null);
        }

        Map<String, Repo> cached = null;

        if (jsonString != null) {
            cached = gson.fromJson(jsonString, new TypeToken<ValueSortedMap<String, Repo>>(){}.getType());
        }

        if (cached == null) {
            cached = new ValueSortedMap<>();
        }

        // Get cached ETag to add in the request header
        String etag = prefs.getString(ETAG_KEY, "");
        Map<String, String> header = new HashMap<>();
        header.put("If-None-Match", etag);

        // Making a request to main URL for repo info
        jsonString = WebService.request(
                context.getString(R.string.url_main), WebService.GET, null, header, false);

        if (!jsonString.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(jsonString);
                // If it gets to this point, the response is valid, update ETag
                etag = WebService.getLastResponseHeader().get(ETAG_KEY).get(0);
                // Maybe bug in Android build tools, sometimes the ETag has crap in it...
                etag = etag.substring(etag.indexOf('\"'), etag.lastIndexOf('\"') + 1);

                // Update repo info
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonobject = jsonArray.getJSONObject(i);
                    String id = jsonobject.getString("description");
                    String name = jsonobject.getString("name");
                    String lastUpdate = jsonobject.getString("pushed_at");
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    Date updatedDate;
                    try {
                        updatedDate = format.parse(lastUpdate);
                    } catch (ParseException e) {
                        continue;
                    }
                    Repo repo = cached.get(id);
                    try {
                        if (repo == null) {
                            Logger.dev("ModuleHelper: Create new repo " + id);
                            repo = new Repo(context, name, updatedDate);
                        } else {
                            Logger.dev("ModuleHelper: Update cached repo " + id);
                            repo.update(updatedDate);
                        }
                        if (repo.getId() != null) {
                            Global.Data.repoMap.put(id, repo);
                        }
                    } catch (BaseModule.CacheModException ignored) {}
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            // Use cached if no internet or no updates
            Logger.dev("ModuleHelper: No updates, use cached");
            Global.Data.repoMap.putAll(cached);
        }

        prefs.edit()
                .putInt(VERSION_KEY, GSON_DB_VER)
                .putString(REPO_KEY, gson.toJson(Global.Data.repoMap))
                .putString(ETAG_KEY, etag)
                .apply();

        Logger.dev("ModuleHelper: Repo load done");
    }

    public static void getModuleList(List<Module> moduleList) {
        moduleList.clear();
        moduleList.addAll(Global.Data.moduleMap.values());
    }

    public static void getRepoLists(List<Repo> update, List<Repo> installed, List<Repo> others) {
        update.clear();
        installed.clear();
        others.clear();
        for (Repo repo : Global.Data.repoMap.values()) {
            Module module = Global.Data.moduleMap.get(repo.getId());
            if (module != null) {
                if (repo.getVersionCode() > module.getVersionCode()) {
                    update.add(repo);
                } else {
                    installed.add(repo);
                }
            } else {
                others.add(repo);
            }
        }
    }

    public static void clearRepoCache(Context context) {
        SharedPreferences repoMap = context.getSharedPreferences(FILE_KEY, Context.MODE_PRIVATE);
        repoMap.edit()
                .remove(ETAG_KEY)
                .remove(VERSION_KEY)
                .apply();
        Global.Events.repoLoadDone.isTriggered = false;
        new Async.LoadRepos(context).exec();
        Toast.makeText(context, R.string.repo_cache_cleared, Toast.LENGTH_SHORT).show();
    }

}
