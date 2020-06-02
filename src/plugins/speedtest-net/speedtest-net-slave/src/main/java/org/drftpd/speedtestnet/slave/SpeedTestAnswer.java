package org.drftpd.speedtestnet.slave;

public class SpeedTestAnswer {
    private final long _bytes;
    private final long _time;

    public SpeedTestAnswer(long bytes, long time) {
        _bytes = bytes;
        _time = time;
    }

    public long getBytes() {
        return _bytes;
    }

    public long getTime() {
        return _time;
    }

}
