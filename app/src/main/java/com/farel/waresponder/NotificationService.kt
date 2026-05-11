package com.farel.waresponder

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File

class NotificationService : NotificationListenerService() {

    private val logFile by lazy {
        File(getExternalFilesDir(null), "waresponder.log")
    }

    private fun log(msg: String) {
        try {
            val line = "${System.currentTimeMillis()} $msg"
            logFile.appendText("$line\n")
            LocalSocketApi.sendLog(line)
            FloatingWindowService.logCallback?.invoke(msg)
        } catch (_: Exception) {}
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        log("✅ Notification Listener CONNECTED")
    }

    // ================= FILTER =================

    private fun isGroupSummary(sbn: StatusBarNotification): Boolean =
        sbn.notification.extras.getBoolean("android.isGroupSummary", false)

    private fun isSystemWhatsappNotification(title: String): Boolean =
        title == "WhatsApp"

    private fun isFromSelf(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras

        if (extras.getBoolean("android.isOutgoing", false)) return true

        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        if (text.startsWith("You:", true) || text.startsWith("Anda:", true)) return true

        val selfName = extras.getString("android.selfDisplayName")
        val title = extras.getString("android.title")
        return !selfName.isNullOrEmpty() && selfName == title
    }

    // ================= MESSAGE EXTRACT =================

    private fun extractLastMessage(extras: Bundle): String? {
        val lines = extras.getCharSequenceArray("android.textLines")
        if (!lines.isNullOrEmpty()) {
            return lines.last().toString() // 🔥 PENTING
        }

        val text = extras.getCharSequence("android.text")?.toString()
        if (text != null && text.matches(Regex("\\d+ pesan baru", RegexOption.IGNORE_CASE))) {
            return null // skip summary
        }

        return text ?: extras.getCharSequence("android.bigText")?.toString()
    }

    // ================= MAIN =================

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        if (sbn.packageName != "com.whatsapp" &&
            sbn.packageName != "com.whatsapp.w4b") return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: return

        if (isGroupSummary(sbn)) return
        if (isSystemWhatsappNotification(title)) return
        if (isFromSelf(sbn)) return

        val text = extractLastMessage(extras) ?: return

        log("📩 From: $title")
        log("💬 Msg: $text")

        // ================= REPLY ACTION =================

        val replyAction = findReplyAction(sbn)
        if (replyAction == null) {
            log("❌ Reply action not found")
            return
        }

        askBotAndReply(title, text, replyAction)
    }

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? {
        sbn.notification.actions?.forEach {
            if (it.remoteInputs != null) return it
        }

        val wearable = Notification.WearableExtender(sbn.notification)
        wearable.actions.forEach {
            if (it.remoteInputs != null) return it
        }

        return null
    }

    // ================= BOT =================

    private fun askBotAndReply(
        sender: String,
        message: String,
        action: Notification.Action
    ) {
        Thread {
            try {
                val json = JSONObject().apply {
                    put("sender", sender)
                    put("message", message)
                }.toString()

                val reply = LocalSocketApi.sendMessage(json)
                if (reply.isNullOrEmpty()) return@Thread

                sendReplyToWhatsapp(reply, action)

            } catch (e: Exception) {
                log("❌ Bot error: ${e.message}")
            }
        }.start()
    }

    private fun sendReplyToWhatsapp(
        replyText: String,
        action: Notification.Action
    ) {
        try {
            val bundle = Bundle()
            action.remoteInputs?.forEach {
                bundle.putCharSequence(it.resultKey, replyText)
            }

            val intent = Intent()
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            action.actionIntent.send(this, 0, intent)

            log("📤 Reply sent")

        } catch (e: Exception) {
            log("❌ Reply failed: ${e.message}")
        }
    }
}
