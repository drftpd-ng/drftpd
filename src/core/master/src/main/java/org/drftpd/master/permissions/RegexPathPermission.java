package org.drftpd.master.permissions;

import org.drftpd.master.vfs.InodeHandle;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexPathPermission extends PathPermission {
    private final Pattern _pat;

    public RegexPathPermission(Pattern pat, Collection<String> users) {
        super(users);
        _pat = pat;
    }

    public boolean checkPath(InodeHandle inode) {
        Matcher m = _pat.matcher(inode.getPath());
        return m.find();
    }

    public String toString() {
        return getClass().getCanonicalName() + "[pat=" + _pat.toString() + ",users=" + _users.toString() + "]";
    }
}
