package com.leovp.demo.basic_components.examples.aidl;
import com.leovp.demo.basic_components.examples.aidl.model.LocalLog;

interface ILocalLogService {

    int getLogCount(String app);

    void writeLog(String appPackage, in LocalLog log);
}