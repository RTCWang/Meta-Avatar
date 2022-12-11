package com.proj.avatar.zego.avatar;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;

import com.proj.avatar.entity.User;
import com.proj.avatar.utils.FileUtils;
import com.proj.avatar.utils.TextureBgRender;
import com.proj.avatar.zego.ZegoMngr;
import com.proj.avatar.zego.rtc.RTCMngr;
import com.zego.avatar.ZegoAvatarErrorCode;
import com.zego.avatar.ZegoAvatarService;
import com.zego.avatar.ZegoAvatarServiceDelegate;
import com.zego.avatar.ZegoAvatarView;
import com.zego.avatar.bean.AvatarCaptureConfig;
import com.zego.avatar.bean.ZegoAvatarServiceState;
import com.zego.avatar.bean.ZegoExpressionDetectMode;
import com.zego.avatar.bean.ZegoServiceConfig;
import com.zego.gl.Texture2dProgram;

import androidx.annotation.RequiresApi;
import im.zego.zegoexpress.ZegoExpressEngine;


public class AvatarMngr implements ZegoAvatarServiceDelegate, RTCMngr.CaptureListener {
    private static final String TAG = "AvatarMngr";
    private static AvatarMngr mInstance;
    private boolean mIsStop = false;

    private User mUser = null;
    private TextureBgRender mBgRender = null;
    private OnAvatarServiceInitSucced listener;
    private ZegoCharacterHelper mCharacterHelper;
    private Application mApp;

    public interface OnAvatarServiceInitSucced {
        void onInitSucced();
    }

    public void setLicense(String license, OnAvatarServiceInitSucced listener) {
        this.listener = listener;
        ZegoAvatarService.addServiceObserver(this);
        String aiPath = FileUtils.getPhonePath(mApp, "AIModel.bundle", "assets"); //   AI 模型的绝对路径
        ZegoServiceConfig config = new ZegoServiceConfig(license, aiPath);
        ZegoAvatarService.init(mApp, config);
    }

    public void stop() {
        mIsStop = true;
        mUser = null;
        stopExpression();
    }

    public void updateUser(User user) {
        mUser = user;
        if (user.shirtIdx == 0) {
            mCharacterHelper.setPackage("m-shirt01");
        } else {
            mCharacterHelper.setPackage("m-shirt02");
        }
        if (user.browIdx == 0) {
            mCharacterHelper.setPackage("brows_1");
        } else {
            mCharacterHelper.setPackage("brows_2");
        }
    }

    /**
     * 启动Avatar，调用此函数之前，请确保已经调用过setLicense
     *
     * @param avatarView
     */
    public void start(TextureView avatarView, User user) {
        mUser = user;
        mIsStop = false;
        initAvatar(avatarView, user);
        startExpression();
    }

    private void initAvatar(TextureView avatarView, User user) {
        String sex = ZegoCharacterHelper.MODEL_ID_MALE;
        if (!user.isMan) sex = ZegoCharacterHelper.MODEL_ID_FEMALE;

        // 创建 helper 简化调用
        // base.bundle 是头模, human.bundle 是全身人模
        mCharacterHelper = new ZegoCharacterHelper(FileUtils.getPhonePath(mApp, "human.bundle", "assets"));
        mCharacterHelper.setExtendPackagePath(FileUtils.getPhonePath(mApp, "Packages", "assets"));
        // 设置形象配置
        mCharacterHelper.setDefaultAvatar(sex);
        // 角色上屏, 必须在 UI 线程, 必须设置过avatar形象后才可调用(用 setDefaultAvatar 或者 setAvatarJson 都可以)
//        mCharacterHelper.setCharacterView(avatarView, () -> {
//        });
        updateUser(user);
//        mCharacterHelper.setPackage("facepaint5");
//        mCharacterHelper.setPackage("m-shirt02");
//        mCharacterHelper.setPackage("irises2");
//        mCharacterHelper.setPackage("ZEGO_Man_Tshirt_0001_0001");
        // 获取当前妆容数据, 可以保存到用户资料中
        String json = mCharacterHelper.getAvatarJson();

    }

