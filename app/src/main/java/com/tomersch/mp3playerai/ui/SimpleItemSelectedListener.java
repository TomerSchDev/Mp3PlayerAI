package com.tomersch.mp3playerai.ui;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {

    public interface OnPosChanged {
        void onChanged(int pos);
    }

    private final OnPosChanged cb;

    public SimpleItemSelectedListener(OnPosChanged cb) {
        this.cb = cb;
    }

    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (cb != null) cb.onChanged(position);
    }

    @Override public void onNothingSelected(AdapterView<?> parent) {}
}
