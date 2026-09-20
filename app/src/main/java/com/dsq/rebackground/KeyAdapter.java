package com.dsq.rebackground;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

public class KeyAdapter extends ArrayAdapter<String> {

    private Context context;
    private ArrayList<String> keys;

    public KeyAdapter(Context context, ArrayList<String> keys) {
        super(context, android.R.layout.simple_spinner_item, keys);  // 使用正确的布局资源
        this.context = context;
        this.keys = keys;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_item, parent, false);
        }

        TextView keyTextView = convertView.findViewById(android.R.id.text1);
        keyTextView.setText(keys.get(position));

        return convertView;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);  // 使用下拉项布局
        }
        TextView keyTextView = convertView.findViewById(android.R.id.text1);
        keyTextView.setText(keys.get(position));

        return convertView;
    }
}

