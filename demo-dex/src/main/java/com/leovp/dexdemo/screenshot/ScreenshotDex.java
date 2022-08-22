package com.leovp.dexdemo.screenshot;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.leovp.dex.DisplayUtil;
import com.leovp.dex.SurfaceControl;
import com.leovp.dex.util.CmnUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * https://github.com/rayworks/DroidCast
 * <p>
 * Author: Michael Leo
 * Date: 2022/1/19 09:01
 */
public class ScreenshotDex {
    private static final String TAG = "ScreenshotDex";

    private static final int IMAGE_QUALITY = 80;
    private static final float IMAGE_SCALE = 0.6F;

    private static final String IMAGE_JPEG = "image/jpeg";
    private static final String IMAGE_WEBP = "image/webp";
    private static final String IMAGE_PNG = "image/png";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String FORMAT = "format";

    private static int width = 0;
    private static int height = 0;

    private static int httpPort = 53516;

    private static DisplayUtil displayUtil;

    public static void main(String[] args) {
        CmnUtil.println(TAG, ">>> main entry - Begin");

        resolveArgs(args);

        Looper.prepare();
        displayUtil = new DisplayUtil();

        AsyncHttpServer httpServer = new AsyncHttpServer();
        httpServer.get("/screenshot", new AnyRequestCallback());

        httpServer.websocket("/src", (webSocket, request) -> {
            Pair<Integer, Integer> pair = getDimension();
            displayUtil.setRotateListener(rotate -> {
                CmnUtil.println(TAG, ">>> rotate to " + rotate);

                // delay for the new rotated screen
                Pair<Integer, Integer> dimen = getDimension();
                sendScreenshotData(webSocket, dimen.first, dimen.second);
            });
            new Thread(() -> {
                while (true) {
                    sendScreenshotData(webSocket, pair.first, pair.second);
                }
            }).start();
        });

        httpServer.listen(httpPort);
        Looper.loop();
    }

