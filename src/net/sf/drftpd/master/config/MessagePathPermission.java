/*
 * Created on 2003-aug-25
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.FtpResponse;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class MessagePathPermission extends PathPermission {
	private ArrayList message;
	public MessagePathPermission(
		String path,
		String messageFile,
		Collection users)
		throws IOException {
		super(path, users);
		message = new ArrayList();
		BufferedReader in = new BufferedReader(new FileReader(messageFile));
		String line;
		while ((line = in.readLine()) != null) {
			message.add(line);
		}
		message.trimToSize();
	}

	public void printMessage(FtpResponse response) {
		for (Iterator iter = message.iterator(); iter.hasNext();) {
			String line = (String) iter.next();
			response.addComment(line);
		}
	}
}
