package edu.asu.sampler;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import java.util.ArrayList;

public class Sampler {

  public static final int RECORDING = AudioRecord.RECORDSTATE_RECORDING;
  public static final int STOPPED = AudioRecord.RECORDSTATE_STOPPED;
  public static final int INITIALIZED = AudioRecord.STATE_INITIALIZED;
  public static final int UNINITIALIZED = AudioRecord.STATE_UNINITIALIZED;
  public static final int MICROPHONE = AudioSource.MIC;
  public static final int SAMPLE_RATE = 44100;
  public final int BUFFER_SIZE = 4*AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MONO,
  ENCODING);
  public final int BUFFER_READ_SIZE = BUFFER_SIZE/4;
  public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
  public static final int CHANNEL_MONO = AudioFormat.CHANNEL_IN_MONO;

  private static byte[] buffer;
  private boolean recordableState;
  private Thread record;
  private ArrayList<Byte> track;
  private AudioRecord audioRecord;

  public Sampler() {
    track = new ArrayList<Byte>();
    recordableState = false;
    buffer = new byte[BUFFER_SIZE];
  }

  public byte[] getBuffer() {
    return buffer;
  }

  public void record() {
    while (!recordableState) {
      audioRecord = new AudioRecord(MICROPHONE, SAMPLE_RATE,
          CHANNEL_MONO, ENCODING, BUFFER_SIZE);
      if (audioRecord.getState() == INITIALIZED) {
        recordableState = true;
        audioRecord.startRecording();
      } else {
        recordableState = false;
      }
    }
    record = new Thread() {
      @Override
      public void run() {
        while (recordableState) {
          try {
            int readBytes = audioRecord.read(buffer, 0, BUFFER_READ_SIZE);
          } catch (Exception e) {
            recordableState = false;
          }
        }
      }
    };
    record.start();
  }


  public void stop() {
    recordableState = false;

    audioRecord.stop();
    audioRecord.release();
  }

  public boolean isRecording() {
    if (audioRecord != null && audioRecord.getRecordingState() == RECORDING)
      return true;
    else
      return false;
  }

  public int getRecordingState() {
    return audioRecord.getRecordingState();
  }

  public int getState() {
    return audioRecord.getState();
  }

}