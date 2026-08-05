package com.pulsenet.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.pulsenet.app.MainActivity;
import com.pulsenet.app.R;

/**
 * مسؤول عن كل ما يخص الإشعارات: إنشاء القنوات، وعرض إشعار وقت وصول معلومة جديدة،
 * وإشعار "جهاز دخل النطاق"، والإشعار الثابت (Foreground) يلي بيقول إنه PulseNet شغال بالخلفية.
 */
public class NotificationHelper {

    // قناة الإشعار الثابت (Foreground Service) - هادئة وبدون صوت
    public static final String CHANNEL_SERVICE = "pulsenet_service_channel";
    // قناة إشعارات المعلومات الجديدة - فيها صوت وتنبيه
    public static final String CHANNEL_NEW_ITEMS = "pulsenet_new_items_channel";

    public static final int SERVICE_NOTIFICATION_ID = 1;
    private static int newItemNotificationId = 100;

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return; // القنوات لازمة من أندرويد 8 فقط

        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_SERVICE, "خدمة PulseNet بالخلفية", NotificationManager.IMPORTANCE_LOW);
        serviceChannel.setDescription("يبقي بث واكتشاف الأجهزة شغال حتى لو التطبيق مقفول");

        NotificationChannel itemsChannel = new NotificationChannel(
                CHANNEL_NEW_ITEMS, "معلومات جديدة", NotificationManager.IMPORTANCE_HIGH);
        itemsChannel.setDescription("تنبيه عند وصول معلومة جديدة، أو دخول جهاز قريب لنطاق الاتصال");

        notificationManager.createNotificationChannel(serviceChannel);
        notificationManager.createNotificationChannel(itemsChannel);
    }

    /** يبني الإشعار الثابت يلي لازم تشغّله أي Foreground Service خلال أول 5 ثواني */
    public NotificationCompat.Builder buildServiceNotification() {
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setContentTitle("PulseNet شغال")
                .setContentText("يبث ويكتشف الأجهزة القريبة بالخلفية")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    /** يعرض إشعار فعلي وقت ما توصل معلومة أو معلومات جديدة من جهاز قريب */
    public void showNewItemsNotification(int count) {
        SharedPreferencesHelper();
        if (!isNotificationsEnabled()) return; // المستخدم طفّى الإشعارات - ما منزعجه

        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = count == 1
                ? "وصلتك معلومة جديدة من جهاز قريب"
                : "وصلتك " + count + " معلومات جديدة من أجهزة قريبة";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_NEW_ITEMS)
                .setContentTitle("PulseNet")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(newItemNotificationId++, builder.build());
    }

    /** يعرض إشعار وقت ما جهاز PulseNet جديد يدخل نطاق الاتصال */
    public void showDeviceInRangeNotification(String deviceName) {
        if (!isNotificationsEnabled()) return;

        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_NEW_ITEMS)
                .setContentTitle("جهاز قريب")
                .setContentText(deviceName + " دخل نطاق الاتصال")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(newItemNotificationId++, builder.build());
    }

    /** يتحقق هل المستخدم مفعّل "الإشعارات المحلية" من شاشة الإعدادات */
    private boolean isNotificationsEnabled() {
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("pulsenet_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("notifications_enabled", true);
    }

    // دالة فاضية - بقيت من مسودة سابقة، بلا أي استخدام فعلي
    private void SharedPreferencesHelper() {
    }
}
