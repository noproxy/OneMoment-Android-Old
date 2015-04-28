package co.yishun.onemoment.app.sync;

import android.accounts.Account;
import android.content.*;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import co.yishun.onemoment.app.config.Config;
import co.yishun.onemoment.app.config.ErrorCode;
import co.yishun.onemoment.app.data.Moment;
import co.yishun.onemoment.app.data.MomentDatabaseHelper;
import co.yishun.onemoment.app.net.request.sync.Data;
import co.yishun.onemoment.app.net.request.sync.DeleteVideo;
import co.yishun.onemoment.app.net.request.sync.GetToken;
import co.yishun.onemoment.app.net.request.sync.GetVideoList;
import co.yishun.onemoment.app.util.AccountHelper;
import co.yishun.onemoment.app.util.CameraHelper;
import co.yishun.onemoment.app.util.LogUtil;
import com.j256.ormlite.dao.Dao;
import com.koushikdutta.ion.Ion;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.android.utils.Etag;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EBean;
import org.androidannotations.annotations.OrmLiteDao;
import org.androidannotations.annotations.SystemService;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handle the transfer of data between a server and an
 * app, using the Android sync adapter framework.
 * <p>
 * Created by Carlos on 3/10/15.
 */
@EBean
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = LogUtil.makeTag(SyncAdapter.class);
    ContentResolver mContentResolver;
    public static final String BUNLDE_IGNORE_NETWORK = "boolean_ignore_network";

    public SyncAdapter(Context context) {
        super(context, true);
        mContentResolver = context.getContentResolver();
    }

    @SystemService
    ConnectivityManager connectivityManager;

    /**
     * To execute sync. When sync end, it will call {@link #syncDone(boolean)} to send broadcast notify sync process ending. And in syncing process, it calls {@link #syncUpdate(UpdateType, int, int, int)} to broadcast progress.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        LogUtil.i(TAG, "onPerformSync, account: " + account.name + ", Bundle: " + extras);

        if (!extras.getBoolean(BUNLDE_IGNORE_NETWORK, false) && !connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
            LogUtil.i(TAG, "cancel sync because network is not wifi");
            return;
        }
        //see:http://developer.android.com/training/sync-adapters/creating-sync-adapter.html

        new GetVideoList().with(getContext()).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
                syncDone(false);
            } else if (result.getCode() == ErrorCode.SUCCESS) {
                sync(toMap(result.getDatas()));
            } else {
                LogUtil.e(TAG, "get video list failed: " + result.getCode());
                syncDone(false);
            }
        });

    }

    @OrmLiteDao(helper = MomentDatabaseHelper.class, model = Moment.class)
    Dao<Moment, Integer> dao;

    @Override public void onSyncCanceled() {
        //TODO how to handle cancel
        LogUtil.i(TAG, "onSyncCanceled");
        super.onSyncCanceled();
    }

    /**
     * compare local video with server's, and determine whether upload or download.
     * <p>
     * TODO Refactor: just determine which action to take, and commit the action task to a queue
     *
     * @param videosOnServer map of server's videos
     */
    private void sync(Map<Integer, Data> videosOnServer) {
        try {
            LogUtil.i(TAG, "video got, start sync");
            for (Moment moment : dao) {
                Integer key = Integer.parseInt(moment.getTime());
                Data video = videosOnServer.get(key);

                if (Thread.interrupted()) {
                    LogUtil.e("TAG", "interrupted");
                    syncDone(false);
                    return;
                }

                LogUtil.v(TAG, "sync iter: " + moment.toString());
                if (video != null) {
                    LogUtil.v(TAG, "on server: " + video.toString());
                    //if server has today moment
                    if (video.getTimeStamp() > moment.getTimeStamp()) {
                        //if server is newer, download and delete older
                        LogUtil.v(TAG, "download: " + video.toString());
                        downloadVideo(video, moment);
                    } else if (video.getTimeStamp() < moment.getTimeStamp()) {
                        //if local is newer, upload and delete older
                        LogUtil.v(TAG, "upload: " + video.toString());
                        uploadMoment(moment, video);
                    } else {
                        LogUtil.v(TAG, "same, do nothing");
                        checkMomentOwnerPrivate(moment);
                    }
                    //the video sync ok, remove
                    videosOnServer.remove(key);
                } else uploadMoment(moment, null);//server not have today moment, upload
            }
            for (Data data : videosOnServer.values()) {
                if (Thread.interrupted()) {
                    LogUtil.e("TAG", "interrupted");
                    return;
                }
                downloadVideo(data, null);//other unhandled video mean they need download
            }
            syncDone(true);
        } catch (Exception e) {
            //catch all to avoid crash background
            e.printStackTrace();
            syncDone(false);
        }
    }

    /**
     * Ensure a moment and it's file is user private. if not, it will be change private.
     */
    private void checkMomentOwnerPrivate(@NonNull Moment moment) {
        try {
            //set file name
            String path = moment.getPath();
            if (CameraHelper.whatTypeOf(path) == CameraHelper.Type.LOCAL) {
                File file = new File(path);
                File syncedFile = CameraHelper.getOutputMediaFile(getContext(), CameraHelper.Type.SYNCED, file);
                if (file.renameTo(syncedFile)) {
                    moment.setPath(syncedFile.getPath());
                } else
                    LogUtil.e(TAG, "unable to rename when checkMomentOwnerPrivate, from " + file.getPath() + " to " + syncedFile.getPath());
            }
            //set database owner
            if (moment.isPublic()) {
                moment.setOwner(AccountHelper.getIdentityInfo(getContext()).get_id());
            }
            dao.update(moment);
            LogUtil.i(TAG, "succeed in setting moment[ " + moment + "] private");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * send broadcast to notify finishing syncing
     */
    private void syncDone(boolean isSuccess) {
        Intent intent = new Intent(SYNC_BROADCAST_DONE);
        intent.putExtra(SYNC_BROADCAST_EXTRA_IS_UPLOAD_CHANGED, isUploadChanged);
        intent.putExtra(SYNC_BROADCAST_EXTRA_IS_DOWNLOAD_CHANGED, isDownloadChanged);
        intent.putExtra(SYNC_BROADCAST_EXTRA_IS_SUCCESS, isSuccess);
        LogUtil.i(TAG, "sync done, send a broadcast. isUploadChanged: " + isUploadChanged + ", isDownloadChanged: " + isDownloadChanged + ", isSuccess " + isSuccess);
        getContext().sendBroadcast(intent);
        isUploadChanged = false;
        isDownloadChanged = false;

        cleanFile();
    }

    private void cleanFile() {
        try {
            //delete useless video
            File mediaDir = CameraHelper.getOutputMediaDir(getContext());
            for (String s : mediaDir.list((dir, filename) -> filename.startsWith(CameraHelper.Type.RECORDED.getPrefix(getContext())))) {
                File toDeleted = new File(mediaDir, s);
                LogUtil.i(TAG, "delete: " + toDeleted);
                if (Thread.interrupted()) {
                    LogUtil.e("TAG", "interrupted");
                    return;
                }
                if (toDeleted.exists()) toDeleted.delete();
            }

            //delete unregistered local video
            for (String s : mediaDir.list((dir, filename) -> {
                try {
                    if (filename.startsWith(CameraHelper.Type.LOCAL.getPrefix(getContext()))) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("path", dir.getPath() + File.pathSeparator + filename);
                        LogUtil.v(TAG, "query for path: " + map.get("path").toString());
                        return (dao.queryForFieldValues(map).isEmpty());
                    } else return false;
                } catch (SQLException e) {
                    e.printStackTrace();
                    return false;
                }
            })) {
                File toDeleted = new File(mediaDir, s);
                if (Thread.interrupted()) {
                    LogUtil.e("TAG", "interrupted");
                    return;
                }
                LogUtil.i(TAG, "delete: " + toDeleted);
                if (toDeleted.exists()) toDeleted.delete();
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Exception when clean useless video");
            e.printStackTrace();
        }
    }

    /**
     * send broadcast to notify syncing progress update.
     * <p>
     * progress is from 0 to 100.
     * <p>
     * {@link #PROGRESS_NOT_AVAILABLE} means no progress data available.
     * {@link #PROGRESS_ERROR} means error occurred.
     */
    private void syncUpdate(UpdateType type, int thisProgress, int thisTypeProgress, int allProgress) {
        Intent intent = new Intent(type.getAction());
        intent.putExtra(SYNC_BROADCAST_EXTRA_THIS_PROGRESS, thisProgress);
        intent.putExtra(SYNC_BROADCAST_EXTRA_TYPE_PROGRESS, thisTypeProgress);
        intent.putExtra(SYNC_BROADCAST_EXTRA_ALL_PROGRESS, allProgress);
        LogUtil.i(TAG, "sync update progress, send a broadcast. type: " + type.name() + ", this progress: " + thisProgress + ", type progress: " + thisTypeProgress + ", all progress: " + allProgress);
        getContext().sendBroadcast(intent);
    }

    enum UpdateType {
        UPLOAD {
            @Override String getAction() { return SYNC_BROADCAST_UPDATE_UPLOAD; }
        },
        DOWNLOAD {
            @Override String getAction() { return SYNC_BROADCAST_UPDATE_DOWNLOAD; }
        };

        abstract String getAction();
    }

    /**
     * whether local data update while sync. if true, need to notify some ui update
     */
    boolean isUploadChanged = false;
    boolean isDownloadChanged = false;

    public static final String SYNC_BROADCAST_DONE = "co.yishun.onemoment.app.sync.done";
    public static final String SYNC_BROADCAST_UPDATE_UPLOAD = "co.yishun.onemoment.app.sync.update.upload";
    public static final String SYNC_BROADCAST_UPDATE_DOWNLOAD = "co.yishun.onemoment.app.sync.update.download";
    public static final String SYNC_BROADCAST_EXTRA_IS_UPLOAD_CHANGED = "is_upload_changed";
    public static final String SYNC_BROADCAST_EXTRA_IS_DOWNLOAD_CHANGED = "is_download_changed";
    public static final String SYNC_BROADCAST_EXTRA_IS_SUCCESS = "is_success";
    public static final String SYNC_BROADCAST_EXTRA_THIS_PROGRESS = "int_this_progress";
    public static final String SYNC_BROADCAST_EXTRA_TYPE_PROGRESS = "int_type_progress";
    public static final String SYNC_BROADCAST_EXTRA_ALL_PROGRESS = "int_all_progress";

    public static final int PROGRESS_NOT_AVAILABLE = -1;
    public static final int PROGRESS_ERROR = -2;

    private UploadManager mUploadManager;

    private UploadManager getUploadManager() {
        if (mUploadManager == null) {
            mUploadManager = new UploadManager();
        }
        return mUploadManager;
    }

    /**
     * upload a moment to server. if success, make moment private.
     *
     * @param moment        to upload
     * @param videoToDelete old version on server
     */
    private void uploadMoment(Moment moment, Data videoToDelete) {
        LogUtil.i(TAG, "upload a moment: " + moment.getPath());
        String qiNiuKey = getQiniuVideoFileName(moment);
        syncUpdate(UpdateType.UPLOAD, 0, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
        new GetToken().setFileName(qiNiuKey).with(getContext()).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
            } else if (result.getCode() != ErrorCode.SUCCESS) LogUtil.e(TAG, "get token failed: " + result.getCode());
            else
                getUploadManager().put(moment.getPath(),
                        qiNiuKey,
                        result.getData().getToken(),
                        (s, responseInfo, jsonObject) -> {
                            LogUtil.i(TAG, responseInfo.toString());
                            if (responseInfo.isOK()) {
                                if (videoToDelete != null) deleteVideo(videoToDelete);
                                LogUtil.i(TAG, "a moment upload ok: " + moment.getPath());
                                checkMomentOwnerPrivate(moment);
                                isUploadChanged = true;
                                syncUpdate(UpdateType.UPLOAD, 100, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
                            } else
                                syncUpdate(UpdateType.UPLOAD, PROGRESS_ERROR, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
                        },
                        new UploadOptions(null, Config.MIME_TYPE, true, null, null)
                );
        });
    }

    @Background void deleteVideo(@NonNull Data videoOnServer) {
        LogUtil.i(TAG, "delete a video: " + videoOnServer.getQiuniuKey());
        new DeleteVideo().setFileName(videoOnServer.getQiuniuKey()).with(getContext()).setCallback((e, result) -> {
            if (e != null) {
                e.printStackTrace();
            } else if (result.getCode() != ErrorCode.SUCCESS)
                LogUtil.e(TAG, "delete video token failed: " + result.getCode());
        });

    }

    private boolean deleteMoment(@NonNull Moment moment) {
        LogUtil.i(TAG, "delete a moment: " + moment.getPath());
        File momentFile = moment.getFile();
        File thumb = new File(moment.getThumbPath());
        File thumbL = new File(moment.getLargeThumbPath());

        if (momentFile.exists()) momentFile.delete();
        if (thumb.exists()) thumb.delete();
        if (thumbL.exists()) thumbL.delete();

        try {
            return dao.delete(moment) == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * download a video from server, then register private moment in database.
     *
     * @param aVideoOnServer video to download.
     * @param momentOld      old local moment. It will do nothing if it is null.
     */
    private void downloadVideo(Data aVideoOnServer, @Nullable Moment momentOld) {
        LogUtil.i(TAG, "download a video: " + aVideoOnServer.getQiuniuKey());
        File fileSynced = CameraHelper.getOutputMediaFile(getContext(), aVideoOnServer);
//            OkHttpClient client = new OkHttpClient();
//            Request request = new Request.Builder().url(Config.getResourceUrl(getContext()) + aVideoOnServer.getQiuniuKey()).get().build();
//            Response response = client.newCall(request).execute();
//            response.body().
        syncUpdate(UpdateType.DOWNLOAD, 0, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);

        //if file exist, just register private moment at database
        try {
            if (fileSynced.exists()) {
                if (Etag.file(fileSynced).equals(aVideoOnServer.getHash())) {
                    String pathToThumb = CameraHelper.createThumbImage(getContext(), fileSynced.getPath());
                    String pathToLargeThumb = CameraHelper.createLargeThumbImage(getContext(), fileSynced.getPath());

                    dao.create(Moment.from(aVideoOnServer, fileSynced.getPath(), pathToThumb, pathToLargeThumb));

                    syncUpdate(UpdateType.DOWNLOAD, 100, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
                    return;
                } else fileSynced.delete();
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }

        //download and register
        Ion.with(getContext()).load(Config.getResourceUrl(getContext()) + aVideoOnServer.getQiuniuKey())
                .write(fileSynced).setCallback((e, result) -> {
            try {
                if (e != null) {
                    throw e;
                }
                if (momentOld != null && !deleteMoment(momentOld)) {
                    LogUtil.e(TAG, "delete old local moment failed: " + momentOld.getPath());
                }

                String pathToThumb = CameraHelper.createThumbImage(getContext(), fileSynced.getPath());
                String pathToLargeThumb = CameraHelper.createLargeThumbImage(getContext(), fileSynced.getPath());

                dao.create(Moment.from(aVideoOnServer, fileSynced.getPath(), pathToThumb, pathToLargeThumb));

                LogUtil.i(TAG, "a video download ok: " + aVideoOnServer.getQiuniuKey());
                isDownloadChanged = true;
                syncUpdate(UpdateType.DOWNLOAD, 100, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
            } catch (Exception e1) {
                syncUpdate(UpdateType.DOWNLOAD, PROGRESS_ERROR, PROGRESS_NOT_AVAILABLE, PROGRESS_NOT_AVAILABLE);
                e1.printStackTrace();
            }
        });

    }

    private String getQiniuVideoFileName(Moment moment) {
        String re = AccountHelper.getIdentityInfo(getContext()).get_id() + Config.URL_HYPHEN + moment.getTime() + Config.URL_HYPHEN + moment.getTimeStamp() + Config.VIDEO_FILE_SUFFIX;
        LogUtil.i(TAG, "qiniu filename: " + re);
        return re;
    }

    /**
     * convert array to HashMap
     * <p>
     * No generics is from Apache ArrayUtils, and generics version is from <a href="http://stackoverflow.com/questions/6416346/adding-generics-to-arrayutils-tomap">Stack Overflow</a>
     *
     * @param array
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, V> toMap(Object[] array) {
        if (array == null) {
            return null;
        }

        final Map<K, V> map = new HashMap<K, V>((int) (array.length * 1.5));
        for (int i = 0; i < array.length; i++) {
            Object object = array[i];
            if (object instanceof Map.Entry) {
                Map.Entry<K, V> entry = (Map.Entry<K, V>) object;
                map.put(entry.getKey(), entry.getValue());
            } else if (object instanceof Object[]) {
                Object[] entry = (Object[]) object;
                if (entry.length < 2) {
                    throw new IllegalArgumentException("Array element " + i
                            + ", '" + object + "', has a length less than 2");
                }
                map.put((K) entry[0], (V) entry[1]);
            } else {
                throw new IllegalArgumentException("Array element " + i + ", '"
                        + object + "', is neither of type Map.Entry nor an Array");
            }
        }
        return map;
    }
}
