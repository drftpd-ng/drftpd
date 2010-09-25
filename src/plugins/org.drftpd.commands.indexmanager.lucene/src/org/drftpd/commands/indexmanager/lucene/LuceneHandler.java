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
package org.drftpd.commands.indexmanager.lucene;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.CorruptIndexException;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.lucene.LuceneEngine;

/**
 * Lucene Implementation specific command handler.
 * This command handler should only be used if Lucene is being used as the indexing engine.
 * @author fr0w
 * @version $Id$
 */
public class LuceneHandler extends CommandInterface {
	
	private static final Logger logger = Logger.getLogger(LuceneHandler.class);
	
	public CommandResponse doUpdateSearcher(CommandRequest request) {
		LuceneEngine ie = (LuceneEngine) GlobalContext.getGlobalContext().getIndexEngine();
		
		try {
			ie.commit();
			
			return new CommandResponse(200, "Searcher updated!");
		} catch (IndexException e) {
			logger.error("Index is corrupted, unable to refresh searcher", e);
			
			// TODO what code should I use here?
			return new CommandResponse(500, "Unable to refresh searcher. Check your logs.");
		}
	}
}
