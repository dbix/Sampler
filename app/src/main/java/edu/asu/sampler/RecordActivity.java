package edu.asu.sampler;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import dbixler.io.Files;


public class RecordActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener
{
    private static final int REQUEST_CODE_OPENER = 1;
    private static final int REQUEST_CODE_CREATOR = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private static final String LOG_TAG = "AudioRecordTest";
    private static final String KEY_IN_RESOLUTION = "is_in_resolution";
    private static String mFileName = null;
    private final String THREE_GP_FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp.mp4";
    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;
    private EditText mEditText = null;
    private boolean mAutoUploading = true;
    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;
    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mIsInResolution;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        if (icicle != null)
        {
            mIsInResolution = icicle.getBoolean(KEY_IN_RESOLUTION, false);
        }
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.mp4";


        setContentView(R.layout.activity_record);

        ImageButton mImageButtonRecord = (ImageButton) findViewById(R.id.image_button_record);
        mImageButtonRecord.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View recordbutton)
            {
                if (recordbutton.isSelected())
                {
                    recordbutton.setSelected(false);
                    onRecord(false);
                    if (mAutoUploading)
                    {
                        saveFileToDrive(mFileName);
                    }
                }
                else
                {
                    onRecord(true);
                    recordbutton.setSelected(true);
                    Toast toast = Toast.makeText(getApplicationContext(), "Recording", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            }
        });

        ImageButton mImageButtonBrowseDrive = (ImageButton) findViewById(R.id.image_button_open_drive);
        mImageButtonBrowseDrive.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                openFileFromDrive();
            }
        });

    }

    /**
     * Called when the Activity is made visible.
     * A connection to Play Services need to be initiated as
     * soon as the activity is visible. Registers {@code ConnectionCallbacks}
     * and {@code OnConnectionFailedListener} on the
     * activities itself.
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        if (mGoogleApiClient == null)
        {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(Drive.API).addScope(Drive.SCOPE_FILE)
                // Optionally, add additional APIs and scopes if required.
                .addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        }
        mGoogleApiClient.connect();
    }

    /**
     * Saves the resolution state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mIsInResolution);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mRecorder != null)
        {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null)
        {
            mPlayer.release();
            mPlayer = null;
        }

        File f = new File(mFileName);
        f.delete();
    }

    /**
     * Called when activity gets invisible. Connection to Play Services needs to
     * be disconnected as soon as an activity is invisible.
     */
    @Override
    protected void onStop()
    {
        if (mGoogleApiClient != null)
        {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.record, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId())
        {
            case R.id.action_upload:
                saveFileToDrive(mFileName);
                return true;
            case R.id.action_settings:
                Intent intent = new Intent(RecordActivity.this, SettingsActivity.class);
                startActivity(intent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Handles Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK)
                {
                    DriveId driveId = (DriveId) data.getParcelableExtra(OpenFileActivityBuilder
                        .EXTRA_RESPONSE_DRIVE_ID);
                    Toast.makeText(this, "Selected file's ID: " + driveId, Toast.LENGTH_SHORT).show();
                    DriveFile driveFile = Drive.DriveApi.getFile(mGoogleApiClient, driveId);
                    ResultCallback<DriveContentsResult> contentsOpenedCallback = new
                        ResultCallback<DriveContentsResult>()
                    {
                        @Override
                        public void onResult(DriveContentsResult result)
                        {
                            if (!result.getStatus().isSuccess())
                            {
                                Toast.makeText(getApplicationContext(), "ERROR: file cannot be opened for some " +
                                    "reason", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            try
                            {
                                DriveContents contents = result.getDriveContents();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(contents
                                    .getInputStream()));
                                byte[] buffer;
                                reader.read();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    };
                    driveFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY,
                        null).setResultCallback(contentsOpenedCallback);
                }
                break;
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void retryConnecting()
    {
        mIsInResolution = false;
        if (!mGoogleApiClient.isConnecting())
        {
            mGoogleApiClient.connect();
        }
    }

    private void saveFileToDrive(final String fileName)
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
                OutputStream outputStream = result.getDriveContents().getOutputStream();
                try
                {
                    byte[] threeGPStream = Files.toByteArray(fileName);
                    outputStream.write(threeGPStream);
                }
                catch (IOException e1)
                {
                    Log.i(LOG_TAG, "Unable to write file contents.");
                }
                // Create the initial metadata - MIME type and title.
                // Note that the user will be able to change the title later.
                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder().setMimeType("audio/mp4")
                    .setTitle(".mp4").build();
                // Create an intent for the file chooser, and start it.
                IntentSender intentSender = Drive.DriveApi.newCreateFileActivityBuilder().setInitialMetadata
                    (metadataChangeSet).setInitialDriveContents(result.getDriveContents()).build(mGoogleApiClient);
                try
                {
                    startIntentSenderForResult(intentSender, REQUEST_CODE_CREATOR, null, 0, 0, 0);
                }
                catch (IntentSender.SendIntentException e)
                {
                    Log.i(LOG_TAG, "Failed to launch file chooser.");
                }
            }
        });
    }

    private void openFileFromDrive()
    {
        IntentSender intentSender = Drive.DriveApi.newOpenFileActivityBuilder().setMimeType(new String[]{"audio/mp4",
            "audio/3gpp", "audio/mpeg"}).build(mGoogleApiClient);
        try
        {
            startIntentSenderForResult(intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        }
        catch (IntentSender.SendIntentException e)
        {
            Log.w(LOG_TAG, "Unable to send intent", e);
        }
    }

    private void onRecord(boolean start)
    {
        if (start)
        {
            startRecording();
        }
        else
        {
            stopRecording();
        }
    }

    private void startRecording()
    {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioEncodingBitRate(16);
        mRecorder.setAudioSamplingRate(44100);
        mRecorder.setOutputFile(mFileName);
        try
        {
            mRecorder.prepare();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording()
    {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    private void onPlay(boolean start)
    {
        if (start)
        {
            startPlaying();
        }
        else
        {
            stopPlaying();
        }
    }

    private void startPlaying()
    {
        mPlayer = new MediaPlayer();
        try
        {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlaying()
    {
        mPlayer.release();
        mPlayer = null;
    }

    /**
     * Called when {@code mGoogleApiClient} is connected.
     */
    @Override
    public void onConnected(Bundle connectionHint)
    {
        Log.i(LOG_TAG, "GoogleApiClient connected");
        Toast toast = Toast.makeText(getApplicationContext(), "GoogleApiClient connected", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
        // TODO: Start making API requests.
    }

    /**
     * Called when {@code mGoogleApiClient} connection is suspended.
     */
    @Override
    public void onConnectionSuspended(int cause)
    {
        Log.i(LOG_TAG, "GoogleApiClient connection suspended");
        retryConnecting();
    }

    /**
     * Called when {@code mGoogleApiClient} is trying to connect but failed.
     * Handle {@code result.getResolution()} if there is a resolution
     * available.
     */
    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
        Log.i(LOG_TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution())
        {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0, new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    retryConnecting();
                }
            }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (mIsInResolution)
        {
            return;
        }
        mIsInResolution = true;
        try
        {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        }
        catch (IntentSender.SendIntentException e)
        {
            Log.e(LOG_TAG, "Exception while starting resolution activity", e);
            retryConnecting();
        }
    }
}