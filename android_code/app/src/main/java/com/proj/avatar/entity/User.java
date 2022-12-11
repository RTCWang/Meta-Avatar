package com.proj.avatar.entity;

import android.graphics.Color;

public class User {
    public String userName;
    public String userId;
    public boolean isMan;
    public int width;
    public int height;
    public int bgColor;
    public int shirtIdx = 0;
    public int browIdx = 0;

    public User(String userName, String userId, int width, int height) {
        this.userName = userName;
        this.userId = userId;
        this.width = width;
        this.height = height;
        this.isMan = true;
        bgColor = Color.argb(255, 33, 66, 99);
    }

    public User(String userName, String userId, boolean isMan, int width, int height) {
        this.userName = userName;
        this.userId = userId;
        this.isMan = isMan;
        this.width = width;
        this.height = height;
    }


}
