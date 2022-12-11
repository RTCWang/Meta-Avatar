package com.proj.avatar.zego.avatar;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;


import com.zego.avatar.bean.AvatarCaptureConfig;
import com.zego.avatar.IZegoAvatarCaptureProvider;
import com.zego.avatar.OnAvatarCaptureCallback;
import com.zego.avatar.bean.ZegoAvatarViewState;
import com.zego.avatar.bean.ZegoBeardInfo;
import com.zego.avatar.bean.ZegoExpression;
import com.zego.avatar.bean.ZegoFaceFeature;
import com.zego.avatar.bean.ZegoFaceShapeType;
import com.zego.avatar.bean.ZegoGenderType;
import com.zego.avatar.bean.ZegoHairInfo;
import com.zego.avatar.ZegoAvatarService;
import com.zego.avatar.ZegoAvatarView;
import com.zego.avatar.ZegoCharacter;
import com.zego.core.ICharacterCallback;
import com.zego.core.ZALog;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


// implemants of ZegoCharacterHelper
public class ZegoCharacterHelperImpl {
    private static final String TAG = "ZegoCharacterHelper";

    private final String mBaseResPath;

    private boolean mHaveExtendPackagesPath = false;
    private String mExtendPackagesPath;


    public static int EXPRESSION_NUM = 52;                                //表情数量
    private static ZegoExpression defaultExpressionValue[];                //用来保存默认表情动画数据
    private long externalExpressionTime = 0;                               //最近一帧外部表情时间戳
    private int defaultExpressionIndex = 0;                                //当前播放的默认表情帧
    private int maxDefaultExpressionIndex = 0;                             //默认表情数据最大帧数
    private int transitionFrameCount = 10;                                 //表情渐变过渡需要帧数
    private int defaultTransitionFrameCount = 0;                           //默认表情当前过渡帧数
    private int externalTransitionFrameCount = 0;                          //外部表情过渡帧数
    private Timer timer = null;                                            //控制默认表情开关的定时器
    private TimerTask task = null;                                         //默认表情定时器任务
    private static int sleepTime = 330;                                    //睡眠动画触发时间
    private ZegoExpression lastExternalExpression = null;                  //最后一帧外部表情数据
    private ZegoExpression lastDefaultExpression = null;                   //最后一帧默认表情数据
    private ZegoExpression fusionExpression = null;                        //上一帧表情

    private ZegoAvatarView mAvatarView;
    private ZegoCharacter mCharacter;
    private JSONObject mAvatarDict;
    private IZegoAvatarCaptureProvider mCaptureProvider;

    private ICharacterCallback mCharacterCallback;

    private static final String MODEL_ID = "modelID";
    private static final String PROPERTY_NODE = "property";
    private static final String PACKAGE_NODE = "package";

    public static final String HAIR_COLOR_ROOT_ID = "hair_root_color";
    public static final String HAIR_COLOR_MAIN_ID = "hair_end_color";
    public static final String COLOR_HAIR_ROOT_PROPERTY = "{\n" +
            "      \"classify\": \"color\",\n" +
            "      \"id\": \"_RootColor\",\n" +
            "      \"matName\": \"hair_common\",\n" +
            "      \"matNodeName\": \"Hair\",\n" +
            "      \"type\": \"mat_property\"\n" +
            "    }";
    public static final String COLOR_HAIR_MAIN_PROPERTY = "{\n" +
            "      \"classify\": \"color\",\n" +
            "      \"id\": \"_MainColor\",\n" +
            "      \"matName\": \"hair_common\",\n" +
            "      \"matNodeName\": \"Hair\",\n" +
            "      \"type\": \"mat_property\"\n" +
            "    }";
    //皮肤颜色
    public static final String SKIN_COLOR_ID = "skin_color_coordinates";
    public static final String SKIN_COLOR_PROPERTY = "{\"classify\":\"combiner\",\"id\":\"combinermakeup_skin_color\"," +
            "\"matName\":\"Male-Face-1\",\"matNodeName\":\"ZEGO_Shape\",\"type\":\"mat_property\"}";

    /**
     * base.bundle/human.bundle 对应的路径，可以放在asset中，也可以是动态下载的sd卡路径，或者assets拷贝后的路径
     *
     * @param baseResPath 对应resource/android/base.bundle
     */
    public ZegoCharacterHelperImpl(String baseResPath) {
        mBaseResPath = baseResPath;
        ZegoConfigLoader.get(baseResPath).preload();
    }

    public static void preload(String baseResPath) {
        ZegoConfigLoader.get(baseResPath).preload();
        Log.i(TAG, "preload is completed！！");
    }


    /**
     * Packages所有文件可以动态下载，这里动态下载的目录
     * 如果是内置在app中的，则需要将该目录拷贝到sd卡中（应用私有目录，不能是公开目录），该路径对应的是拷贝后的绝对路径
     *
     * @param path 对应resource/android/Packages
     */

    public void setExtendPackagePath(String path) {
        mHaveExtendPackagesPath = true;
        mExtendPackagesPath = path;
    }

    /**
     * @param modelID 男性：MODEL_ID_MALE or 女性：MODEL_ID_FEMALE
     */

