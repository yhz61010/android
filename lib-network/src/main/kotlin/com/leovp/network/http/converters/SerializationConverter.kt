@file:Suppress("unused")

package com.leovp.network.http.converters

import com.drake.net.convert.NetConverter
import com.drake.net.exception.ConvertException
import com.drake.net.exception.RequestParamsException
import com.drake.net.exception.ServerResponseException
import com.drake.net.request.kType
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Response

/**
 * Author: Michael Leo
 * Date: 2023/9/6 10:32
 */
class SerializationConverter : NetConverter {

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val jsonDecoder = Json {
            // Do not encode `null` fields.
            // During decoding, the absence of a field value as treated as `null`
            // for nullable properties without a default value.
            explicitNulls = false
            // Check the document for details.
            coerceInputValues = false
            encodeDefaults = true
            prettyPrint = false
            ignoreUnknownKeys = true
        }
    }

    override fun <R> onConvert(succeed: Type, response: Response): R? {
        try {
            return NetConverter.onConvert(succeed, response)
        } catch (err: ConvertException) {
            val code = response.code
            when {
                code in 200..299 -> {
                    // multiCatch(
                    //     runBlock = {
                    //         // Business error.
                    //         val errorRes: ApiResponseResult = jsonDecoder.decodeFromString(bodyString)
                    //         throw ApiException(errorRes.code, errorRes.message, e)
                    //     },
                    //     exceptions = arrayOf(
                    //         SerializationException::class,
                    //         IllegalArgumentException::class
                    //     )
                    // )

                    return response.body.string().let { bodyString ->
                        runCatching {
                            val kType = response.request.kType ?: throw ConvertException(
                                response, "Request does not contain KType"
                            )
                            bodyString.parseBody<R?>(kType)
                        }.getOrElse {
                            throw ConvertException(response, cause = err)
                        }
                    }
                }

                code in 400..499 -> throw RequestParamsException(response, code.toString(), err)

                code >= 500 -> throw ServerResponseException(response, code.toString(), err)
                else -> throw ConvertException(response = response, cause = err)
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "MemberVisibilityCanBePrivate")
    fun <R> String.parseBody(succeed: KType): R? =
        jsonDecoder.decodeFromString(Json.serializersModule.serializer(succeed), this) as? R
}
