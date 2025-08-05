package com.leovp.mvvm

/**
 * Author: Michael Leo
 * Date: 2023/9/13 16:03
 */
sealed interface ApiResult<out T> {

    /**
     * Successfully receive network result without error message.
     */
    data class Success<T>(val data: T) : ApiResult<T>

    /**
     * Successfully receive network result with business logic error message.
     */
    data class Error<T>(val code: Int, val message: String?) : ApiResult<T>

    /**
     * The network encounters unexpected exception before getting a response
     * from the network such as IOException, UnKnownHostException and etc.
     */
    data class Exception<T>(val throwable: Throwable) : ApiResult<T>
}