    public void setDefaultAvatar(String modelID) {
        ZALog.i(TAG, "setDefaultAvatar " + modelID);
        try {
            modelID = modelID.replace("human", "");     //兼容旧方法传入的人模ID
            mAvatarDict = new JSONObject();
            mAvatarDict.put(MODEL_ID, modelID);
            combineDefault(modelID);
        } catch (JSONException e) {
            e.printStackTrace();
            ZALog.e(TAG, "setDefaultAvatar defultJson error:" + e.getMessage());
        }

        applyToShow();

    }

    public String getModelID() {
        if (mAvatarDict == null) {
            return null;
        }
        try {
            return mAvatarDict.getString(MODEL_ID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }


    public String getAvatarJson() {
        if (mAvatarDict == null) {
            ZALog.e(TAG, "getAvatarJson have no avatar json yet!");
            return null;
        }
        try {
            mAvatarDict.put("version", ZegoAvatarService.getVersion());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return mAvatarDict.toString();
    }


    public void setAvatarJson(String json) {
        ZALog.i(TAG, "setAvatarJson json:" + json);

        try {
            mAvatarDict = new JSONObject(json);
            String modelName = mAvatarDict.optString(MODEL_ID);
            modelName = modelName.replace("human", "");     //兼容旧数据的人模ID
            mAvatarDict.put(MODEL_ID, modelName);    //把这个放回默认Json
            combineDefault(modelName);
            applyToShow();
        } catch (JSONException e) {
            e.printStackTrace();
            ZALog.e(TAG, "setAvatarJson error:" + e.getMessage());
        }

    }


    public void setPackage(String packageID) {
        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setPackage error,mCharacter is null");
            return;
        }

        setPackageJson(packageID);
        //更新包
        loadPackageInternal(packageID);
    }


    public void setHairColor(ZegoCharacterHelper.ZegoColor rootColor, ZegoCharacterHelper.ZegoColor mainColor) {
        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setHairColor error,mCharacter is null");
            return;
        }

        if (rootColor == null && mainColor == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setHairColor error, color all is null");
            return;
        }

        if (rootColor != null) {
            if (getHairMaterials() != null) {
                setPropertyJson(HAIR_COLOR_ROOT_ID, rootColor.toString());
                mCharacter.setProperty(COLOR_HAIR_ROOT_PROPERTY.replace("hair_common", getHairMaterials()), rootColor.toString());
            } else {
                setPropertyJson(HAIR_COLOR_ROOT_ID, rootColor.toString());
                mCharacter.setProperty(COLOR_HAIR_ROOT_PROPERTY, rootColor.toString());
            }
        }

        if (mainColor != null) {
            if (getHairMaterials() != null) {
                setPropertyJson(HAIR_COLOR_MAIN_ID, mainColor.toString());
                mCharacter.setProperty(COLOR_HAIR_MAIN_PROPERTY.replace("hair_common", getHairMaterials()), mainColor.toString());
            } else {
                setPropertyJson(HAIR_COLOR_MAIN_ID, mainColor.toString());
                mCharacter.setProperty(COLOR_HAIR_MAIN_PROPERTY, mainColor.toString());
            }
        }
    }

    public String getHairMaterials() {
        try {
            JSONObject packageJson = mAvatarDict.getJSONObject(PACKAGE_NODE);
            String packageID = packageJson.getString("hair");
            String hairPath = getPackagePath(packageID);
            JSONArray properties = getPackageJsonObj(hairPath).getJSONArray("properties");
            if (properties.length() < 1) {
                ZALog.e(TAG, "getPackage hair properties length is zero");
            }
            // 这里取第一个property作为这个包的包类型
            JSONObject property = properties.getJSONObject(0);
            if (property.has("materials")) {
                JSONArray materialsArray = property.getJSONArray("materials");
                String materialsString = "";
                for (int i = 0; i < materialsArray.length(); i++) {
                    materialsString = materialsString + materialsArray.getString(i);
                    if (i != materialsArray.length() - 1) {
                        materialsString = materialsString + ',';
                    }
                }
                Log.i(TAG, "hair materialsString is: " + materialsString);
                return materialsString;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ZegoCharacterHelper.ZegoColor[] getHairColor() {
        try {
            ZegoCharacterHelper.ZegoColor[] result = new ZegoCharacterHelper.ZegoColor[2];

            JSONObject propertyJson;
            if (mAvatarDict.has(PROPERTY_NODE)) {
                propertyJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);
                String color = propertyJson.getString(HAIR_COLOR_ROOT_ID);
                result[0] = ZegoCharacterHelper.ZegoColor.fromString(color);

                color = propertyJson.getString(COLOR_HAIR_MAIN_PROPERTY);
                result[1] = ZegoCharacterHelper.ZegoColor.fromString(color);
                return result;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 设置肤色
     */

    public void setSkinColorCoordinates(ZegoCharacterHelper.ZegoPoint point) {

        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setSkinRange error,mCharacter is null");
            return;
        }

        String value = point.x + "," + point.y;
        setPropertyJson(SKIN_COLOR_ID, value);

        mCharacter.setProperty(SKIN_COLOR_PROPERTY, value);
    }


    public ZegoCharacterHelper.ZegoPoint getSkinColorCoordinates() {
        ZegoCharacterHelper.ZegoPoint result = null;
        try {
            JSONObject propertyJson;
            if (mAvatarDict.has(PROPERTY_NODE)) {
                propertyJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);
                String coordStr = propertyJson.getString(SKIN_COLOR_ID);
                result = ZegoCharacterHelper.ZegoPoint.fromString(coordStr);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * @param propertyID 捏脸维度的 key 值, 取值范围为ZegoCharacterHelper中定义的FACESHAPE_开头的常量
     * @param floatValue
     */

    public void setFaceShape(String propertyID, Float floatValue) {
        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setProperty error,mCharacter is null");
            return;
        }

        if (propertyID == null || propertyID.length() <= 0) {
            ZALog.e(TAG, "ZegoCharacterHelper setProperty error,propertyID is null");
            return;
        }

        // 更新缓存数据
        setPropertyJson(propertyID, floatValue);

        // 更新人模
        mCharacter.setProperty(propertyID, floatValue);
    }


    public boolean setExpression(ZegoExpression expression) {
        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper, setExpression: Error, character is null.");
            return false;
        }

        //关闭默认表情， 默认表情过渡到外部表情， 设置默认表情index置0，当前设置表情赋值给上一帧外部表情，更新时间戳
        defaultTransitionFrameCount = 0;
        if (!defaultToExternalExpression(expression)) {
            mCharacter.setExpression(expression);
            lastExternalExpression = expression;
        }
        externalExpressionTime = System.currentTimeMillis();
        defaultExpressionIndex = 0;
        return true;
    }

    // 增加设置默认表情接口，读取默认表情数据
    public void setDefaultExpression(String path) {
        Log.i(TAG, "expressionValue txtPath: " + path);
        String values = readStringFromFile(path);
        String lines[] = values.split("\n");
        maxDefaultExpressionIndex = lines.length;
        defaultExpressionValue = new ZegoExpression[lines.length];
        for (int i = 0; i < lines.length; i++) {
            defaultExpressionValue[i] = ZegoExpression.jsonToExpression(lines[i]);
//            Log.i(TAG, "defaultExpressionValue: " + defaultExpressionValue[i].expressionToJson());
        }
        //lastDefaultExpression = defaultExpressionValue[0];      //把第一帧设置为上一帧默认表情数据
    }

    // 播放默认表情动画接口
    public void enableDefaultExpression(boolean enable) {
        //关掉定时器
        if (!enable) {
            defaultExpressionIndex = 0;
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
            return;
        }

        //起一个定时器，周期性拿系统时间跟上一帧实时检测表情的时间做比较，大于阈值则开始跑默认表情
        if (timer == null) {
            timer = new Timer();
        }
        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    if (System.currentTimeMillis() - externalExpressionTime > sleepTime && defaultExpressionIndex < maxDefaultExpressionIndex) {
                        //把外部到过渡设置为0
                        externalTransitionFrameCount = 0;
                        if (!externalToDefaultExpression()) {
                            //已经融合完了，直接继续走默认表情
                            mCharacter.setExpression(defaultExpressionValue[defaultExpressionIndex]);
                            lastDefaultExpression = defaultExpressionValue[defaultExpressionIndex];
                            defaultExpressionIndex++;
                        }
                    }
                }
            };
            timer.schedule(task, 0, 30);
        }
    }

    //外部表情过渡到默认表情
    private boolean externalToDefaultExpression() {
        //如果没有外部默认表情或者已经融合完了，直接跑路
        if (lastExternalExpression == null || defaultTransitionFrameCount >= transitionFrameCount) {
            //Log.i(TAG,"默认表情融合完成！");
            return false;
        }

        Log.i(TAG, "外部表情 --> 默认表情！");
        //融合第一帧
        if (defaultTransitionFrameCount == 0) {
            fusionExpression = lastExternalExpression;    //将上一帧到外部表情给到表情融合第一帧
        }
        ZegoExpression tempExpression = new ZegoExpression();
        for (int i = 0; i < EXPRESSION_NUM; i++) {
            tempExpression.expressions[i] = fusionExpression.expressions[i] * 0.9f + defaultExpressionValue[defaultExpressionIndex].expressions[i] * 0.1f;
            if (i == 0) {
                tempExpression.angleX = fusionExpression.angleX * 0.9f + defaultExpressionValue[defaultExpressionIndex].angleX * 0.1f;
                tempExpression.angleY = fusionExpression.angleY * 0.9f + defaultExpressionValue[defaultExpressionIndex].angleY * 0.1f;
                tempExpression.angleZ = fusionExpression.angleZ * 0.9f + defaultExpressionValue[defaultExpressionIndex].angleZ * 0.1f;
            }
        }
        //设置融合完的表情，融合完的表情设置为上一帧默认表情
        mCharacter.setExpression(tempExpression);
        fusionExpression = tempExpression;
        lastDefaultExpression = tempExpression;
        defaultTransitionFrameCount++;     //过渡帧数递增
        defaultExpressionIndex++;          //当前默认帧数递增
        return true;
    }

    private boolean defaultToExternalExpression(ZegoExpression expression) {
        //如果没有上一帧默认表情或者融合完了，直接跑路
        if (lastDefaultExpression == null || externalTransitionFrameCount >= transitionFrameCount) {
            //Log.i(TAG,"外部表情融合完成！");
            return false;
        }

        Log.i(TAG, "默认表情 --> 外部表情！");
        //将上一帧的默认表情赋值给融合表情，默认表情当前帧数置为0
        if (externalTransitionFrameCount == 0) {
            if (defaultExpressionIndex >= maxDefaultExpressionIndex) {
                defaultExpressionIndex = maxDefaultExpressionIndex - 1;
            }
            fusionExpression = defaultExpressionValue[defaultExpressionIndex];
        }
        //表情融合
        ZegoExpression tempExpression = new ZegoExpression();
        for (int i = 0; i < EXPRESSION_NUM; i++) {
            tempExpression.expressions[i] = fusionExpression.expressions[i] * 0.9f + expression.expressions[i] * 0.1f;
            if (i == 0) {
                tempExpression.angleX = fusionExpression.angleX * 0.9f + expression.angleX * 0.1f;
                tempExpression.angleY = fusionExpression.angleY * 0.9f + expression.angleY * 0.1f;
                tempExpression.angleZ = fusionExpression.angleZ * 0.9f + expression.angleZ * 0.1f;
            }
        }
        mCharacter.setExpression(tempExpression);
        lastExternalExpression = tempExpression;
        fusionExpression = tempExpression;
        externalTransitionFrameCount++;
        return true;
    }

    public void setAvatarSize(int pos) {
        if (mCharacter != null) {
            mCharacter.setAvatarSize(pos);
        }
    }

    public void setViewport(ZegoAvatarViewState viewport) {
        if (mCharacter == null) return;
        mCharacter.setViewport(viewport);
    }

    public void playAnimation(String animationName) {
        if (mCharacter == null) return;
        mCharacter.playAnimation(animationName);
    }

    public void enableAnimation(boolean enableAnimation) {
        if (mCharacter == null) return;
        mCharacter.enableAnimation(enableAnimation);
    }


    public void applyFaceFeature(ZegoFaceFeature faceFeature) {
        try {
            String modelID;
            mAvatarDict = new JSONObject();
            if (faceFeature.gender == ZegoGenderType.Male) {
                modelID = ZegoCharacterHelper.MODEL_ID_MALE;
                mAvatarDict.put(MODEL_ID, modelID);
            } else {
                modelID = ZegoCharacterHelper.MODEL_ID_FEMALE;
                mAvatarDict.put(MODEL_ID, modelID);
            }

            applyGlass(faceFeature.glass);
            applyFaceShape(faceFeature.faceShapeFeatures);
            applyBeard(faceFeature.beardInfo, faceFeature.gender);
            applyHair(faceFeature.hairInfo, faceFeature.gender);
            combineDefault(modelID);

            //前面的部分全部构造数据，最后一步做显示
            applyToShow();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    /**
     * 设置要上屏的view
     */

    public boolean setCharacterView(ZegoAvatarView view) {
        return setCharacterView(view, null);
    }

    /**
     * 设置要上屏的view
     */
    public boolean setCharacterView(ZegoAvatarView view, ICharacterCallback callback) {
        mCharacterCallback = callback;

        if (view == null) {
            ZALog.e("ZegoCharacterHelper, setCharacterView: Error, view is null.");
            return false;
        }

        mAvatarView = view;

        if (mCharacter == null) {
            ZALog.e("ZegoCharacterHelper, setCharacterView: Error, have no character yet，please make sure 。setAvatarJson or applyFaceFeature called");
            return false;
        } else {
            mAvatarView.setCharacter(mCharacter, mCharacterCallback);
        }


        externalExpressionTime = System.currentTimeMillis();    //上屏的时候记录时间
        return true;
    }

    /**
     * 开始采集获取Avatar内容
     *
     * @param config   采集配置参数
     * @param callback 获取内容回调，包含texture
     */
    public void startCaptureAvatar(AvatarCaptureConfig config, OnAvatarCaptureCallback callback) {
        if (mCaptureProvider == null)
            mCaptureProvider = ZegoAvatarService.createCaptureProvider();

        if (mCharacter != null) {
            mCaptureProvider.setCharacter(mCharacter);
        }

//        if(config.preview != null && mCaptureProvider instanceof ZegoAvatarCaptureProvider)
//            ((ZegoAvatarCaptureProvider)mCaptureProvider).setPreview(config.preview);

        mCaptureProvider.startCaptureAvatar(config, callback);
    }

    /**
     * 停止采集获取Avatar内容
     */
    public void stopCaptureAvatar() {
        if (mCaptureProvider != null) {
            mCaptureProvider.stopCaptureAvatar();
        }
    }

    /*************************************************************私有方法***************************************************************************************/
    //应用眼镜
    private void applyGlass(boolean glass) {
        if (!glass) {
            this.setPackageJson("glasses_empty");
        } else {
            //随机一个眼镜
            this.setPackageJson("glasses1");
        }
    }

    //应用捏脸维度
    private void applyFaceShape(Map<ZegoFaceShapeType, Float> faceShapeFeatures) {
        for (ZegoFaceShapeType key : faceShapeFeatures.keySet()) {
            String propertyID = key.getId();
            Float propertyValue = faceShapeFeatures.get(key);
            ZALog.d(TAG, "applyFaceShape propertyKey:" + propertyID + ",propertyValue:" + propertyValue);
            setPropertyJson(propertyID, propertyValue);
        }
    }

    /**
     * 胡子对应枚举
     * <p>
     * typeID:
     * value: 对应资源
     **/
    public static enum BeardResEnum {
        /**
         * 男性
         */
        MALE_BEARD_NO(0, "MALE_BEARD_NO"), //无胡子
        MALE_BEARD_TOP(1, "MALE_BEARD_TOP"), //上方胡子
        MALE_BEARD_BOTTOM(2, "MALE_BEARD_BOTTOM"), //下方胡子
        MALE_BEARD_TOPBOTTOM(4, "MALE_BEARD_TOPBOTTOM"), //上下方胡子
        MALE_BEARD_ALL(5, "MALE_BEARD_ALL"); //上下左右胡子

        private int typeID;
        private String value;

        BeardResEnum(int id, String value) {
            this.typeID = id;
            this.value = value;
        }

        public int getTypeId() {
            return this.typeID;
        }

        public String getResValue() {
            return this.value;
        }

        public static BeardResEnum getEnum(ZegoBeardInfo beard) {
            if (beard.has) {
                if (beard.leftAndRight)
                    return MALE_BEARD_ALL;

                if (beard.top && beard.bottom && !beard.leftAndRight)
                    return MALE_BEARD_TOPBOTTOM;

                if (beard.top && !beard.bottom && !beard.leftAndRight)
                    return MALE_BEARD_TOP;

                if (!beard.top && beard.bottom && !beard.leftAndRight)
                    return MALE_BEARD_BOTTOM;
            }

            return MALE_BEARD_NO;
        }


        /**
         * 查找对应的胡子 Enum
         *
         * @param typeID 类别ID
         * @return 胡子Enum
         */
        public static BeardResEnum getEnum(int typeID) {
            for (BeardResEnum item : BeardResEnum.values()) {
                if (item.typeID == (typeID)) {
                    return item;
                }
            }

            return MALE_BEARD_NO;
        }
    }

    //应用胡子
    private void applyBeard(ZegoBeardInfo beard, ZegoGenderType gender) {
        if (gender == ZegoGenderType.Male) {
            String packageName = getBeardPackage(BeardResEnum.getEnum(beard).getResValue());
            this.setPackageJson(packageName);
        }
    }

    private String getBeardPackage(String beardValue) {
        JSONObject propertyMapJson = ZegoConfigLoader.get(mBaseResPath).getConfig(ZegoConfigLoader.FILE_MALE_AVATAR_PROPERTY_MAP);
        ZALog.d(TAG, "beard before apply, beardValue = " + beardValue);

        String beardPackage = "m-beard_empty";

        try {
            if (propertyMapJson != null) {
                if (propertyMapJson.has("beard_detection_map")) ;
                {
                    JSONObject beardDetectionJson = propertyMapJson.getJSONObject("beard_detection_map");
                    if (beardDetectionJson.has(beardValue)) {
                        beardPackage = beardDetectionJson.getString(beardValue);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return beardPackage;
    }

    private String getHairPackage(ZegoHairInfo hair, ZegoGenderType gender) {
        JSONObject propertyMapJson = null;
        if (gender == ZegoGenderType.Female)
            propertyMapJson = ZegoConfigLoader.get(mBaseResPath).getConfig(ZegoConfigLoader.FILE_FEMALE_AVATAR_PROPERTY_MAP);
        else
            propertyMapJson = ZegoConfigLoader.get(mBaseResPath).getConfig(ZegoConfigLoader.FILE_MALE_AVATAR_PROPERTY_MAP);

        ZALog.d(TAG, "hair before apply, length = " + (hair.length.getInt() - 1) + ", forehead = " + (hair.bangs.getInt() - 1)
                + ", ponytail = " + (hair.ponytail.getInt() - 1));

        String key = null;
        String defaultKey = null;
        try {
            if (propertyMapJson != null) {
                if (propertyMapJson.has("hair_detection_map")) ;
                {
                    JSONObject hairDetectionJson = propertyMapJson.getJSONObject("hair_detection_map");
                    Iterator it = hairDetectionJson.keys();
                    while (it.hasNext()) {//遍历JSONObject
                        key = it.next().toString();
                        if (key.equals("default_hair_key")) {
                            defaultKey = hairDetectionJson.getString(key);
                        } else {
                            Object v = hairDetectionJson.get(key);
                            if (v instanceof JSONObject) {
                                JSONObject j = (JSONObject) v;
                                if (j.has("hair_infos")) {
                                    JSONArray hairInfos = j.getJSONArray("hair_infos");
                                    for (int i = 0; i < hairInfos.length(); i++) {
                                        int length = -1;
                                        int forehead = -1;
                                        int ponytail = -1;
                                        JSONObject info = hairInfos.getJSONObject(i);
                                        if (info.has("length"))
                                            length = info.getInt("length");
                                        if (info.has("forehead"))
                                            forehead = info.getInt("forehead");
                                        if (info.has("ponytail"))
                                            ponytail = info.getInt("ponytail");

                                        if (hair.length.getInt() - 1 == length
                                                && hair.bangs.getInt() - 1 == forehead
                                                && hair.ponytail.getInt() - 1 == ponytail) {

                                            ZALog.d(TAG, "getHairResKey length = " + length + ", forehead = " + forehead + ", ponytail = " + ponytail);
                                            return key;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return defaultKey;
    }

    /**
     * 寻找头发类型 ，设置头发
     **/
    private void applyHair(ZegoHairInfo hair, ZegoGenderType gender) {

        String packageName = getHairPackage(hair, gender);
        ZALog.d(TAG, "getHairResKey hair = " + hair.toString() + ", package = " + packageName + ", gender = " + gender.getInt());
        if (packageName != null) {
            this.setPackageJson(packageName);
        } else {
            ZALog.e(TAG, "error, getHairResKey HairProcessor hair = " + hair.toString() + ", package = null, gender = " + gender.getInt());
        }
    }


    private static String readStringFromAssets(Context context, String path) {
        StringBuilder stringBuilder = new StringBuilder();
        AssetManager assetManager = context.getAssets();
        try {
            BufferedReader bf = new BufferedReader(new InputStreamReader(assetManager.open(path)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    protected static String readStringFromFile(String strFilePath) {
        String result = "";
        File file = new File(strFilePath);
        //如果path是传递过来的参数，可以做一个非目录的判断
        if (file.isDirectory()) {
            Log.e("TestFile", "The File doesn't not exist.");
        } else {
            try {
                InputStream instream = new FileInputStream(file);
                if (instream != null) {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    String line;
                    //分行读取
                    while ((line = buffreader.readLine()) != null) {
                        result += line + "\n";
                    }
                    instream.close();
                }
            } catch (java.io.FileNotFoundException e) {
                ZALog.e(TAG, "The File doesn't not exist.");
            } catch (IOException e) {
                ZALog.e(TAG, e.getMessage());
            }
        }

        ZALog.d(TAG, "readStringFromFile " + strFilePath + " result:" + result);
        return result;
    }


    private String getPackagePath(String packageID) {

        if (packageID == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setPackage error,package is null");
            return null;
        }

        if (mHaveExtendPackagesPath) {
            //外置的package文件夹中查找
            File packageFile = new File(mExtendPackagesPath, packageID);
            if (packageFile.exists()) {
                String packagePath = packageFile.getPath();
                ZALog.i(TAG, "load extend package " + packageID + " from path:" + packagePath);
                return packagePath;
            }
        }

        File packageFile = new File(mBaseResPath, "Packages/" + packageID);
        if (packageFile.exists()) {
            //base bundle中查找
            String packagePath = packageFile.getPath();
            ZALog.i(TAG, "load base package " + packageID + " from path:" + packagePath);
            return packagePath;
        }


        ZALog.e(TAG, "package:" + packageID + " is not exit !");
        return null;
    }

    //拿 package.json 文件的 JOSN 对象
    private JSONObject getPackageJsonObj(String packagePath) {

        File packageFile = new File(packagePath, "package.json");
        String packageJson = readStringFromFile(packageFile.getAbsolutePath());
        try {
            JSONObject packageJsonObject = new JSONObject(packageJson);
            return packageJsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }


    private String getPackageType(String packagePath) {
        try {
            JSONArray properties = getPackageJsonObj(packagePath).getJSONArray("properties");
            if (properties.length() < 1) {
                ZALog.e(TAG, "getPackageType properties length is zero");
                return null;
            }
            // 这里取第一个property作为这个包的包类型
            JSONObject property = properties.getJSONObject(0);
            return property.getString("type");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 设置 package
     */
    private void loadPackageInternal(String packageID) {
        if (mCharacter == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setPackage error,mCharacter is null");
            return;
        }

        String packagePath = getPackagePath(packageID);
        if (null != packagePath) {
            ZALog.i(TAG, "loadPackageInternal setPackage path:" + packagePath);
            mCharacter.setPackage(packagePath);
        }

    }

    private void combineDefault(String modelID) {

        try {

            JSONObject defaultJson = ZegoConfigLoader.get(mBaseResPath).getDefaultJson(modelID);
            // 设置默认package
            if (defaultJson.has(PACKAGE_NODE)) {
                JSONObject packageJson = (JSONObject) defaultJson.get(PACKAGE_NODE);

                if (!mAvatarDict.has(PACKAGE_NODE)) {
                    mAvatarDict.put(PACKAGE_NODE, new JSONObject());
                }

                JSONObject targetJson = (JSONObject) mAvatarDict.get(PACKAGE_NODE);
                Iterator<String> its = packageJson.keys();
                while (its.hasNext()) {
                    String key = its.next();
                    String packageID = packageJson.getString(key);

                    if (!targetJson.has(key)) {
                        if (("shirt".equals(key) || "pants".equals(key)) && targetJson.has("suit")) {
                            // 已经有套装的前提下, 就不要从默认json里拷贝上衣和裤子了
                            continue;
                        }
                        targetJson.put(key, packageID);
                    }
                }
            }

            //设置默认properties
            if (defaultJson.has(PROPERTY_NODE)) {
                JSONObject propertyJson = (JSONObject) defaultJson.get(PROPERTY_NODE);
                if (!mAvatarDict.has(PROPERTY_NODE)) {
                    mAvatarDict.put(PROPERTY_NODE, new JSONObject());
                }

                JSONObject targetJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);
                Iterator<String> its = propertyJson.keys();
                while (its.hasNext()) {
                    String key = its.next();
                    String value = propertyJson.getString(key);
                    if (!targetJson.has(key)) {
                        targetJson.put(key, value);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            ZALog.e(TAG, "setDefaultAvatar defultJson error:" + e.getMessage());
        }
    }

    private void setPackageJson(String packageID) {
        if (packageID == null) {
            ZALog.e(TAG, "ZegoCharacterHelper setPackage error,package is null");
            return;
        }

        // 更新缓存数据
        try {
            String packagePath = getPackagePath(packageID);
            if (null == packagePath) {
                ZALog.e(TAG, "ZegoCharacterHelper setPackage package:" + packageID + " path is null");
                return;
            }


            if (!mAvatarDict.has(PACKAGE_NODE)) {
                JSONObject p = new JSONObject();
                mAvatarDict.put(PACKAGE_NODE, p);
            }
            JSONObject packageJson = mAvatarDict.getJSONObject(PACKAGE_NODE);
            String type = getPackageType(packagePath);
            ZALog.d(TAG, "setPackage, read type:" + type + " from package:" + packageID);

            packageJson.put(type, packageID);
            ZALog.d(TAG, "packageJson:" + packageJson);
            if ("suit".equals(type)) {
                packageJson.remove("shirt");
                packageJson.remove("pants");
            } else if ("shirt".equals(type)) {
                // 如果type类型为shirt，把身上套装去掉
                packageJson.remove("suit");
            } else if ("pants".equals(type)) {
                // 如果type类型为pants，把身上套装去掉
                packageJson.remove("suit");
            }
            mAvatarDict.put(PACKAGE_NODE, packageJson);
        } catch (JSONException e) {
            ZALog.e(TAG, "ZegoCharacterHelper setPackage to mAvatarDict error");
            e.printStackTrace();
        }

    }

    private void setPropertyJson(String key, String value) {
        // 更新缓存数据
        try {
            JSONObject propertyJson;
            if (mAvatarDict.has(PROPERTY_NODE)) {
                propertyJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);
            } else {
                propertyJson = new JSONObject();
            }
            propertyJson.put(key, value);
            ZALog.d(TAG, "setFaceShape propertyJson:" + propertyJson);
            mAvatarDict.put(PROPERTY_NODE, propertyJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setPropertyJson(String key, float value) {
        // 更新缓存数据
        try {
            JSONObject propertyJson;
            if (mAvatarDict.has(PROPERTY_NODE)) {
                propertyJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);
            } else {
                propertyJson = new JSONObject();
            }
            propertyJson.put(key, value);
            ZALog.d(TAG, "setFaceShape propertyJson:" + propertyJson);
            mAvatarDict.put(PROPERTY_NODE, propertyJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //将装扮捏脸显示出来
    private boolean applyToShow() {
        // 设置人模路径
        try {
            ZALog.i("initCharacter, mAvatarDict:" + mAvatarDict.toString());

            String modelName = mAvatarDict.optString(MODEL_ID);
            modelName = modelName.replace("human", "");     //兼容旧数据的人模ID
            String modelAbsolutePath = ZegoConfigLoader.get(mBaseResPath).getModelAbsPath(modelName);
            Log.i(TAG, "ZegoCharacterHelper,modelAbsolutePath: " + modelAbsolutePath);
            if (TextUtils.isEmpty(modelAbsolutePath)) {
                ZALog.e("ZegoCharacterHelper, initCharacter: Error, create character failed. modelPath is empty");
                return false;
            }
            mCharacter = ZegoAvatarService.createCharacter(modelAbsolutePath);

            if (mCharacter == null) {
                ZALog.e("ZegoCharacterHelper, initCharacter: Error, create character failed. modelPath=" + modelAbsolutePath);
                return false;
            }

            // 设置package
            if (mAvatarDict.has(PACKAGE_NODE)) {
                JSONObject packageJson = (JSONObject) mAvatarDict.get(PACKAGE_NODE);
                Iterator<String> its = packageJson.keys();
                while (its.hasNext()) {
                    String key = its.next();
                    String packageID = packageJson.getString(key);

                    loadPackageInternal(packageID);
                }
            }

            //设置properties
            if (mAvatarDict.has(PROPERTY_NODE)) {
                JSONObject propertyJson = (JSONObject) mAvatarDict.get(PROPERTY_NODE);

                Iterator<String> its = propertyJson.keys();
                while (its.hasNext()) {
                    String key = its.next();
                    String value = propertyJson.getString(key);

                    if (HAIR_COLOR_ROOT_ID.equals(key)) {
                        if (!value.isEmpty()) {
                            if (getHairMaterials() != null) {
                                mCharacter.setProperty(COLOR_HAIR_ROOT_PROPERTY.replace("hair_common", getHairMaterials()), value);
                            } else {
                                mCharacter.setProperty(COLOR_HAIR_ROOT_PROPERTY, value);
                            }
                        }
                    } else if (HAIR_COLOR_MAIN_ID.equals(key)) {
                        if (!value.isEmpty()) {
                            if (getHairMaterials() != null) {
                                mCharacter.setProperty(COLOR_HAIR_MAIN_PROPERTY.replace("hair_common", getHairMaterials()), value);
                            } else {
                                mCharacter.setProperty(COLOR_HAIR_MAIN_PROPERTY, value);
                            }
                        }
                    } else if (SKIN_COLOR_ID.equals(key)) {
                        if (!value.isEmpty()) {
                            mCharacter.setProperty(SKIN_COLOR_PROPERTY, value);
                        }
                    } else {
                        mCharacter.setProperty(key, value);
                    }
                }

            }

            // 更换view的character
//            if (mAvatarView != null && mAvatarView.getCharacter() != mCharacter) {
            if (mAvatarView != null) {
                ZALog.i(TAG, "initCharacter mAvatarView!!");
                mAvatarView.removeCharacter();
                mAvatarView.setCharacter(mCharacter, mCharacterCallback);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    public ZegoCharacter getCharacter() {
        return mCharacter;
    }

    public boolean hasPackage(String packageID) {
        if (TextUtils.isEmpty(packageID)) {
            return false;
        }
        String packagePath = getPackagePath(packageID);
        return null != packagePath;
    }

    public boolean hasPackageType(String type) {
        if (mAvatarDict.has(PACKAGE_NODE)) {
            try {
                JSONObject packageJson = mAvatarDict.getJSONObject(PACKAGE_NODE);
                return packageJson.has(type);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 检查指定的 avatarJson 中的 pacakge 是否都在本地存在, 不存在的packageID组装成数组返回, 如果都存在, 返回 null
     *
     * @param avatarJson
     * @return avatarJson 中本地不存在的资源ID 列表, 如果都存在, 返回 null
     */
    public List<String> checkAvatarPackage(String avatarJson) {
        if (TextUtils.isEmpty(avatarJson)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(avatarJson);
            JSONObject pkgs = json.optJSONObject(PACKAGE_NODE);
            if (pkgs != null) {
                Iterator<String> its = pkgs.keys();
                List<String> missing = new LinkedList<>();
                while (its.hasNext()) {
                    String key = its.next();
                    String packageID = pkgs.getString(key);

                    if (!this.hasPackage(packageID)) {
                        missing.add(packageID);
                    }
                }
                if (!missing.isEmpty()) {
                    return missing;
                }
            }
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /********************* ZegoConfigLoader *************************/
    static class ZegoConfigLoader {
        public static final String TAG = "ZegoConfigLoader";

        public static final String FILE_MALE_AVATAR_PROPERTY_MAP = "Makeupjson/male_avatar_property_map.json";
        public static final String FILE_FEMALE_AVATAR_PROPERTY_MAP = "Makeupjson/female_avatar_property_map.json";
        public static final String FILE_MALE_DEFAULT = "Makeupjson/male_default.json";
        public static final String FILE_FEMALE_DEFAULT = "Makeupjson/female_default.json";

        private static final String KEY_MODEL_PATH = "modelPath";

        private final String mBaseResPath;

        public static ConcurrentHashMap<String, ZegoConfigLoader> sConfigLoaders = new ConcurrentHashMap<>();

        /**
         * 根据 bundle 路径获取 configLoader
         *
         * @param baseResPath
         * @return
         */
        public static ZegoConfigLoader get(String baseResPath) {
            if (sConfigLoaders.containsKey(baseResPath)) {
                return sConfigLoaders.get(baseResPath);
            } else {
                ZegoConfigLoader loader = new ZegoConfigLoader(baseResPath);
                sConfigLoaders.put(baseResPath, loader);
                return loader;
            }
        }


        /**
         * 模型资源路径 path
         */
        private final ConcurrentHashMap<String, String> mPrefabPathMap = new ConcurrentHashMap<>();

        /**
         * 模型资源路径 path
         */
        private final ConcurrentHashMap<String, JSONObject> mJsonMap = new ConcurrentHashMap<>();

        private boolean mIsLoad = false;

        private ZegoConfigLoader(String baseResPath) {
            mBaseResPath = baseResPath;
        }

        public void preload() {
            if (mIsLoad) {
                return;
            }
            synchronized (ZegoConfigLoader.class) {
                // todo 这里可能要开线程
                loadConfig(FILE_MALE_AVATAR_PROPERTY_MAP);
                loadConfig(FILE_FEMALE_AVATAR_PROPERTY_MAP);
                JSONObject config = loadConfig(FILE_MALE_DEFAULT);
                if (config != null) {
                    mPrefabPathMap.put(ZegoCharacterHelper.MODEL_ID_MALE, config.optString(KEY_MODEL_PATH));
                }
                config = loadConfig(FILE_FEMALE_DEFAULT);
                if (config != null) {
                    mPrefabPathMap.put(ZegoCharacterHelper.MODEL_ID_FEMALE, config.optString(KEY_MODEL_PATH));
                }
                mIsLoad = true;
            }
        }

        private JSONObject loadConfig(String filePath) {
            File file = new File(mBaseResPath, filePath);
            String str = readStringFromFile(file.getAbsolutePath());
            if (TextUtils.isEmpty(str)) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(str);
                mJsonMap.put(filePath, json);
                return json;
            } catch (JSONException e) {
                e.printStackTrace();
                ZALog.e(TAG, "加载配置出错: " + file.getAbsolutePath() + ", error: " + e.getMessage());
            }
            return null;
        }

        public JSONObject getConfig(String filePath) {
            synchronized (ZegoConfigLoader.class) {
                JSONObject obj = mJsonMap.get(filePath);
                if (obj == null) {// 没有就拉取一次
                    obj = loadConfig(filePath);
                }
                return obj;
            }
        }

        public String getModelAbsPath(String modelID) {
            if (TextUtils.isEmpty(modelID)) {
                return null;
            }
            modelID = modelID.replace("human", "");     //兼容旧方法传入的人模ID
            return new File(mBaseResPath, Objects.requireNonNull(mPrefabPathMap.get(modelID))).getAbsolutePath();
        }

        public JSONObject getDefaultJson(String modelId) {
            return mJsonMap.get(ZegoCharacterHelper.MODEL_ID_MALE.equals(modelId) ? FILE_MALE_DEFAULT : FILE_FEMALE_DEFAULT);
        }
    }
}

