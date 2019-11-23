/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.commands.usermanagement.securepass;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.event.ReloadEvent;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;

/**
 * @author : CyBeR
 * @version : v1.0 
 */

public class SecurePassManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(SecurePassManager.class);

	private ArrayList<Integer> _length;
	private ArrayList<Integer> _uppercase;
	private ArrayList<Integer> _lowercase;
	private ArrayList<Integer> _numeric;
	private ArrayList<Integer> _special;
	private ArrayList<String> _perms;	
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
		loadConf();
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadConf();
    }	
	
    /*
     * Get the securePass Plugin
     */
    public static SecurePassManager getSecurePass() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof SecurePassManager) {
    			return (SecurePassManager) plugin;
    		}
    	}
    	throw new RuntimeException("SecurePass plugin is not loaded.");
    }    
    
    /*
     * Loads configs by reading file line by line.
     * Must be done this way as we read the same "command" string multiple times
     */
	private void loadConf() {
		_length = new ArrayList<>();
		_uppercase = new ArrayList<>();
		_lowercase = new ArrayList<>();
		_numeric = new ArrayList<>();
		_special = new ArrayList<>();
		_perms = new ArrayList<>();
		
		LineNumberReader inRead = null;
		
		try {
			inRead = new LineNumberReader(new FileReader("conf/plugins/securepass.conf"));			   
			String line;
			while ((line = inRead.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
		   
				if (!st.hasMoreTokens()) {
					continue;
				}
				String cmd = st.nextToken();
		   
				if (cmd.equals("securepass")) {
					try {
						
						int length = Integer.parseInt(st.nextToken().trim());
						int lowercase = Integer.parseInt(st.nextToken().trim());
						int uppercase = Integer.parseInt(st.nextToken().trim());
						int numeric = Integer.parseInt(st.nextToken().trim());
						int special = Integer.parseInt(st.nextToken().trim());
						String perms = st.nextToken("").trim();
						
						_length.add(length);
						_uppercase.add(uppercase);
						_lowercase.add(lowercase);
						_numeric.add(numeric);
						_special.add(special);
						_perms.add(perms);
						
					} catch (NumberFormatException e) {
                        logger.warn("NumberFormatException when reading securepass line {}", inRead.getLineNumber(), e);
					}
				}
		   }
	   } catch (Exception e) {
		   logger.warn("Exception when reading securepass conf", e);
	   } finally {
		   try {
			   if (inRead != null) {
				   inRead.close();
			   }
		   } catch (IOException ex) {
			   //couldn't close file - ignore
		   }
	   }		
	}
	
	/*
	 * Checks perms if this password applies to user
	 */
	private boolean checkPermission(User user, String perm) {
		if (user == null) {
			return perm.equals("*");
		}

		Permission perms = new Permission(perm);
		return perms.check(user);
	}
	
	/*
	 * Checks the password and makes sure it conforms to
	 * the settings specified in the conf file
	 */
	public boolean checkPASS(String password,User user) {
		String lowercase = "abcdefghijklmnopqrstuvwxyz";
		String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		String numeric = "0123456789";
		String special = "!@#$%^&*(){}`~-_=+\\[]/?.,><:;";

		for (int i = 0; i < _length.size(); i++) {
			if (checkPermission(user,_perms.get(i))) {
				if (password.length() < _length.get(i)) {
					return false;
				}
				
				int numlc = 0;
				int numuc = 0;
				int numnum = 0;
				int numspecial = 0;					

				/*
				 * Goes though each character and inumerates the correct varialbe
				 */
				for (int j=0; j<password.length();j++) {
					if (lowercase.indexOf(password.charAt(j)) > -1) {
						numlc++;
					} else if (uppercase.indexOf(password.charAt(j)) > -1) {
						numuc++;
					} else if (numeric.indexOf(password.charAt(j)) > -1) {
						numnum++;
					} else if (special.indexOf(password.charAt(j)) > -1) {
						numspecial++;
					}
				}
				
				/*
				 * Checks to make sure the password is secure enough
				 */
				if (((numlc < _lowercase.get(i)) && (_lowercase.get(i) > 0)) ||
					((numuc < _uppercase.get(i)) && (_uppercase.get(i) > 0)) ||
					((numnum < _numeric.get(i)) && (_numeric.get(i) > 0)) ||
					((numspecial < _special.get(i)) && (_special.get(i) > 0))) {
					
					return false;
				}
				
			}
		}
		return true;
	}
	
	/*
	 * Returns a string representative on how to add IPs and their restrictions
	 */
	public String outputConfs(User user) {		
		StringBuilder returnstring = new StringBuilder("Unable to add PASSWORD, must conform to following specs:");
		returnstring.append("\n| Length | Upper Case | Lower Case | Numeric | Special Char |");
		for (int i = 0; i < _length.size(); i++) {
			if (checkPermission(user,_perms.get(i))) {
				returnstring.append("\n");
				if (_length.get(i) > 0) {
					returnstring.append(" | LN = " + _length.get(i) + " ");
				} 
				if (_uppercase.get(i) > 0) {
					returnstring.append(" | UC = " + _uppercase.get(i) + " ");
				} 
				if (_lowercase.get(i) > 0) {
					returnstring.append(" | LC = " + _lowercase.get(i) + " ");
				} 
				if (_numeric.get(i) > 0) {
					returnstring.append(" | NUM = " + _numeric.get(i) + " ");
				} 
				if (_special.get(i) > 0) {
					returnstring.append(" | SP = " + _special.get(i) + " ");
				}
				returnstring.append(" |");
			}
		}
		return returnstring.toString();
	}
	
}