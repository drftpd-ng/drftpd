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

import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.OutCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import junit.framework.TestCase;

import net.sf.drftpd.event.Event;

import org.drftpd.plugins.SiteBot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import java.net.UnknownHostException;

import java.util.ArrayList;


/**
 * @author zubov
 * @version $Id$
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
        SB irc = new SB();

        irc.actionPerformed(new Event("RELOAD"));
        irc.connect();

        BufferedReader in = new BufferedReader(new StringReader(
                    "Sample text\nMore text"));
        Textoutput.sendTextToIRC(irc.getIRCConnection(), "zubov", in);
        assertEquals(irc.msgs.size(), 2);
        assertEquals("Sample text",
            ((MessageCommand) irc.msgs.get(0)).getMessage());
        assertEquals("More text",
            ((MessageCommand) irc.msgs.get(1)).getMessage());
    }

    public static class SB extends SiteBot {
        public final ArrayList<OutCommand> msgs = new ArrayList<OutCommand>();

        public SB() throws IOException {
            super();
        }

        public IRCConnection getIRCConnection() {
            return new IRCConnection() {
                    public void sendCommand(OutCommand arg) {
                        msgs.add(arg);
                    }
                };
        }

        public void actionPerformed() {
            return;
        }

        public void connect() {
            return;
        }
    }
}
