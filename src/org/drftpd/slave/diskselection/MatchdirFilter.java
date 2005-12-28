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

package org.drftpd.slave.diskselection;

import java.util.ArrayList;
import java.util.Properties;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import org.drftpd.PropertyHelper;

/**
 * Sample configuration.
 * <pre>
 * X.filter=matchdir
 * X.assign=<rootNumber>+100000
 * X.match=<path glob match>
 * </pre>
 * @author fr0w
 **/
public class MatchdirFilter extends DiskFilter {
	
	private Pattern _p;
	private Perl5Matcher _m = new Perl5Matcher();
	private ArrayList _assignList;
	private String _pattern;
	
	public MatchdirFilter(Properties p, Integer i) {
		super(p, i);
		_assignList = AssignRoot.parseAssign(PropertyHelper.getProperty(p, i + ".assign"));
		_pattern = PropertyHelper.getProperty(p, i + ".match");
		
		try {
			_p = new GlobCompiler().compile(_pattern, GlobCompiler.CASE_INSENSITIVE_MASK);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void process(ScoreChart sc, String path) {
		if (_m.matches(path, _p)) {
			AssignRoot.addScoresToChart(_assignList, sc);
		}
	}	
}
