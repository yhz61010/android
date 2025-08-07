package com.leovp.demo.basiccomponents.examples.koin

import android.os.Bundle
import com.leovp.android.exts.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.databinding.ActivityKoinBinding
import com.leovp.log.LogContext
import com.leovp.log.base.ITAG
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf

class KoinActivity : BaseDemonstrationActivity<ActivityKoinBinding>(R.layout.activity_koin) {
    override fun getTagName(): String = ITAG

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityKoinBinding =
        ActivityKoinBinding.inflate(layoutInflater)

    private val firstPresenter: MySimplePresenter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        toast(firstPresenter.sayHello())

        val wheelFL: Wheel = get { parametersOf("FL") }
        val wheelFR: Wheel = get { parametersOf("FR") }
        val wheelBL: Wheel = get { parametersOf("BL") }
        val wheelBR: Wheel = get { parametersOf("BR") }

        val bmwWheels: List<Wheel> =
            get { parametersOf(arrayListOf(wheelFL, wheelFR, wheelBL, wheelBR)) }
        val bmwEngine: Engine = get { parametersOf("bmw", "fuel") }
        val bmw: Car = get { parametersOf(bmwEngine, bmwWheels) }

        bmw.start()
        bmw.drive()
    }
}

class Car(val engine: Engine, val wheels: List<Wheel>) {
    fun start() {
        engine.start()
    }

    fun stop() {
        engine.stop()
    }

    fun drive() {
        LogContext.log.i(ITAG, "Drive mode")
        for (wheel in wheels) {
            wheel.turn()
        }
    }

    @Suppress("unused")
    fun park() {
        LogContext.log.i(ITAG, "Park mode")
        for (wheel in wheels) wheel.stop()
    }
}

class Engine(val name: String, val type: String) {
    fun start() {
        LogContext.log.i(ITAG, "Engine started")
    }

    fun stop() {
        LogContext.log.i(ITAG, "Engine stopped")
    }
}

class Wheel(val identity: String) {
    fun turn() {
        LogContext.log.i(ITAG, "Wheel $identity has turned.")
    }

    fun stop() {
        LogContext.log.i(ITAG, "Wheel $identity has stopped.")
    }
}

class MySimplePresenter(private val repo: HelloRepository) {
    fun sayHello(): String = "${repo.giveHello()} from $this"
}

interface HelloRepository {
    fun giveHello(): String
}

class HelloRepositoryImpl : HelloRepository {
    override fun giveHello(): String = "Hello Koin"
}
