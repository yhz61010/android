package com.leovp.demo_dex.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Author: Michael Leo
 * Date: 2022/1/6 13:47
 */
public class Util {
    private static final Util ourInstance = new Util();

    public static Util getInstance() {
        return ourInstance;
    }

    private Util() {
    }

    public List<String> getIp() {
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
            e.printStackTrace();
        }
        return ifconfig;
    }
}
