package com.leovp.leoandroidbaseutil.basic_components.examples.koin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.leovp.androidbase.exts.android.toast
import com.leovp.leoandroidbaseutil.R
import com.leovp.leoandroidbaseutil.databinding.ActivityKoinBinding
import com.leovp.log_sdk.LogContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf

class KoinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKoinBinding

    private val firstPresenter: MySimplePresenter by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_koin)
        binding = ActivityKoinBinding.inflate(layoutInflater).apply { setContentView(root) }

        toast(firstPresenter.sayHello())

        val wheelFL: Wheel = get { parametersOf("FL") }
        val wheelFR: Wheel = get { parametersOf("FR") }
        val wheelBL: Wheel = get { parametersOf("BL") }
        val wheelBR: Wheel = get { parametersOf("BR") }

        val bmwWheels: List<Wheel> = get { parametersOf(arrayListOf(wheelFL, wheelFR, wheelBL, wheelBR)) }
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
        LogContext.log.i("Drive mode")
        val wheel0 = wheels[0]
        for (wheel in wheels) {
            wheel.turn()
        }
    }

    fun park() {
        LogContext.log.i("Park mode")
        for (wheel in wheels) wheel.stop()
    }
}

class Engine(val name: String, val type: String) {
    fun start() {
        LogContext.log.i("Engine started")
    }

    fun stop() {
        LogContext.log.i("Engine stopped")
    }
}

class Wheel(val identity: String) {
    fun turn() {
        LogContext.log.i("Wheel $identity has turned.")
    }

    fun stop() {
        LogContext.log.i("Wheel $identity has stopped.")
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