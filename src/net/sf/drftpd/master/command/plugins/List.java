package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.VirtualDirectory;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class List implements CommandHandler {
	private Logger logger = Logger.getLogger(List.class);

	/**
	 * <code>NLST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command causes a directory listing to be sent from
	 * server to user site.  The pathname should specify a
	 * directory or other system-specific file group descriptor; a
	 * null argument implies the current directory.  The server
	 * will return a stream of names of files and no other
	 * information.
	 *
	 *
	 * <code>LIST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command causes a list to be sent from the server to the
	 * passive DTP.  If the pathname specifies a directory or other
	 * group of files, the server should transfer a list of files
	 * in the specified directory.  If the pathname specifies a
	 * file then the server should send current information on the
	 * file.  A null argument implies the user's current working or
	 * default directory.  The data transfer is over the data
	 * connection
	 * 
	 *                LIST
	 *                   125, 150
	 *                      226, 250
	 *                      425, 426, 451
	 *                   450
	 *                   500, 501, 502, 421, 530
	 */
	public FtpReply execute(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		String argument = request.getArgument();
		String directoryName = null;
		String options = "";
		//String pattern = "*";

		// get options, directory name and pattern
		//argument == null if there was no argument for LIST
		if (argument != null) {
			//argument = argument.trim();
			StringBuffer optionsSb = new StringBuffer(4);
			StringTokenizer st = new StringTokenizer(argument, " ");
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if (token.charAt(0) == '-') {
					if (token.length() > 1) {
						optionsSb.append(token.substring(1));
					}
				} else {
					directoryName = token;
				}
			}
			options = optionsSb.toString();
		}

		// check options
		//		boolean allOption = options.indexOf('a') != -1;
		boolean fulldate = options.indexOf('T') != -1;
		boolean detailOption =
			request.getCommand().equals("LIST")
				|| request.getCommand().equals("STAT")
				|| options.indexOf('l') != -1;
		//		boolean directoryOption = options.indexOf("d") != -1;

		DataConnectionHandler dataconn = null;
		if (!request.getCommand().equals("STAT")) {
			try {
				dataconn =
					(DataConnectionHandler) conn
						.getCommandManager()
						.getCommandHandler(
						DataConnectionHandler.class);
			} catch (ObjectNotFoundException e2) {
				throw new RuntimeException(e2);
			}
			if (!dataconn.mbPasv
				&& !dataconn.mbPort) {
				return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
			}
		}

		LinkedRemoteFile directoryFile;
		if (directoryName != null) {
			try {
				directoryFile =
					conn.getCurrentDirectory().lookupFile(directoryName);
			} catch (FileNotFoundException ex) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
			if (!conn
				.getConfig()
				.checkPrivPath(conn.getUserNull(), directoryFile)) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		} else {
			directoryFile = conn.getCurrentDirectory();
		}

		PrintWriter out = conn.getControlWriter();
		Socket dataSocket = null;
		Writer os;

		if (request.getCommand().equals("STAT")) {
			os = out;
			out.println("213- Status of " + request.getArgument() + ":");
		} else {
			out.print(FtpReply.RESPONSE_150_OK);
			out.flush();
			try {
				dataSocket = dataconn.getDataSocket();
				os =
					new PrintWriter(
						new OutputStreamWriter(dataSocket.getOutputStream()));
				//out2 = dataSocket.getChannel();
			} catch (IOException ex) {
				logger.warn("from master", ex);
				return new FtpReply(425, ex.getMessage());
			}
		}

////////////////
		java.util.List listFiles = ListUtils.list(directoryFile, conn);
//		//////////////

		try {
			if (request.getCommand().equals("LIST")
				|| request.getCommand().equals("STAT")) {
				VirtualDirectory.printList(listFiles, os, fulldate);
			} else if (request.getCommand().equals("NLST")) {
				VirtualDirectory.printNList(listFiles, detailOption, os);
				//VirtualDirectory.printNList(listFiles, detailOption, dataChannel);
			}
		} catch (IOException ex) {
			logger.warn("from master", ex);
			return new FtpReply(450, ex.getMessage());
		} finally {

			FtpReply response =
				(FtpReply) FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION.clone();

			try {
				if (!request.getCommand().equals("STAT")) {
					os.flush();
					dataSocket.shutdownOutput();
					os.close();
					dataSocket.close();
					response.addComment(conn.status());
					return response;
				} else {
					out.println("213 End of Status");
					return null;
				}
			} catch (IOException e1) {
				logger.error("", e1);
				return null;
			}
		}
		//redo connection handling
		//conn.reset();

	}
	/**
	 * <code>STAT [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause a status response to be sent over
	 * the control connection in the form of a reply.
	 */
	//	public void doSTAT(FtpRequest request, PrintWriter out) {
	//		reset();
	//		if (request.hasArgument()) {
	//			doLIST(request, out);
	//		} else {
	//			out.print(FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
	//		}
	//		return;
	//	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
	 */
	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}
	public String[] getFeatReplies() {
		return null;
	}

	public void load(CommandManagerFactory initializer) {
	}
	public void unload() {
	}
}
