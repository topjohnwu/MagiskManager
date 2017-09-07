package com.topjohnwu.magisk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.topjohnwu.magisk.adapters.ModulesAdapter;
import com.topjohnwu.magisk.asyncs.LoadModules;
import com.topjohnwu.magisk.components.Fragment;
import com.topjohnwu.magisk.module.Module;
import com.topjohnwu.magisk.utils.Logger;
import com.topjohnwu.magisk.utils.Topic;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

public class ModulesFragment extends Fragment implements Topic.Subscriber {

    private static final int FETCH_ZIP_CODE = 2;

    private Unbinder unbinder;
    @BindView(R.id.swipeRefreshLayout) SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recyclerView) RecyclerView recyclerView;
    @BindView(R.id.empty_rv) TextView emptyRv;
    @OnClick(R.id.fab)
    public void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        startActivityForResult(intent, FETCH_ZIP_CODE);
    }

    private List<Module> listModules = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_modules, container, false);
        unbinder = ButterKnife.bind(this, view);

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            recyclerView.setVisibility(View.GONE);
            new LoadModules(getActivity()).exec();
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mSwipeRefreshLayout.setEnabled(recyclerView.getChildAt(0).getTop() >= 0);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        getActivity().setTitle(R.string.modules);

        return view;
    }

    @Override
    public void onTopicPublished(Topic topic) {
        Logger.dev("ModulesFragment: UI refresh triggered");
        updateUI();
    }

    @Override
    public Topic[] getSubscription() {
        return new Topic[] { getApplication().moduleLoadDone };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FETCH_ZIP_CODE && resultCode == Activity.RESULT_OK && data != null) {
            // Get the URI of the selected file
            Intent intent = new Intent(getActivity(), FlashActivity.class);
            intent.setData(data.getData()).putExtra(FlashActivity.SET_ACTION, FlashActivity.FLASH_ZIP);
            startActivity(intent);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    private void updateUI() {
        listModules.clear();
        listModules.addAll(getApplication().moduleMap.values());
        if (listModules.size() == 0) {
            emptyRv.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyRv.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.setAdapter(new ModulesAdapter(listModules));
        }
        mSwipeRefreshLayout.setRefreshing(false);
    }
}
