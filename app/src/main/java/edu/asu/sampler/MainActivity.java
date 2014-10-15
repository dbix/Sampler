package edu.asu.sampler;

import android.app.Activity;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.MetadataChangeSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import edu.asu.sampler.pcm.PcmAudioHelper;
import edu.asu.sampler.pcm.WavAudioFormat;


public class MainActivity extends Activity implements ConnectionCallbacks, OnConnectionFailedListener
{

    /* The sampling rate for the audio recorder. */
    private static final int SAMPLING_RATE = 44100;
    private static final int MONO = AudioFormat.CHANNEL_IN_MONO;
    private static final int PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private static final String FILENAME_REGEX = "[A-Za-z0-9_-]*.wav";

    /* Tag for Log.d's */
    private final String LOG_TAG = getClass().getName();

    /* Data */ ArrayList<Short> mTrack;
    private GoogleApiClient mGoogleApiClient;

    /* Audio visualization views */
    private WaveformView mWaveformView;
    private TextView mDecibelView;
    private String mDecibelFormat;
    private ProgressBar mProgressBar;
    private VerticalProgressBar mDecibelProgressBar;

    /* Buttons */
    private Button mSaveButton;
    private Button mRecordButton;
    private boolean mCollectingData;

    /* Other views */
    private TextView mEditTextFileName;

    /* Media attributes */
    private RecordingThread mRecordingThread;
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private int mBufferSize;
    private short[] mAudioBuffer;
    private ByteBuffer mByteBuffer;
    private String mPcmFilePath;
    private String mWavFilePath;
    private long mFreeMemory;
    private long mTotalMemory;
    private long mUsedMemory;
    private int mPercentUsedMemory;
    private File mPcmFile;
    private boolean mNewFileHasBeenRecorded;
    private WavAudioFormat mWavAudioFormat;
    private File mWavFile;
    private boolean mIsRecording;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mWaveformView = (WaveformView) findViewById(R.id.waveform_view);
        mDecibelView = (TextView) findViewById(R.id.decibel_view);
        mDecibelProgressBar = (VerticalProgressBar) findViewById(R.id.decibelProgressBar);

