package edu.asu.sampler;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.MetadataChangeSet;


public class MainActivity extends Activity implements ConnectionCallbacks, OnConnectionFailedListener {

  /* The sampling rate for the audio recorder. */
  private static final int SAMPLING_RATE = 44100;
  private static final int MONO = AudioFormat.CHANNEL_IN_MONO;
  private static final int PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;
  private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
  private static final int REQUEST_CODE_CREATOR = 2;
  private static final int REQUEST_CODE_RESOLUTION = 3;

  private GoogleApiClient mGoogleApiClient;

  /* Tag for Log.d's */
  private final String LOG_TAG = getClass().getName();
  /* Data */
  ArrayList<Short> mTrack;
  /* Audio visualization views */
  private WaveformView mWaveformView;
  private TextView mDecibelView;
  private String mDecibelFormat;
  private ProgressBar mProgressBar;
  private VerticalProgressBar mDecibelProgressBar;
  /* Buttons and corresponding media classes */
  private Button mStopButton;
  private Button mResetButton;
  private Button mPlayButton;
  private Button mRecordButton;
  private boolean mCollectingData;
  /* Media attributes */
  private RecordingThread mRecordingThread;
  private MediaRecorder mRecorder = null;
  private MediaPlayer mPlayer = null;
  private int mBufferSize;
  private short[] mAudioBuffer;
  private String mFilePath;
  private long mFreeMemory;
  private long mTotalMemory;
  private long mUsedMemory;
  private int mPercentUsedMemory;
  private File mTempFile;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
    mWaveformView = (WaveformView) findViewById(R.id.waveform_view);
    mDecibelView = (TextView) findViewById(R.id.decibel_view);
    mDecibelProgressBar = (VerticalProgressBar) findViewById(R.id.decibelProgressBar);
    mRecordButton = (Button) findViewById(R.id.btnRecord);
    mResetButton = (Button) findViewById(R.id.btnReset);
    mPlayButton = (Button) findViewById(R.id.btnPlay);

