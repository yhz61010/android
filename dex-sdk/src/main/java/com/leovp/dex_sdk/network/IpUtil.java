package com.leovp.dex_sdk.network;

import com.leovp.dex_sdk.util.CmnUtil;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Author: Michael Leo
 * Date: 2022/1/18 10:07
 */
public class IpUtil {
    private static final String TAG = "IpUtil";

    public static List<String> getIp() {
        final List<String> ifconfig = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> niList = NetworkInterface.getNetworkInterfaces(); niList.hasMoreElements(); ) {
                NetworkInterface ni = niList.nextElement();
                for (Enumeration<InetAddress> iaList = ni.getInetAddresses(); iaList.hasMoreElements(); ) {
                    InetAddress addr = iaList.nextElement();
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && addr.isSiteLocalAddress()) {
                        String address = addr.getHostAddress();
                        if (address != null) {
                            ifconfig.add(address);
                        }
                    }
                }
            }
        } catch (Exception e) {
            CmnUtil.println(TAG, "getIp() error.", e);
        }
        return ifconfig;
    }
}
