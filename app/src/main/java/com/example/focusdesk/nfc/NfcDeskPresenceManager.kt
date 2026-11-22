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
import kotlin.math.max

class NfcDeskPresenceManager(
    private val activity: Activity,
    var onTagScanned: ((tagUid: String) -> Unit)? = null,
    var onPhoneLiftedFromDesk: ((sessionId: Long) -> Unit)?,
    var onSessionStarted: ((sessionId: Long) -> Unit)?) {

    val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    private var presenceJob: Job? = null
    private var activeNfcA: NfcA? = null
    @Volatile private var lastSeenMillis: Long = 0L
    private var isMonitoringActive = false
    private var activeSessionId: Long = -1L

    private var currentTag: Tag? = null

    fun setActiveSessionId(sessionId: Long) {
        activeSessionId = sessionId
        isMonitoringActive = true
        if (currentTag != null) startPresenceCheckLoop(currentTag!!, sessionId)
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
        presenceJob?.cancel()
        presenceJob = null
        activeNfcA?.close()
        activeNfcA = null
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun handleTagDiscovered(tag: Tag) {
        val uidHex = tag.id.joinToString(":") { "%02X".format(it) }
        currentTag = tag
        if (isMonitoringActive && activeSessionId > 0) {
            val freshNfcA = NfcA.get(tag) ?: return
            try {
                if (!freshNfcA.isConnected) freshNfcA.connect()
                activeNfcA = freshNfcA
                lastSeenMillis = System.currentTimeMillis()
            } catch (_: Exception) {}
        } else {
            onTagScanned?.invoke(uidHex)
        }
    }

    fun startPresenceCheckLoop(tag: Tag, sessionId: Long) {
        val nfcA = NfcA.get(tag) ?: return
        activeNfcA = nfcA
        presenceJob?.cancel()
        presenceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                lastSeenMillis = System.currentTimeMillis()
                val gracePeriodMillis = 2000L
                while (isMonitoringActive) {
                    val nfc = activeNfcA
                    try {
                        if (nfc == null) {
                            throw IOException("Brak aktywnego taga NFC")
                        }
                        if (!nfc.isConnected) {
                            nfc.connect()
                        }
                        nfc.transceive(byteArrayOf(0x30.toByte(), 0x00.toByte()))
                        lastSeenMillis = System.currentTimeMillis()
                        delay(1000)
                    } catch (e: Exception) {
                        val timeSinceLastSeen = System.currentTimeMillis() - lastSeenMillis
                        if (timeSinceLastSeen >= gracePeriodMillis) {
                            isMonitoringActive = false
                            CoroutineScope(Dispatchers.Main).launch {
                                onPhoneLiftedFromDesk?.invoke(sessionId)
                            }
                            break
                        }
                        delay(300)
                    }
                }
            } catch (e: Exception) {
                if (isMonitoringActive) {
                    CoroutineScope(Dispatchers.Main).launch { onPhoneLiftedFromDesk?.invoke(sessionId) }
                    isMonitoringActive = false
                }
                return@launch
            } finally {
                try {
                    activeNfcA?.close()
                    activeNfcA = null
                    presenceJob = null
                    isMonitoringActive = false
                } catch (_: Exception) {}
            }
        }
    }

    fun clearActiveSession() {
        isMonitoringActive = false
        presenceJob?.cancel()
        presenceJob = null
        try {
            activeNfcA?.close()
            activeNfcA = null
        } catch (e: Exception){}
        activeSessionId = -1L
    }
}