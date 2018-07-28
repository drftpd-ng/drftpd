package org.drftpd.commands.xdupe;

import org.drftpd.commandmanager.*;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.Set;
import java.util.StringTokenizer;

public class XDupePreHook implements PreHookInterface {
	public void initialize(StandardCommandManager manager) {
		
	}
	
	public CommandRequestInterface doXDupeCheck(CommandRequest request) {
		DirectoryHandle dir = request.getCurrentDirectory();
		
		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (!st.hasMoreTokens()) {
			return request;
		}
		String arg = st.nextToken();		
		String fileName = arg;
		if (arg.equalsIgnoreCase("STOR")) {
			if (!st.hasMoreTokens()) {
				return request;
			}
			fileName = st.nextToken();
		}
		
		FileHandle f = dir.getNonExistentFileHandle(fileName);
		
		// XDUPE should only run if file exists.
		if (!f.exists()) {
			return request;
		}
		
		User user = request.getSession().getUserNull(request.getUser());
		int xdupe = request.getSession().getObjectInteger(XDupe.XDUPE);		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		String mode1or4 = "";

		Set<FileHandle> files;
		try {
			files = dir.getFiles(user);
		} catch (FileNotFoundException e) {
			// 'dir' does not exist. let STOR handle the failure
			return request;
		}
		
		for (FileHandle file : files) {			
			switch (xdupe) {
				case 1 :
					if (file.getName().length() > 66)
						response.addComment("X-DUPE: "+ file.getName().substring(0, 65));
					else if (file.getName().length() + mode1or4.length() > 66) {
						response.addComment("X-DUPE: " + mode1or4);
						mode1or4 = file.getName();
					} else
						mode1or4 = (mode1or4.length() > 0 ? mode1or4 + " " : "") + file.getName();
					continue;
				case 2 :
					response.addComment("X-DUPE: " + (file.getName().length() > 66 
									? file.getName().substring(0, 65) 
											: file.getName()));
					continue;
				case 3 :
					response.addComment("X-DUPE: " + file.getName());
					continue;
				case 4 :
					if (mode1or4.length() + file.getName().length() <= 1010)
						mode1or4 = (mode1or4.length() > 0 ? mode1or4 + " " : "") + file.getName();
            }
		}
		
		if (mode1or4.length() > 0)
			response.addComment("X-DUPE: " + mode1or4);
		
		request.setDeniedResponse(response);
		request.setAllowed(false);

		return request;
	}

}
