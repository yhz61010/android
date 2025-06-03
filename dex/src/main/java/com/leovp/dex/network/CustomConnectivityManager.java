package com.leovp.dex.network;

import android.net.IConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.leovp.dex.DexHelper;
import com.leovp.dex.FakeContext;
import com.leovp.dex.util.CmnUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CustomConnectivityManager {
    private static final String TAG = "ConnMgr";

    public void registerNetworkCallback(@NonNull NetworkRequest request,
                                        @NonNull NetworkCallback networkCallback) {
        registerNetworkCallback(request, networkCallback, getDefaultHandler());
    }

    public void registerNetworkCallback(@NonNull NetworkRequest request,
                                        @NonNull NetworkCallback networkCallback, @NonNull Handler handler) {
        CallbackHandler cbHandler = new CallbackHandler(handler);
        NetworkCapabilities nc = null;
        try {
            Class<?> requestClass = Class.forName("android.net.NetworkRequest");
            Field field = requestClass.getField("networkCapabilities");
            nc = (NetworkCapabilities) field.get(request);
        } catch (Exception e) {
            CmnUtil.println(TAG, "registerNetworkCallback() error.", e);
        }

        sendRequestForNetwork(nc, networkCallback, 0, LISTEN, TYPE_NONE, cbHandler);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private @Nullable String getAttributionTag() {
        return DexHelper.getInstance().getContext(FakeContext.PACKAGE_NAME).getAttributionTag();
    }
    NetworkCallback callback;

    private NetworkRequest sendRequestForNetwork(NetworkCapabilities need, NetworkCallback callback,
                                                 int timeoutMs, int action, int legacyType, CallbackHandler handler) {
        this.callback = callback;
        NetworkRequest request = null;
        try {

            Messenger messenger = new Messenger(handler);
            Binder binder = new Binder();
            if (action == LISTEN) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 or above
                    // NetworkCapabilities networkCapabilities,
                    // Messenger messenger, IBinder binder, int callbackFlags, String callingPackageName,
                    // String callingAttributionTag
                    Method method = IConnectivityManager.class.getMethod("listenForNetwork",
                        NetworkCapabilities.class,
                        Messenger.class, IBinder.class, Integer.class, String.class,
                        String.class);
                    method.setAccessible(true);
                    method.invoke(mService, need, messenger, binder, callback.mFlags, FakeContext.PACKAGE_NAME, getAttributionTag());
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) { // Android 11
                    Method method = IConnectivityManager.class.getMethod("listenForNetwork", NetworkCapabilities.class, Messenger.class, IBinder.class, String.class);
                    method.setAccessible(true);
                    method.invoke(mService, need, messenger, binder, FakeContext.PACKAGE_NAME);
                } else { // Android 10 or below
                    request = mService.listenForNetwork(need, messenger, binder);
                }

            } else {
                request = mService.requestNetwork(need, messenger, timeoutMs, binder, legacyType);
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "sendRequestForNetwork() error.", e);
        }
        return request;
    }

    private final IConnectivityManager mService;
    final Handler handler = new Handler(msg -> {
        CmnUtil.println(TAG, msg.what);
        return false;
    });

    public CustomConnectivityManager(IConnectivityManager mService) {
        this.mService = mService;
    }

    private Handler getDefaultHandler() {
        return handler;
    }

    public void registerDefaultNetworkCallback(NetworkCallback networkCallback) {
        registerDefaultNetworkCallback(networkCallback, getDefaultHandler());
    }

    public void registerDefaultNetworkCallback(NetworkCallback networkCallback,
                                               Handler handler) {
        // This works because if the NetworkCapabilities are null,
        // ConnectivityService takes them from the default request.
        //
        // Since the capabilities are exactly the same as the default request's
        // capabilities, this request is guaranteed, at all times, to be
        // satisfied by the same network, if any, that satisfies the default
        // request, i.e., the system default network.
        CallbackHandler cbHandler = new CallbackHandler(handler);
        sendRequestForNetwork(null /* NetworkCapabilities need */, networkCallback, 0,
                REQUEST, TYPE_NONE, cbHandler);
    }

    private static final int LISTEN = 1;
    private static final int REQUEST = 2;
    public static final int TYPE_NONE = -1;

    public static final int BASE = 0x00080000;
    public static final int CALLBACK_PRECHECK = BASE + 1;
    public static final int CALLBACK_AVAILABLE = BASE + 2;
    public static final int CALLBACK_LOSING = BASE + 3;
    public static final int CALLBACK_LOST = BASE + 4;
    public static final int CALLBACK_UNAVAIL = BASE + 5;
    public static final int CALLBACK_CAP_CHANGED = BASE + 6;
    public static final int CALLBACK_IP_CHANGED = BASE + 7;
    private static final int EXPIRE_LEGACY_REQUEST = BASE + 8;
    public static final int CALLBACK_SUSPENDED = BASE + 9;
    public static final int CALLBACK_RESUMED = BASE + 10;
    public static final int CALLBACK_BLK_CHANGED = BASE + 11;

    private class CallbackHandler extends Handler {
        CallbackHandler(Looper looper) {
            super(looper);
        }

        CallbackHandler(Handler handler) {
        }

        @Override
        public void handleMessage(Message message) {
//            final NetworkRequest request = getObject(message, NetworkRequest.class);
            final Network network = getObject(message, Network.class);

            switch (message.what) {
                case CALLBACK_PRECHECK: {
                    callback.onPreCheck(network);
                    break;
                }
                case CALLBACK_AVAILABLE: {
//                    NetworkCapabilities cap = getObject(message, NetworkCapabilities.class);
//                    LinkProperties lp = getObject(message, LinkProperties.class);
                    callback.onAvailable(network);
                    break;
                }
                case CALLBACK_LOSING: {
                    callback.onLosing(network, message.arg1);
                    break;
                }
                case CALLBACK_LOST: {
                    callback.onLost(network);
                    break;
                }

                case CALLBACK_CAP_CHANGED: {
                    NetworkCapabilities cap = getObject(message, NetworkCapabilities.class);
                    callback.onCapabilitiesChanged(network, cap);
                    break;
                }
                case CALLBACK_IP_CHANGED: {
                    LinkProperties lp = getObject(message, LinkProperties.class);
                    callback.onLinkPropertiesChanged(network, lp);
                    break;
                }

                case CALLBACK_BLK_CHANGED: {
                    boolean blocked = message.arg1 != 0;
                    callback.onBlockedStatusChanged(network, blocked);
                }
            }
        }

        private <T> T getObject(Message msg, Class<T> c) {
            return msg.getData().getParcelable(c.getSimpleName());
        }
    }

    public static class NetworkCallback {
        public static final int FLAG_NONE = 0;
        public static final int FLAG_INCLUDE_LOCATION_INFO = 1 << 0;

        public NetworkCallback() {
            this(FLAG_NONE);
        }

        private static final int VALID_FLAGS = FLAG_INCLUDE_LOCATION_INFO;

        public NetworkCallback(int flags) {
            if ((flags & VALID_FLAGS) != flags) {
                throw new IllegalArgumentException("Invalid flags");
            }
            mFlags = flags;
        }
        public void onPreCheck(Network network) {
        }

        public void onAvailable(Network network,
                                NetworkCapabilities networkCapabilities,
                                LinkProperties linkProperties, boolean blocked) {
            onAvailable(network);
            if (!networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)) {
                onNetworkSuspended(network);
            }
            onCapabilitiesChanged(network, networkCapabilities);
            onLinkPropertiesChanged(network, linkProperties);
            onBlockedStatusChanged(network, blocked);
        }

        public void onAvailable(Network network) {
        }

        public void onLosing(Network network, int maxMsToLive) {
        }

        public void onLost(Network network) {
        }

        public void onUnavailable() {
        }

        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        }

        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        }

        public void onNetworkSuspended(Network network) {
        }

        public void onNetworkResumed(Network network) {
        }

        public void onBlockedStatusChanged(Network network, boolean blocked) {
        }

        private NetworkRequest networkRequest;
        private final int mFlags;
    }
}
