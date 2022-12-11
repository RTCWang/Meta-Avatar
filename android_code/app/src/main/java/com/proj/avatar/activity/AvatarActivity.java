package com.proj.avatar.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.widget.RadioGroup;

import com.azeesoft.lib.colorpicker.ColorPickerDialog;
import com.proj.avatar.R;
import com.proj.avatar.entity.User;
import com.proj.avatar.zego.ZegoMngr;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

public class AvatarActivity extends BaseActivity implements ZegoMngr.ZegoListener {
    private int vWidth = 720;
    private int vHeight = 1080;
    private String mRoomId = "R_0001";
    private String mStreamId = "S_0001";
    private User user = new User("User_0002", "User_0002", vWidth, vHeight);
    private TextureView mTextureView;
    //    private ZegoAvatarView mAvatarView;
    private ZegoMngr mZegoMngr;
    private ColorPickerDialog colorPickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        Toolbar toolbar = findViewById(R.id.toolBar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        colorPickerDialog = ColorPickerDialog.createColorPickerDialog(this, ColorPickerDialog.DARK_THEME);
        colorPickerDialog.setOnColorPickedListener(new ColorPickerDialog.OnColorPickedListener() {
            @Override
            public void onColorPicked(int color, String hexVal) {
                user.bgColor = color;
            }
        });
        Intent intent = getIntent();
        boolean isMan = intent.getBooleanExtra("isMan", true);
        user.isMan = isMan;
        mTextureView = findViewById(R.id.avatar_view);
//        mAvatarView = findViewById(R.id.zego_avatar_view);
        // 设置背景颜色
        initViews();
        mZegoMngr = ZegoMngr.getInstance(getApplication());
        // 启动表情驱动, 注意: 如果启用了动画(enableAnimation), 由于骨骼冲突(动画在动头, 表情驱动也在旋转头部), 头就会动不了
//        startExpression(ZegoExpressionDetectMode.Camera);
        mZegoMngr.start(mTextureView, user, mRoomId, mStreamId, this);
    }

    private void initViews() {
        RadioGroup shirt = findViewById(R.id.shirt_rg);
        shirt.setOnCheckedChangeListener((group, checkedId) -> {//设置组中单选按钮选中事件
            user.shirtIdx = checkedId == R.id.shirt_1 ? 0 : 1;
            mZegoMngr.updateUser(user);
        });

        RadioGroup brow = findViewById(R.id.brow_rg);
        brow.setOnCheckedChangeListener((group, checkedId) -> {//设置组中单选按钮选中事件
            user.browIdx = checkedId == R.id.brow_1 ? 0 : 1;
            mZegoMngr.updateUser(user);
        });
        findViewById(R.id.bgBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                colorPickerDialog.setHexaDecimalTextColor(user.bgColor); //There are many functions like this
                colorPickerDialog.show();
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onZegoError(String errMsg) {
        toast(errMsg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mZegoMngr.stop();//清理引用，防止内存泄露
    }
}