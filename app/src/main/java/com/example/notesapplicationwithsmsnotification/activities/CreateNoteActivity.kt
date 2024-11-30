package com.example.notesapplicationwithsmsnotification.activities

import android.R.attr.type
import android.annotation.SuppressLint
import com.example.notesapplicationwithsmsnotification.ReminderReceiver
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Patterns
import kotlinx.coroutines.withContext
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.database.Cursor
import android.os.Build
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.notesapplicationwithsmsnotification.entities.Note
import com.example.notesapplicationwithsmsnotification.R
import com.example.notesapplicationwithsmsnotification.database.NotesDatabase
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.text.SimpleDateFormat
import android.text.format.DateFormat
import java.util.*

class CreateNoteActivity : AppCompatActivity() {

    private lateinit var inputNoteTitle: EditText
    private lateinit var inputNoteSubtitle: EditText
    private lateinit var inputNoteText: EditText
    private lateinit var textDateTime: TextView
    private lateinit var viewSubtitleIndicator: View
    private lateinit var imageNote: ImageView
    private lateinit var textWebURL: TextView
    private lateinit var layoutWebURL: LinearLayout

    private var selectedNoteColor: String = "#333333"
    private var selectedImagePath: String = ""
    private var alreadyAvailableNote: Note? = null

    private val REQUEST_CODE_STORAGE_PERMISSION = 1
    private val REQUEST_CODE_SELECT_IMAGE = 2

    private lateinit var dialogAddURL: AlertDialog
    private lateinit var dialogDeleteNote: AlertDialog

