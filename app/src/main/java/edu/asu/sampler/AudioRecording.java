package edu.asu.sampler;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Created by dbixler on 10/10/14.
 */
public class AudioRecording
{

    private static final int BUFFER_READ_SIZE = 8192;
    private AudioRecord mAudioRecord;
    private String mAudioFilePath;
    private File mAudioFile;
    private RandomAccessFile mRandomAccessFile;
    private ByteBuffer mAudioBuffer;
    private boolean mRecordableState;

    public AudioRecording(String audioFilePath)
    {
        try
        {
            mAudioFilePath = audioFilePath;
            mAudioFile = new File(mAudioFilePath);
            mRandomAccessFile = new RandomAccessFile(mAudioFile, "rw");
            mAudioBuffer = ByteBuffer.allocate(BUFFER_READ_SIZE);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, 10000);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (NullPointerException e)
        {
            e.printStackTrace();
        }
    }

    public void startRecording()
    {

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, 10000);

        if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED)
        {
            mRecordableState = true;
            mAudioRecord.startRecording();
        }
        else
        {
            mRecordableState = false;
        }

        while (mRecordableState)
        {
            try
            {
                mAudioBuffer.reset();
                int readBytes = mAudioRecord.read(mAudioBuffer, mAudioBuffer.limit());
                mAudioBuffer.flip();
                byte[] data = new byte[mAudioBuffer.limit()];
                mAudioBuffer.get(data);
                mRandomAccessFile.write(data);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                mAudioRecord.stop();
                mRecordableState = false;
            }
            catch (NullPointerException e)
            {
                e.printStackTrace();
                mAudioRecord.stop();
                mRecordableState = false;
            }
        }

        mAudioRecord.stop();
        mAudioRecord.release();

    }

    public void stopRecording()
    {
        mRecordableState = false;
    }

}
