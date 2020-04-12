package org.drftpd.master.plugins.nukefilter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.drftpd.master.commands.approve.metadata.Approve;
import org.drftpd.master.commands.nuke.NukeException;
import org.drftpd.master.commands.nuke.NukeUtils;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.commands.nuke.NukeEvent;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.TimerTask;

/**
 * @author phew
 */
public class NukeFilterNukeTask extends TimerTask {
	private static final Logger logger = LogManager.getLogger(NukeFilterNukeTask.class);
	
	private DirectoryHandle dir;
	private String reason;
	private int nukex;
	
	public NukeFilterNukeTask(NukeFilterNukeItem nfni) {
		dir = nfni.getDirectoryHandle();
		reason = nfni.getReason();
		nukex = nfni.getNukex();
	}
	
	public void run() {
		if(dir.getName().startsWith("[NUKED]-")) {
			//do not try nuking a nuked directory
			return;
		}
		
		if(Approve.isApproved(dir)) {
			return;
		}
		
		User nuker;
		try {
			nuker = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(
					NukeFilterManager.getNukeFilterManager().getNukeFilterSettings().getNuker());
		} catch(NoSuchUserException e) {
            logger.error("error loading nuker: {}", e.getMessage());
			return;
		} catch (UserFileException e) {
            logger.error("error loading nuker: {}", e.getMessage());
			return;
		}
		
		NukeData nd;
		try {
			nd = NukeUtils.nuke(dir, nukex, reason, nuker);
		} catch (NukeException e) {
            logger.error("error nuking: {}", e.getMessage());
			return;
		}
		NukeEvent nuke = new NukeEvent(nuker, "NUKE", nd);
		GlobalContext.getEventService().publishAsync(nuke);
	}

}
