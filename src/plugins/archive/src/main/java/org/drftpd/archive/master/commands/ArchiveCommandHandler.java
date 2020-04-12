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
package org.drftpd.archive.master.commands;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import org.drftpd.master.GlobalContext;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.commands.*;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.archive.master.Archive;
import org.drftpd.archive.master.DuplicateArchiveException;
import org.drftpd.archive.master.archivetypes.ArchiveHandler;
import org.drftpd.archive.master.archivetypes.ArchiveType;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import java.io.FileNotFoundException;
import java.util.*;


/*
 * @author zubov
 * @version $Id$
 */
public class ArchiveCommandHandler extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(ArchiveCommandHandler.class);

	private ResourceBundle _bundle;
	
    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();

    }
    
    public ArchiveCommandHandler() {
        super();
    }
    
    private Archive getArchive() throws ObjectNotFoundException {
    	Archive archive = null;
		List<PluginInterface> pluginList = GlobalContext.getGlobalContext().getPlugins();
		for (PluginInterface pi : pluginList) {
			if (pi instanceof Archive) {
				archive = (Archive) pi;
				return archive;
			}
		}
		throw new ObjectNotFoundException("Archive is not loaded");
	}

    public CommandResponse doARCHIVE(CommandRequest request) throws ImproperUsageException {
    	CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        String dirname = st.nextToken();
        DirectoryHandle dir;
        User user = request.getSession().getUserNull(request.getUser());

        try {
			dir = request.getCurrentDirectory().getDirectory(dirname, user);
		} catch (FileNotFoundException e1) {
			env.put("dirname", dirname);
			response.addComment(request.getSession().jprintf(_bundle, env,  "baddir"));

			return response;
		} catch (ObjectNotValidException e) {
			response.addComment("Archive only works on Directories");
			return response;
		}

        Archive archive;

        try {
            archive = getArchive();
        } catch (ObjectNotFoundException e3) {
        	response.addComment(request.getSession().jprintf(_bundle, env, "loadarchive"));
            return response;
        }

        String archiveTypeName = null;
        ArchiveType archiveType = null;

        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
        
        if (st.hasMoreTokens()) { // load the specific type
            archiveTypeName = st.nextToken();

            Properties props = new Properties();

            while (st.hasMoreTokens()) {
                addConfig(props, st.nextToken());
            }
            
            archiveType = archive.getArchiveType(0,archiveTypeName,section,props);
            if (archiveType == null) {
                logger.error("Serious error, ArchiveType: {} does not exists", archiveTypeName);
				env.put("archivetypename", archiveTypeName);
				response.addComment(request.getSession().jprintf(_bundle, env, "incompatible"));
				return response;
			}
        }

        HashSet<RemoteSlave> slaveSet = new HashSet<>();

        while (st.hasMoreTokens()) {
            String slavename = st.nextToken();

            try {
                RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
                slaveSet.add(rslave);
            } catch (ObjectNotFoundException e2) {
                env.put("slavename", slavename);
                response.addComment(request.getSession().jprintf(_bundle, env, "badslave"));
            }
        }

        archiveType.setDirectory(dir);

        
        try {
            archive.checkPathForArchiveStatus(dir.getPath());
        } catch (DuplicateArchiveException e) {
            env.put("exception", e.getMessage());
            response.addComment(request.getSession().jprintf(_bundle, env, "fail"));
        }

        if (!slaveSet.isEmpty()) {
            archiveType.setRSlaves(slaveSet);
        }

        ArchiveHandler archiveHandler = new ArchiveHandler(archiveType);

        archiveHandler.start();
        env.put("dirname", dir.getPath());
        env.put("archivetypename", archiveTypeName);
        response.addComment(request.getSession().jprintf(_bundle, env, "success"));

        return response;
    }

    private void addConfig(Properties props, String string) {
        if (string.indexOf('=') == -1) {
            throw new IllegalArgumentException(string + " does not contain an = and is therefore not a property");
        }

        String[] data = string.split("=");

        if (data.length != 2) {
            throw new IllegalArgumentException(string + " is therefore not a property because it has no definite key");
        }

        if (props.containsKey(data[0])) {
            throw new IllegalArgumentException(string + " is already contained in the Properties");
        }

        props.put("0." + data[0].toLowerCase(), data[1]);
    }

    public CommandResponse doLISTARCHIVETYPES(CommandRequest request) {
    	CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        int x = 1;
        Map<String, Object> env = new HashMap<>();
        Archive archive;

        try {
            archive = getArchive();
        } catch (ObjectNotFoundException e) {
        	response.addComment(request.getSession().jprintf(_bundle, env, "loadarchive"));
            return response;
        }

        for (Iterator<String> iter = archive.getTypesMap().keySet().iterator();iter.hasNext();x++) {
        	String type = iter.next();
            response.addComment(x + ": " + type);        	
        }
        
        return response;
    }

    public String[] getFeatReplies() {
        return null;
    }
    
}
