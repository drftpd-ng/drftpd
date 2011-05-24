package org.drftpd.slave;

import java.io.IOException;
import java.io.InputStream; 

public class ThrottledInputStream extends InputStream {
    private long counter;
    private InputStream in;
    private long lastCounter;
    private long lastMillis;
    private long maxBytesPerSecond;

    public ThrottledInputStream(InputStream input, long maximumBytesPerSecond) {
        in = input;
        setMaxBytesPerSecond(maximumBytesPerSecond);
        lastMillis = System.currentTimeMillis();
    }

    public ThrottledInputStream(InputStream input) {
        this(input, Long.MAX_VALUE);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public long getCounter() {
        return counter;
    }

    @Override
    public int read() throws IOException {
        int result = in.read();
        if (result != -1) {
            counter++;
            waitIfNecessary();
        }
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int result = in.read(b);
        if (result > 0) {
            counter += result;
            waitIfNecessary();
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result > 0) {
        	counter += result;
            waitIfNecessary();
        }
        return result;
    }

    public void resetCounter() {
        setCounter(0);
    }

    public void setCounter(long newValue) {
        counter = newValue;
    }

    public void setMaxBytesPerSecond(long maxBytes) {
        if (maxBytes < 0) {
        	throw new IllegalArgumentException("Maximum bytes per second must be at least.");
        }
        maxBytesPerSecond = maxBytes;
    }

    @Override
    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    private void waitIfNecessary() {
        if (maxBytesPerSecond == 0) {
        	return;
        }

        long diffBytes = counter - lastCounter;
        long millis = System.currentTimeMillis();
        long diffMillis = millis - lastMillis;
        long waitMillis = (1000 * diffBytes - diffMillis * maxBytesPerSecond) / maxBytesPerSecond;

        if (waitMillis > 0) {
            try {
                Thread.sleep(waitMillis);
            }
            catch (InterruptedException ie) {
            }
        }

        lastCounter = counter;
        lastMillis = System.currentTimeMillis();
    } 
}
