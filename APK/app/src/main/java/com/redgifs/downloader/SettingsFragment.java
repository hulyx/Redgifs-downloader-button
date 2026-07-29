package com.redgifs.downloader;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "redgifs_prefs";
    private static final String KEY_HD_ONLY = "hd_only";
    private static final String KEY_SHOW_FAB = "show_fab";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);

        SwitchMaterial hdSwitch = view.findViewById(R.id.switch_hd_only);
        SwitchMaterial showFabSwitch = view.findViewById(R.id.switch_show_fab);

        hdSwitch.setChecked(prefs.getBoolean(KEY_HD_ONLY, true));
        showFabSwitch.setChecked(prefs.getBoolean(KEY_SHOW_FAB, false));

        hdSwitch.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(KEY_HD_ONLY, checked).apply());

        showFabSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_SHOW_FAB, checked).apply();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshFabVisibility();
            }
        });
    }
}
