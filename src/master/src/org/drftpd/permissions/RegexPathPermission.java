package org.drftpd.permissions;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexPathPermission extends PathPermission {
    private Pattern _pat;

    public RegexPathPermission(Pattern pat, Collection<String> users) {
        super(users);
        _pat = pat;
    }

	public boolean checkPath(String path) {
		Matcher m = _pat.matcher(path);
		return m.find();
	}
	
	public String toString() {
		return getClass().getCanonicalName()+"[pat="+_pat.toString()+",users="+_users.toString()+"]";
	}
}
