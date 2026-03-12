package com.leovp.mvvm

/**
 * Author: Michael Leo
 * Date: 2025/7/7 10:56
 */
sealed interface BaseAction<State> {
    interface Simple<State> : BaseAction<State> {
        fun reduce(state: State): State
    }

    interface WithExtra<State, T : Any> :
        BaseAction<State> {
        fun reduce(
            state: State,
            extra: T,
        ): State
    }

    interface WithOptional<State, T> :
        BaseAction<State> {
        fun reduce(
            state: State,
            extra: T?,
        ): State
    }
}
