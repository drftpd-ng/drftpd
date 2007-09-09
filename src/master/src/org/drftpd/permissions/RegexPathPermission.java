package org.drftpd.permissions;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drftpd.vfs.DirectoryHandle;

public class RegexPathPermission extends PathPermission {
    private Pattern _pat;

    public RegexPathPermission(Pattern pat, Collection<String> users) {
        super(users);
        _pat = pat;
    }

	public boolean checkPath(DirectoryHandle path) {
		Matcher m = _pat.matcher(path.getPath());
		
		return m.find();
	}
}
