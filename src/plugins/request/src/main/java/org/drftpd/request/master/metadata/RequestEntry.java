package org.drftpd.request.master.metadata;

import java.io.Serializable;

public class RequestEntry implements Serializable {
    private String _user;
    private String _name;
    private String _prefix;
    private long _creationTime;

    public RequestEntry(String name, String user, String prefix, long creationTime) {
        _name = name;
        _user = user;
        _prefix = prefix;
        _creationTime = creationTime;
    }

    public void setUser(String user) {
        _user = user;
    }

    public String getUser() {
        return _user;
    }

    public void setPrefix(String prefix) {
        _prefix = prefix;
    }

    public String getPrefix() {
        return _prefix;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    public void setCreationTime(long creationTime) {
        _creationTime = creationTime;
    }

    public long getCreationTime() {
        return _creationTime;
    }

    public String getDirectoryName() {
        return getPrefix() + getUser() + "-" + getName();
    }

    public String getFilledDirectoryName(String prefix) {
        return prefix + getUser() + "-" + getName();
    }
}
