package com.leovp.mvvm.viewmodel

interface BaseAction<State> {
    fun execute(state: State): State
}
