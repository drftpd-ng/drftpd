package net.sf.drftpd.master.config;

import java.util.Collection;

/**
 * @author mog
 *
 * @version $Id: CommandPermission.java,v 1.2 2003/12/23 13:38:20 mog Exp $
 */
public class CommandPermission {
	private String command;
	
	public CommandPermission(String command, Collection users) {
		this.command = command;
		
	}
}
