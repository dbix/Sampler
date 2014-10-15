package edu.asu.sampler;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.File;

/**
 * Created by dbixler on 10/10/14.
 */
public class AudioTrackPlayback
{

    private String mAudioFilePath;
    private File mAudioFile;
    private short[] mAudioBuffer;
    private AudioTrack mAudioTrack;

    public AudioTrackPlayback(String audioFilePath)
    {
        mAudioFilePath = audioFilePath;
        mAudioFile = new File(mAudioFilePath);
        mAudioBuffer = new short[(int) (mAudioFile.length() / 2)];
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, mAudioBuffer.length, AudioTrack.MODE_STREAM);
    }

    public void startPlayback()
    {
        mAudioTrack.play();
    }

    public void stopPlayback()
    {
        mAudioTrack.stop();
    }

    public void pausePlayback()
    {
        mAudioTrack.pause();
    }

    public int getPlayState()
    {
        return mAudioTrack.getPlayState();
    }

    public int getState()
    {
        return mAudioTrack.getState();
    }
}
