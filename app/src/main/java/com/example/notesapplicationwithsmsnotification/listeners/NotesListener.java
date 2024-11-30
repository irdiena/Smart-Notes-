package com.example.notesapplicationwithsmsnotification.listeners;

import com.example.notesapplicationwithsmsnotification.entities.Note;

public interface NotesListener {
    void onNoteClicked(Note note, int position);
}
