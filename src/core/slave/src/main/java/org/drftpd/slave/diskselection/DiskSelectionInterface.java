package org.drftpd.slave.diskselection;


import org.drftpd.slave.Slave;
import org.drftpd.slave.vfs.Root;

public abstract class DiskSelectionInterface {
    private final Slave _slave;

    public DiskSelectionInterface(Slave slave) {
        _slave = slave;
    }

    public Slave getSlaveObject() {
        return _slave;
    }

    public abstract Root getBestRoot(String dir);
}
