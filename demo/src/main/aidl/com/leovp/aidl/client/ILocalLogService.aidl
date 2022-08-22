package com.leovp.aidl.client;
import com.leovp.aidl.client.model.LocalLog;

interface ILocalLogService {

    int getLogCount(String app);

    void writeLog(String appPackage, in LocalLog log);
}
