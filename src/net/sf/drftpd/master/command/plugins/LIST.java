package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFileInterface;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class LIST implements CommandHandler {
	private Logger logger = Logger.getLogger(LIST.class);

	private final static String DELIM = " ";

	private final static String[] MONTHS =
		{
			"Jan",
			"Feb",
			"Mar",
			"Apr",
			"May",
			"Jun",
			"Jul",
			"Aug",
			"Sep",
			"Oct",
			"Nov",
			"Dec" };

	private final static DateFormat AFTER_SIX = new SimpleDateFormat(" yyyy");

	private final static DateFormat BEFORE_SIX = new SimpleDateFormat("HH:mm");

	private final static DateFormat FULL =
		new SimpleDateFormat("HH:mm:ss yyyy");

	private final static String NEWLINE = "\r\n";

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
			if (!dataconn.mbPasv && !dataconn.mbPort) {
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
			out.write(
				"213- Status of " + request.getArgument() + ":" + NEWLINE);
		} else {
			out.write(FtpReply.RESPONSE_150_OK);
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
				printList(listFiles, os, fulldate);
			} else if (request.getCommand().equals("NLST")) {
				printNList(listFiles, detailOption, os);
				//VirtualDirectory.printNList(listFiles, detailOption, dataChannel);
			}
		} catch (IOException ex) {
			logger.warn("from master", ex);
			return new FtpReply(450, ex.getMessage());
		} finally {

			FtpReply response =
				(FtpReply) FtpReply
					.RESPONSE_226_CLOSING_DATA_CONNECTION
					.clone();

			try {
				if (!request.getCommand().equals("STAT")) {
					os.flush();
					dataSocket.shutdownOutput();
					os.close();
					dataSocket.close();
					response.addComment(conn.status());
					return response;
				} else {
					return new FtpReply(213, "End of Status");
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

	/**
	 * Print file list. Detail listing.
	 * <pre>
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printList(Collection files, Writer os, boolean fulldate)
		throws IOException {
		//out = new BufferedWriter(out);
		os.write("total 0" + NEWLINE);

		// print file list
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			RemoteFileInterface file = (RemoteFileInterface) iter.next();
			LIST.printLine(file, os, fulldate);
		}
	}

	public static String getUnixDate(long date, boolean fulldate) {
		Date date1 = new Date(date);
		long dateTime = date1.getTime();
		if (dateTime < 0) {
			return "------------";
		}

		Calendar cal = new GregorianCalendar();
		cal.setTime(date1);
		String firstPart = MONTHS[cal.get(Calendar.MONTH)] + ' ';

		String dateStr = String.valueOf(cal.get(Calendar.DATE));
		if (dateStr.length() == 1) {
			dateStr = ' ' + dateStr;
		}
		firstPart += dateStr + ' ';

		long nowTime = System.currentTimeMillis();
		if (fulldate) {
			return firstPart + FULL.format(date1);
		} else if (
			Math.abs(nowTime - dateTime) > 183L * 24L * 60L * 60L * 1000L) {
			return firstPart + AFTER_SIX.format(date1);
		} else {
			return firstPart + BEFORE_SIX.format(date1);
		}
	}

	/**
	 * Get size
	 * @deprecated
	 */
	private static String getLength(RemoteFileInterface fl) {
		String initStr = "             ";
		String szStr = Long.toString(fl.length());
		if (szStr.length() > initStr.length()) {
			return szStr;
		}
		return initStr.substring(0, initStr.length() - szStr.length()) + szStr;
	}

	/**
	 * Get file name.
	 */
	private static String getName(LinkedRemoteFile fl) {
		String flName = fl.getName();

		int lastIndex = flName.lastIndexOf("/");
		if (lastIndex == -1) {
			return flName;
		} else {
			return flName.substring(lastIndex + 1);
		}
	}

	/**
	 * Get permission string.
	 */
	private static String getPermission(RemoteFileInterface fl) {

		StringBuffer sb = new StringBuffer(13);
		sb.append(fl.isDirectory() ? 'd' : '-');

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		return sb.toString();
	}

	//TODO MOVE ME
	public static boolean isLegalFileName(String fileName) {
		assert fileName != null;
		return !(fileName.indexOf("/") != -1)
			&& !fileName.equals(".")
			&& !fileName.equals("..");
	}

	public static String PADDING = "          ";
	private static String padToLength(String value, int length) {
		if (value.length() >= length)
			return value;
		assert PADDING.length() > length : "padding must be longer than length";
		return PADDING.substring(0, length - value.length()) + value;
	}

	/**
	 * Get each directory line.
	 */
	public static void printLine(
		RemoteFileInterface fl,
		Writer out,
		boolean fulldate)
		throws IOException {
		StringBuffer line = new StringBuffer();
		if (fl instanceof LinkedRemoteFile
			&& !((LinkedRemoteFile) fl).isAvailable()) {
			line.append("----------");
		} else {
			line.append(getPermission(fl));
		}
		line.append(DELIM);
		line.append((fl.isDirectory() ? "3" : "1"));
		line.append(DELIM);
		line.append(padToLength(fl.getUsername(), 8));
		line.append(DELIM);
		line.append(padToLength(fl.getGroupname(), 8));
		line.append(DELIM);
		line.append(getLength(fl));
		line.append(DELIM);
		line.append(getUnixDate(fl.lastModified(), fulldate));
		line.append(DELIM);
		line.append(fl.getName());
		line.append(NEWLINE);
		out.write(line.toString());

		if (fl.isDirectory() && fl instanceof LinkedRemoteFile) {
			LinkedRemoteFile file = (LinkedRemoteFile) fl;
			try {
				int filesleft = file.lookupSFVFile().filesLeft();
				if (filesleft != 0)
					out.write(
						"l--------- 3 "
							+ padToLength(fl.getUsername(), 8)
							+ DELIM
							+ padToLength(fl.getGroupname(), 8)
							+ "             0 "
							+ getUnixDate(fl.lastModified(), fulldate)
							+ DELIM
							+ fl.getName()
							+ "-MISSING-"
							+ filesleft
							+ "-FILES -> "
							+ fl.getName()
							+ NEWLINE);
			} catch (IOException e) {
			} // errors getting SFV? FINE! We don't care!
		}
		if (fl instanceof LinkedRemoteFile) {
			LinkedRemoteFile file = (LinkedRemoteFile) fl;
			if (!file.isAvailable())
				out.write(
					"l--------- 3 "
						+ padToLength(fl.getUsername(), 8)
						+ DELIM
						+ padToLength(fl.getGroupname(), 8)
						+ "             0 "
						+ getUnixDate(fl.lastModified(), fulldate)
						+ DELIM
						+ fl.getName()
						+ "-OFFLINE"
						+ " -> "+fl.getName()
						+ NEWLINE);
		}
	}

	/**
	 * Print file list.
	 * <pre>
	 *   -l : detail listing
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printNList(
		Collection fileList,
		boolean bDetail,
		Writer out)
		throws IOException {

		for (Iterator iter = fileList.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (bDetail) {
				printLine(file, out, false);
			} else {
				out.write(file.getName() + NEWLINE);
			}
		}
	}
}
