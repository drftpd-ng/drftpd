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

import java.util.ArrayList;

import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;



/**
 * @author zubov
 * @version $Id
 */
public class ExcludePath {
	private Pattern _pat;
	public ExcludePath(Pattern pat) {
		_pat = pat;
	}
	
	public boolean checkPath(LinkedRemoteFileInterface file) {
		String path = file.getPath();
		if(file.isDirectory()) path = path.concat("/");
		Perl5Matcher m = new Perl5Matcher();
		return m.matches(path, _pat);
	}
	
	public static void makePermission(ArrayList arr, String st)
		throws MalformedPatternException {
		arr.add(
			new ExcludePath(
				new GlobCompiler().compile(st)));
	}
}