    private lateinit var reminderText: TextView
    private lateinit var layoutSetReminder: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_note)
        supportActionBar?.hide()

        // Initialize views
        inputNoteTitle = findViewById(R.id.inputNoteTitle)
        inputNoteSubtitle = findViewById(R.id.inputNoteSubtitle)
        inputNoteText = findViewById(R.id.inputNote)
        textDateTime = findViewById(R.id.textDateTime)
        viewSubtitleIndicator = findViewById(R.id.viewSubtitleIndicator)
        imageNote = findViewById(R.id.imageNote)
        textWebURL = findViewById(R.id.textWebURL)
        layoutWebURL = findViewById(R.id.layoutWebURL)


        // Set back button listener
        findViewById<ImageView>(R.id.imageBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle back button press with callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Set current date and time
        val dateTime = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm a", Locale.getDefault()).format(Date())
        textDateTime.text = dateTime

        // Save button listener
        findViewById<ImageView>(R.id.imageSave).setOnClickListener {
            saveNote()
        }

        findViewById<ImageView>(R.id.imageRemoveWebURL).setOnClickListener {
            textWebURL.text = null
            layoutWebURL.visibility = View.GONE
        }

        findViewById<ImageView>(R.id.imageRemoveImage).setOnClickListener {
            imageNote.setImageBitmap(null)
            imageNote.visibility = View.GONE
            findViewById<ImageView>(R.id.imageRemoveImage).visibility = View.GONE
            selectedImagePath = ""
        }

        if (intent.getBooleanExtra("isFromQuickActions", false)) {
            val type = intent.getStringExtra("quickActionType")
            if (type != null) {
                if (type == "image") {
                    val selectedImagePath = intent.getStringExtra("imagePath")
                    if (selectedImagePath != null) {
                        this.selectedImagePath = selectedImagePath
                        imageNote.setImageBitmap(BitmapFactory.decodeFile(selectedImagePath))
                        imageNote.visibility = View.VISIBLE
                        findViewById<View>(R.id.imageRemoveImage).visibility = View.VISIBLE
                    }
                } else if (type == "URL") {
                    textWebURL.setText(intent.getStringExtra("URL"))
                    layoutWebURL.visibility = View.VISIBLE
                }
            }
        }

        initMiscellaneous()
        setSubtitleIndicatorColor()

        // If updating an existing note
        @Suppress("DEPRECATION")
        if (intent.getBooleanExtra("isViewOrUpdate", false)) {
            alreadyAvailableNote = intent.getSerializableExtra("note") as? Note
            setViewOrUpdateNote()
        }

        reminderText = findViewById(R.id.reminderText)
        layoutSetReminder = findViewById(R.id.layoutSetReminder)

        layoutSetReminder.setOnClickListener {
            showDateTimePickerDialog()
        }
    }

    private fun setViewOrUpdateNote() {
        alreadyAvailableNote?.let { note ->
            inputNoteTitle.setText(note.title)
            inputNoteSubtitle.setText(note.subtitle)
            inputNoteText.setText(note.noteText)
            textDateTime.text = note.dateTime

            if (!note.imagePath.isNullOrEmpty()) {
                imageNote.setImageBitmap(BitmapFactory.decodeFile(note.imagePath))
                imageNote.visibility = View.VISIBLE
                findViewById<ImageView>(R.id.imageRemoveImage).visibility = View.VISIBLE
                selectedImagePath = note.imagePath ?: ""
            }

            if (!note.url.isNullOrEmpty()) {
                textWebURL.text = note.url
                layoutWebURL.visibility = View.VISIBLE
            }

            selectedNoteColor = note.color ?: "#333333"
            setSubtitleIndicatorColor()
            updateColorSelection(selectedNoteColor)

            findViewById<View>(R.id.layoutDeleteNote).visibility = View.VISIBLE
            Log.d("CreateNoteActivity", "layoutDeleteNote set to visible for existing note")
        }
    }

    private fun saveNote() {
        if (inputNoteTitle.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Note title can't be empty!", Toast.LENGTH_SHORT).show()
            return
        } else if (inputNoteSubtitle.text.toString().trim().isEmpty() &&
            inputNoteText.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Note can't be empty!", Toast.LENGTH_SHORT).show()
            return
        }

        val note = Note().apply {
            title = inputNoteTitle.text.toString()
            subtitle = inputNoteSubtitle.text.toString()
            noteText = inputNoteText.text.toString()
            dateTime = textDateTime.text.toString()
            color = selectedNoteColor
            setImagePath(selectedImagePath)
            if (layoutWebURL.visibility == View.VISIBLE) {
                setUrl(textWebURL.text.toString())
            }
            alreadyAvailableNote?.let { setId(it.getId()) }
        }

        // Coroutine for database operation
        CoroutineScope(Dispatchers.IO).launch {
            NotesDatabase.getDatabase(applicationContext).noteDao().insertNote(note)
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK, Intent())
                finish()
            }
        }
    }

    private fun initMiscellaneous() {
        val layoutMiscellaneous: LinearLayout = findViewById(R.id.layoutMiscellaneous)
        val bottomSheetBehavior = BottomSheetBehavior.from(layoutMiscellaneous)

        layoutMiscellaneous.findViewById<TextView>(R.id.textMiscellaneous).setOnClickListener {
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // Set color selection listeners
        val colorViews = listOf(
            R.id.viewColor1 to "#333333",
            R.id.viewColor2 to "#FF4842",
            R.id.viewColor3 to "#FDBE3B",
            R.id.viewColor4 to "#008000",
        )

        colorViews.forEachIndexed { index, pair ->
            layoutMiscellaneous.findViewById<View>(pair.first).setOnClickListener {
                selectedNoteColor = pair.second
                setSubtitleIndicatorColor()
                val imageIds = listOf(
                    R.id.imageColor1, R.id.imageColor2, R.id.imageColor3,
                    R.id.imageColor4
                )
                imageIds.forEachIndexed { i, imageId ->
                    layoutMiscellaneous.findViewById<ImageView>(imageId).setImageResource(
                        if (i == index) R.drawable.ic_done else 0
                    )
                }
            }
        }

        // Pre-select color if applicable
        alreadyAvailableNote?.color?.let {
            when (it) {
                "#FF4842" -> layoutMiscellaneous.findViewById<View>(R.id.viewColor2).performClick()
                "#FDBE3B" -> layoutMiscellaneous.findViewById<View>(R.id.viewColor3).performClick()
                "#008000" -> layoutMiscellaneous.findViewById<View>(R.id.viewColor4).performClick()
                else -> layoutMiscellaneous.findViewById<View>(R.id.viewColor1).performClick() // Default selection
            }
        }

        // Additional functionalities
        layoutMiscellaneous.findViewById<View>(R.id.layoutAddImage).setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            if (ContextCompat.checkSelfPermission(
                    applicationContext, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            } else {
                selectImage()
            }
        }

        layoutMiscellaneous.findViewById<View>(R.id.layoutAddUrl).setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            showAddURLDialog()
        }

        //if "alreadyAvailableNote" is not null then it means the user is viewing or updating already added not from database
        //therefore we are displaying delete note option
        // Show delete option if alreadyAvailableNote exists
        if (alreadyAvailableNote != null) {
            layoutMiscellaneous.findViewById<View>(R.id.layoutDeleteNote).visibility = View.VISIBLE
            layoutMiscellaneous.findViewById<View>(R.id.layoutDeleteNote).setOnClickListener {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                showDeleteNoteDialog()
            }
        }
    }

    private fun showDeleteNoteDialog() {
        // Check if dialogDeleteNote has been initialized
        if (!::dialogDeleteNote.isInitialized) {
            val builder = AlertDialog.Builder(this)

            // Inflate the layout and get the reference to the container
            val view = layoutInflater.inflate(
                R.layout.layout_delete_note,
                null // Use null as the container, we will add it directly
            )

            // Set the view for the dialog
            builder.setView(view)

            // Create the dialog
            dialogDeleteNote = builder.create()

            // Set background to transparent to avoid blocking content behind the dialog
            dialogDeleteNote.window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Find the buttons and set their click listeners
            val deleteNoteButton = view.findViewById<TextView>(R.id.textDeleteNote)
            val cancelButton = view.findViewById<TextView>(R.id.textCancel)

            // Delete note action
            deleteNoteButton.setOnClickListener {
                // Start coroutine to delete the note
                CoroutineScope(Dispatchers.IO).launch {
                    alreadyAvailableNote?.let { note ->
                        // Delete the note from the database
                        NotesDatabase.getDatabase(applicationContext).noteDao().deleteNote(note)
                    }
                    // Return the result back to the activity on the main thread
                    withContext(Dispatchers.Main) {
                        val intent = Intent().apply {
                            putExtra("isNoteDeleted", true)
                        }
                        setResult(RESULT_OK, intent)
                        finish() // Close the activity
                    }
                }
            }

            // Cancel button action
            cancelButton.setOnClickListener {
                // Dismiss the dialog when the cancel button is clicked
                dialogDeleteNote.dismiss()
            }

            // Log to confirm that the dialog is being created
            Log.d("CreateNoteActivity", "Dialog created, now showing.")
        }

        // Show the dialog
        dialogDeleteNote.show()
    }



    private fun updateColorSelection(color: String) {
        val colorViewMap = mapOf(
            "#333333" to R.id.imageColor1,
            "#FF4842" to R.id.imageColor2,
            "#FDBE3B" to R.id.imageColor3,
            "#008000" to R.id.imageColor4
        )

        colorViewMap.forEach { (colorCode, imageId) ->
            findViewById<ImageView>(imageId).setImageResource(
                if (color == colorCode) R.drawable.ic_done else 0
            )
        }
    }


    private fun setSubtitleIndicatorColor() {
        (viewSubtitleIndicator.background as GradientDrawable).setColor(Color.parseColor(selectedNoteColor))
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_SELECT_IMAGE)
        }
    }

    private fun getPathFromUri(contentUri: Uri): String? {
        var filePath: String? = null
        contentResolver.query(contentUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex("_data")
                filePath = if (index != -1) cursor.getString(index) else null
            }
        }
        return filePath
    }

    private fun showAddURLDialog() {
        if (::dialogAddURL.isInitialized) {
            dialogAddURL.show()
            return
        }

        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.layout_add_url, findViewById<ViewGroup>(R.id.layoutAddUrlContainer), false)
        builder.setView(view)

        dialogAddURL = builder.create()
        if (dialogAddURL.window != null) {
            dialogAddURL.window!!.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val inputURL = view.findViewById<EditText>(R.id.inputURL)
        inputURL.requestFocus()

        view.findViewById<TextView>(R.id.textAdd).setOnClickListener {
            val url = inputURL.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter URL", Toast.LENGTH_SHORT).show()
            } else if (!Patterns.WEB_URL.matcher(url).matches()) {
                Toast.makeText(this, "Enter valid URL", Toast.LENGTH_SHORT).show()
            } else {
                textWebURL.text = url
                layoutWebURL.visibility = View.VISIBLE
                dialogAddURL.dismiss()
            }
        }

        view.findViewById<TextView>(R.id.textCancel).setOnClickListener {
            dialogAddURL.dismiss()
        }

        dialogAddURL.show()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { selectedImageUri ->
                val imagePath = getPathFromUri(selectedImageUri)
                if (!imagePath.isNullOrEmpty()) {
                    selectedImagePath = imagePath
                    imageNote.setImageBitmap(BitmapFactory.decodeFile(imagePath))
                    imageNote.visibility = View.VISIBLE
                    findViewById<ImageView>(R.id.imageRemoveImage).visibility = View.VISIBLE
                }
            }
        }
    }

    // Handle request permission result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectImage()
        }
    }

    private fun showDateTimePickerDialog() {
        // Get the note details from the EditText fields
        val noteTitle = findViewById<EditText>(R.id.inputNoteTitle).text.toString()
        val noteSubtitle = findViewById<EditText>(R.id.inputNoteSubtitle).text.toString()
        val noteContent = findViewById<EditText>(R.id.inputNote).text.toString()

        val calendar = Calendar.getInstance()

        // Set default time zone to Kuala Lumpur (GMT +8)
        calendar.timeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur")

        // Date Picker Dialog
        val datePicker = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            // Set the date in the calendar
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            // Time Picker Dialog
            val timePicker = TimePickerDialog(this, { _, hourOfDay, minute ->
                // Set the time in the calendar
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)

                // Once date and time are set, show the reminder text
                reminderText.text = "Reminder set for: ${calendar.time}"

                // Schedule the alarm with the note data
                setReminderAlarm(calendar, noteTitle, noteSubtitle, noteContent)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), DateFormat.is24HourFormat(this))

            timePicker.show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

        datePicker.show()
    }

    private fun setReminderAlarm(calendar: Calendar, noteTitle: String, noteSubtitle: String, noteContent: String) {
        // Check if the app has permission to set exact alarms (for API 31 and higher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // If permission is not granted, inform the user or handle accordingly
                Log.e("CreateNoteActivity", "App does not have permission to schedule exact alarms.")
                return
            }
        }

        // Proceed with setting the alarm
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)

        // Pass the note data to the intent
        intent.putExtra("noteTitle", noteTitle)
        intent.putExtra("noteSubtitle", noteSubtitle)
        intent.putExtra("noteContent", noteContent)
        intent.putExtra("reminderDate", calendar.timeInMillis)

        // Specify the mutability flag for PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_MUTABLE if you plan to modify the PendingIntent
        )

        // Set the alarm for the specified time
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

        // Optional: You can also add a Toast or Log to confirm the reminder is set
        Log.d("CreateNoteActivity", "Reminder set for: ${calendar.time}")
    }
}
