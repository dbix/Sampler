package dbixler.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Created by dbixler on 10/15/14.
 */
public class Paths
{

    public static Path get(String first, String... more) throws IOException
    {
        if (more == null || more.length == 0)
        {
            String dir = System.getProperty("user.dir") + File.pathSeparator + first;
            Files.touch(dir);
            return Path.createPath(dir);
        }
        else
        {
            String dir = File.pathSeparator + first;
            for (String dirName : more)
            {
                dir += File.pathSeparator + dirName;
            }
            return Path.createPath(dir);
        }
    }


    public Path get(URI uri)
    {
        return Path.createPath(uri.getPath());
    }
}