    private static void resolveArgs(String[] args) {
        if (args.length > 0) {
            String[] params = args[0].split("=");

            if ("--port".equals(params[0])) {
                try {
                    httpPort = Integer.parseInt(params[1]);
                    CmnUtil.println(TAG, TAG + " | Port set to " + httpPort);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @NonNull
    private static Pair<Integer, Integer> getDimension() {
        Point displaySize = displayUtil.getCurrentDisplaySize();

        int width = 1080;
        int height = 1920;
        if (displaySize != null) {
            width = (int) (displaySize.x * IMAGE_SCALE);
            height = (int) (displaySize.y * IMAGE_SCALE);
        }
        return new Pair<>(width, height);
    }

    private static void sendScreenshotData(WebSocket webSocket, int width, int height) {
        try {
            byte[] inBytes =
                    getScreenImageInBytes(
                            Bitmap.CompressFormat.JPEG,
                            width,
                            height,
                            (w, h, rotation) -> {
                                JSONObject obj = new JSONObject();
                                try {
                                    obj.put("width", w);
                                    obj.put("height", h);
                                    obj.put("rotation", rotation);

                                    webSocket.send(obj.toString());
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            });
            webSocket.send(inBytes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getScreenImageInBytes(
            Bitmap.CompressFormat compressFormat,
            int w,
            int h,
            @Nullable ImageDimensionListener resolver)
            throws IOException {

        int destWidth = w;
        int destHeight = h;
        Bitmap bitmap = SurfaceControl.screenshot(destWidth, destHeight, Surface.ROTATION_0);

        if (bitmap == null) {
            CmnUtil.println(TAG, String.format(Locale.ENGLISH,
                    ">>> failed to generate image with resolution %d:%d%n",
                    ScreenshotDex.width,
                    ScreenshotDex.height));

            destWidth /= 2;
            destHeight /= 2;

            bitmap = SurfaceControl.screenshot(destWidth, destHeight, Surface.ROTATION_0);
        }

        CmnUtil.println(TAG, String.format(Locale.ENGLISH,
                "Bitmap generated with resolution %d:%d, process id %d | thread id %d%n",
                destWidth,
                destHeight,
                Process.myPid(),
                Process.myTid()));

        int screenRotation = displayUtil.getScreenRotation();

        if (screenRotation != 0) {
            switch (screenRotation) {
                case 1: // 90 degree rotation (counter-clockwise)
                    bitmap = displayUtil.rotateBitmap(bitmap, -90f);
                    break;
                case 3: // 270 degree rotation
                    bitmap = displayUtil.rotateBitmap(bitmap, 90f);
                    break;
                case 2: // 180 degree rotation
                    bitmap = displayUtil.rotateBitmap(bitmap, 180f);
                    break;
                default:
                    break;
            }
        }

        assert bitmap != null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        CmnUtil.println(TAG, "Bitmap final dimens : " + width + "x" + height);
        if (resolver != null) {
            resolver.onResolveDimension(width, height, screenRotation);
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bitmap.compress(compressFormat, IMAGE_QUALITY, bout);
        bout.flush();

        // "Make sure to call Bitmap.recycle() as soon as possible, once its content is not
        // needed anymore."
        bitmap.recycle();

        return bout.toByteArray();
    }

    interface ImageDimensionListener {
        void onResolveDimension(int width, int height, int rotation);
    }

    static class AnyRequestCallback implements HttpServerRequestCallback {
        private Pair<Bitmap.CompressFormat, String> mapRequestFormatInfo(ImageFormat imageFormat) {
            Bitmap.CompressFormat compressFormat;
            String contentType;

            switch (imageFormat) {
                case JPEG:
                    compressFormat = Bitmap.CompressFormat.JPEG;
                    contentType = IMAGE_JPEG;
                    break;
                case PNG:
                    compressFormat = Bitmap.CompressFormat.PNG;
                    contentType = IMAGE_PNG;
                    break;
                case WEBP:
                    compressFormat = Bitmap.CompressFormat.WEBP;
                    contentType = IMAGE_WEBP;
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported image format detected");
            }

            return new Pair<>(compressFormat, contentType);
        }

        @Nullable
        private Pair<Bitmap.CompressFormat, String> getImageFormatInfo(String reqFormat) {
            ImageFormat format = ImageFormat.JPEG;

            if (!TextUtils.isEmpty(reqFormat)) {
                ImageFormat imageFormat = ImageFormat.resolveFormat(reqFormat);
                if (ImageFormat.UNKNOWN.equals(imageFormat)) {
                    return null;
                } else {
                    // default format
                    format = imageFormat;
                }
            }

            return mapRequestFormatInfo(format);
        }

        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            try {
                Multimap pairs = request.getQuery();

                String width = pairs.getString(WIDTH);
                String height = pairs.getString(HEIGHT);
                String reqFormat = pairs.getString(FORMAT);

                Pair<Bitmap.CompressFormat, String> formatInfo = getImageFormatInfo(reqFormat);

                if (formatInfo == null) {
                    response.code(400);
                    response.send(String.format(Locale.ENGLISH, "Unsupported image format : %s", reqFormat));

                    return;
                }

                if (!TextUtils.isEmpty(width) && !TextUtils.isEmpty(height) &&
                        TextUtils.isDigitsOnly(width) && TextUtils.isDigitsOnly(height)) {
                    ScreenshotDex.width = Integer.parseInt(width);
                    ScreenshotDex.height = Integer.parseInt(height);
                }

                if (ScreenshotDex.width == 0 || ScreenshotDex.height == 0) {
                    // dimension initialization
                    Point point = displayUtil.getCurrentDisplaySize();

                    if (point != null && point.x > 0 && point.y > 0) {
                        ScreenshotDex.width = point.x;
                        ScreenshotDex.height = point.y;
                    } else {
                        ScreenshotDex.width = 480;
                        ScreenshotDex.height = 800;
                    }
                }

                int destWidth = ScreenshotDex.width;
                int destHeight = ScreenshotDex.height;

                byte[] bytes = getScreenImageInBytes(formatInfo.first, destWidth, destHeight, null);
                response.send(formatInfo.second, bytes);

            } catch (Exception e) {
                e.printStackTrace();

                response.code(500);
                String template = ":(  Failed to generate the screenshot on device / emulator : %s - %s - Android OS : %s";
                String error = String.format(Locale.ENGLISH, template, Build.MANUFACTURER, Build.DEVICE, Build.VERSION.RELEASE);
                response.send(error);
            }
        }
    }
}
