package com.example.shayon.MusicInstructor;

import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.shayon.MusicInstructor.tarsos.PitchDetectionResult;
import com.example.shayon.MusicInstructor.tarsos.Yin;
import com.example.shayon.MusicInstructor.utils.AudioUtils;
import com.example.shayon.MusicInstructor.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tuner {
    private static final String TAG = Tuner.class.getSimpleName();
    private int sampleRate;
    private int bufferSize;
    private volatile int readSize;
    private volatile int amountRead;
    private volatile float[] buffer;
    private volatile short[] intermediaryBuffer;
    private AudioRecord audioRecord;
    private volatile Yin yin;
    private volatile Note currentNote;
    private volatile Note lastNote;
    private volatile PitchDetectionResult result;
    private volatile boolean isRecording;
    private volatile Handler handler;
    boolean first_run = true;
    private Thread thread;
    private Context mContext;
    private List<MusicItem> noteList = TunerActivity.wListMusic;
    public int currIndex = 0;
    int count = 0;
    private int currScore;
    private int counter = 0;
    long startTime = 0;
    private String[] lastNotes = new String[6];
    final ArrayList<failedNotes> wFailedNotes = new ArrayList<failedNotes>();
    Boolean is_counted = false;


    //provide the tuner view implementing the TunerUpdate to the constructor
    public Tuner(Context context){
        mContext = context;
        init();

    }

    public void init(){
        this.sampleRate = AudioUtils.getSampleRate();
        this.bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
        this.readSize = bufferSize / 4;
        this.buffer = new float[readSize];
        this.intermediaryBuffer = new short[readSize];
        this.isRecording = false;
        this.audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        this.yin = new Yin(sampleRate, readSize);
        this.currentNote = new Note(Note.DEFAULT_FREQUENCY);
        this.lastNote = new Note(Note.DEFAULT_FREQUENCY);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void start(){
        if(audioRecord != null) {
            isRecording = true;
            audioRecord.startRecording();
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //Runs off the UI thread
                    findAndPrintNote();
                }
            }, "Tuner Thread");
            thread.start();
        }
    }

    // this algorithm was too dependent on the user having perfect tempo
    // decided to not use this algorithm
    private void startRecordingPlayer()
    {
        while(isRecording){
            final int noteListLength = noteList.size();
            amountRead = audioRecord.read(intermediaryBuffer, 0, readSize);
            buffer = shortArrayToFloatArray(intermediaryBuffer);
            result = yin.getPitch(buffer);
            currentNote.changeTo(result.getPitch());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (currentNote.getFrequency() != Note.UNKNOWN_FREQUENCY) {
                        LinearLayout backgroundView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.background);
                        LinearLayout tunerView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.tuner_view);
                        TextView txtView = (TextView)
                                ((Activity) mContext).findViewById(R.id.note_text);
                        String currNote = currentNote.getNote();
                        ++counter;
                        txtView.setText(currNote);

                        if (currIndex == noteListLength) {
                            return;
                        }
                        System.out.println("SIZE" + noteList.size());
                        System.out.println("Obj" + noteList.get(0).note);
                        if (isSameNote(noteList.get(currIndex).note, currNote)) {
                            tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                            backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    LinearLayout backgroundView = (LinearLayout)
                                            ((Activity) mContext).findViewById(R.id.background);
                                    LinearLayout tunerView = (LinearLayout)
                                            ((Activity) mContext).findViewById(R.id.tuner_view);
                                    tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                    backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                }
                            }, 2000);
                            assessment();
                            // place a return here
                            isRecording = false;
                            return;

                        }
                    }
                }
            });
        }
    }

    // this algorithm was too dependent on the user having
    // perfect tempo. Decided not to use this algorithm.
    private void assessment()
    {
        LinearLayout backgroundView = (LinearLayout)
                ((Activity) mContext).findViewById(R.id.background);
        LinearLayout tunerView = (LinearLayout)
                ((Activity) mContext).findViewById(R.id.tuner_view);
        TextView txtView = (TextView)
                ((Activity) mContext).findViewById(R.id.note_text);
        Map<NoteInfo, String> errorList = new HashMap<NoteInfo, String>();
        for (int i = 1; i < noteList.size(); ++i)
        {
            ArrayList<NoteInfo> noteInfoList = new ArrayList<NoteInfo>();
            MusicItem music_item = noteList.get(i);
            // time for each beat
            double duration_to_play = 60.0 / 128.0;
            long current_time = System.currentTimeMillis();
            long end_time = current_time + (int)(duration_to_play * 1000);
            Boolean first_run = true;
            long start_time = 0;
            while (System.currentTimeMillis() < end_time) {
                amountRead = audioRecord.read(intermediaryBuffer, 0, readSize);
                buffer = shortArrayToFloatArray(intermediaryBuffer);
                result = yin.getPitch(buffer);

                lastNote = currentNote;
                if (currentNote == null)
                {
                    currentNote.changeTo(result.getPitch());
                    lastNote = currentNote;
                }
                else
                {
                    currentNote.changeTo(result.getPitch());
                }

                if (currentNote.getFrequency() != Note.UNKNOWN_FREQUENCY)
                {
                    txtView.setText(currentNote.getNote());
                    if (first_run)
                    {
                        start_time = System.currentTimeMillis();
                        first_run = false;
                    }
                    else if (!isSameNote(currentNote.getNote(), lastNote.getNote()))
                    {
                        long endTime = System.currentTimeMillis();
                        long duration_time = endTime - start_time;

                        NoteInfo note_info = new NoteInfo();
                        note_info.setNote(lastNote.getNote());
                        note_info.setTime(duration_time);
                        noteInfoList.add(note_info);
                        System.out.println("NOTE" + note_info.getNote());
                        System.out.println("TIME" + note_info.getTime());
                        first_run = true;
                    }
                }
            }
            NoteInfo note_info = new NoteInfo();
            note_info.setNote(lastNote.getNote());
            note_info.setTime(System.currentTimeMillis() - start_time);
            noteInfoList.add(note_info);
            System.out.println("NOTE" + note_info.getNote());
            System.out.println("TIME" + note_info.getTime());

            NoteInfo largest_Note = new NoteInfo();
            for (NoteInfo nifo : noteInfoList)
            {
                if (nifo.getTime() > largest_Note.getTime())
                {
                    largest_Note = nifo;
                }
            }
            String currNote = largest_Note.getNote();
            txtView.setText("L:" + currNote);
            // show note
            if (currNote != "" && isSameNote(noteList.get(i).note, currNote))
            {
                // make background green
                ++currScore;

            }
            else {
                // make background red update error
                errorList.put(largest_Note, noteList.get(i).note);

            }
        }
        System.out.println("ErrorList:" + errorList.size());
    }


    // this algorithm was too dependent on time and expected the user to
    // play the song at perfect tempo
    // Decided not to use this algorithm.

    private void startObservingStudent()
    {
        while(isRecording){
            final int noteListLength = noteList.size();
            amountRead = audioRecord.read(intermediaryBuffer, 0, readSize);
            buffer = shortArrayToFloatArray(intermediaryBuffer);
            result = yin.getPitch(buffer);

            currentNote.changeTo(result.getPitch());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (currentNote.getFrequency() != Note.UNKNOWN_FREQUENCY) {
                        LinearLayout backgroundView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.background);
                        LinearLayout tunerView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.tuner_view);
                        TextView txtView = (TextView)
                                ((Activity) mContext).findViewById(R.id.note_text);
                        String currNote = currentNote.getNote();
                        ++counter;
                        txtView.setText(currNote);
                        if (currIndex == noteListLength) {
                            return;
                        }
                        else if (isSameNote(noteList.get(currIndex).note, currNote)) {
                            ++currIndex;
                            tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                            backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    LinearLayout backgroundView = (LinearLayout)
                                            ((Activity) mContext).findViewById(R.id.background);
                                    LinearLayout tunerView = (LinearLayout)
                                            ((Activity) mContext).findViewById(R.id.tuner_view);
                                    tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                    backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                }
                            }, 2000);

                        } else {

                        }

                    }
                }
            });
        }

    }

    private void findAndPrintNote()
    {

        while(isRecording){
            final int noteListLength = noteList.size();
            amountRead = audioRecord.read(intermediaryBuffer, 0, readSize);
            buffer = shortArrayToFloatArray(intermediaryBuffer);
            result = yin.getPitch(buffer);

           if (first_run)
            {
                startTime = System.currentTimeMillis();
                first_run = false;
            }
            currentNote.changeTo(result.getPitch());



            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (currentNote.getFrequency() != Note.UNKNOWN_FREQUENCY) {
                        LinearLayout backgroundView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.background);
                        LinearLayout tunerView = (LinearLayout)
                                ((Activity) mContext).findViewById(R.id.tuner_view);
                        TextView txtView = (TextView)
                                ((Activity) mContext).findViewById(R.id.note_text);
                        String currNote = currentNote.getNote();
                        ++counter;
                        txtView.setText(currNote);
                            if (currIndex == noteListLength) {
                                return;
                            }

                            if (System.currentTimeMillis() - startTime < 500) {
                                // compare the notes to array, increment current index
                                System.out.println(currentNote.getNote());
                                System.out.println(noteList.get(currIndex).note);
                                System.out.println(currIndex);
                                try {
                                    if (isSameNote(noteList.get(currIndex).note, currentNote.getNote())) {
                                        tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                                        backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.green));
                                        final Handler handler = new Handler();
                                        handler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                LinearLayout backgroundView = (LinearLayout)
                                                        ((Activity) mContext).findViewById(R.id.background);
                                                LinearLayout tunerView = (LinearLayout)
                                                        ((Activity) mContext).findViewById(R.id.tuner_view);

                                                tunerView.setBackgroundResource(R.drawable.main_background);
                                                backgroundView.setBackgroundResource(R.drawable.main_background);
                                                //tunerView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                                //backgroundView.setBackgroundColor(ContextCompat.getColor(mContext, R.color.white));
                                            }
                                        }, 2000);
                                       is_counted = true;


                                    }
                                } catch (Exception e) {
                                    // ignore an invlid value was fed to currentNote

                                }
                                if (currIndex == 0) {
                                    first_run = true;
                                }
                            }
                            else if (!is_counted)
                            {
                                ++currIndex;
                                failedNotes fn = new failedNotes();
                                fn.note_observed = currentNote.getNote();
                                fn.note_expected = noteList.get(currIndex - 1).note;
                                fn.song_note = currIndex;
                                wFailedNotes.add(fn);
                                first_run = true;
                                is_counted = false;
                            }
                            else
                            {
                                ++currIndex;
                                ++count;
                                first_run = true;
                            }
                        }
                    }
            });
        }
    }

    private boolean isSameNote(String note, String currNote) {

        if ((note != "" || currNote != "" || currNote != "na") && note.substring(0, 1).equals(currNote.substring(0, 1))) {
            return true;
        }
        return false;

    }

    public void updateScore() {
        TextView txtView = (TextView)
                ((Activity) mContext).findViewById(R.id.note_text);

        double result = ((double)count / 26) * 100;
        if (result == 100)
        {
            String s = String.format("%.2f", result);
            txtView.setText("Perfect, you got " + s + "% correct!");
            txtView.setTextSize(20);
        }
        else if (result > 70)
        {
            String s = String.format("%.2f", result);
            txtView.setText("Excellent, you got " + s + "% correct!");
            txtView.append("\n");
            txtView.append("Incorrect note analysis: \n");
            if (wFailedNotes.size() >  0) {
                for (int i = 0; i < wFailedNotes.size(); ++i) {
                    txtView.append("Note #" + wFailedNotes.get(i).song_note + " Played: " +
                            wFailedNotes.get(i).note_observed + ", Expected: "
                            + wFailedNotes.get(i).note_expected + "\n");
                }
            }
            txtView.setTextSize(20);
        }
        else if (result > 50)
        {
            String s = String.format("%.2f", result);
            txtView.setText("Good, you got " + s + "% correct!");
            txtView.append("\n");
            if (wFailedNotes.size() >  0) {
                txtView.append("Incorrect note analysis: \n");
                for (int i = 0; i < wFailedNotes.size(); ++i) {
                    txtView.append("Note #" + wFailedNotes.get(i).song_note + " Played: " +
                            wFailedNotes.get(i).note_observed + ", Expected: "
                            + wFailedNotes.get(i).note_expected + "\n");
                }
            }
            txtView.setTextSize(20);
        }
        else
        {
            String s = String.format("%.2f", result);
            txtView.setText("Keep trying, you got " + s + "% correct!");
            txtView.append("\n");
            if (wFailedNotes.size() >  0) {
                txtView.append("Incorrect note analysis: \n");
                for (int i = 0; i < wFailedNotes.size(); ++i) {
                    txtView.append("Note #" + wFailedNotes.get(i).song_note + " Played: " +
                            wFailedNotes.get(i).note_observed + ", Expected: "
                            + wFailedNotes.get(i).note_expected + "\n");
                }
            }
            txtView.setTextSize(20);
        }



    }

    private float[] shortArrayToFloatArray(short[] array){
        float[] fArray = new float[array.length];
        for(int i = 0; i < array.length; i++){
            fArray[i] = (float) array[i];
        }
        return fArray;
    }

    public void stop(){
        isRecording = false;
        if(audioRecord != null) {
            audioRecord.stop();
        }
    }

    public void release(){
        isRecording = false;
        if(audioRecord != null) {
            audioRecord.release();
        }
    }

    public boolean isInitialized(){
        if(audioRecord != null){
            return true;
        }
        return false;
    }

}