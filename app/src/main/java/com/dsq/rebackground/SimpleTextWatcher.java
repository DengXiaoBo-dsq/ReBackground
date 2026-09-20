package com.dsq.rebackground;

import android.text.TextWatcher;
import android.text.Editable;

public abstract class SimpleTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // 默认空实现
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // 默认空实现
    }

    @Override
    public void afterTextChanged(Editable s) {
        // 默认空实现
    }
}