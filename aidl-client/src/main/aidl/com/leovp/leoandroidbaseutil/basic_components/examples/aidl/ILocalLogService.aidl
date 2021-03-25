package com.leovp.leoandroidbaseutil.basic_components.examples.aidl;
import com.leovp.leoandroidbaseutil.basic_components.examples.aidl.model.LocalLog;

interface ILocalLogService {

    int getLogCount(String app);

    void writeLog(String appPackage, in LocalLog log);
}