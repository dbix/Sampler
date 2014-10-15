package dbixler.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by dbixler on 10/10/14.
 */
public class Files
{

    private Files()
    {
    }

    public static boolean moveFile(String from, String to) throws IOException
    {
        Files.touch(to);
        File fromFile = new File(from);
        File toFile = new File(to);

        if (fromFile.exists() && fromFile.isFile())
        {
            MappedByteBuffer mbb = Files.toMappedByteBuffer(from);
            FileOutputStream fos = new FileOutputStream(toFile);
            FileChannel fc = fos.getChannel();
            fc.write(mbb);
            fromFile.delete();
            return true;
        }
        else
        {
            return false;
        }
    }

    public static MappedByteBuffer toMappedByteBuffer(String file) throws IOException
    {
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY, 0L, fc.size());
    }

    public static boolean touch(String dir) throws IOException
    {
        File f = new File(dir);
        if (f.isFile())
        {
            return true;
        }
        else
        {
            Files.mkDirs(dir.substring(0, dir.lastIndexOf(File.separatorChar)));
            return f.createNewFile();
        }
    }

    public static Path mkDirs(String dir)
    {
        return Path.mkDirs(dir);
    }

    public static byte[] toByteArray(String file) throws IOException
    {
        FileInputStream fis = new FileInputStream(file);
        FileChannel fc = fis.getChannel();
        MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, 0L, fc.size());
        byte[] ba = new byte[(int) fc.size()];

        for (int i = 0; i < fc.size(); i++)
        {
            ba[i] = mbb.get(i);
        }

        return ba;
    }

    public static void writeByteArray(byte[] b, String dir) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(Files.getFile(dir));
        FileChannel fc = fos.getChannel();
        ByteBuffer bb = ByteBuffer.wrap(b);
        fc.write(bb);
        fos.close();
        fc.close();
    }

    public static File getFile(String dir) throws IOException
    {
        File f = new File(dir);
        if (!f.isFile())
        {
            f.createNewFile();
        }
        return f;
    }

    public static void writeByteBuffer(ByteBuffer bb, String dir) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(Files.getFile(dir));
        FileChannel fc = fos.getChannel();
        bb.rewind();
        fc.write(bb);
        fos.close();
        fc.close();
    }


    public static void writeIntArray(int[] i, String dir) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(Files.getFile(dir));
        FileChannel fc = fos.getChannel();
        ByteBuffer bb = ByteBuffer.allocate(i.length * 4);

        for (int j : i)
        {
            bb.put(Bytes.fromInt(j));
        }

        bb.rewind();
        fc.write(bb);
        fos.close();
        fc.close();
    }


    public static void writeShortArray(short[] s, String dir) throws IOException
    {
        FileOutputStream fos = new FileOutputStream(Files.getFile(dir));
        FileChannel fc = fos.getChannel();
        ByteBuffer bb = ByteBuffer.allocate(s.length * 2);

        for (short j : s)
        {
            bb.put(Bytes.fromShort(j));
        }

        bb.rewind();
        fc.write(bb);
        fos.close();
        fc.close();
    }
}
