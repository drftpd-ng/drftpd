package org.drftpd;

import org.drftpd.master.master.ConnectionManager;

public class MasterBoot {

    public static void main(String... args) {
        ConnectionManager.boot();
    }
}
