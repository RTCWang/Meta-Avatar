package com.proj.avatar.activity;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.proj.avatar.R;
import com.proj.avatar.activity.BaseActivity;
import com.proj.avatar.zego.KeyCenter;
import com.proj.avatar.zego.avatar.AvatarMngr;
import com.zego.avatar.ZegoAvatarService;
import com.zego.avatar.bean.ZegoAvatarServiceState;
import com.zego.avatar.bean.ZegoServiceConfig;

public class MainActivity extends BaseActivity implements AvatarMngr.OnAvatarServiceInitSucced, View.OnClickListener {
    private AvatarMngr avatarMngr = null;
    private boolean isMan = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.okBtn).setOnClickListener(this);
        initAvatar();

        RadioGroup rg = findViewById(R.id.sex_rg);
        rg.setOnCheckedChangeListener((group, checkedId) -> {//设置组中单选按钮选中事件
            isMan = checkedId == R.id.sex_man;
        });
    }

    private void initAvatar() {
        KeyCenter.getLicense(this, (code, message, response) -> {
            if (code == 0) {
                KeyCenter.avatarLicense = response.getLicense();
                showLoading("正在初始化...");
                avatarMngr = AvatarMngr.getInstance(getApplication());
                avatarMngr.setLicense(KeyCenter.avatarLicense, this);
            } else {
                toast("License 获取失败, code: " + code);
            }
        });

    }

    private void openAvatarActivity() {
        if (!checkPermission()) {
            requestPermission();
            return;
        }
        if (ZegoAvatarService.getState() != ZegoAvatarServiceState.InitSucceed) {
            //这里也可以使用ZegoAvatarService.addServiceObserver监听初始化状态
            toast("avatar初始化未完成！");
            return;
        }
        Intent intent = new Intent(MainActivity.this, AvatarActivity.class);
        intent.putExtra("isMan", isMan);
        this.startActivity(intent);

    }

    /**
     * Avatar SDK初始化完成回调
     */
    @Override
    public void onInitSucced() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideLoading();
            }
        });
    }

    @Override
    protected void onGrantedAllPermission() {
        openAvatarActivity();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.okBtn) {
            openAvatarActivity();
        }
    }
}