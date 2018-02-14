package com.topjohnwu.magisk.asyncs;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.webkit.WebView;

import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.utils.WebService;
import com.topjohnwu.superuser.ShellUtils;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MarkDownWindow extends ParallelTask<Void, Void, String> {

    private String mTitle;
    private String mUrl;
    private InputStream is;


    public MarkDownWindow(Activity context, String title, String url) {
        super(context);
        mTitle = title;
        mUrl = url;
    }

    public MarkDownWindow(Activity context, String title, InputStream in) {
        super(context);
        mTitle = title;
        is = in;
    }

    @Override
    protected String doInBackground(Void... voids) {
        MagiskManager mm = MagiskManager.get();
        String md;
        if (mUrl != null) {
            md = WebService.getString(mUrl);
        } else {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ShellUtils.pump(is, out);
                md = out.toString();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                return "";
            }
        }
        String css;
        try (
            InputStream in = mm.getAssets().open(mm.isDarkTheme ? "dark.css" : "light.css");
            ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            ShellUtils.pump(in, out);
            css = out.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        Node doc = parser.parse(md);
        return String.format("<style>%s</style>%s", css, renderer.render(doc));
    }

    @Override
    protected void onPostExecute(String html) {
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
        alert.setTitle(mTitle);

        WebView wv = new WebView(getActivity());
        wv.loadDataWithBaseURL("fake://", html, "text/html", "UTF-8", null);

        alert.setView(wv);
        alert.setNegativeButton(R.string.close, (dialog, id) -> dialog.dismiss());
        alert.show();
    }
}
