package dbixler.io;

/**
 * Created by dbixler on 10/15/14.
 */
public class Bytes
{

    public static byte[] fromInt(int n)
    {
        return new byte[]{(byte) (n & 0xff), (byte) ((n >> 8) & 0xff), (byte) ((n >> 16) & 0xff),
            (byte) ((n >> 24) & 0xff)};
    }


    public static byte[] fromShort(int n)
    {
        return new byte[]{(byte) (n & 0xff), (byte) ((n >> 8) & 0xff)};
    }


    public static int toInt(byte[] b)
    {
        return ((int) b[0] & 0xff) +
            (((int) b[1] & 0xff) << 8) +
            (((int) b[2] & 0xff) << 16) +
            (((int) b[3] & 0xff) << 24);
    }


    public static short toShort(byte[] b)
    {
        short n = 0;
        n = (short) (((short) b[0] & 0xff) + (((short) b[1] & 0xff) << 8));
        return n;
    }

}
