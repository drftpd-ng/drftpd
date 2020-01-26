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

package org.drftpd.commands.usermanagement.ipsecurity;

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

public class IpSecurityManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(IpSecurityManager.class);

	private ArrayList<Integer> _ident;
	private ArrayList<String> _octets;
	private ArrayList<Integer> _numip;
	private ArrayList<Integer> _hostm;
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
     * Get the ipSecurity Plugin
     */
    public static IpSecurityManager getIpSecurity() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof IpSecurityManager) {
    			return (IpSecurityManager) plugin;
    		}
    	}
    	throw new RuntimeException("IpSecurity plugin is not loaded.");
    }
	

    /*
     * Loads configs by reading file line by line.
     * Must be done this way as we read the same "command" string multiple times
     */
	private void loadConf() {
		_ident = new ArrayList<>();
		_octets = new ArrayList<>();
		_numip = new ArrayList<>();
		_hostm = new ArrayList<>();
		_perms = new ArrayList<>();
		LineNumberReader inRead = null;
	
	   try{
		   inRead = new LineNumberReader(new FileReader("conf/plugins/ipsecurity.conf"));
		   
		   String line;
		   while ((line = inRead.readLine()) != null) {
			   StringTokenizer st = new StringTokenizer(line);
			   
			   if (!st.hasMoreTokens()) {
				   continue;
			   }
			   String cmd = st.nextToken();
			   
			   if(cmd.equals("ipsecurity")) {
				   try {
					   int ident = Integer.parseInt(st.nextToken().trim());
					   String octets = st.nextToken().trim();
					   int hostm = Integer.parseInt(st.nextToken().trim());
					   int numip = Integer.parseInt(st.nextToken().trim());
					   String perms = st.nextToken("").trim();
					   
					   _ident.add(ident);
					   _octets.add(octets);
					   _hostm.add(hostm);
					   _numip.add(numip);
					   _perms.add(perms);
				   } catch (NumberFormatException e) {
                       logger.debug("Error while parsing ipsecurity line {}", inRead.getLineNumber());
				   }
			   }
		   }
	   } catch (Exception e) {
		   logger.warn("Exception when reading ipsecurity conf", e);
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
	 * Checks if host has an Ident line and returns if permitted
	 */
	private boolean checkIdent(String ident, int currentident ) {
        return (currentident != 1) || (!ident.equals("*"));
    }
	
	/*
	 * This checks the octects to make sure they are correct and not a insecure set
	 * Also checks hostmask if using that, and compares it to the setup in conf file
	 */
	private boolean checkOctets(String octets, String currentoctet, int isHost) {
		String[] split_octets = octets.split("\\.");
		String[] split_currentoctets = currentoctet.split("\\.");
		
		if (isHost == 1) {
			if (split_currentoctets.length > 0) {
				
				/*
				 * Makes sure the length of the dns name does not exceed
				 * the size of the conf
				 */
				if (split_octets.length > split_currentoctets.length) {
                    logger.debug("Invalid Input Host Mask: {}", octets);
					return false;
				}
				
				/*
				 * This makes sure something is besides a * in the hostmask.
				 */
				for (int i=0;i<split_currentoctets.length;i++) {
					if (i < split_octets.length) {
						if ((!split_currentoctets[i].equals("*")) &&
							split_octets[i].equals("*")) {
							return false;
						}
					} else {
						if (!split_currentoctets[i].equals("*")) {
							return false;
						}						
					}
				}
				return true;
			}
		} else {
			if (split_currentoctets.length == 4) {
				/*
				 * This checks to make sure if the ip is smaller than 4 octets 
				 * that the last octets are * and allowed
				 */
				for (int i=4 - split_octets.length;i>0;--i) {
					if (!split_currentoctets[4-i].equals("*")) {
						return false;
					}
				}

				/*
				 * Make sure the input of the hostmask is IPv4
				 * Will have to make a set for IPv6
				 */
				if (split_octets.length > split_currentoctets.length) {
                    logger.debug("Invalid Input Host Mask: {}", octets);
					return false;
				}
				
				/*
				 * Checks to make sure that the octets are correct if
				 * it is not a *
				 */
				for (int i=0;i<4;i++) {
					if (i < split_octets.length) {
						if (!split_currentoctets[i].equals("*")) {
							if (!split_octets[i].matches("^(\\d{1,3})$")) {
								return false;
							}
						}
					} else {
						if (!split_currentoctets[i].equals("*")) {
							return false;
						}
					}
				}
				return true;
			}
            logger.debug("Invalid Octet in conf file: {}", currentoctet);
		}
		return false;
	}
	
	/*
	 * Makes sure user is not over max number of ips
	 */
	private boolean checkNumIPS(int numips, int currentips) {
		if (currentips == 0) {
			return true;
		}
		return numips < currentips;
	}
	
	/*
	 * Checks perms if this ip applies to user
	 */
	private boolean checkPermission(User user, String perm) {
		if (user == null) {
			return perm.equals("*");
		}
		
		Permission perms = new Permission(perm);
		return perms.check(user);
	}

	/*
	 * Main method to check both Octets/Hostname and number of IPs
	 */
	public boolean checkIP(String ident, String octets, int numip, User user) {
		for (int i = 0; i < _ident.size(); i++) {
			if (checkPermission(user,_perms.get(i))) {
				if ((checkOctets(octets,_octets.get(i),_hostm.get(i))) &&
						(checkNumIPS(numip,_numip.get(i))) &&
						(checkIdent(ident,_ident.get(i)))) {
							return true;
				}
			}
		}
		return false;
	}

	/*
	 * Returns a string representative on how to add IPs and their restrictions
	 */
	public String outputConfs(User user) {	
		StringBuilder returnstring = new StringBuilder("Unable to add IP, must be of the following form(s):");
		int numip = 0;
		for (int i = 0; i < _ident.size(); i++) {
			if (checkPermission(user,_perms.get(i))) {
				if (_ident.get(i) == 1) {
					returnstring.append("\nident@");
				} else {
					returnstring.append("\n*@");
				}
	
				returnstring.append(_octets.get(i));
				
				if (_hostm.get(i) == 1) {
					returnstring.append(" - (Replace X's with ispprovider.com)");
				}
				
				if (!(_numip.get(i) == 0)) {
					try {
						numip = _numip.get(i);
					} catch (NumberFormatException e) {
						// cannot parse integer....ignore
					}
				}
			}
		}

		if (numip > 0) {
			returnstring.append("\nAccount restricted to " + numip + " IP(s)");
		}

		return returnstring.toString();
		
	}

}
