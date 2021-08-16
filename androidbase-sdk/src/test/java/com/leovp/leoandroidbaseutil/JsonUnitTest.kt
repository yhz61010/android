package com.leovp.leoandroidbaseutil

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.leovp.androidbase.exts.kotlin.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.lang.reflect.Type


/**
 * Author: Michael Leo
 * Date: 2021/8/4 11:22
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(Log::class)
class JsonUnitTest {
    @Test
    fun jsonTest() {
        val transientBean = TransientBean(1, "Name1", "This is a cool guy1.")
        Assert.assertEquals("{\"id\":1,\"name\":\"Name1\"}", transientBean.toJsonString())
        val convTransientBean = "{\"id\":2,\"name\":\"Name1\",\"desc\":\"I'm a cool man1!\"}".toObject(TransientBean::class.java)!!
        Assert.assertEquals("id: 2, name: Name1, desc: null", convTransientBean.toString())
        var type: Type = object : TypeToken<TransientBean>() {}.type
        val convTransientTypeBean = "{\"id\":22,\"name\":\"Name11\",\"desc\":\"I'm a cool man11!\"}".toObject<TransientBean>(type)!!
        Assert.assertEquals("id: 22, name: Name11, desc: null", convTransientTypeBean.toString())

        // ======================

        val excludeBean = ExcludeBean(3, "Name2", "This is a cool guy2.")
        Assert.assertEquals("{\"id\":3,\"desc\":\"This is a cool guy2.\"}", excludeBean.toJsonString())
        val convExcludeBean = "{\"id\":4,\"name\":\"Name2\",\"desc\":\"I'm a cool man2!\"}".toObject(ExcludeBean::class.java)!!
        Assert.assertEquals("id: 4, name: null, desc: I'm a cool man2!", convExcludeBean.toString())
        type = object : TypeToken<ExcludeBean>() {}.type
        val convExcludeTypeBean = "{\"id\":44,\"name\":\"Name22\",\"desc\":\"I'm a cool man22!\"}".toObject<ExcludeBean>(type)!!
        Assert.assertEquals("id: 44, name: null, desc: I'm a cool man22!", convExcludeTypeBean.toString())

        // ===========================

        val serializeExcludeBean = SerializeExcludeBean(5, "Name3", "This is a cool guy3.")
        Assert.assertEquals("{\"id\":5,\"name\":\"Name3\"}", serializeExcludeBean.toJsonString())
        val convSerializeExcludeBean = "{\"id\":6,\"name\":\"Name3\",\"desc\":\"I'm a cool man3!\"}".toObject(SerializeExcludeBean::class.java)!!
        Assert.assertEquals("id: 6, name: Name3, desc: I'm a cool man3!", convSerializeExcludeBean.toString())
        type = object : TypeToken<SerializeExcludeBean>() {}.type
        val convSerializeExcludeTypeBean = "{\"id\":66,\"name\":\"Name33\",\"desc\":\"I'm a cool man33!\"}".toObject<SerializeExcludeBean>(type)!!
        Assert.assertEquals("id: 66, name: Name33, desc: I'm a cool man33!", convSerializeExcludeTypeBean.toString())

        // ========

        val serializeExcludeBean2 = SerializeExcludeBean2(50, "Name30", "This is a cool guy30.")
        Assert.assertEquals("{\"id\":50,\"name\":\"Name30\"}", serializeExcludeBean2.toJsonString())
        val convSerializeExcludeBean2 = "{\"id\":66,\"name\":\"Name33\",\"desc\":\"I'm a cool man33!\"}".toObject(SerializeExcludeBean2::class.java)!!
        Assert.assertEquals("id: 66, name: Name33, desc: I'm a cool man33!", convSerializeExcludeBean2.toString())
        type = object : TypeToken<SerializeExcludeBean2>() {}.type
        val convSerializeExcludeTypeBean2 = "{\"id\":666,\"name\":\"Name333\",\"desc\":\"I'm a cool man333!\"}".toObject<SerializeExcludeBean2>(type)!!
        Assert.assertEquals("id: 666, name: Name333, desc: I'm a cool man333!", convSerializeExcludeTypeBean2.toString())


        // ========

        val deserializeExcludeBean = DeserializeExcludeBean2(7, "Name4", "This is a cool guy4.")
        Assert.assertEquals("{\"id\":7,\"name\":\"Name4\",\"desc\":\"This is a cool guy4.\"}", deserializeExcludeBean.toJsonString())
        val convDeserializeExcludeBean = "{\"id\":8,\"name\":\"Name4\",\"desc\":\"I'm a cool man4!\"}".toObject(DeserializeExcludeBean::class.java)!!
        Assert.assertEquals("id: 8, name: Name4, desc: null", convDeserializeExcludeBean.toString())
        type = object : TypeToken<DeserializeExcludeBean>() {}.type
        val convDeserializeExcludeTypeBean = "{\"id\":88,\"name\":\"Name44\",\"desc\":\"I'm a cool man44!\"}".toObject<DeserializeExcludeBean>(type)!!
        Assert.assertEquals("id: 88, name: Name44, desc: null", convDeserializeExcludeTypeBean.toString())

        // ===========================

        val deserializeExcludeBean2 = DeserializeExcludeBean(70, "Name40", "This is a cool guy40.")
        Assert.assertEquals("{\"id\":70,\"name\":\"Name40\",\"desc\":\"This is a cool guy40.\"}", deserializeExcludeBean2.toJsonString())
        val convDeserializeExcludeBean2 = "{\"id\":88,\"name\":\"Name44\",\"desc\":\"I'm a cool man44!\"}".toObject(DeserializeExcludeBean2::class.java)!!
        Assert.assertEquals("id: 88, name: Name44, desc: null", convDeserializeExcludeBean2.toString())
        type = object : TypeToken<DeserializeExcludeBean2>() {}.type
        val convDeserializeExcludeTypeBean2 = "{\"id\":888,\"name\":\"Name444\",\"desc\":\"I'm a cool man444!\"}".toObject<DeserializeExcludeBean2>(type)!!
        Assert.assertEquals("id: 888, name: Name444, desc: null", convDeserializeExcludeTypeBean2.toString())
    }

    data class TransientBean(val id: Int, val name: String, @Transient val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }

    data class ExcludeBean(val id: Int, @Exclude val name: String, val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }

    data class SerializeExcludeBean(val id: Int, val name: String, @Exclude(deserialize = false) val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }

    data class DeserializeExcludeBean(val id: Int, val name: String, @Exclude(serialize = false) val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }

    data class SerializeExcludeBean2(val id: Int, val name: String, @ExcludeSerialize val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }

    data class DeserializeExcludeBean2(val id: Int, val name: String, @ExcludeDeserialize val desc: String) {
        override fun toString(): String = "id: $id, name: $name, desc: $desc"
    }
}