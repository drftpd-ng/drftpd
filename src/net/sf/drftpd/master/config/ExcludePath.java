/*
 * Created on Jan 15, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.config;

import java.util.ArrayList;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

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
	
	public boolean checkPath(LinkedRemoteFile file) {
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