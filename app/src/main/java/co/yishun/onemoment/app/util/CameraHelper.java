/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.yishun.onemoment.app.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import co.yishun.onemoment.app.config.Config;
import co.yishun.onemoment.app.net.request.sync.Data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Camera related utilities.
 */
public class CameraHelper {
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    static final Object lock = new Object();
    private static final String TAG = LogUtil.makeTag(CameraHelper.class);
    private static boolean isFrontCamera = false;

    /**
     * Iterate over supported camera preview sizes to see which one best fits the
     * dimensions of the given view while maintaining the aspect ratio. If none can,
     * be lenient with the aspect ratio.
     *
     * @param sizes        Supported camera preview sizes.
     * @param targetWidth  The width of the view.
     * @param targetHeight The height of the view.
     * @return Best match camera preview size to fit in the view.
     */
    public static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
//        double minHeightDiff = Double.MAX_VALUE;
//        double minWidthDiff = Double.MAX_VALUE;

        double ratio = ((double) targetWidth) / targetHeight;
        LogUtil.i(TAG, "target height: " + targetHeight + ", target ratio: " + ratio);
        double minRatioDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            if (size.height < targetHeight || size.width < targetWidth) continue;
            double sizeRatio = ((float) size.width) / size.height;
            double ratioDiff = Math.abs(sizeRatio - ratio);
            if (ratioDiff < minRatioDiff) {
                minRatioDiff = ratioDiff;
                optimalSize = size;
            }
            LogUtil.v("iter height", "width: " + size.width + ", height: " + size.height + ", ratio: " + sizeRatio);
//                if (size.height >= targetHeight && Math.abs(size.height - targetHeight) <= minHeightDiff
//                        && size.width >= targetWidth && Math.abs(size.width - targetWidth) <= minWidthDiff) {
//                    optimalSize = size;
//                    minHeightDiff = Math.abs(size.height - targetHeight);
//                    minWidthDiff = Math.abs(size.width - targetWidth);
//                }
        }
        LogUtil.i("selected size", "width: " + optimalSize.width + ", height: " + optimalSize.height);
        return optimalSize;
    }

    /**
     * @return the default camera on the device. Return null if there is no camera on the device.
     */
    public static Camera getCameraInstance() {
        LogUtil.d(TAG, "lock at getInstance");
        synchronized (lock) {
            return isFrontCamera ? getDefaultFrontFacingCameraInstance() : getDefaultBackFacingCameraInstance();
        }
    }

    public static boolean isFrontCamera() {
        return isFrontCamera;
    }

    public static void setFrontCamera(boolean isFront) {
        isFrontCamera = isFront;
    }

    public static void releaseCamera(Camera camera) {
        LogUtil.d(TAG, "lock at release");
        synchronized (lock) {
            camera.release();
        }
        LogUtil.d(TAG, "unlock at getInstance");
    }

    /**
     * @return the default rear/back facing camera on the device. Returns null if camera is not
     * available.
     */
    private static Camera getDefaultBackFacingCameraInstance() {
        return getDefaultCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    /**
     * @return the default front facing camera on the device. Returns null if camera is not
     * available.
     */
    private static Camera getDefaultFrontFacingCameraInstance() {
        return getDefaultCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }


    /**
     * @param position Physical position of the camera i.e Camera.CameraInfo.CAMERA_FACING_FRONT
     *                 or Camera.CameraInfo.CAMERA_FACING_BACK.
     * @return the default camera on the device. Returns null if camera is not available.
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static Camera getDefaultCamera(int position) {
        // Find the total number of cameras available
        int mNumberOfCameras = Camera.getNumberOfCameras();

        // Find the ID of the back-facing ("default") camera
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        try {
            for (int i = 0; i < mNumberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == position) {
                    return Camera.open(i);
                }
            }
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Fail to connect to camera service", e);
        }
        return null;
    }

    /**
     * Creates a media file in the {@code Environment.DIRECTORY_PICTURES} directory. The directory
     * is persistent and available to other applications like gallery.
     *
     * @param type Media type. Can be video or image.
     * @return A file object pointing to the newly created file.
     */
    @Deprecated
    public static File getOutputMediaFile(int type, Context context) {
        File mediaStorageDir = context.getDir(Config.VIDEO_STORE_DIR, Context.MODE_PRIVATE);

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            throw new IllegalStateException("unsupported type");
        }
        return mediaFile;
    }

    public enum Type {
        SYNCED {
            @Override
            public String getPrefix(Context context) {
                return AccountHelper.getIdentityInfo(context).get_id();
            }
        }, RECORDED {
            @Override
            public String getPrefix(Context context) {
                return "VID";
            }
        }, LOCAL {
            @Override
            public String getPrefix(Context context) {
                return "LOC";
            }
        }, LARGE_THUMB {
            @Override
            public String getPrefix(Context context) {
                return "LAT";
            }
        }, MICRO_THUMB {
            @Override
            public String getPrefix(Context context) {
                return "MIT";
            }
        };

        public abstract String getPrefix(Context context);
    }

    public static String getOutputMediaPath(Context context, Type type, @Nullable Long timestamp) {
        return getOutputMediaFile(context, type, timestamp).getPath();
    }

    public static File getOutputMediaFile(Context context, Type type, @Nullable Long timestamp) {
        File mediaStorageDir = context.getDir(Config.VIDEO_STORE_DIR, Context.MODE_PRIVATE);
        String time = new SimpleDateFormat(Config.TIME_FORMAT).format(timestamp == null ? new Date() : new Date(timestamp));
        return new File(mediaStorageDir.getPath() + File.separator + type.getPrefix(context) + time + Config.URL_HYPHEN + timestamp + Config.VIDEO_FILE_SUFFIX);
    }

    public static File getOutputMediaFile(Context context, Data syncedVideo) {
        return getOutputMediaFile(context, Type.SYNCED, syncedVideo.getTimeStamp());
    }

    public static long parseTimeStamp(File file) {
        return parseTimeStamp(file.getPath());
    }

    public static long parseTimeStamp(String pathOrFileName) {
        return Long.parseLong(pathOrFileName.substring(pathOrFileName.lastIndexOf(Config.URL_HYPHEN), pathOrFileName.lastIndexOf(".")));
    }

    /**
     * Get Converted path from origin media file path.
     *
     * @param path to the media file
     * @return path to the converted file.
     */
    @Deprecated
    public static String getConvertedMediaFile(String path) {
        File file = new File(path);
        String s = file.getParentFile().toString() + "/CON_" + file.getName();
        LogUtil.i(TAG, s);
        return s;
    }

    @Deprecated
    public static String getThumbFileName(String videoPathOrThumbPath, int kind) {
        File file = new File(videoPathOrThumbPath);
        String fileName = file.getName().substring(0, file.getName().lastIndexOf('.'));
        return fileName + ((kind == MediaStore.Images.Thumbnails.FULL_SCREEN_KIND) ? "_full" : "") + ".png";
    }

    public static String createLargeThumbImage(Context context, String videoPath) throws IOException {
        return createThumbImage(context, videoPath, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
    }

    public static String createThumbImage(Context context, String videoPath) throws IOException {
        return createThumbImage(context, videoPath, MediaStore.Images.Thumbnails.MICRO_KIND);
    }

    @Deprecated
    private static String createThumbImage(Context context, String videoPath, int kind) throws IOException {
        File thumbStorageDir = context.getDir(Config.VIDEO_THUMB_STORE_DIR, Context.MODE_PRIVATE);
        File thumbFile = new File(thumbStorageDir.getPath() + File.separator + getThumbFileName(videoPath, kind));

        if (thumbFile.exists()) thumbFile.delete();
        FileOutputStream fOut = new FileOutputStream(thumbFile);
        Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
        bitmap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
        fOut.flush();
        fOut.close();
        return thumbFile.getPath();
    }

}
