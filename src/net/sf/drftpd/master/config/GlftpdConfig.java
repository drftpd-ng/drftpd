/*
 * Created on 2003-aug-13
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class GlftpdConfig {
	private Map map = new HashMap();
	public GlftpdConfig(Reader in2) throws IOException {
		BufferedReader in = new BufferedReader(in2);

		try {
			String line;
			while ((line = in.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				if(!st.hasMoreTokens()) continue;
				String command = st.nextToken();
				if(command.charAt(0) == '-') command = command.substring(1);
				if(!st.hasMoreTokens()) continue;
				
				String arg1 = st.nextToken();
				if(arg1.charAt(0) == '-' || arg1.charAt(0) == '=' 
				|| arg1.startsWith("!=") || arg1.startsWith("!-")) {
					ArrayList args = new ArrayList();
					args.add(arg1);
					while(st.hasMoreTokens()) {
						args.add(st.nextToken());
					}
					System.out.println("commandpermission: "+line);
					map.put(command, new CommandPermission(command, args));
				} else {
					String path = arg1;
					ArrayList args = new ArrayList();
					while(st.hasMoreTokens()) {
						args.add(st.nextToken());
					}
					map.put(command, new PathPermission(command, path, args));
				}		
			}
		} catch (IOException e) {
			throw e;
		}
	}
	
	public CommandPermission getCommandPermission(String command) {
		return (CommandPermission)map.get(command);
	}
	public PathPermission getPathPermission(String command) {
		return (PathPermission)map.get(command);
	}
	
	public static void main(String args[]) throws Exception {
		GlftpdConfig config = new GlftpdConfig(new FileReader("/jail/glftpd/glftpd.conf"));
		System.out.println(config.getCommandPermission("users"));
	}
}
