/*
 * Created on 2003-aug-25
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Permission {
	private Collection users;
	public Permission(Collection users) {
		this.users = users;
	}
	
	public boolean check(User user) {
		for (Iterator iter = this.users.iterator(); iter.hasNext();) {
			String aclUser = (String) iter.next();
			boolean allow = true;
			if (aclUser.charAt(0) == '!') {
				allow = false;
				aclUser = aclUser.substring(1);
			}
			if (aclUser.equals("*")) {
				return allow;
			}
			else if(aclUser.charAt(0) == '-') {
				//USER
				if(aclUser.substring(1).equals(user.getUsername())) {
					return allow;
				}
				continue;
			}
			else if (aclUser.charAt(0) == '=') {
				//GROUP
				if(user.isMemberOf(aclUser.substring(1))) {
					return allow;
				}
			}
			else {
				//FLAG, we don't have flags, we have groups and that's the same but multiple letters
				if(user.isMemberOf(aclUser)) {
					return allow;
				}
			}
		}
		// didn't match.. 
		return false;
	}

}
