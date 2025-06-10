package com.leovp.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.os.Parcelable
import com.leovp.log.base.LogOutType
import com.leovp.log.base.e
import com.leovp.log.base.i
import com.leovp.log.base.w
import com.leovp.nfc.util.getParcelableExtraOrNull

/**
 * Author: Michael Leo
 * Date: 2025/6/6 10:52
 */
class NfcReader() {
    companion object {
        private const val TAG = "NFCReader"
        val logTypeNfc = LogOutType(hashCode())
    }

    fun handleIntent(intent: Intent) {
        val tag = intent.getParcelableExtraOrNull<Parcelable>(NfcAdapter.EXTRA_TAG) as? Tag ?: return
        i {
            this.tag = TAG
            message = "tag=$tag"
            outputType = logTypeNfc
        }

        when {
            Ndef.get(tag) != null -> readNdef(Ndef.get(tag))
            MifareClassic.get(tag) != null -> readMifareClassic(MifareClassic.get(tag))
            NfcA.get(tag) != null -> readNfcA(NfcA.get(tag))
            NfcB.get(tag) != null -> readNfcB(NfcB.get(tag))
            NfcF.get(tag) != null -> readNfcF(NfcF.get(tag))
            IsoDep.get(tag) != null -> readIsoDep(IsoDep.get(tag))
            else -> w {
                this.tag = TAG
                this.message = "未知标签类型"
                outputType = logTypeNfc
            }
        }
    }

    private fun readNdef(ndef: Ndef) {
        try {
            ndef.connect()
            val message = ndef.ndefMessage
            val payloads = message.records.joinToString("\n") { String(it.payload.drop(3).toByteArray()) }
            i {
                this.tag = TAG
                this.message = "NDEF 内容:\n$payloads"
                outputType = logTypeNfc
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 NDEF 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun readMifareClassic(mfc: MifareClassic) {
        try {
            mfc.connect()
            val auth = mfc.authenticateSectorWithKeyA(1, MifareClassic.KEY_DEFAULT)
            if (auth) {
                val block = mfc.sectorToBlock(1)
                val data = mfc.readBlock(block)
                i {
                    this.tag = TAG
                    message = "MifareClassic 数据: ${data.joinToString(" ") { it.toUByte().toString(16) }}"
                    outputType = logTypeNfc
                }
            } else {
                w {
                    this.tag = TAG
                    message = "MifareClassic 认证失败"
                    outputType = logTypeNfc
                }
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 MifareClassic 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { mfc.close() }
        }
    }

    private fun readNfcA(nfcA: NfcA) {
        try {
            nfcA.connect()
            val uid = nfcA.tag.id.joinToString(" ") { String.format("%02X", it) }
            i {
                this.tag = TAG
                message = "NfcA UID: $uid"
                outputType = logTypeNfc
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 NfcA 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { nfcA.close() }
        }
    }

    private fun readNfcB(nfcB: NfcB) {
        try {
            nfcB.connect()
            i {
                this.tag = TAG
                message = "NfcB tag connected: ${nfcB.tag.id.joinToString(" ") { String.format("%02X", it) }}"
                outputType = logTypeNfc
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 NfcB 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { nfcB.close() }
        }
    }

    private fun readNfcF(nfcF: NfcF) {
        try {
            nfcF.connect()
            i {
                this.tag = TAG
                message = "NfcF tag UID: ${nfcF.tag.id.joinToString(" ") { String.format("%02X", it) }}"
                outputType = logTypeNfc
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 NfcF 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { nfcF.close() }
        }
    }

    private fun readIsoDep(isoDep: IsoDep) {
        try {
            isoDep.connect()
            val hi = isoDep.tag.id.joinToString(" ") { String.format("%02X", it) }
            i {
                this.tag = TAG
                message = "IsoDep UID: $hi"
                outputType = logTypeNfc
            }
        } catch (e: Exception) {
            e {
                this.tag = TAG
                throwable = e
                message = "读取 IsoDep 失败: ${e.message}"
                outputType = logTypeNfc
            }
        } finally {
            runCatching { isoDep.close() }
        }
    }
}
