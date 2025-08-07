package com.leovp.demo.basiccomponents.examples

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.os.Bundle
import android.provider.Settings
import com.leovp.android.exts.toast
import com.leovp.demo.R
import com.leovp.demo.base.BaseDemonstrationActivity
import com.leovp.demo.basiccomponents.examples.log.d
import com.leovp.demo.databinding.ActivityNfcBinding
import com.leovp.log.base.ITAG
import com.leovp.nfc.NfcReader

/**
 * Author: Michael Leo
 * Date: 2025/6/4 13:43
 */
class NfcActivity : BaseDemonstrationActivity<ActivityNfcBinding>(R.layout.activity_nfc) {
    override fun getTagName() = ITAG

    private var nfcAdapter: NfcAdapter? = null
    private val nfcReader = NfcReader()

    override fun getViewBinding(savedInstanceState: Bundle?): ActivityNfcBinding =
        ActivityNfcBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            toast("Does not support NFC", error = true)
            finish()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            toast("Please enable NFC")
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
        intent?.let { nfcReader.handleIntent(intent) }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    @SuppressLint("SetTextI18n")
    override fun onNewIntent(intent: Intent) {
        d(tag) { "=====> onNewIntent <=====" }
        super.onNewIntent(intent)
        nfcReader.handleIntent(intent)
    }

    private fun enableForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        val techList = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(Ndef::class.java.name)
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
    }
}
