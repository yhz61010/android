package com.leovp.androidbase.utils.ui

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.leovp.kotlin.exts.fail

/**
 * Author: Michael Leo
 * Date: 2021/10/13 14:50
 *
 * Usage:
 * ```kotlin
 * private val myActivityLauncher =
 *      BetterActivityResult.registerForActivityResult(this, ActivityResultContracts.StartActivityForResult()) { result ->
 *          if(result.resultCode == Activity.RESULT_OK) {
 *              val result = result.data?.getStringExtra("extra")
 *          }
 *      }
 * myActivityLauncher.launch(Intent(this, LogActivity::class.java))
 * ```
 */
class BetterActivityResult<I, O> private constructor(
    caller: ActivityResultCaller,
    contract: ActivityResultContract<I, O>,
    private var defaultResult: ((O) -> Unit)? = null
) {
    private var internalResult: ((O) -> Unit)? = null

    private val launcher: ActivityResultLauncher<I> = caller.registerForActivityResult(contract) { re: O ->
        val finalResult = (this.internalResult ?: this.defaultResult) ?: fail("The [Result] can not be null.")
        finalResult(re)
    }

    /**
     * Launch activity, same as [ActivityResultLauncher.launch] except that it allows a callback
     * executed after receiving a result from the target activity.
     *
     * @param result If this parameter is `null`, make sure you have already set the `result` when call [BetterActivityResult.registerForActivityResult].
     * If you set `result` in both [launch] and BetterActivityResult constructor, the `result` in [launch] will be used first.
     */
    @JvmOverloads
    fun launch(input: I, result: ((O) -> Unit)? = null) {
        internalResult = result
        launcher.launch(input)
    }

    companion object {
        /**
         * Register activity result using a [ActivityResultContract] and an in-place activity result callback like
         * the default approach. You can still customise callback using [launch].
         *
         * @param result If this parameter is `null`, make sure you have already set the `result` when call [BetterActivityResult.launch]
         * If you set `result` in both [launch] and BetterActivityResult constructor, the `result` in [launch] will be used first.
         */
        fun <I, O> registerForActivityResult(
            caller: ActivityResultCaller,
            contract: ActivityResultContract<I, O>,
            result: ((O) -> Unit)? = null
        ): BetterActivityResult<I, O> {
            return BetterActivityResult(caller, contract, result)
        }
    }
}
