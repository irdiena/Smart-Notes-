package com.example.notesapplicationwithsmsnotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.notesapplicationwithsmsnotification.activities.CreateNoteActivity
import com.example.notesapplicationwithsmsnotification.activities.MainActivity
import java.text.SimpleDateFormat
import java.util.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Get note details from the Intent
        val noteTitle = intent.getStringExtra("noteTitle") ?: "Untitled"
        val noteSubtitle = intent.getStringExtra("noteSubtitle") ?: "No subtitle"
        val noteContent = intent.getStringExtra("noteContent") ?: "No content"
        val reminderDateMillis = intent.getLongExtra("reminderDate", 0L)

        // Limit the note content length to 100 characters
        val limitedContent = if (noteContent.length > 100) {
            noteContent.substring(0, 100) + "..."
        } else {
            noteContent
        }

        // Format the reminder date to display it in a human-readable format
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val reminderDate = dateFormat.format(Date(reminderDateMillis))

        // Get NotificationManager system service
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure notification channel exists for Android 8.0 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "ReminderChannel"
            val channelName = "Reminder Notifications"
            val channelDescription = "Notifications for reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val notificationChannel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }

        // Prepare the notification Intent to open MainActivity
        val notificationIntent = Intent(context, MainActivity::class.java) // Change to MainActivity
        notificationIntent.putExtra("noteTitle", noteTitle)
        notificationIntent.putExtra("noteSubtitle", noteSubtitle)
        notificationIntent.putExtra("noteContent", noteContent)

        // Create PendingIntent to launch MainActivity
        val pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        // Build the notification with the PendingIntent
        val notification = NotificationCompat.Builder(context, "ReminderChannel")
            .setContentTitle("Reminder: $noteTitle")
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentIntent(pendingIntent) // Only call setContentIntent once
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Subtitle: $noteSubtitle\n$limitedContent...\n$reminderDate")) // Multiline format
            .build()

        // Notify the user
        notificationManager.notify(0, notification)
    }
}

