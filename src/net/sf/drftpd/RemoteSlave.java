package net.sf.drftpd;

import java.net.InetAddress;
import java.io.Serializable;
import net.sf.drftpd.slave.Slave;

public class RemoteSlave implements Serializable {

    public RemoteSlave() {}
    public RemoteSlave(Slave slave) {
	setSlave(slave);
    }

    private transient Slave slave;
    public void setSlave(Slave slave) {
	this.slave = slave;
    }
    public Slave getSlave() { return slave; }

    private boolean online;
    public boolean isOnline() {
	return online;
    }
    public void setOnline(boolean online) {
	this.online = online;
    }

    public RemoteSlave(String name) {
	this.name = name;
    }

    protected String name;
    public String getName() {
	return name;
    }

    protected transient InetAddress address;
    public InetAddress getAddress() { return address; }
    public void setAddress(InetAddress address) {
	this.address = address;
    }

}
