package co.yishun.onemoment.app.config;

/**
 * Created by Carlos on 2/15/15.
 */
public class Config {
    private final static String URL_MAIN = "http://test.yishun.co";

    private final static String URL_SIGN_UP_BY_PHONE = "/api/v2/signup";
    private final static String URL_SIGN_UP_BY_WEIBO = "/api/v2/weibo_signup";
    private final static String URL_SIGN_UP_BY_WECHAT = "/api/v2/weixin_signup";

    private final static String URL_SIGN_IN = "/api/v2/signin";
    private final static String URL_RESET_PASSWORD = "/api/v2/reset_password";
    private final static String URL_IDENTITY_INFO_GET = "/api/v2/account/";
    private final static String URL_IDENTITY_INFO_UPDATE = "/api/v2/update_account/";

    private final static String URL_CHECK_NICKNAME = "/api/v2/check_nickname";
    public static final String URL_VERIFICATION_SEND_SMS = "/api/v2/send_verify_sms";
    public static final String URL_VERIFICATION_VERIFY = "/api/v2/verify_phone";


    private final static String PRIVATE_KEY = "jfio2q3de0ajd0923i9faoik209q0r83u4rjew";

    public static String getUrlSignUpByPhone() {
        return URL_MAIN + URL_SIGN_UP_BY_PHONE;
    }

    public static String getUrlSignUpByWeibo() {
        return URL_MAIN + URL_SIGN_UP_BY_WEIBO;
    }

    public static String getUrlSignUpByWeChat() {
        return URL_MAIN + URL_SIGN_UP_BY_WECHAT;

    }

    public static String getUrlSignIn() {
        return URL_MAIN + URL_SIGN_IN;
    }

    public static String getUrlResetPassword() {
        return URL_MAIN + URL_RESET_PASSWORD;
    }

    public static String getUrlIdentityInfoGet() {
        return URL_MAIN + URL_IDENTITY_INFO_GET;
    }

    public static String getUrlIdentityInfoUpdate() {
        return URL_MAIN + URL_IDENTITY_INFO_UPDATE;
    }

    public static String getUrlCheckNickname() {
        return URL_MAIN + URL_CHECK_NICKNAME;
    }

    public static String getUrlVerificationSendSms() {
        return URL_MAIN + URL_VERIFICATION_SEND_SMS;
    }

    public static String getUrlVerificationVerify() {
        return URL_MAIN + URL_VERIFICATION_VERIFY;
    }

    public static String getPrivateKey() {
        return PRIVATE_KEY;
    }


}