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
package net.sf.drftpd.master.command.plugins;

import java.io.IOException;
import java.net.UnknownHostException;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.irc.IRCListener;
import junit.framework.TestCase;

/*
 * @author zubov
 * @version $Id
 */
public class TextoutputTest extends TestCase {

	/**
	 * Constructor for TextoutputTest.
	 * @param arg0
	 */
	public TextoutputTest(String arg0) {
		super(arg0);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(TextoutputTest.class);
	}

	public void testSendTextToIRC() throws UnknownHostException, IOException {
		IRCListener irc = new IRCListener();
		irc.actionPerformed(new Event("RELOAD"));
		irc.connect();
		Textoutput.sendTextToIRC(irc.getIRCConnection(),"zubov","affils");
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
