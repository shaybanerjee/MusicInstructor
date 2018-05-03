package com.example.shayon.MusicInstructor;

public class NoteInfo {
    private String note;
    private long time;

    NoteInfo()
    {
        note = "na";
        time = 0;
    }
    public String getNote()
    {
        return note;
    }
    public long getTime()
    {
        return time;
    }
    public void setNote(String note_Val)
    {
        note = note_Val;
    }
    public void setTime(long time_val)
    {
        time = time_val;
    }
}
