package com.example.focusdesk.nfc

import android.app.Activity
import android.nfc.*
import android.nfc.tech.NfcA
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlinx.coroutines.Job

class NfcDeskPresenceManager(
    private val activity: Activity,
    var onTagScanned: ((tagUid: String) -> Unit)? = null,
    var onPhoneLiftedFromDesk: ((sessionId: Long) -> Unit)?,
    var onSessionStarted: ((sessionId: Long) -> Unit)?) {

    val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    private var presenceJob: Job? = null
    private var activeNfcA: NfcA? = null
    private var isMonitoringActive = false
    private var activeSessionId: Long = -1L

    fun setActiveSessionId(sessionId: Long) {
        activeSessionId = sessionId
    }

    fun startScanning() {
        val info = nfcAdapter?.isEnabled
        if(info == true) {
            nfcAdapter.enableReaderMode(activity, NfcAdapter.ReaderCallback { tag -> handleTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, Bundle()
            )
        } else {
            return
        }
    }

    fun stopScanning() {
        isMonitoringActive = false
        activeSessionId = -1L
        presenceJob?.cancel()
        presenceJob = null
        activeNfcA?.close()
        activeNfcA = null
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun handleTagDiscovered(tag: Tag) {
        val uidHex = tag.id.joinToString(":") { "%02X".format(it) }
        onTagScanned?.invoke(uidHex)

        if (activeSessionId < 1) return

        isMonitoringActive = true
        startPresenceCheckLoop(tag, activeSessionId)
    }

    fun startPresenceCheckLoop(tag: Tag, sessionId: Long) {
        val nfcA = NfcA.get(tag)
        activeNfcA = nfcA
        presenceJob?.cancel()
        presenceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isMonitoringActive) {
                    if (!nfcA.isConnected) nfcA.connect()
                    nfcA.transceive(byteArrayOf(0x30.toByte(), 0x00.toByte()))
                    delay(1000)
                }
            } catch (e: IOException) {
                isMonitoringActive = false
                CoroutineScope(Dispatchers.Main).launch { onPhoneLiftedFromDesk?.invoke(sessionId) }
                return@launch
            } finally {
                nfcA.close()
                if (activeNfcA == nfcA) {
                    activeNfcA = null
                }
                presenceJob = null
                isMonitoringActive = false
            }
        }
    }
}