        mEditTextFileName = (EditText) findViewById(R.id.action_bar_layout);
        mRecordButton = (Button) findViewById(R.id.buttonRecord);
        mSaveButton = (Button) findViewById(R.id.buttonUploadToDrive);
        mIsRecording = false;
        mNewFileHasBeenRecorded = false;
        mProgressBar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
        mDecibelProgressBar.getProgressDrawable().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);

        try
        {
            mPcmFile = File.createTempFile("tmp", ".wav");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        mWavAudioFormat = WavAudioFormat.mono16Bit(SAMPLING_RATE);
        mBufferSize = 3 * AudioRecord.getMinBufferSize(SAMPLING_RATE, MONO, PCM_16BIT);
        mAudioBuffer = new short[mBufferSize / 2];
        mDecibelFormat = getResources().getString(R.string.decibel_format);
        mNewFileHasBeenRecorded = false;
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        mNewFileHasBeenRecorded = false;
        mRecordingThread = new RecordingThread(mPcmFile.getAbsolutePath());

        if (mGoogleApiClient == null)
        {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Drive.API).addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        }

        mGoogleApiClient.connect();
        mRecordingThread.start();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        try
        {
            mRecordingThread.stopRunning();
            mRecordingThread = null;
            mGoogleApiClient.disconnect();
        }
        catch (NullPointerException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        Log.d(LOG_TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowTitleEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
            case R.id.buttonRecord:
                System.out.println("buttonRecord");
                return true;
            case R.id.buttonUploadToDrive:
                saveFileToDrive();
                return true;
            case R.id.editTextFileName:
                findViewById(R.id.editTextFileName).requestFocus();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void saveFileToDrive()
    {
        // Start by creating a new contents, and setting a callback.
        Log.i(LOG_TAG, "Creating new contents.");
        Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveContentsResult>()
        {
            @Override
            public void onResult(DriveContentsResult result)
            {
                if (!result.getStatus().isSuccess())
                {
                    Log.i(LOG_TAG, "Failed to create new contents.");
                    return;
                }
                Log.i(LOG_TAG, "New contents created.");


                try
                {
                    mWavFile = convertPcmToWav(mPcmFile);
                    System.out.println(mWavFile.getAbsolutePath());
                    InputStream fos = new FileInputStream(mWavFile);
                    DataInputStream dos = new DataInputStream(fos);
                    byte[] track = new byte[(int) mWavFile.length()];
                    dos.read(track);


                    OutputStream outputStream = result.getDriveContents().getOutputStream();
                    //RandomAccessFile f = new RandomAccessFile(mWavFile, "r");
                    //f.read(track);

                    outputStream.write(track);
                }
                catch (IOException e1)
                {
                    Log.i(LOG_TAG, "Unable to write file contents.");
                }
                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder().setMimeType("audio/wav")
                    .setTitle(mEditTextFileName.getText().toString()).build();
                IntentSender intentSender = Drive.DriveApi.newCreateFileActivityBuilder().setInitialMetadata
                    (metadataChangeSet).setInitialDriveContents(result.getDriveContents()).build(mGoogleApiClient);
                try
                {
                    startIntentSenderForResult(intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
                }
                catch (SendIntentException e)
                {
                    Log.i(LOG_TAG, "Failed to launch file chooser.");
                }
            }
        });
    }

    private File convertPcmToWav(File pcmFile)
    {
        WavAudioFormat wavAudioFormat;

        try
        {
            mWavFile = File.createTempFile("tmp", ".wav");
            PcmAudioHelper.convertRawToWav(mWavAudioFormat, mPcmFile, mWavFile);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return mWavFile;
    }

    public void buttonRecordClicked(View v)
    {
        mNewFileHasBeenRecorded = true;
        if (!mIsRecording)
        {
            mRecordingThread.startWriting();
            Toast.makeText(getApplicationContext(), "Recording", Toast.LENGTH_SHORT).show();
            mIsRecording = true;
        }
        else
        {
            mRecordingThread.stopWriting();
            mIsRecording = false;
            Toast.makeText(getApplicationContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        }
    }

    public void buttonDriveClicked(View v)
    {
        mEditTextFileName = (EditText) findViewById(R.id.editTextFileName);
        try
        {
            if (mEditTextFileName.getText().toString().matches(FILENAME_REGEX))
            {
                saveFileToDrive();
            }
            else if (!mNewFileHasBeenRecorded)
            {
                String msg = "You must record a sound first";
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
            else
            {
                String msg = "Valid file names include A-Z, a-z, 0-9, -, and _, ending with .wav";
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }
        catch (NullPointerException e)
        {
            mEditTextFileName.setText("");
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
        // Called whenever the API client fails to connect.
        Log.i(LOG_TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution())
        {
            // show the localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            return;
        }
        // The failure has a resolution. Resolve it.
        // Called typically when the app is not yet authorized, and an
        // authorization
        // dialog is displayed to the user.
        try
        {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        }
        catch (SendIntentException e)
        {
            Log.e(LOG_TAG, "Exception while starting resolution activity", e);
        }
    }

    @Override
    public void onConnected(Bundle connectionHint)
    {
        Log.i(LOG_TAG, "API client connected.");
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
        Log.i(LOG_TAG, "GoogleApiClient connection suspended");
    }

    /**
     * A background thread that receives audio from the microphone and sends it to the waveform
     * visualizing view.
     */
    private class RecordingThread extends Thread
    {

        FileOutputStream mFos;
        DataOutputStream mDos;
        AudioRecord mAudioRecord;
        String mFilePath;
        private boolean mShouldContinue;
        private boolean mShouldWriteData;
        private DiskUsage diskUsage = new DiskUsage();
        private File mFile;

        public RecordingThread(String path)
        {
            try
            {
                mFilePath = path;
                mFile = new File(mFilePath);
                mFile.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            mShouldContinue = true;
            mShouldWriteData = false;
        }

        public synchronized void startWriting()
        {
            try
            {
                mFile.createNewFile();
                mFos = new FileOutputStream(mFilePath);
                mDos = new DataOutputStream(mFos);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            mShouldWriteData = true;
        }

        public synchronized void stopWriting()
        {
            mShouldWriteData = false;
        }

        public synchronized void stopRunning()
        {
            mShouldContinue = false;
        }

        @Override
        public void run()
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, MainActivity.this.mBufferSize);

            mAudioRecord.startRecording();

            while (shouldContinue())
            {
                int readBytes = mAudioRecord.read(mAudioBuffer, 0, mAudioBuffer.length);
                mWaveformView.updateAudioData(mAudioBuffer);
                updateDecibelLevel();
                if (shouldWriteData())
                {
                    try
                    {
                        for (int i = 0; i < mAudioBuffer.length; i++)
                        {
                            mDos.writeShort(mAudioBuffer[i]);
                        }
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            try
            {
                mDos.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (NullPointerException e)
            {
        /* Do nothing, it was never open */
            }

            mAudioRecord.stop();
            mAudioRecord.release();
        }

        private synchronized boolean shouldContinue()
        {
            return mShouldContinue;
        }

        private synchronized boolean shouldWriteData()
        {
            return mShouldWriteData;
        }

        private void updateDecibelLevel()
        {
            // Compute the root-mean-squared of the sound buffer and then apply the formula for
            // computing the decibel level, 20 * log_10(rms). This is an uncalibrated calculation
            // that assumes no noise in the samples; with 16-bit recording, it can range from
            // -90 dB to 0 dB.
            double sum = 0;

            for (short rawSample : mAudioBuffer)
            {
                double sample = rawSample / 32768.0;
                sum += sample * sample;
            }

            double rms = Math.sqrt(sum / mAudioBuffer.length);
            final double db = 20 * Math.log10(rms);

            // Update the text view on the main thread.
            mDecibelView.post(new Runnable()
            {
                @Override
                public void run()
                {
                    mDecibelView.setText(String.format(mDecibelFormat, db));

                    if (db < -12)
                    {
                        mDecibelView.setTextColor(Color.BLACK);
                    }
                    else if (db > -12 && db < -6)
                    {
                        mDecibelView.setTextColor(Color.YELLOW);
                    }
                    else if (db > -6)
                    {
                        mDecibelView.setTextColor(Color.RED);
                    }
                    else
                    {
                        mDecibelView.setTextColor(Color.BLACK);
                    }

                    long diskUsed = diskUsage.sdCardUsed();
                    long diskTotal = diskUsage.sdCardTotal();
                    int diskPercentage = Double.valueOf(100 * (double) diskUsed / (double) diskTotal).intValue();
                    mProgressBar.setProgress(diskPercentage);
                    mDecibelProgressBar.setProgress((int) Math.abs(db));
                    mDecibelView.setText(String.format(mDecibelFormat, db));
                }
            });
        }
    }

    public class WriteDataThread extends Thread
    {

        ByteBuffer data;
        File f;
        FileOutputStream fos;
        DataOutputStream dos;

        public WriteDataThread(ByteBuffer data, File f)
        {
            this.data = data;
            this.f = f;
            try
            {
                fos = new FileOutputStream(f, true);
                //dos = new DataOutputStream(fos);
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

        @Override
        public void run()
        {
            try
            {
                fos.write(data.array());
                fos.close();
                //        for (short s : data.array())
                //          dos.writeShort(s);
                //        dos.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            return;
        }
    }

}
