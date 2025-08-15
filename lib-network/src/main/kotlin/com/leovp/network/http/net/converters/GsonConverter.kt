@file:Suppress("unused")

package com.leovp.network.http.net.converters

import com.drake.net.convert.NetConverter
import com.drake.net.exception.ConvertException
import com.drake.net.exception.RequestParamsException
import com.drake.net.exception.ServerResponseException
import com.google.gson.GsonBuilder
import java.lang.reflect.Type
import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2022/5/13 16:43
 */
class GsonConverter : NetConverter {
    companion object {
        private val gson = GsonBuilder().serializeNulls().create()
    }

    override fun <R> onConvert(succeed: Type, response: Response): R? {
        try {
            return NetConverter.onConvert(succeed, response)
        } catch (err: ConvertException) {
            val code = response.code
            when {
                code in 200..299 -> {
                    return response.body.string().let { bodyString ->
                        runCatching {
                            gson.fromJson<R>(bodyString, succeed)
                        }.getOrElse {
                            throw ConvertException(response, cause = err)
                        }
                    }
                }

                code in 400..499 -> throw RequestParamsException(response, code.toString())
                code >= 500 -> throw ServerResponseException(response, code.toString())
                else -> throw ConvertException(response, cause = err)
            }
        }
    }
}
