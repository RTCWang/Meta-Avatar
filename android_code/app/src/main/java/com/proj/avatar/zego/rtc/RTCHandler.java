package com.proj.avatar.zego.rtc;

import android.util.Log;

import java.util.ArrayList;

import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoUser;

public class RTCHandler extends IZegoEventHandler {

    private static final String TAG = "RTCHandler";
    private IRTCEventListener listener;

    public interface IRTCEventListener {
        void onRoomTokenWillExpire(String roomID);
    }

    public RTCHandler(IRTCEventListener listener) {
        this.listener = listener;
    }

    // nothing
    @Override
    public void onDebugError(int errorCode, String funcName, String info) {
        Log.e("Avatar", "error: " + errorCode + ", info: " + info);
    }

    @Override
    public void onRoomUserUpdate(String roomID, ZegoUpdateType updateType, ArrayList<ZegoUser> userList) {
        super.onRoomUserUpdate(roomID, updateType, userList);
        if (updateType == ZegoUpdateType.ADD) {
            for (ZegoUser user : userList) {
                Log.e(TAG, "新增用户" + user.userName);
            }
        } else if (updateType == ZegoUpdateType.DELETE) {
            for (ZegoUser user : userList) {
                Log.e(TAG, "用户退出" + user.userName);
            }
        }
    }

    @Override
    public void onRoomTokenWillExpire(String roomID, int remainTimeInSecond) {
        if (listener != null) listener.onRoomTokenWillExpire(roomID);
    }
}
