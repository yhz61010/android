package com.leovp.demo;

import static org.koin.core.context.DefaultContextExtKt.stopKoin;

import android.app.Application;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import com.leovp.log.LLog;
import com.leovp.log.LogContext;
import com.leovp.pref.LPref;
import com.leovp.pref.PrefContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Author: Michael Leo
 * Date: 2022/2/8 09:32
 */
@RunWith(RobolectricTestRunner.class)
@PrepareForTest(Log.class)
@Config(sdk = {32}, shadows = {ShadowLog.class})
public class Pref4Java {
    private final Application context = ApplicationProvider.getApplicationContext();

    static class NullObject {
    }

    @Before
    public void setUp() {
        stopKoin(); // To remove 'A Koin Application has already been started'
        ShadowLog.stream = System.out;
        LogContext.INSTANCE.setLogImp(new LLog("LEO"));
    }

    @After
    public void tearDown() {
        stopKoin();
    }

    @Test
    public void defaultPref4Java() {
        PrefContext.INSTANCE.setPrefImp(new LPref(context, context.getPackageName()));

        PrefContext.INSTANCE.getPref().put4Java("string", "this is a string", String.class);
        PrefContext.INSTANCE.getPref().put4Java("boolean", true, boolean.class);
        PrefContext.INSTANCE.getPref().put4Java("long", 1234567L, long.class);
        PrefContext.INSTANCE.getPref().put4Java("int", 10, int.class);
        PrefContext.INSTANCE.getPref().put4Java("float", 3.14f, float.class);
        HashMap<String, Integer> intMap = new HashMap<>();
        intMap.put("k_int1", 1);
        intMap.put("k_int2", 2);
        PrefContext.INSTANCE.getPref().put4Java("object_int", intMap, Map.class);
        HashMap<String, Float> floatMap = new HashMap<>();
        floatMap.put("k_float1", 11.1f);
        floatMap.put("k_float2", 22.2f);
        PrefContext.INSTANCE.getPref().put4Java("object_float", floatMap, Map.class);
        PrefContext.INSTANCE.getPref().put4Java("null_str", null, String.class);
        PrefContext.INSTANCE.getPref().put4Java("null_obj", null, NullObject.class);
        PrefContext.INSTANCE.getPref().putSet("set", new HashSet<>() {
            {
                add("s1");
                add("s2");
            }
        });

        Assert.assertEquals("this is a string", PrefContext.INSTANCE.getPref().getString("string", null));
        Assert.assertTrue(PrefContext.INSTANCE.getPref().get4Java("boolean", false, boolean.class));
        Assert.assertEquals(1234567L, PrefContext.INSTANCE.getPref().get4Java("long", 0L, long.class).longValue());
        Assert.assertEquals(10, PrefContext.INSTANCE.getPref().get4Java("int", 0, int.class).intValue());
        Assert.assertEquals(3.14d, PrefContext.INSTANCE.getPref().get4Java("float", 0f, float.class).doubleValue(), 0.001d);
        Map<?, ?> mapIntObj = PrefContext.INSTANCE.getPref().getObject4Java("object_int");
        Assert.assertEquals(new HashMap<>() {
            {
                put("k_int1", 1.0);
                put("k_int2", 2.0);
            }
        }, mapIntObj);
        Map<?, ?> mapFloatObj = PrefContext.INSTANCE.getPref().getObject4Java("object_float");
        Assert.assertEquals(new HashMap<>() {
            {
                put("k_float1", 11.1);
                put("k_float2", 22.2);
            }
        }, mapFloatObj);
        Assert.assertEquals("<null string>", PrefContext.INSTANCE.getPref().getString("null_str", "<null string>"));
        Assert.assertNull(PrefContext.INSTANCE.getPref().getString("null_str", null));
        Assert.assertNull(PrefContext.INSTANCE.getPref().getObject4Java("null_str"));
        Assert.assertEquals("null", PrefContext.INSTANCE.getPref().getString("null_obj", null));
        Assert.assertNull(PrefContext.INSTANCE.getPref().getObject4Java("null_obj"));
        Assert.assertNull(PrefContext.INSTANCE.getPref().getString("pure_null", null));
        Assert.assertEquals(new HashSet<>() {
            {
                add("s1");
                add("s2");
            }
        }, PrefContext.INSTANCE.getPref().getStringSet("set", Collections.emptySet()));
        Assert.assertEquals(new HashSet<>() {
            {
                add("s1");
                add("s2");
            }
        }, PrefContext.INSTANCE.getPref().getStringSet("set", null));
    }
}
