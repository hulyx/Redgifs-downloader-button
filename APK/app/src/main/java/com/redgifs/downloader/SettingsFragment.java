package com.redgifs.downloader;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "redgifs_prefs";
    private static final String KEY_HD_ONLY = "hd_only";
    private static final String KEY_AUTO_INJECT = "auto_inject";

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
        SwitchMaterial autoInjectSwitch = view.findViewById(R.id.switch_auto_inject);

        hdSwitch.setChecked(prefs.getBoolean(KEY_HD_ONLY, true));
        autoInjectSwitch.setChecked(prefs.getBoolean(KEY_AUTO_INJECT, true));

        hdSwitch.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(KEY_HD_ONLY, checked).apply());

        autoInjectSwitch.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit().putBoolean(KEY_AUTO_INJECT, checked).apply());
    }
}
