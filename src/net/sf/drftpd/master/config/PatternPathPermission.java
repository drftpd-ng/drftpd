/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.config;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * @author mog
 * @version $Id: PatternPathPermission.java,v 1.6 2004/02/10 00:03:08 mog Exp $
 */
public class PatternPathPermission extends PathPermission {
	Pattern _pat;
	public PatternPathPermission(Pattern pat, Collection users) {
		super(users);
		 _pat = pat;
	}
	
	public boolean checkPath(LinkedRemoteFile file) {
		String path = file.getPath();
		if(file.isDirectory()) path = path.concat("/");
		Perl5Matcher m = new Perl5Matcher();
		return m.matches(path, _pat); 
	}
}
