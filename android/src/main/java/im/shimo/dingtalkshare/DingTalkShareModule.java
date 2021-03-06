package im.shimo.dingtalkshare;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.android.dingtalk.share.ddsharemodule.DDShareApiFactory;
import com.android.dingtalk.share.ddsharemodule.IDDAPIEventHandler;
import com.android.dingtalk.share.ddsharemodule.IDDShareApi;
import com.android.dingtalk.share.ddsharemodule.ShareConstant;
import com.android.dingtalk.share.ddsharemodule.message.BaseReq;
import com.android.dingtalk.share.ddsharemodule.message.BaseResp;
import com.android.dingtalk.share.ddsharemodule.message.SendAuth;
import com.android.dingtalk.share.ddsharemodule.message.DDImageMessage;
import com.android.dingtalk.share.ddsharemodule.message.DDMediaMessage;
import com.android.dingtalk.share.ddsharemodule.message.DDWebpageMessage;
import com.android.dingtalk.share.ddsharemodule.message.SendMessageToDD;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class DingTalkShareModule extends ReactContextBaseJavaModule implements IDDAPIEventHandler {
    private static final String TAG = "DingTalkShare";

    private static final String NOT_INSTALLED_CODE = "NOT_INSTALLED";
    private static final String NOT_SUPPORTED_CODE = "NOT_SUPPORTED";
    private static final String SHARE_FAILED_CODE = "SHARE_FAILED";

    private static DingTalkShareModule mInstance;
    // 不能在构造方法里初始化，因为构造方法获取不到需要的 Activity.
    private static IDDShareApi mDDShareApi;
    private Promise mPromise;

    public DingTalkShareModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    public static IDDShareApi getDdShareApi(Context context) {
        if (mDDShareApi == null) {
            String appId = getAppID(context);
            mDDShareApi = DDShareApiFactory.createDDShareApi(context, appId, true);
        }
        return mDDShareApi;
    }

    public static DingTalkShareModule getInstance(ReactApplicationContext reactContext) {
        if (mInstance == null) {
            mInstance = new DingTalkShareModule(reactContext);
        }
        return mInstance;
    }

    @Override
    public String getName() {
        return "RNDingTalkShareModule";
    }

    @ReactMethod
    public void isInstalled(Promise promise) {
        IDDShareApi ddShareApi = getDdShareApi(getCurrentActivity());
        promise.resolve(ddShareApi.isDDAppInstalled());
    }

    @ReactMethod
    public void isSupported(Promise promise) {
        IDDShareApi ddShareApi = getDdShareApi(getCurrentActivity());
        promise.resolve(ddShareApi.isDDSupportAPI());
    }

    @ReactMethod
    public void shareWebPage(String url, String thumbImage, String title, String content, Promise promise) {
        mPromise = promise;
        if (!checkSupport()) {
            return;
        }
        //初始化一个DDWebpageMessage并填充网页链接地址
        DDWebpageMessage webPageObject = new DDWebpageMessage();
        webPageObject.mUrl = url;

        //构造一个DDMediaMessage对象
        DDMediaMessage webMessage = new DDMediaMessage();
        webMessage.mMediaObject = webPageObject;

        //填充网页分享必需参数，开发者需按照自己的数据进行填充
        webMessage.mTitle = title;
        webMessage.mContent = content;
        webMessage.mThumbUrl = thumbImage;
        //构造一个Req
        SendMessageToDD.Req webReq = new SendMessageToDD.Req();
        webReq.mMediaMessage = webMessage;

        if (!getDdShareApi(getCurrentActivity()).sendReq(webReq)) {
            mPromise.reject(SHARE_FAILED_CODE, "分享失败");
        }
    }

    /**
     * 分享图片
     */
    @ReactMethod
    private void shareImage(String image, Promise promise) {
        mPromise = promise;
        if (!checkSupport()) {
            return;
        }
        //初始化一个DDImageMessage
        DDImageMessage imageObject = new DDImageMessage();
        if (isLocalResource(image)) {
            imageObject.mImagePath = image;
        } else {
            imageObject.mImageUrl = image;
        }

        //构造一个mMediaObject对象
        DDMediaMessage mediaMessage = new DDMediaMessage();
        mediaMessage.mMediaObject = imageObject;

        //构造一个Req
        SendMessageToDD.Req req = new SendMessageToDD.Req();
        req.mMediaMessage = mediaMessage;

        if (!getDdShareApi(getCurrentActivity()).sendReq(req)) {
            mPromise.reject(SHARE_FAILED_CODE, "分享失败");
        }
    }

    //授权登录
    @ReactMethod
    public void getAuthCode(Promise promise) {
        mPromise = promise;
        if (!checkSupport()) {
            return;
        }
   
        SendAuth.Req req = new SendAuth.Req();
        req.scope = SendAuth.Req.SNS_LOGIN;
        req.state = "test";

        if (!getDdShareApi(getCurrentActivity()).sendReq(req)) {
            mPromise.reject(SHARE_FAILED_CODE, "授权失败");
        }
    }

    @Override
    public void onReq(BaseReq baseReq) {
        Log.d(TAG, "onReq");
    }

    @Override
    public void onResp(BaseResp baseResp) {
        int errCode = baseResp.mErrCode;

        if (baseResp.getType() == ShareConstant.COMMAND_SENDAUTH_V2 && (baseResp instanceof SendAuth.Resp)){
            SendAuth.Resp authResp = (SendAuth.Resp) baseResp;

            switch (errCode) {
                case BaseResp.ErrCode.ERR_OK:
                    WritableMap map = Arguments.createMap();
                    map.putString("code", authResp.code);
                    mPromise.resolve(map);
                    break;
                case BaseResp.ErrCode.ERR_USER_CANCEL:
                    mPromise.reject(errCode + "", "授权取消");
                    break;
                default:
                    mPromise.reject(errCode + "", "授权异常");
                    break;
            }
        } else {
            switch (errCode) {
                case BaseResp.ErrCode.ERR_OK:
                    mPromise.resolve("分享成功");
                    break;
                case BaseResp.ErrCode.ERR_USER_CANCEL:
                    mPromise.resolve("分享取消");
                    break;
                default:
                    mPromise.reject(errCode+"", "分享失败"+baseResp.mErrStr);
                    break;
            }
        }
    }

    /**
     * 获取钉钉 App ID
     *
     * @param context
     * @return
     */
    public static String getAppID(Context context) {
        ApplicationInfo appInfo = null;
        try {
            appInfo = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA);
            return appInfo.metaData.get("DT_APP_ID").toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void handleIntent(Intent intent) {
        if (mInstance != null && mDDShareApi != null) {
            mDDShareApi.handleIntent(intent, mInstance);
        }
    }

    private boolean isLocalResource(String url) {
        Uri thumbUri = Uri.parse(url);
        // Verify scheme is set, so that relative uri (used by static resources) are not handled.
        String scheme = thumbUri.getScheme();
        return (scheme == null || scheme.equals("file"));
    }

    private boolean checkSupport() {
        IDDShareApi ddShareApi = getDdShareApi(getCurrentActivity());
        if (!ddShareApi.isDDAppInstalled()) {
            mPromise.reject(NOT_INSTALLED_CODE, "请安装钉钉客户端");
            return false;
        } else if (!ddShareApi.isDDSupportAPI()) {
            mPromise.reject(NOT_SUPPORTED_CODE, "请升级钉钉客户端");
            return false;
        }
        return true;
    }
}