    mProgressBar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
    mDecibelProgressBar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);

    mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
    mFilePath += "/Sampler/tmp.3gp";
    mTempFile = new File(mFilePath);
    mTempFile.mkdirs();
    System.out.println(mTempFile.getAbsolutePath());
    mBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, MONO, PCM_16BIT);
    mAudioBuffer = new short[mBufferSize / 2];
    mDecibelFormat = getResources().getString(R.string.decibel_format);

    mRecordButton.setOnClickListener(new View.OnClickListener() {
      boolean mStartRecording = true;

      @Override
      public void onClick(View view) {
        onRecord(mStartRecording);
        if (mStartRecording) {
          mRecordButton.setText("Stop");
          Toast.makeText(getApplicationContext(), "Recording", Toast.LENGTH_SHORT).show();
        } else {
          mRecordButton.setText("Record");
        }
        mCollectingData = mStartRecording;
        mStartRecording = !mStartRecording;
      }
    });

    mPlayButton.setOnClickListener(new View.OnClickListener() {
      boolean mStartPlaying = true;

      @Override
      public void onClick(View view) {
        onPlay(mStartPlaying);
        if (mStartPlaying) {
          mPlayButton.setText("Stop");
        } else {
          mPlayButton.setText("Play");
        }
        mStartPlaying = !mStartPlaying;
      }
    });

    mResetButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
      }
    });


    // Compute the minimum required audio buffer size and allocate the buffer.

  }

  @Override
  public void onConnectionFailed(ConnectionResult result) {
// Called whenever the API client fails to connect.
    Log.i(LOG_TAG, "GoogleApiClient connection failed: " + result.toString());
    if (!result.hasResolution()) {
// show the localized error dialog.
      GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
      return;
    }
// The failure has a resolution. Resolve it.
// Called typically when the app is not yet authorized, and an
// authorization
// dialog is displayed to the user.
    try {
      result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
    } catch (SendIntentException e) {
      Log.e(LOG_TAG, "Exception while starting resolution activity", e);
    }
  }
  
  @Override
  public void onConnected(Bundle connectionHint) {
    Log.i(LOG_TAG, "API client connected.");
    saveFileToDrive();
  }

  @Override
  public void onConnectionSuspended(int cause) {
    Log.i(LOG_TAG, "GoogleApiClient connection suspended");
  }

  private void saveFileToDrive() {
// Start by creating a new contents, and setting a callback.
    Log.i(LOG_TAG, "Creating new contents.");
    mTempFile = new File(mFilePath);
    ByteArrayInputStream bais = new ByteArrayInputStream()
    Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveContentsResult>() {
      @Override
      public void onResult(DriveContentsResult result) {
        if (!result.getStatus().isSuccess()) {
          Log.i(LOG_TAG, "Failed to create new contents.");
          return;
        }
        Log.i(LOG_TAG, "New contents created.");
        // Get an output stream for the contents.
        OutputStream outputStream = result.getDriveContents().getOutputStream();
// Write the bitmap data from it.
        ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream);
        try {
          outputStream.write(bitmapStream.toByteArray());
        } catch (IOException e1) {
          Log.i(LOG_TAG, "Unable to write file contents.");
        }
// Create the initial metadata - MIME type and title.
// Note that the user will be able to change the title later.
        MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
            .setMimeType("image/jpeg").setTitle("Android Photo.png").build();
// Create an intent for the file chooser, and start it.
        IntentSender intentSender = Drive.DriveApi
            .newCreateFileActivityBuilder()
            .setInitialMetadata(metadataChangeSet)
            .setInitialDriveContents(result.getDriveContents())
            .build(mGoogleApiClient);
        try {
          startIntentSenderForResult(
              intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
        } catch (SendIntentException e) {
          Log.i(LOG_TAG, "Failed to launch file chooser.");
        }
      }
    });
  }

  private void onRecord(boolean start) {
    if (start) {
      startRecording();
    } else {
      stopRecording();
    }
  }

  private void startRecording() {
    mTrack = new ArrayList<Short>();
    mCollectingData = true;
  }

  private void stopRecording() {
    mCollectingData = false;
  }

  private void onPlay(boolean start) {
    if (start) {
      startPlaying();
    } else {
      stopPlaying();
    }
  }

  private void startPlaying() {
    mPlayer = new MediaPlayer();
    try {
      mPlayer.setDataSource(mFilePath);
      mPlayer.prepare();
      mPlayer.start();
    } catch (IOException e) {
      Log.e(LOG_TAG, "prepare() failed");
    }
  }

  private void stopPlaying() {
    mPlayer.release();
    mPlayer = null;
  }

  @Override
  protected void onResume() {
    super.onResume();

    mRecordingThread = new RecordingThread();
    mRecordingThread.start();
  }

  @Override
  protected void onPause() {
    super.onPause();

    if (mRecordingThread != null) {
      mRecordingThread.stopRunning();
      try {
        Thread.sleep(1000,0);
        mRecordingThread = null;
      } catch (InterruptedException e) {

      } catch (NullPointerException e) {

      }
    }
  }


  private class PlayingThread extends Thread {

    private boolean mShouldContinue = true;

    @Override
    public void run() {
      int bufferSize = AudioTrack.getMinBufferSize(SAMPLING_RATE, MONO, PCM_16BIT);
      short[] buffer = new short[bufferSize / 4];
      try {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(mTempFile)));
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLING_RATE, MONO, PCM_16BIT, bufferSize,
            AudioTrack.MODE_STREAM);
        audioTrack.play();
        while (mShouldContinue && dis.available() > 0) {
          int i = 0;
          while (dis.available() > 0 && i < buffer.length) {
            buffer[i] = dis.readShort();
            i++;
          }
          audioTrack.write(buffer, 0, buffer.length);
        }
        dis.close();
      } catch (Throwable t) {
        Log.e("AudioTrack", "Playback Failed");
      }
    }

    public synchronized boolean shouldContinue() {
      return mShouldContinue;
    }

    public synchronized void stopRunning() {
      mShouldContinue = false;
    }
  }


  /**
   * A background thread that receives audio from the microphone and sends it to the waveform
   * visualizing view.
   */
  private class RecordingThread extends Thread {

    private boolean mShouldContinue = true;
    private DiskUsage diskUsage = new DiskUsage();

    /**
     * Notifies the thread that it should stop running at the next opportunity.
     */
    public synchronized void stopRunning() {
      mShouldContinue = false;
    }

    @Override
    public void run() {
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

      AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE,
          AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);

      DataOutputStream dos;
      FileOutputStream fos;
      mTempFile = new File(mFilePath);
      try {
        mTempFile = new File(mFilePath);
        fos = new FileOutputStream(mTempFile);
        dos = new DataOutputStream(new BufferedOutputStream(fos));
      } catch (FileNotFoundException e) {
        dos = null;
        fos = null;
      }

      record.startRecording();

      while (shouldContinue()) {
        record.read(mAudioBuffer, 0, mBufferSize / 2);
        mWaveformView.updateAudioData(mAudioBuffer);
        if (mCollectingData && dos != null) {
          try {
            for (short s : mAudioBuffer) {
              dos.write(s);
            }
          } catch (IOException e) {
          }
        }
        updateDecibelLevel();
      }
      try {
        assert dos != null;
        assert fos != null;
        dos.close();
        fos.close();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (NullPointerException e) {

      }

      record.stop();
      record.release();
    }

    /**
     * Gets a value indicating whether the thread should continue running.
     *
     * @return true if the thread should continue running or false if it should stop
     */
    private synchronized boolean shouldContinue() {
      return mShouldContinue;
    }

    /**
     * Computes the decibel level of the current sound buffer and updates the appropriate text
     * view.
     */
    private void updateDecibelLevel() {
      // Compute the root-mean-squared of the sound buffer and then apply the formula for
      // computing the decibel level, 20 * log_10(rms). This is an uncalibrated calculation
      // that assumes no noise in the samples; with 16-bit recording, it can range from
      // -90 dB to 0 dB.
      double sum = 0;

      for (short rawSample : mAudioBuffer) {
        double sample = rawSample / 32768.0;
        sum += sample * sample;
      }

      double rms = Math.sqrt(sum / mAudioBuffer.length);
      final double db = 20 * Math.log10(rms);

      // Update the text view on the main thread.
      mDecibelView.post(new Runnable() {
        @Override
        public void run() {
          mDecibelView.setText(String.format(mDecibelFormat, db));
          if (db < -12) {
            mDecibelView.setTextColor(Color.BLACK);
          } else if (db > -12 && db < -6) {
            mDecibelView.setTextColor(Color.YELLOW);
          } else if (db > -6) {
            mDecibelView.setTextColor(Color.RED);
          } else {
            mDecibelView.setTextColor(Color.BLACK);
          }
          mDecibelView.setText(String.format(mDecibelFormat, db));
          long diskUsed = diskUsage.sdCardUsed();
          long diskTotal = diskUsage.sdCardTotal();
          int diskPercentage = Double.valueOf(100 * (double) diskUsed / (double) diskTotal)
              .intValue();
          mDecibelProgressBar.setProgress((int) Math.abs(db));
          mProgressBar.setProgress(diskPercentage);
        }
      });
    }
  }

}
