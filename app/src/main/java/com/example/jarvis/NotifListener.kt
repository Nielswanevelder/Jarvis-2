package com.example.jarvis

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val pkg = sbn.packageName
        val known = MessageStore.APPS[pkg]
        if (known == null && n.category != Notification.CATEGORY_MESSAGE) return
        val e = n.extras ?: return
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = (e.getCharSequence(Notification.EXTRA_TEXT)
            ?: e.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: return
        val app = known ?: pkg.substringAfterLast('.')
        MessageStore.add(applicationContext, Msg(app, title, text, sbn.postTime))
    }
}
