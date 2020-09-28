package com.leovp.androidbase.utils.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.text.Spanned
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BadgeIconType
import androidx.core.app.NotificationCompat.NotificationVisibility
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat

/**
 * Author: Michael Leo
 * Date: 20-5-20 上午9:46
 */
object NotificationUtil {

    /**
     * Return `channelId` that passed in if create notification channel successfully
     * or else `null` will be returned.
     *
     * You must use this around API check like this:
     * ```kotlin
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
     *  // Call createNotificationChannel() method
     * }
     * ```
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun createNotificationChannel(
        ctx: Context,
        channelEnableVibrate: Boolean = false,
        uniqChannelId: String? = null,
        userVisibleChannelName: String,
        userVisibleDescription: String? = null,
        channelLockScreenVisibility: Int = NotificationCompat.VISIBILITY_PUBLIC,
        important: Int = NotificationManager.IMPORTANCE_LOW
    ): String? {
        // NotificationChannels are required for Notifications on O (API 26) and above.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Initializes NotificationChannel.
            val notificationChannel = NotificationChannel(uniqChannelId, userVisibleChannelName, important)
            notificationChannel.lightColor = Color.BLUE
            notificationChannel.description = userVisibleDescription
            notificationChannel.enableVibration(channelEnableVibrate)
            notificationChannel.lockscreenVisibility = channelLockScreenVisibility

            // Adds NotificationChannel to system. Attempting to create an existing notification
            // channel with its original values performs no operation, so it's safe to perform the
            // below sequence.
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
            uniqChannelId
        } else {
            // Returns null for pre-O (26) devices.
            null
        }
    }

    /**
     * ```kotlin
     * // Click Action
     * val notifyIntent = Intent(ctx, MainActivity::class.java)
     * val mainPendingIntent = PendingIntent.getActivity(
     * ctx,
     * SystemClock.elapsedRealtime().toInt(),
     * notifyIntent,
     * PendingIntent.FLAG_UPDATE_CURRENT
     * )
     *
     * // Dismiss Action.
     * val dismissIntent = Intent(Cctx, MessagingIntentService::class.java)
     * dismissIntent.action = MessagingIntentService.ACTION_DISMISS
     * dismissIntent.putExtra(MessagingIntentService.EXTRA_APP, appName)
     *  dismissIntent.putExtra(MessagingIntentService.EXTRA_NOTIFICATION, notificationId)
     * val dismissPendingIntent =
     * PendingIntent.getService(CustomApplication.getInstance(), SystemClock.elapsedRealtime().toInt(), dismissIntent, 0)
     * ```
     */
    fun generateBigTextStyleNotification(
        ctx: Context,
        uniqChannelId: String? = null,
        @DrawableRes smallIcon: Int,
        @DrawableRes largeIcon: Int?,
        contentTitle: String? = null,
        contentText: String,
        summaryText: String? = null,
        outgoing: Boolean = false,
        @BadgeIconType badgeIconType: Int = NotificationCompat.BADGE_ICON_SMALL,
        priority: Int = NotificationCompat.PRIORITY_LOW,
        @NotificationVisibility visibility: Int = NotificationCompat.VISIBILITY_PUBLIC,
        @ColorRes accentColor: Int? = null,
        clickIntent: PendingIntent? = null,
        dismissIntent: PendingIntent? = null
    ): Notification {
        val htmlContextText: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(contentText, HtmlCompat.FROM_HTML_MODE_COMPACT)
        } else {
            Html.fromHtml(contentText)
        }
        val bigTextStyle =
            NotificationCompat.BigTextStyle() // Overrides ContentText in the big form of the template.
                .bigText(htmlContextText) // Overrides ContentTitle in the big form of the template.
                .setBigContentTitle(contentTitle) // Summary line after the detail section in the big form of the template.
                // Note: To improve readability, don't overload the user with info. If Summary Text
                // doesn't add critical information, you should skip it.
                .setSummaryText(summaryText)

        val notificationCompatBuilder: NotificationCompat.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (uniqChannelId == null) {
                    throw IllegalArgumentException("uniqChannelId must not be null")
                }
                NotificationCompat.Builder(ctx, uniqChannelId)
            } else {
                NotificationCompat.Builder(ctx)
            }
        GlobalNotificationBuilder.notificationCompatBuilderInstance = notificationCompatBuilder
        notificationCompatBuilder
            .setStyle(bigTextStyle) // BIG_TEXT_STYLE sets title and content for API 16 (4.1 and after).
            .setContentTitle(contentTitle) // Title for API <16 (4.0 and below) devices.
            .setContentText(htmlContextText) // Content for API <24 (7.0 and below) devices.
            .setSmallIcon(smallIcon)
        largeIcon?.let { notificationCompatBuilder.setLargeIcon(BitmapFactory.decodeResource(ctx.resources, it)) }
        clickIntent?.let { notificationCompatBuilder.setContentIntent(it) }
        notificationCompatBuilder.setDefaults(NotificationCompat.DEFAULT_ALL) // Set primary color (important for Wear 2.0 Notifications).
        accentColor?.let { notificationCompatBuilder.setColor(ContextCompat.getColor(ctx, it)) }
        notificationCompatBuilder
            //                .setGroup(appPkg)
            //                .setGroupSummary(true)
            .setBadgeIconType(badgeIconType)
            .setPriority(priority)
            .setVisibility(visibility)
            .setOngoing(outgoing)
        dismissIntent?.let { notificationCompatBuilder.setDeleteIntent(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationCompatBuilder.setCategory(Notification.CATEGORY_MESSAGE)
        }
//        val notification = notificationCompatBuilder.build()
//        val mNotificationManagerCompat = NotificationManagerCompat.from(ctx)
//        mNotificationManagerCompat.notify(notificationId, notification)
        return notificationCompatBuilder.build()
    }

    fun notify(ctx: Context, notificationId: Int) {
        NotificationManagerCompat.from(ctx)
            .notify(notificationId, GlobalNotificationBuilder.notificationCompatBuilderInstance!!.build())
    }
}