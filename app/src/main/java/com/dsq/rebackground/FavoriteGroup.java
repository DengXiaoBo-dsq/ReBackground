package com.dsq.rebackground;

import java.util.ArrayList;
import java.util.List;

public class FavoriteGroup {
    public String groupName;
    public List<String> colors;

    public FavoriteGroup() {
        this.colors = new ArrayList<>();
    }

    public FavoriteGroup(String groupName) {
        this.groupName = groupName;
        this.colors = new ArrayList<>();
    }
}