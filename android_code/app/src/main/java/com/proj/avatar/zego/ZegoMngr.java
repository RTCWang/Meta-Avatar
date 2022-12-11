package com.proj.avatar.zego;

import android.app.Application;
import android.util.Log;
import android.view.TextureView;

import com.proj.avatar.entity.User;
import com.proj.avatar.zego.avatar.AvatarMngr;
import com.proj.avatar.zego.rtc.RTCHandler;
import com.proj.avatar.zego.rtc.RTCMngr;
import com.zego.avatar.ZegoAvatarView;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoCaptureHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;

public class ZegoMngr {
    private final static String TAG = "ZegoMngr";
    private static ZegoMngr mInstance;
    private AvatarMngr mAvatarMngr;
    private RTCMngr mRTCMngr;

    public interface ZegoListener {
        void onZegoError(String errMsg);
    }

    public void updateUser(User user) {
        mAvatarMngr.updateUser(user);
    }

    /**
     * 将登陆房间，启动avatar，自动推流聚合在一个函数
     */
    public void start(TextureView avatarView, User user, String roomId, String streamId, ZegoListener listener) {
        mAvatarMngr.start(avatarView, user);
        mRTCMngr.setCustomVideo(user.width, user.height, mAvatarMngr);
        mRTCMngr.start(user.userId, user.userName, roomId, new RTCMngr.RTCListener() {
            @Override
            public void onLogin(int errCode) {
                if (errCode == 0) {//登陆成功，开始推流
                    Log.e(TAG, "===>开始推流");
                    mRTCMngr.pushStream(streamId, avatarView);
                } else {
                    if (listener != null) listener.onZegoError("登陆即构RTC失败！");
                }
            }
        });
    }

    public void stop() {
        mRTCMngr.stop();
        mAvatarMngr.stop();
    }


    private ZegoMngr(Application app) {
        mAvatarMngr = AvatarMngr.getInstance(app);
        mRTCMngr = RTCMngr.getInstance(app);
    }

    public static ZegoMngr getInstance(Application app) {
        if (null == mInstance) {
            synchronized (ZegoMngr.class) {
                if (null == mInstance) {
                    mInstance = new ZegoMngr(app);
                }
            }
        }
        return mInstance;
    }
}
