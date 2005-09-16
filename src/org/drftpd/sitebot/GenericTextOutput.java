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
package org.drftpd.sitebot;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author zubov
 * @version $Id$
 */
public class GenericTextOutput extends IRCCommand {
	private static final Logger logger = Logger.getLogger(GenericTextOutput.class);
    private HashMap<String,String> _commands;

    public GenericTextOutput() {
		super();
        reload();
    }
    
    private void reload() {
        _commands = new HashMap<String,String>();

        BufferedReader in;

        try {
            in = new BufferedReader(new InputStreamReader(
                        new FileInputStream("conf/generictextoutput.conf")));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("conf/generictextoutput.conf could not be opened",
                e);
        }

        String line;
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#") || line.trim().equals("")) {
                    continue;
                }

                String[] args = line.split(" ");

                if (args.length < 2) {
                    continue;
                }

                _commands.put(args[0], args[1]);
              
            }
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        } finally {
            try {
                in.close();
            } catch (IOException e2) {
            }
        }
    }

	public ArrayList<String> doText(String args, MessageCommand msgc) {
	    ArrayList<String> out = new ArrayList<String>();
        String msg = msgc.getMessage();

        for (Iterator iter = _commands.keySet().iterator(); iter.hasNext();) {
            String trigger = (String) iter.next();

            if (msg.startsWith(trigger)) {
                try {
                    BufferedReader rd = new BufferedReader(new InputStreamReader(
                            new FileInputStream((String) _commands.get(trigger)), "ISO-8859-1"));
                    String line;

                    try {
                        while ((line = rd.readLine()) != null) {
                            if (!line.startsWith("#")) {
                                out.add(line);
                            }
                        }
                    } finally {
                        rd.close();
                    }
                    break;
                } catch (UnsupportedEncodingException e) {
                    logger.warn(e);
                } catch (FileNotFoundException e) {
                    logger.warn(e);
                    out.add(e.getMessage());
                } catch (IOException e) {
                    logger.warn(e);
                }
            }
        }
		return out;
	}
}
