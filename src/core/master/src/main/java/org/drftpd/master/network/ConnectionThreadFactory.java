package org.drftpd.master.network;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


public class ConnectionThreadFactory implements ThreadFactory {
    public static String getIdleThreadName(long threadId) {
        return "FtpConnection Handler-" + threadId + " - Waiting for connections";
    }

    public Thread newThread(Runnable r) {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName(getIdleThreadName(t.getId()));
        return t;
    }
}