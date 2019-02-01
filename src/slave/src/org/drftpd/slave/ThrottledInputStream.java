package org.drftpd.slave;

import java.io.IOException;
import java.io.InputStream; 

public class ThrottledInputStream extends InputStream {
    private long _counter;
    private InputStream _in;
    private long _lastCounter;
    private long _lastMillis;
    private long _maxBytesPerSecond;
    private Object _monitor;

    public ThrottledInputStream(InputStream input, long maximumBytesPerSecond) {
        _in = input;
        setMaxBytesPerSecond(maximumBytesPerSecond);
        _lastMillis = System.currentTimeMillis();
        _monitor = new Object();
    }

    public ThrottledInputStream(InputStream input) {
        this(input, Long.MAX_VALUE);
    }

    @Override
    public void close() throws IOException {
        _in.close();
    }

    public long getCounter() {
        return _counter;
    }

    @Override
    public int read() throws IOException {
        int result = _in.read();
        if (result != -1) {
            _counter++;
            waitIfNecessary();
        }
        return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int result = _in.read(b);
        if (result > 0) {
            _counter += result;
            waitIfNecessary();
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = _in.read(b, off, len);
        if (result > 0) {
        	_counter += result;
            waitIfNecessary();
        }
        return result;
    }

    public void resetCounter() {
        setCounter(0);
    }

    public void setCounter(long newValue) {
        _counter = newValue;
    }

    public void setMaxBytesPerSecond(long maxBytes) {
        if (maxBytes < 0) {
        	throw new IllegalArgumentException("Maximum bytes per second must be at least one.");
        }
        _maxBytesPerSecond = maxBytes;
    }

    @Override
    public long skip(long n) throws IOException {
        return _in.skip(n);
    }
    
    public void wake() {
    	synchronized (_monitor) {
    		_monitor.notify();
    	}
    }

    private void waitIfNecessary() {
        if (_maxBytesPerSecond == 0) {
        	return;
        }

        long diffBytes = _counter - _lastCounter;
        long millis = System.currentTimeMillis();
        long diffMillis = millis - _lastMillis;
        long waitMillis = (1000 * diffBytes - diffMillis * _maxBytesPerSecond) / _maxBytesPerSecond;

        if (waitMillis > 0) {
        	synchronized (_monitor) {
	            try {
	                _monitor.wait(waitMillis);
	            }
	            catch (InterruptedException ie) {
	            }
        	}
        }

        _lastCounter = _counter;
        _lastMillis = System.currentTimeMillis();
    } 
}
