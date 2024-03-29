package co.yishun.onemoment.app.net.result;

/**
 * Created by Carlos on 2/16/15.
 */
public class VerificationResult {
    private int code;
    private String msg;
    private Data data;
    private int error_code;

    public int getErrorCode() {
        return error_code;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Data getData() {
        return data;
    }

    public static class Data {
        private String nickname;

        public String getNickname() {
            return nickname;
        }
    }
}
