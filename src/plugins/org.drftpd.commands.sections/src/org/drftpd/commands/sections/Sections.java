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
package org.drftpd.commands.sections;

import java.util.Date;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.conf.DatedSection;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @author mog
 * @version $Id$
 */
public class Sections extends CommandInterface {
	private ResourceBundle _bundle;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = ResourceBundle.getBundle(this.getClass().getName());
    }

    public CommandResponse doSITE_SECTIONS(CommandRequest request) {
        CommandResponse response = new CommandResponse(200);

        ReplacerEnvironment env = new ReplacerEnvironment();

        for (SectionInterface section : GlobalContext.getGlobalContext().getSectionManager().getSections()) {
            env.add("section", section.getName());
            env.add("path", section.getCurrentDirectory());
            if (section instanceof DatedSection) {
            	DatedSection ds = (DatedSection) section;
            	ds.processNewDate(new Date());
            }
            response.addComment(request.getSession().jprintf(_bundle, "section", env, request.getUser()));
        }

        return response;
    }
}
