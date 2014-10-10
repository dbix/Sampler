package edu.asu.sampler;

import android.os.Environment;
import android.os.StatFs;

import java.io.File;

/**
 * Created by dbixler on 10/10/14.
 */
public class DiskUsage {

  File path;
  StatFs statFs;

  public DiskUsage() {
    path = Environment.getExternalStorageDirectory();
    statFs = new StatFs(path.getPath());
  }

  public long sdCardTotal() {
    long totalBytes = statFs.getTotalBytes();
    return totalBytes;
  }

  public long sdCardAvailable() {
    long availBytes = statFs.getAvailableBytes();
    return availBytes;
  }

  public long sdCardUsed() {
    long usedBytes = sdCardTotal() - sdCardAvailable();
    return usedBytes;
  }

}
