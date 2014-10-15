package dbixler.io;

import java.io.File;
import java.io.IOException;

/**
 * Created by dbixler on 10/15/14.
 */

public class Path
{

    private static String mPath;


    private Path(String path)
    {
        mPath = path;
    }


    private Path(File f)
    {
        mPath = f.getPath();
    }


    public static Path createPath(String path)
    {
        return new Path(path);
    }


    public static Path mkDirs(String path)
    {
        File f = new File(path);
        f.mkdirs();
        return new Path(f);
    }


    public void append(String dirName)
    {
        mPath += File.pathSeparator + dirName;
    }


    public void setPath(String path)
    {
        mPath = path;
    }


    public File toFile() throws IOException
    {
        File f = new File(mPath);
        if (!f.isFile())
        {
            f.createNewFile();
        }
        return f;
    }


    public String toString()
    {
        return mPath;
    }
}

