package net.sf.drftpd.master.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.FtpReply;

/**
 * @author mog
 *
 * @version $Id: MessagePathPermission.java,v 1.5 2004/01/13 20:30:54 mog Exp $
 */
public class MessagePathPermission extends StringPathPermission {
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
		try {
			while ((line = in.readLine()) != null) {
				message.add(line);
			}
		} finally {
			in.close();
		}
		message.trimToSize();
	}

	public void printMessage(FtpReply response) {
		for (Iterator iter = message.iterator(); iter.hasNext();) {
			String line = (String) iter.next();
			response.addComment(line);
		}
	}
}
