// Android WiFi helper and utility library
// Copyright Daniel Cook - Apache License
package dev.danielc.common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.provider.Settings;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class WiFiComm {
    public static final String TAG = "wifi";

    private static ConnectivityManager cm = null;
    public void setConnectivityManager(ConnectivityManager cm) {
        WiFiComm.cm = cm;
    }

    // Error codes
    public static final int NOT_AVAILABLE = -101;
    public static final int NOT_CONNECTED = -102;
    public static final int UNSUPPORTED_SDK = -103;

    static volatile Network wifiDevice = null;
    static volatile Network foundWiFiDevice = null;
    private volatile boolean selecting;
    private volatile boolean selectedAddressReady;
    private ConnectivityManager.NetworkCallback networkListener;
    public boolean selectedNetworkReady(){
        return selectedAddressReady&&isNetworkValid(foundWiFiDevice);
    }
    public static boolean hasCameraAddress(LinkProperties links){
        if(links==null)return false;
        for(android.net.LinkAddress address:links.getLinkAddresses()){
            java.net.InetAddress ip=address.getAddress();
            if(ip instanceof java.net.Inet4Address&&!ip.isAnyLocalAddress()&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())return true;
        }
        return false;
    }
    public boolean isSelecting(){return selecting;}
    public long selectedNetworkHandle(){Network selected=foundWiFiDevice;return selected==null?NOT_AVAILABLE:selected.getNetworkHandle();}
    public Object selectionToken(){return lastCallback;}

    /** Release only the app-owned hotspot request, after PTP CloseSession has finished. */
    public synchronized void releaseCameraNetwork(){
        ConnectivityManager.NetworkCallback previous=lastCallback;
        lastCallback=null;foundWiFiDevice=null;selectedAddressReady=false;selecting=false;
        if(previous!=null&&cm!=null){try{cm.unregisterNetworkCallback(previous);}catch(IllegalArgumentException|SecurityException ignored){}}
    }

    // Non-static event handlers since a frontend may need to spawn two listeners
    public Handler handler = null;
    public Runnable onAvailable = null;
    public Runnable onWiFiSelectAvailable = null;
    public Runnable onWiFiSelectCancel = null;
    public boolean blockEvents = false;

    void run(Runnable r) {
        if (blockEvents) return;
        if (r != null) {
            if (handler == null) {
                r.run();
            } else {
                handler.post(r);
            }
        }
    }

    volatile ConnectivityManager.NetworkCallback lastCallback = null;

    /** Opens an Android 10+ popup to prompt the user to select a WiFi network */
    public int connectToCameraAccessPoint(Context ctx, String ssid, String password) {
        return requestAccessPoint(ctx, password.isEmpty() ? null : password, null, ssid, true);
    }

    public int connectToVisibleCameraAccessPoint(Context ctx, String ssid, String password) {
        return requestAccessPoint(ctx, password.isEmpty()?null:password, null, ssid, false);
    }

    public int connectToAccessPoint(Context ctx, String password, PatternMatcher pattern) {
        return requestAccessPoint(ctx, password, pattern, null, false);
    }

    private synchronized int requestAccessPoint(Context ctx, String password, PatternMatcher pattern, String ssid, boolean hidden) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ConnectivityManager connectivityManager = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            ConnectivityManager.NetworkCallback previous=lastCallback;
            lastCallback=null;foundWiFiDevice=null;selectedAddressReady=false;selecting=false;
            if(previous!=null){try{connectivityManager.unregisterNetworkCallback(previous);}catch(IllegalArgumentException ignored){}}

            WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
            if (ssid != null) { builder.setSsid(ssid); builder.setIsHiddenSsid(hidden); }
            else builder.setSsidPattern(pattern);

            if (password != null) {
                builder.setWpa2Passphrase(password);

            }
            NetworkSpecifier specifier = builder.build();

            NetworkRequest request = new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(specifier)
                            .build();

            lastCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                  synchronized(WiFiComm.this){
                    if(lastCallback!=this)return;
                    selecting=false;
                    if(network.equals(foundWiFiDevice))return;
                    selectedAddressReady=false;
                    Log.d(TAG, "Network selected by user: " + network);
                    foundWiFiDevice = network;
                    run(onWiFiSelectAvailable);
                    isHandlingConflictingConnections();
                  }
                }
                @Override
                public void onUnavailable() {
                  synchronized(WiFiComm.this){
                    if(lastCallback!=this)return;
                    selecting=false;
                    Log.d(TAG, "Network unavailable, not selected by user");
                    foundWiFiDevice = null;selectedAddressReady=false;
                    run(onWiFiSelectCancel);
                  }
                }
                @Override
                public void onLost(Network network) {
                  synchronized(WiFiComm.this){
                    if(lastCallback!=this||!network.equals(foundWiFiDevice))return;
                    selecting=false;foundWiFiDevice = null;selectedAddressReady=false;
                    run(onWiFiSelectCancel);
                  }
                }
                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties links) {
                    synchronized(WiFiComm.this){if(lastCallback==this&&network.equals(foundWiFiDevice))selectedAddressReady=hasCameraAddress(links);}
                }
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    Log.d(TAG, "Selected Wi-Fi capabilities updated");
                }
            };
            selecting=true;
            try{connectivityManager.requestNetwork(request, lastCallback, 90000);}catch(RuntimeException e){selecting=false;lastCallback=null;throw e;}
            return 0;
        } else {
            return UNSUPPORTED_SDK;
        }
    }

    public synchronized void startNetworkListeners(Context ctx) {
        if(networkListener!=null)return;
        ConnectivityManager m = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
        requestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Wifi network is available: " + network.getNetworkHandle());
                wifiDevice = network;
                run(onAvailable);
            }
            @Override
            public void onLost(Network network) {
                Log.e(TAG, "Lost network\n");
                if(network.equals(wifiDevice))wifiDevice = null;
            }
            @Override
            public void onUnavailable() {
                Log.e(TAG, "Network unavailable\n");
                wifiDevice = null;
            }
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                Log.e(TAG, "capabilities changed");
            }
        };

        // TODO: Gracefully handle this
        try {
            m.registerNetworkCallback(requestBuilder.build(), networkCallback);
            networkListener=networkCallback;
        } catch (Exception e) {
            Log.e(TAG, "Unable to observe Wi-Fi networks", e);
        }
    }

    /** Determine if the device is handling two different WiFi connections at the same time, on the same band.
     * On Android 12+ devices, this causes a 2x rx/tx speed hit.
     * https://source.android.com/docs/core/connect/wifi-sta-sta-concurrency#local-only */
    public static boolean isHandlingConflictingConnections() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (isNetworkValid(wifiDevice) && isNetworkValid(foundWiFiDevice)) {
                NetworkCapabilities c1 = cm.getNetworkCapabilities(wifiDevice);
                if (c1 == null) return false;
                WifiInfo info1 = (WifiInfo) c1.getTransportInfo();
                if (info1 == null) return false;
                int mainBand = info1.getFrequency() / 100;
                NetworkCapabilities c2 = cm.getNetworkCapabilities(foundWiFiDevice);
                if (c2 == null) return false;
                WifiInfo info2 = (WifiInfo) c2.getTransportInfo();
                if (info2 == null) return false;
                if (info1.equals(info2)) return false; // Android may sometimes give the same object in both listeners
                int secondBand = info2.getFrequency() / 100;
                return mainBand == secondBand;
            }
        }
        return false;
    }

    public static boolean isNetworkValid(Network net) {
        if (cm == null) return false;
        if (net == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(net);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    // If we go through WifiNetworkSpecifier, then the device may be handling two concurrent connections.
    // This is up to a 2x performance hit.
    public static boolean isWiFiModuleCapableOfHandlingTwoConnections(Context ctx) {
        WifiManager wm = (WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 'Query whether or not the device supports concurrent station (STA) connections
            // for local-only connections using WifiNetworkSpecifier.'
            return wm.isStaConcurrencyForLocalOnlyConnectionsSupported();
        }
        // If below 31, then Android supposedly doesn't support concurrent connections at all
        return false;
    }

    public static boolean isHotSpotEnabled(Context ctx) {
        WifiManager wm = (WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            Method m = wm.getClass().getDeclaredMethod("isWifiApEnabled");
            m.setAccessible(true);
            if ((boolean)m.invoke(wm) == false) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    @SuppressLint("ObsoleteSdkInt")
    public static long getNetworkHandle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return UNSUPPORTED_SDK;
        }

        Network selected=foundWiFiDevice,defaultWifi=wifiDevice;
        // Prefer user-selected network
        if (isNetworkValid(selected)) {
            Log.d(TAG, "Returning found wifi device");
            return selected.getNetworkHandle();
        }

        if (isNetworkValid(defaultWifi)) {
            Log.d(TAG, "Returning default WiFi");
            return defaultWifi.getNetworkHandle();
        }

        Log.d(TAG, "WiFi network not available");
        return NOT_AVAILABLE;
    }

    public static void openHotSpotSettings(Context ctx) {
        Intent tetherSettings = new Intent();
        tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        ctx.startActivity(tetherSettings);
    }

    public static void openWiFiSettings(Activity ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.startActivityForResult(new Intent(Settings.Panel.ACTION_WIFI), 0);
        } else {
            ctx.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        }
    }
}