    // 启动表情检测
    private void startExpression() {
        // 启动表情检测前要申请摄像头权限, 这里是在 MainActivity 已经申请过了
        ZegoAvatarService.getInteractEngine().startDetectExpression(ZegoExpressionDetectMode.Camera, expression -> {
            // 表情直接塞给 avatar 驱动
            mCharacterHelper.setExpression(expression);
        });
    }

    // 停止表情检测
    private void stopExpression() {
        // 不用的时候记得停止
        ZegoAvatarService.getInteractEngine().stopDetectExpression();
    }

    // 获取到 avatar 纹理后的处理
    public void onCaptureAvatar(int textureId, int width, int height) {
        if (mIsStop || mUser == null) { // rtc 的 onStop 是异步的, 可能activity已经运行到onStop了, rtc还没
            return;
        }
        boolean useFBO = true;
        if (mBgRender == null) {
            mBgRender = new TextureBgRender(textureId, useFBO, width, height, Texture2dProgram.ProgramType.TEXTURE_2D_BG);
        }
        mBgRender.setInputTexture(textureId);
        float r = Color.red(mUser.bgColor) / 255f;
        float g = Color.green(mUser.bgColor) / 255f;
        float b = Color.blue(mUser.bgColor) / 255f;
        float a = Color.alpha(mUser.bgColor) / 255f;
        mBgRender.setBgColor(r, g, b, a);
        mBgRender.draw(useFBO); // 画到 fbo 上需要反向的
        ZegoExpressEngine.getEngine().sendCustomVideoCaptureTextureData(mBgRender.getOutputTextureID(), width, height, System.currentTimeMillis());

//        ZegoExpressEngine.getEngine().sendCustomVideoCaptureTextureData(textureId, width, height, System.currentTimeMillis());

    }

    @Override
    public void onStartCapture() {
        if (mUser == null) return;
//        // 收到回调后，开发者需要执行启动视频采集相关的业务逻辑，例如开启摄像头等
        AvatarCaptureConfig config = new AvatarCaptureConfig(mUser.width, mUser.height);
//        // 开始捕获纹理
        mCharacterHelper.startCaptureAvatar(config, this::onCaptureAvatar);
    }

    @Override
    public void onStopCapture() {
        Log.e(TAG, "结束推流");
        mCharacterHelper.stopCaptureAvatar();
//        stopExpression();
    }


    private void initRes(Application app) {
        // 先把资源拷贝到SD卡，注意：线上使用时，需要做一下判断，避免多次拷贝。资源也可以做成从网络下载。
        if (!FileUtils.checkFile(app, "AIModel.bundle", "assets"))
            FileUtils.copyAssetsDir2Phone(app, "AIModel.bundle", "assets");
        if (!FileUtils.checkFile(app, "base.bundle", "assets"))
            FileUtils.copyAssetsDir2Phone(app, "base.bundle", "assets");
        if (!FileUtils.checkFile(app, "human.bundle", "assets"))
            FileUtils.copyAssetsDir2Phone(app, "human.bundle", "assets");
        if (!FileUtils.checkFile(app, "Packages", "assets"))
            FileUtils.copyAssetsDir2Phone(app, "Packages", "assets");

    }

    @Override
    public void onError(ZegoAvatarErrorCode code, String desc) {
        Log.e(TAG, "errorcode : " + code.getErrorCode() + ",desc : " + desc);
    }

    @Override
    public void onStateChange(ZegoAvatarServiceState state) {
        if (state == ZegoAvatarServiceState.InitSucceed) {
            Log.i("ZegoAvatar", "Init success");
            // 要记得及时移除通知
            ZegoAvatarService.removeServiceObserver(this);
            if (listener != null) listener.onInitSucced();
        }
    }

    private AvatarMngr(Application app) {
        mApp = app;
        initRes(app);
    }

    public static AvatarMngr getInstance(Application app) {
        if (null == mInstance) {
            synchronized (AvatarMngr.class) {
                if (null == mInstance) {
                    mInstance = new AvatarMngr(app);
                }
            }
        }
        return mInstance;
    }
}
