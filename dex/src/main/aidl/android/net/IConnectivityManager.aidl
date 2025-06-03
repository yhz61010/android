package android.net;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.ProxyInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.ResultReceiver;

/**
 * Interface that answers queries about, and allows changing, the
 * state of network connectivity.
 */
interface IConnectivityManager {
    NetworkRequest listenForNetwork(in NetworkCapabilities networkCapabilities,
        in Messenger messenger, in IBinder binder);

    NetworkRequest requestNetwork(in NetworkCapabilities networkCapabilities,
        in Messenger messenger, int timeoutSec, in IBinder binder, int legacy);
}
