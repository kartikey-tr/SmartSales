package com.torpedoes.smartsales.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.torpedoes.smartsales.service.WhatsAppNotificationService

object NotificationPermissionHelper {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val pkgName     = context.packageName
        val flat        = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val names       = flat.split(":").map { ComponentName.unflattenFromString(it) }
        return names.any { it?.packageName == pkgName }
    }

    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}