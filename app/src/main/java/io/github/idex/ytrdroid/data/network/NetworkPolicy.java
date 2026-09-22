package io.github.idex.ytrdroid.data.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public class NetworkPolicy {
    private final Context context;

    public NetworkPolicy(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public boolean isNetworkAvailable() {
        if (context == null) return true;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public boolean isWifiConnected() {
        if (context == null) return true;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public boolean canExecute(boolean wifiOnly) {
        if (!isNetworkAvailable()) return false;
        return !wifiOnly || isWifiConnected();
    }
}
