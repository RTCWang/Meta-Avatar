package com.proj.avatar.zego.rtc;

import android.app.Application;
import android.util.Log;
import android.view.TextureView;

import com.proj.avatar.zego.KeyCenter;
import com.proj.avatar.zego.ZegoMngr;
import com.proj.avatar.zego.avatar.AvatarMngr;

import org.json.JSONObject;

import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.callback.IZegoCustomVideoCaptureHandler;
import im.zego.zegoexpress.callback.IZegoEventHandler;
import im.zego.zegoexpress.constants.ZegoPublishChannel;
import im.zego.zegoexpress.constants.ZegoScenario;
import im.zego.zegoexpress.constants.ZegoVideoBufferType;
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset;
import im.zego.zegoexpress.entity.ZegoCanvas;
import im.zego.zegoexpress.entity.ZegoCustomVideoCaptureConfig;
import im.zego.zegoexpress.entity.ZegoEngineProfile;
import im.zego.zegoexpress.entity.ZegoRoomConfig;
import im.zego.zegoexpress.entity.ZegoUser;
import im.zego.zegoexpress.entity.ZegoVideoConfig;

public class RTCMngr implements RTCHandler.IRTCEventListener {

    private static final String TAG = "RTCMngr";
    private ZegoExpressEngine mRTCEngine;
    private static RTCMngr mInstance;
    private String mRoomId;
    private String mUserId;
    private RTCHandler mRTCHandler = new RTCHandler(this);


    public interface CaptureListener {
        void onStartCapture();

        void onStopCapture();
    }

    public interface RTCListener {
        void onLogin(int errCode);
    }

    private RTCMngr(Application app) {
        mRTCEngine = createRTCEngine(app, mRTCHandler);
    }

    public static RTCMngr getInstance(Application app) {
        if (null == mInstance) {
            synchronized (RTCMngr.class) {
                if (null == mInstance) {
                    mInstance = new RTCMngr(app);
                }
            }
        }
        return mInstance;
    }

    private ZegoExpressEngine createRTCEngine(Application app, IZegoEventHandler handler) {


        ZegoEngineProfile profile = new ZegoEngineProfile();
        profile.appID = KeyCenter.APP_ID;
        profile.scenario = ZegoScenario.GENERAL;  // 通用场景接入
        profile.application = app;
        ZegoExpressEngine engine = ZegoExpressEngine.createEngine(profile, handler);
//
//        //设置自定义视频采集，主要用于Avatar画面实时传输
//        setCustomVideo(engine);

        return engine;
    }

    public void start(String userId, String userName, String roomId, RTCListener listener) {
        Log.e(TAG, "准备登陆房间");
        loginRoom(userId, userName, roomId, listener);
    }

    public void stop() {
        loginOut();
    }

    public void setCustomVideo(int videoWidth, int videoHeight, RTCMngr.CaptureListener listener) {

        // 自定义视频采集
        ZegoCustomVideoCaptureConfig videoCaptureConfig = new ZegoCustomVideoCaptureConfig();
        // 选择 GL_TEXTURE_2D 类型视频帧数据
        videoCaptureConfig.bufferType = ZegoVideoBufferType.GL_TEXTURE_2D;
        // 启动自定义视频采集
        mRTCEngine.enableCustomVideoCapture(true, videoCaptureConfig, ZegoPublishChannel.MAIN);

        // 设置自定义视频采集回调
        mRTCEngine.setCustomVideoCaptureHandler(new IZegoCustomVideoCaptureHandler() {
            @Override
            public void onStart(ZegoPublishChannel zegoPublishChannel) {
                if (listener != null) {
                    listener.onStartCapture();
                }
            }

            @Override
            public void onStop(ZegoPublishChannel zegoPublishChannel) {
                if (listener != null) {
                    listener.onStopCapture();
                }

            }
        });

        // 设置视频配置, 要跟 avatar 的输出尺寸一致
        ZegoVideoConfig videoConfig = new ZegoVideoConfig(ZegoVideoConfigPreset.PRESET_720P);
        // 输出纹理是正方形的, 要配置一下
        videoConfig.setEncodeResolution(videoWidth, videoHeight);
        mRTCEngine.setVideoConfig(videoConfig);
    }

    public void pushStream(String streamId, TextureView tv) {

        mRTCEngine.startPublishingStream(streamId);
        mRTCEngine.startPreview(new ZegoCanvas(tv));

    }

    public boolean loginRoom(String userId, String userName, String roomId, RTCListener listener) {
        mRoomId = roomId;
        mUserId = userId;
        ZegoUser user = new ZegoUser(userId, userName);
        ZegoRoomConfig config = new ZegoRoomConfig();
        config.token = getToken(userId, roomId); // 请求开发者服务端获取
        config.isUserStatusNotify = true;
        mRTCEngine.loginRoom(roomId, user, config, (int error, JSONObject extendedData) -> {
            if (listener != null) {
                listener.onLogin(error);
            }
        });
        Log.e(TAG, "登录房间：" + roomId);
        return true;
    }

    public void loginOut() {
        mRTCEngine.stopPublishingStream();
        mRTCEngine.logoutRoom(mRoomId);
    }

    @Override
    public void onRoomTokenWillExpire(String roomID) {
        mRTCEngine.renewToken(roomID, getToken(mUserId, roomID));
    }

    /**
     * 此函数应该放在服务器端执行，以防止泄露ServerSecret
     */
    public static String getToken(String userId, String roomId) {
        TokenEntity tokenEntity = new TokenEntity(KeyCenter.APP_ID, userId, roomId, 60 * 60, 1, 1);

        String token = TokenUtils.generateToken04(tokenEntity);
        return token;
    }

    //Express SDK 开始 RTC 推流时的处理handler，这里面来启动、停止Avatar检测，以及开始、停止导出纹理
//    private final IZegoCustomVideoCaptureHandler mCustomVideoCaptureHandler = new IZegoCustomVideoCaptureHandler() {
//
//        @Override
//        public void onStart(ZegoPublishChannel channel) {
//            // 收到回调后，开发者需要执行启动视频采集相关的业务逻辑，例如开启摄像头等
//            AvatarCaptureConfig config = new AvatarCaptureConfig(mVideoWidth, mVideoHeight);
//            // 开始捕获纹理
//            mCharacterHelper.startCaptureAvatar(config, AvatarStreamActivity.this::onCaptureAvatar);
//            // 启动表情随动
//            startExpression();
//        }
//
//        @Override
//        public void onStop(ZegoPublishChannel channel) {
//            // 收到回调后，开发者需要执行停止视频采集相关的业务逻辑，例如关闭摄像头等
//            // 停止捕获纹理
//            // 注意!!!! 有可能不被执行的!!! 用户在退出应用时，也需要退出，请参考3.8
//            mCharacterHelper.stopCaptureAvatar();
//            stopExpression();
//        }
//    };
}
