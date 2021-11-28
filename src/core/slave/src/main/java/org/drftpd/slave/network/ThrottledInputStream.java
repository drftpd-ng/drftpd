/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.slave.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

public class ThrottledInputStream extends InputStream {

    private static final Logger logger = LogManager.getLogger(ThrottledInputStream.class);

    private final InputStream _in;

    private final long _maxBytesPerSecond;
    private final long _startTime = System.nanoTime();

    private long _bytesRead = 0;
    private long _totalSleepTime = 0;

    private static final Object _monitor = new Object();
    private static final long SLEEP_DURATION_MS = 30;

    public ThrottledInputStream(InputStream input) {
        this(input, Long.MAX_VALUE);
    }

    public ThrottledInputStream(InputStream input, long maximumBytesPerSecond) {
        assert(maximumBytesPerSecond >= 0);
        assert(input != null);

        this._in = input;
        this._maxBytesPerSecond = maximumBytesPerSecond;
        logger.debug("Initialized with maximum Bytes/second as {}", _maxBytesPerSecond);
    }

    /** @inheritDoc */
    @Override
    public void close() throws IOException {
        _in.close();
    }

    /** @inheritDoc */
    @Override
    public int read() throws IOException {
        throttle();
        int result = _in.read();
        if (result != -1) {
            _bytesRead++;
        }
        return result;
    }

    /** @inheritDoc */
    @Override
    public int read(byte[] b) throws IOException {
        throttle();
        int result = _in.read(b);
        if (result != -1) {
            _bytesRead += result;
        }
        return result;
    }

    /** @inheritDoc */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        throttle();
        int result = _in.read(b, off, len);
        if (result != -1) {
            _bytesRead += result;
        }
        return result;
    }

    public void wake() {
        synchronized (_monitor) {
            _monitor.notify();
        }
    }

    private void throttle() {
        if (_maxBytesPerSecond <= 0) {
            return;
        }
        while (getBytesPerSec() > _maxBytesPerSecond) {
            synchronized (_monitor) {
                try {
                    _monitor.wait(SLEEP_DURATION_MS);
                    _totalSleepTime += SLEEP_DURATION_MS;
                } catch (InterruptedException e) {
                    logger.warn("Caught InterruptedException while throttling", e);
                    break;
                }
            }
        }
    }

    public long getTotalBytesRead() {
        return _bytesRead;
    }

    /**
     * Return the number of bytes read per second
     */
    public long getBytesPerSec() {
        long elapsed = (System.nanoTime() - _startTime) / 1000000000;
        if (elapsed == 0) {
            return _bytesRead;
        } else {
            return _bytesRead / elapsed;
        }
    }

    public long getTotalSleepTime() {
        return _totalSleepTime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ThrottledInputStream{bytesRead=");
        sb.append(getTotalBytesRead());
        sb.append(", maxBytesPerSec=");
        sb.append(_maxBytesPerSecond);
        sb.append(", bytesPerSec=");
        sb.append(getBytesPerSec());
        sb.append(", totalSleepTimeInSeconds=");
        if (getTotalSleepTime() == 0) {
            sb.append('0');
        } else {
            sb.append(getTotalSleepTime() / 1000);
        }
        return sb.toString();
    }
}
