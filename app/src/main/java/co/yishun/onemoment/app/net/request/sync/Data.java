package co.yishun.onemoment.app.net.request.sync;

import co.yishun.onemoment.app.config.Config;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by Carlos on 2015/4/5.
 */
public class Data implements Serializable, Comparable<Data>, Map.Entry<Integer, Data> {
    private String mimeType;
    private String fsize;
    private String hash;
    private String key;
    private String putTime;

    //<userID>-<time>-<timestamp>.mp4
    public Data() {
    }

    public String getFsize() {
        return fsize;
    }

    public String getHash() {
        return hash;
    }

    public String getQiuniuKey() {
        return key;
    }

    @Override
    public Integer getKey() {
        return Integer.parseInt(getTime());
    }

    public String getTime() {
        return this.key.substring(key.indexOf(Config.URL_HYPHEN), key.lastIndexOf(Config.URL_HYPHEN));
    }

    @Override
    public Data getValue() {
        return this;
    }

    @Override
    public Data setValue(Data object) {
        return null;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getPutTime() {
        return putTime;
    }

    public long getTimeStamp() {
        return Long.parseLong(key.substring(key.lastIndexOf(Config.URL_HYPHEN), key.lastIndexOf(".")));
    }

    @Override
    public int compareTo(Data another) {
        return this.getKey() - another.getKey();
    }
}