package com.leovp.mvvm.viewmodel

/**
 * Author: Michael Leo
 * Date: 2025/8/20 10:18
 */
sealed interface BaseAction<State> {
    interface Simple<State> : BaseAction<State> {
        fun reduce(state: State): State
    }

    interface WithExtra<State, T : Any> : BaseAction<State> {
        fun reduce(state: State, extra: T): State
    }

    interface WithOptional<State, T> : BaseAction<State> {
        fun reduce(state: State, extra: T?): State
    }
}

// interface IBaseAction<State>
//
// interface BaseAction<State> : IBaseAction<State> {
//     fun reduce(state: State): State
// }
//
// interface BaseExtraAction<State, T : Any> : IBaseAction<State> {
//     fun reduce(state: State, obj: T): State
// }
//
// sealed class Action<State> : IBaseAction<State> {
//
//     data class SimpleAction<State>(
//         private val reducer: (State) -> State,
//     ) : Action<State>() {
//         fun reduce(state: State): State = reducer(state)
//     }
//
//     data class ExtraAction<State, T : Any>(
//         private val reducer: (State, T) -> State,
//     ) : Action<State>() {
//         fun reduce(state: State, obj: T): State = reducer(state, obj)
//     }
// }
