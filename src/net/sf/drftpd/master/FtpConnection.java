package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.IllegalTargetException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.event.irc.UploaderPosition;
import net.sf.drftpd.master.queues.NukeLog;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.DirectoryRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Transfer;
import socks.server.Ident;

/**
 * This class handles each ftp connection. Here all the ftp command
 * methods take two arguments - a FtpRequest and a PrintWriter object. 
 * This is the main backbone of the ftp server.
 * <br>
 * The ftp command method signature is: 
 * <code>public void doXYZ(FtpRequest request, PrintWriter out)</code>.
 * <br>
 * Here <code>XYZ</code> is the capitalized ftp command. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */

public class FtpConnection extends BaseFtpConnection {
	private final static SimpleDateFormat DATE_FMT =
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");
	private static Logger logger =
		Logger.getLogger(FtpConnection.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	private NukeLog nukelog;
	// just set mstRenFr to null instead of this extra boolean?
	//private boolean mbRenFr = false;
	private LinkedRemoteFile mstRenFr = null;

	// command state specific temporary variables

	//unneeded? "mlSkipLen == 0" is almost the same thing.
	//private boolean mbReset = false;
	private long resumePosition = 0;

	//	public final static String ANONYMOUS = "anonymous";

	private char type = 'A';
	private short xdupe = 0;

	//	private boolean mbUser = false;
	//private boolean mbPass = false;
	private UserManager userManager;
	public FtpConnection(
		Socket sock,
		UserManager userManager,
		SlaveManagerImpl slaveManager,
		LinkedRemoteFile root,
		ConnectionManager connManager,
		NukeLog nukelog) {

		super(connManager, sock);
		this.userManager = userManager;
		this.slaveManager = slaveManager;

		this.setCurrentDirectory(root);
		this.nukelog = nukelog;
	}

	////////////////////////////////////////////////////////////
	/////////////////   all the FTP handlers   /////////////////
	////////////////////////////////////////////////////////////
	/**
	 * <code>ABOR &lt;CRLF&gt;</code><br>
	 *
	 * This command tells the server to abort the previous FTP
	 * service command and any associated transfer of data.
	 * No action is to be taken if the previous command
	 * has been completed (including data transfer).  The control
	 * connection is not to be closed by the server, but the data
	 * connection must be closed.  
	 * Current implementation does not do anything. As here data 
	 * transfers are not multi-threaded. 
	 */
	//TODO implement ABOR
	 public void doABOR(FtpRequest request, PrintWriter out) {
	     // reset state variables
	     resetState();
	     //mDataConnection.reset();
	     out.print(FtpResponse.RESPONSE_226_CLOSING_DATA_CONNECTION);
	     return;
	 }

	/**
	 * <code>APPE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to accept the data
	 * transferred via the data connection and to store the data in
	 * a file at the server site.  If the file specified in the
	 * pathname exists at the server site, then the data shall be
	 * appended to that file; otherwise the file specified in the
	 * pathname shall be created at the server site.
	 */
	//TODO implement APPE
	/*
	 public void doAPPE(FtpRequest request, PrintWriter out) {
	    
	     // reset state variables
	     resetState();
	     
	     // argument check
	     if(!request.hasArgument()) {
	        out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
	        return;  
	     }
	     
	     // get filenames
	     String fileName = request.getArgument();
	     fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
	     String physicalName = user.getVirtualDirectory().getPhysicalName(fileName);
	     File requestedFile = new File(physicalName);
	     String args[] = {fileName};
	     
	     // check permission
	     if(!user.getVirtualDirectory().hasWritePermission(physicalName, true)) {
	         out.write(ftpStatus.getResponse(450, request, user, args));
	         return;
	     }
	     
	     // now transfer file data
	     out.write(ftpStatus.getResponse(150, request, user, args));
	     InputStream is = null;
	     OutputStream os = null;
	     try {
	         Socket dataSoc = mDataConnection.getDataSocket();
	         if (dataSoc == null) {
	              out.write(ftpStatus.getResponse(550, request, user, args));
	              return;
	         }
	         
	         is = dataSoc.getInputStream();
	         RandomAccessFile raf = new RandomAccessFile(requestedFile, "rw");
	         raf.seek(raf.length());
	         os = user.getOutputStream( new FileOutputStream(raf.getFD()) );
	         
	         StreamConnector msc = new StreamConnector(is, os);
	         msc.setMaxTransferRate(user.getMaxUploadRate());
	         msc.setObserver(this);
	         msc.connect();
	         
	         if(msc.hasException()) {
	             out.write(ftpStatus.getResponse(451, request, user, args));
	         }
	         else {
	             mConfig.getStatistics().setUpload(requestedFile, user, msc.getTransferredSize());
	         }
	         
	         out.write(ftpStatus.getResponse(226, request, user, args));
	     }
	     catch(IOException ex) {
	         out.write(ftpStatus.getResponse(425, request, user, args));
	     }
	     finally {
	     try {
		 is.close();
		 os.close();
		 mDataConnection.reset(); 
	     } catch(Exception ex) {
		 ex.printStackTrace();
	     }
	     }
	 }
	*/
	public void doAUTH(FtpRequest request, PrintWriter out) {
		//TODO implement AUTH
		out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
		return;
	}
	/**
	 * <code>CDUP &lt;CRLF&gt;</code><br>
	 *
	 * This command is a special case of CWD, and is included to
	 * simplify the implementation of programs for transferring
	 * directory trees between operating systems having different
	 * syntaxes for naming the parent directory.  The reply codes
	 * shall be identical to the reply codes of CWD.      
	 */
	public void doCDUP(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// change directory
		try {
			setCurrentDirectory(currentDirectory.getParentFile());
		} catch (FileNotFoundException ex) {
		}

		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());
		out.print(response);
	}

	/**
	 * <code>CWD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command allows the user to work with a different
	 * directory for file storage or retrieval without
	 * altering his login or accounting information.  Transfer
	 * parameters are similarly unchanged.  The argument is a
	 * pathname specifying a directory.
	 */
	public void doCWD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// get new directory name
		String dirName = "";
		if (request.hasArgument()) {
			dirName = request.getArgument();
		}
		LinkedRemoteFile newCurrentDirectory;
		try {
			newCurrentDirectory = currentDirectory.lookupFile(dirName);
		} catch (FileNotFoundException ex) {
			FtpResponse response = new FtpResponse(550, ex.getMessage());
			out.print(response);
			return;
		}

		if (!newCurrentDirectory.isDirectory()) {
			out.print(new FtpResponse(550, dirName + ": Not a directory"));
			return;
		}
		currentDirectory = newCurrentDirectory;
		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());

		Collection uploaders =
			IRCListener.topFileUploaders2(currentDirectory.getFiles());
		for (Iterator iter = uploaders.iterator(); iter.hasNext();) {
			UploaderPosition stat = (UploaderPosition) iter.next();

			String str1;
			try {
				str1 =
					IRCListener.formatUser(
						userManager.getUserByName(stat.getUsername()));
			} catch (NoSuchUserException e2) {
				continue;
			} catch (IOException e2) {
				logger.log(Level.SEVERE, "Error reading userfile", e2);
				continue;
			}

			response.addComment(
				str1 + " [" + stat.getFiles() + "f/" + stat.getBytes() + "b]");
		}

		out.print(response.toString());
	}

	/**
	 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the file specified in the pathname to be
	 * deleted at the server site.
	 */
	public void doDELE(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			requestedFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.print(
				new FtpResponse(550, "File not found: " + ex.getMessage()));
			return;
		}

		// check permission
		if (!getUser().getUsername().equals(requestedFile.getOwner())
			&& !getUser().isAdmin()) {
			out.print(
				new FtpResponse(
					550,
					"Permission denied. You are neither the owner or an admin."));
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_250_ACTION_OKAY.clone();

		User uploader;
		try {
			uploader = this.userManager.getUserByName(requestedFile.getOwner());
			uploader.updateCredits(
				(long) - (requestedFile.length() * uploader.getRatio()));
		} catch (IOException e) {
			response.addComment("Error removing credits: " + e.getMessage());
		} catch (NoSuchUserException e) {
			response.addComment("Error removing credits: " + e.getMessage());
		}

		// now delete
		//try {
		connManager.dispatchFtpEvent(
			new DirectoryFtpEvent(getUser(), "DELE", requestedFile));
		requestedFile.delete();
		out.print(response);
		//out.write(ftpStatus.getResponse(250, request, user, args));
		//}
		// catch{
		//	out.write(ftpStatus.getResponse(450, request, user, args));
		//}
	}

	/**
	 * <code>HELP [&lt;SP&gt; <string>] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause the server to send helpful
	 * information regarding its implementation status over the
	 * control connection to the user.  The command may take an
	 * argument (e.g., any command name) and return more specific
	 * information as a response.
	 */
	public void doHELP(FtpRequest request, PrintWriter out) {

		// print global help
		//		if (!request.hasArgument()) {
		FtpResponse response = new FtpResponse(214);
		response.addComment("The following commands are recognized.");
		//out.write(ftpStatus.getResponse(214, null, user, null));
		Method methods[] = this.getClass().getDeclaredMethods();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			Class parameterTypes[] = method.getParameterTypes();
			if (parameterTypes.length == 2
				&& parameterTypes[0] == FtpRequest.class
				&& parameterTypes[1] == PrintWriter.class) {
				String commandName =
					method.getName().substring(2).replace('_', ' ');
				response.addComment(commandName);
			}
		}
		out.print(response);
		return;
		//		}
		//
		//		// print command specific help
		//		String ftpCmd = request.getArgument().toUpperCase();
		//		String args[] = null;
		//		FtpRequest tempRequest = new FtpRequest(ftpCmd);
		//		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
		//		return;
	}

	/**
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
	public void doLIST(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

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
		boolean allOption = options.indexOf('a') != -1;
		boolean detailOption = options.indexOf('l') != -1;
		boolean directoryOption = options.indexOf("d") != -1;

		LinkedRemoteFile directoryFile;
		if (directoryName != null) {
			try {
				directoryFile = currentDirectory.lookupFile(directoryName);
			} catch (IOException ex) {
				out.print(new FtpResponse(450, ex.getMessage()));
				return;
			}
		} else {
			directoryFile = currentDirectory;
		}

		out.print(FtpResponse.RESPONSE_150_OK);
		Writer os = null;

		Socket dataSocket;
		try {
			dataSocket = getDataSocket();
		} catch (IOException ex) {
			out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
			return;
		}

		Map listFilesMap = directoryFile.getFilesMap();

		Collection listFiles = new ArrayList(listFilesMap.values());

		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();

		try {
			//			int missing = 0;
			//			int bad = 0;
			//			int good = 0;
			//
			SFVFile sfvfile = directoryFile.lookupSFVFile();
			//			Map sfventries = sfvfile.getEntries();
			//			for (Iterator iter = sfventries.entrySet().iterator();
			//				iter.hasNext();
			//				) {
			//				Map.Entry element = (Map.Entry) iter.next();
			//				String fileName = (String) element.getKey();
			//				Long checksum = (Long) element.getValue();
			//
			//				LinkedRemoteFile file =
			//					(LinkedRemoteFile) listFilesMap.get(fileName);
			//				if (file == null) {
			//					missing++;
			//				} else if (file.getCheckSum() == checksum.longValue()) {
			//					good++;
			//				} else {
			//					bad++;
			//				}
			//			}
			int good = sfvfile.finishedFiles();
			String statusDirName =
				"[" + (good * 100) / sfvfile.size() + "% complete]";
			listFiles.add(
				new DirectoryRemoteFile(
					directoryFile,
					"drftpd",
					"drftpd",
					statusDirName));
		} catch (NoAvailableSlaveException e) {
			logger.log(Level.WARNING, "No available slaves for SFV file");
		} catch (IOException e) {
			logger.log(Level.WARNING, "IO error loading SFV file", e);
		} catch (ObjectNotFoundException e) {
			// no sfv file in directory - just skip it
		}

		try {
			if (mbPort) {
				os = new OutputStreamWriter(dataSocket.getOutputStream());

				try {
					VirtualDirectory.printList(listFiles, os);
				} catch (IOException ex) {
					out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
					return;
				}
				os.flush();
				response.addComment(status());
				out.print(response);
			} else { //mbPasv
				//TODO passive transfer mode
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			reset();
		}
	}
	/**
	 * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 * 
	 * Returns the date and time of when a file was modified.
	 */
	public void doMDTM(FtpRequest request, PrintWriter out) {

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// reset state variables
		resetState();

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile reqFile;
		try {
			reqFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//String physicalName =
		//	user.getVirtualDirectory().getPhysicalName(fileName);
		//File reqFile = new File(physicalName);

		// now print date
		//if (reqFile.exists()) {
		out.print(
			new FtpResponse(
				213,
				DATE_FMT.format(new Date(reqFile.lastModified()))));
		//out.print(ftpStatus.getResponse(213, request, user, args));
		//} else {
		//	out.write(ftpStatus.getResponse(550, request, user, null));
		//}
	}

	/**
	 * <code>MKD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be created as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 * 
	 * 
	 *                MKD
	 *                   257
	 *                   500, 501, 502, 421, 530, 550
	 */
	public void doMKD(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		//String dirName = request.getArgument();
		//if (!VirtualDirectory.isLegalFileName(fileName)) {
		//	out.println(
		//		"553 Requested action not taken. File name not allowed.");
		//	return;
		//}

		Object ret[] =
			currentDirectory.lookupNonExistingFile(request.getArgument());
		LinkedRemoteFile dir = (LinkedRemoteFile) ret[0];
		String createdDirName = (String) ret[1];

		if (createdDirName == null) {
			out.print(
				new FtpResponse(
					550,
					"Requested action not taken. "
						+ createdDirName
						+ " exists"));
			return;
		}

		if (!VirtualDirectory.isLegalFileName(createdDirName)) {
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}

		try {
			LinkedRemoteFile createdDir =
				dir.createDirectory(
					getUser().getUsername(),
					getUser().getGroup(),
					createdDirName);
			out.print(
				new FtpResponse(
					257,
					"\"" + createdDir.getPath() + "\" created."));

			connManager.dispatchFtpEvent(
				new DirectoryFtpEvent(getUser(), "MKD", createdDir));
			return;
		} catch (ObjectExistsException ex) {
			out.println("550 directory " + createdDirName + " already exists");
			return;
		}

		// check permission
		//		if (!getVirtualDirectory().hasCreatePermission(physicalName, true)) {
		//			out.write(ftpStatus.getResponse(450, request, user, args));
		//			return;
		//		}
	}

	/**
	 * <code>MODE &lt;SP&gt; <mode-code> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * the data transfer modes described in the Section on
	 * Transmission Modes.
	 */
	public void doMODE(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (request.getArgument().equalsIgnoreCase("S")) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
	}

	/**
	 * <code>NLST [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command causes a directory listing to be sent from
	 * server to user site.  The pathname should specify a
	 * directory or other system-specific file group descriptor; a
	 * null argument implies the current directory.  The server
	 * will return a stream of names of files and no other
	 * information.
	 */
	public void doNLST(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		String directoryName = "./";
		String options = "";
		//String pattern = "*";
		String argument = request.getArgument();

		// get options, directory name and pattern
		if (argument != null) {
			argument = argument.trim();
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
		boolean bAll = options.indexOf('a') != -1;
		boolean bDetail = options.indexOf('l') != -1;

		LinkedRemoteFile directoryFile;
		if (directoryName != null) {
			try {
				directoryFile = currentDirectory.lookupFile(directoryName);
			} catch (IOException ex) {
				out.print(new FtpResponse(450, ex.getMessage()));
				return;
			}
		} else {
			directoryFile = currentDirectory;
		}

		out.print(FtpResponse.RESPONSE_150_OK);
		Writer os = null;
		try {
			Socket dataSocket;
			try {
				dataSocket = getDataSocket();
			} catch (IOException ex) {
				out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
				return;
			}

			if (mbPort) {
				os = new OutputStreamWriter(dataSocket.getOutputStream());

				try {
					VirtualDirectory.printNList(
						directoryFile.getFiles(),
						bDetail,
						os);
				} catch (IOException ex) {
					out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
					return;
				}
				os.flush();
				FtpResponse response =
					(FtpResponse) (FtpResponse
						.RESPONSE_226_CLOSING_DATA_CONNECTION)
						.clone();
				response.addComment(status());
				out.print(response);
			} else { //mbPasv
				//TODO passive transfer mode
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			out.print(FtpResponse.RESPONSE_425_CANT_OPEN_DATA_CONNECTION);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			reset();
		}
		//
		//		 
		//		 out.print(FtpResponse.RESPONSE_150_OK);
		//		 Writer os = null;
		//		 try {
		//		     Socket dataSoc = mDataConnection.getDataSocket();
		//		     if (dataSoc == null) {
		//		          out.write(ftpStatus.getResponse(550, request, user, null));
		//		          return;
		//		     }
		//		     
		//		     os = new OutputStreamWriter(dataSoc.getOutputStream());
		//		     
		//		     if (!VirtualDirectory.printNList(request.getArgument(), os)) {
		//		         out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		//		     }
		//		     else {
		//		        os.flush();
		//		        out.write(ftpStatus.getResponse(226, request, user, null));
		//		     }
		//		 }
		//		 catch(IOException ex) {
		//		     out.write(ftpStatus.getResponse(425, request, user, null));
		//		 }
		//		 finally {
		//		 try {
		//		 os.close();
		//		 } catch(Exception ex) {
		//		 e.printStackTrace();
		//		 }
		//		     mDataConnection.reset();
		//		 }
	}

	/**
	 * <code>NOOP &lt;CRLF&gt;</code><br>
	 *
	 * This command does not affect any parameters or previously
	 * entered commands. It specifies no action other than that the
	 * server send an OK reply.
	 */
	public void doNOOP(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}

	/**
	 * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string specifying the user's
	 * password.  This command must be immediately preceded by the
	 * user name command.
	 */
	public void doPASS(FtpRequest request, PrintWriter out) {

		// set state variables

		if (getUser() == null) {
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			resetState();
			return;
		}
		resetState();
		//		mbPass = true;

		// set user password and login
		String pass = request.hasArgument() ? request.getArgument() : "";

		// login failure - close connection
		if (getUser().checkPassword(pass)) {
			FtpResponse response =
				(FtpResponse) FtpResponse.RESPONSE_230_USER_LOGGED_IN.clone();
			try {
				response.addComment(
					new BufferedReader(
						new FileReader("ftp-data/text/welcome.txt")));
			} catch (IOException e) {
				logger.log(
					Level.WARNING,
					"Error reading ftp-data/text/welcome.txt",
					e);
			}
			authenticated = true;
			out.print(response);
			connManager.dispatchFtpEvent(new UserEvent(getUser(), "LOGIN"));
		} else {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			/*
			ConnectionService conService = mConfig.getConnectionService();
			if (conService != null) {
			    conService.closeConnection(user.getSessionId());
			}
			*/
		}
	}

	/**
	 * <code>PASV &lt;CRLF&gt;</code><br>
	 *
	 * This command requests the server-DTP to "listen" on a data
	 * port (which is not its default data port) and to wait for a
	 * connection rather than initiate one upon receipt of a
	 * transfer command.  The response to this command includes the
	 * host and port address this server is listening on.
	 */
	public void doPASV(FtpRequest request, PrintWriter out) {
		out.print(
			new FtpResponse(500, "Sorry, no PASV for distributed FTP")
				.toString());
		/*
		 if (!setPasvCommand()) {
		     out.write(ftpStatus.getResponse(550, request, user, null));
		     return;   
		 }
		 
		 InetAddress servAddr = getInetAddress();
		
		 if(servAddr == null) {
		     //servAddr = mConfig.getSelfAddress();
		 //servAddr = InetAddress.getLocalHost();
		 servAddr = mControlSocket.getLocalAddress();
		 }        
		
		 int servPort = getPort();
		
		 String addrStr = servAddr.getHostAddress().replace( '.', ',' ) + ',' + (servPort>>8) + ',' + (servPort&0xFF);
		 String[] args = {addrStr};
		 
		 out.write(ftpStatus.getResponse(227, request, user, args));
		 if (!listenPasvConnection()) {
		    out.write(ftpStatus.getResponse(425, request, user, args));
		 }
		*/
	}

	/**
	 * <code>PORT &lt;SP&gt; <host-port> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a HOST-PORT specification for the data port
	 * to be used in data connection.  There are defaults for both
	 * the user and server data ports, and under normal
	 * circumstances this command and its reply are not needed.  If
	 * this command is used, the argument is the concatenation of a
	 * 32-bit internet host address and a 16-bit TCP port address.
	 * This address information is broken into 8-bit fields and the
	 * value of each field is transmitted as a decimal number (in
	 * character string representation).  The fields are separated
	 * by commas.  A port command would be:
	 *
	 *   PORT h1,h2,h3,h4,p1,p2
	 * 
	 * where h1 is the high order 8 bits of the internet host address.
	 */
	public void doPORT(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		InetAddress clientAddr = null;
		int clientPort = 0;

		// argument check
		if (!request.hasArgument()) {
			//Syntax error in parameters or arguments
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");
		if (st.countTokens() != 6) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get data server
		String dataSrvName =
			st.nextToken()
				+ '.'
				+ st.nextToken()
				+ '.'
				+ st.nextToken()
				+ '.'
				+ st.nextToken();
		try {
			clientAddr = InetAddress.getByName(dataSrvName);
		} catch (UnknownHostException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			//out.write(ftpStatus.getResponse(552, request, user, null));
			return;
		}
		setPortCommand(clientAddr, clientPort);
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}

	/**
	 * <code>PWD  &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the name of the current working
	 * directory to be returned in the reply.
	 */
	public void doPWD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();
		out.print(
			new FtpResponse(
				257,
				"\"" + currentDirectory.getPath() + "\" is current directory"));
	}

	/**
	 * <code>QUIT &lt;CRLF&gt;</code><br>
	 *
	 * This command terminates a USER and if file transfer is not
	 * in progress, the server closes the control connection.
	 */
	public void doQUIT(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// and exit
		//out.write(ftpStatus.getResponse(221, request, user, null));
		FtpResponse response = new FtpResponse(221, "Goodbye");
		out.print(response.toString());
		stop();
		/*
		    ConnectionService conService = mConfig.getConnectionService();
		    if (conService != null) {
		        conService.closeConnection(user.getSessionId());
		    }
		*/
	}

	/**
	 * <code>REST &lt;SP&gt; <marker> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field represents the server marker at which
	 * file transfer is to be restarted.  This command does not
	 * cause file transfer but skips over the file to the specified
	 * data checkpoint.  This command shall be immediately followed
	 * by the appropriate FTP service command which shall cause
	 * file transfer to resume.
	 */
	public void doREST(FtpRequest request, PrintWriter out) {
		//TODO test REST
		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variables
		resetState();

		String skipNum = request.getArgument();
		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (resumePosition < 0) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			resumePosition = 0;
			return;
		}
		out.print(FtpResponse.RESPONSE_350_PENDING_FURTHER_INFORMATION);
	}

	/**
	 * <code>RETR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to transfer a copy of the
	 * file, specified in the pathname, to the server- or user-DTP
	 * at the other end of the data connection.  The status and
	 * contents of the file at the server site shall be unaffected.
	 * 
	 *                RETR
	 *                   125, 150
	 *                      (110)
	 *                      226, 250
	 *                      425, 426, 451
	 *                   450, 550
	 *                   500, 501, 421, 530
	 */

	public void doRETR(FtpRequest request, PrintWriter out) {
		// set state variables
		long resumePosition = this.resumePosition;
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile remoteFile;
		RemoteFile staticRemoteFile;
		try {
			remoteFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.println("550 " + fileName + ": No such file");
			return;
		}
		if (!remoteFile.isFile()) {
			out.println("550 " + remoteFile + ": not a plain file.");
			return;
		}
		if (getUser().getRatio() != 0
			&& getUser().getCredits() < remoteFile.length()) {
			out.println("550 Not enough credits.");
			return;
		}
		/*
		// check permission
		if(!user.getVirtualDirectory().hasReadPermission(physicalName, true)) {
		    out.write(ftpStatus.getResponse(550, request, user, args));
		    return;
		}
		*/
		//Transfer transfer;
		RemoteSlave rslave;

		try {
			rslave = remoteFile.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD);
		} catch (NoAvailableSlaveException ex) {
			out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
			return;
		}

		// get socket depending on the selection
		if (mbPort) {
			try {
				transfer =
					rslave.getSlave().doConnectSend(
						remoteFile.getPath(),
						getType(),
						resumePosition,
						mAddress,
						miPort);
			} catch (RemoteException ex) {
				rslave.handleRemoteException(ex);
				out.print(
					new FtpResponse(450, "Remote error: " + ex.getMessage()));
				return;
			} catch (FileNotFoundException ex) {
				out.print(
					new FtpResponse(
						450,
						"File not found on slave: " + ex.getMessage()));
				logger.log(
					Level.SEVERE,
					"File not found on slave, [rslave=" + rslave + "]",
					ex);
				return;
			} catch (IOException ex) {
				out.print(
					new FtpResponse(
						450,
						ex.getClass().getName()
							+ " from slave: "
							+ ex.getMessage()));
				logger.log(Level.SEVERE, "rslave=" + rslave, ex);
				return;
			}
		} else {
			out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
			return;
		}
		out.print(new FtpResponse(150, "File status okay; about to open data connection from "+rslave.getName()+"."));
		out.flush();

		try {
			transfer.transfer();
		} catch (RemoteException ex) {
			rslave.handleRemoteException(ex);
			out.print(FtpResponse.RESPONSE_426_CONNECTION_CLOSED_TRANSFER_ABORTED);
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

//		TransferThread transferThread = new TransferThread(rslave, transfer);
//		System.err.println("Calling interruptibleSleepUntilFinished");
//		try {
//			transferThread.interruptibleSleepUntilFinished();
//		} catch (Throwable e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		System.err.println("Finished");
		
		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();

		try {
			long transferedBytes = transfer.getTransfered();
			User user = getUser();
			if (user.getRatio() != 0) {
				user.updateCredits(-transferedBytes);
			}
			user.updateDownloadedBytes(transferedBytes);
			user.commit();
		} catch (RemoteException ex) {
			rslave.handleRemoteException(ex);
		} catch (UserFileException e) {

		}
		out.print(response);
		reset();
	}

	/**
	 * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be removed as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 */
	public void doRMD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get file names
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			requestedFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException e) {
			out.print(new FtpResponse(550, fileName + ": " + e.getMessage()));
			return;
		}

		if (!requestedFile.isDirectory()) {
			out.print(new FtpResponse(550, fileName + ": Not a directory"));
			return;
		}
		if (requestedFile.dirSize() != 0) {
			out.print(new FtpResponse(550, fileName + ": Directory not empty"));
			return;
		}
		// check permission
		//if(!user.getVirtualDirectory().hasWritePermission(physicalName, true)) {
		//	out.write(ftpStatus.getResponse(450, request, user, args));
		//	return;
		//}

		// now delete
		connManager.dispatchFtpEvent(
			new DirectoryFtpEvent(getUser(), "RMD", requestedFile));
		requestedFile.delete();
		out.print(FtpResponse.RESPONSE_250_ACTION_OKAY);
	}

	/**
	 * <code>RNFR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the old pathname of the file which is
	 * to be renamed.  This command must be immediately followed by
	 * a "rename to" command specifying the new file pathname.
	 */
	public void doRNFR(FtpRequest request, PrintWriter out) {
		//out.print(new FtpResponse(500, "Command not implemented").toString());

		// reset state variable
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variable

		// get filenames
		//String fileName = request.getArgument();
		//fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		//mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);

		try {
			mstRenFr = currentDirectory.lookupFile(request.getArgument());
		} catch (FileNotFoundException e) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			resetState();
			return;
		}

		out.print(
			new FtpResponse(350, "File exists, ready for destination name"));
		//out.write(ftpStatus.getResponse(350, request, user, args));

	}

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	public void doRNTO(FtpRequest request, PrintWriter out) {
		//out.print(new FtpResponse(500, "Command not implemented").toString());

		// argument check
		if (!request.hasArgument()) {
			resetState();
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// set state variables
		if (mstRenFr == null) {
			resetState();
			out.print(FtpResponse.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS);
			return;
		}

		// get filenames
		//String fromFileStr = user.getVirtualDirectory().getVirtualName(mstRenFr);
		//String toFileStr = request.getArgument();
		//toFileStr = user.getVirtualDirectory().getAbsoluteName(toFileStr);
		//String physicalToFileStr = user.getVirtualDirectory().getPhysicalName(toFileStr);
		//File fromFile = new File(mstRenFr);
		//File toFile = new File(physicalToFileStr);
		//String args[] = {fromFileStr, toFileStr};

		String to = currentDirectory.lookupPath(request.getArgument());
		logger.info("argument = " + request.getArgument());
		logger.info("to = " + to);

		LinkedRemoteFile fromFile = mstRenFr;
		resetState();

		// check permission
		//if(!user.getVirtualDirectory().hasCreatePermission(physicalToFileStr, true)) {
		//   out.write(ftpStatus.getResponse(553, request, user, null));
		//   return;
		//}

		// now rename
		//if(fromFile.renameTo(toFile)) {
		//    out.write(ftpStatus.getResponse(250, request, user, args));
		//}
		logger.info("mstRenFr = " + mstRenFr);
		try {
			fromFile.renameTo(to);
		} catch (ObjectExistsException e) {
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			e.printStackTrace();
			return;
		} catch (IllegalTargetException e) {
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			e.printStackTrace();
			return;
		}

		//out.write(FtpResponse.RESPONSE_250_ACTION_OKAY.toString());
		FtpResponse response =
			new FtpResponse(
				250,
				request.getCommand() + " command successfull.");
		out.print(response);
	}

	public void doSITE_ADDIP(FtpRequest request, PrintWriter out) {
		resetState();

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.println("200 USAGE: SITE ADDIP <username> <ident@ip>");
			return;
		}
		FtpResponse response = new FtpResponse(200);

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
			response.addComment("Adding masks");
			for (int i = 1; i < args.length; i++) {
				String string = args[i];
				try {
					myUser.addIPMask(string);
					response.addComment(
						"Added " + string + " to " + myUser.getUsername());
				} catch (DuplicateElementException e) {
					response.addComment(
						string + " already added to " + myUser.getUsername());
				}
			}
			myUser.commit(); // throws UserFileException
			//userManager.save(user2);
		} catch (NoSuchUserException ex) {
			out.println("200 No such user: " + args[0]);
			return;
		} catch (UserFileException ex) {
			response.addComment(ex.getMessage());
			out.print(response);
			return;
		} catch (IOException ex) {
			out.print(new FtpResponse(200, "IO Error: " + ex.getMessage()));
			return;
		}
		out.print(response);
		return;
	}

	/**
	 * USAGE: site adduser <user> <password> [<ident@ip#1> ... <ident@ip#5>]
	 *	Adds a user. You can have wild cards for users that have dynamic ips
	 *	Examples: *@192.168.1.* , frank@192.168.*.* , bob@192.*.*.*
	 *	(*@192.168.1.1[5-9] will allow only 192.168.1.15-19 to connect but no one else)
	 *
	 *	If a user is added by a groupadmin, that user will have the GLOCK
	 *	flag enabled and will inherit the groupadmin's home directory.
	 *	
	 *	All default values for the user are read from file default.user in
	 *	/glftpd/ftp-data/users. Comments inside describe what is what.
	 *	Gadmins can be assigned their own default.<group> userfiles
	 *	as templates to be used when they add a user, if one is not found,
	 *	default.user will be used.
	 *	default.groupname files will also be used for "site gadduser".
	 *
	 *	ex. site ADDUSER Archimede mypassword 
	 *
	 *	This would add the user 'Archimede' with the password 'mypassword'.
	 *
	 *	ex. site ADDUSER Archimede mypassword *@127.0.0.1
	 *	
	 *	This would do the same as above + add the ip '*@127.0.0.1' at the
	 *	same time.
	 *
	 *	HOMEDIRS:
	 *	After login, the user will automatically be transferred into his/her
	 *	homedir. As of 1.16.x this dir is now "kinda" chroot'ed and they are
	 *	now unable to "cd ..".
	 *
	 *
	 * @param request
	 * @param out
	 */
	public void doSITE_ADDUSER(FtpRequest request, PrintWriter out) {
		resetState();

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String newUsername = args[0];
		String pass = args[1];
		User newUser;
		FtpResponse response = new FtpResponse(200);
		response.addComment(newUsername + " created");
		try {
			newUser = userManager.create(newUsername);
			newUser.setPassword(pass);
			newUser.setComment("Added by " + getUser().getUsername());

			for (int i = 2; i < args.length; i++) {
				String string = args[i];
				try {
					newUser.addIPMask(string);
					response.addComment("Added IP mask " + string);
				} catch (DuplicateElementException e1) {
					response.addComment("IP mask " + string + "already added");
				}
			}
			newUser.commit();
		} catch (UserFileException e) {
			response.addComment(e.getMessage());
		}
		out.print(response);
	}

	/**
	 * USAGE: site change <user> <field> <value> - change a field for a user
	   site change =<group> <field> <value> - change a field for each member of group <group>
	   site change { <user1> <user2> .. } <field> <value> - change a field for each user in the list
	   site change * <field> <value>     - change a field for everyone
	
	Type "site change user help" in glftpd for syntax.
	
	Fields available:
	
	Field			Description
	-------------------------------------------------------------
	ratio		Upload/Download ratio. 0 = Unlimited (Leech)
	wkly_allotment 	The number of kilobytes that this user will be given once a week
			(you need the reset binary enabled in your crontab).
			Syntax: site change user wkly_allotment "#,###"
			The first number is the section number (0=default section),
			the second is the number of kilobytes to give.
			(user's credits are replaced, not added to, with this value)
			Only one section at a time is supported,
	homedir		This will change the user's homedir.
			NOTE: This command is disabled by default.  To enable it, add
			"min_homedir /site" to your config file, where "/site" is the
			minimum directory that users can have, i.e. you can't change
			a user's home directory to /ftp-data or anything that doesn't
			have "/site" at the beginning.
			Important: don't use a trailing slash for homedir!
			Users CAN NOT cd, list, upload/download, etc, outside of their
	                    home dir. It acts similarly to chroot() (try man chroot).
	startup_dir	The directory to start in. ex: /incoming will start the user
			in /glftpd/site/incoming if rootpath is /glftpd and homedir is /site.
			Users CAN cd, list, upload/download, etc, outside of startup_dir.
	idle_time	Sets the default and maximum idle time for this user (overrides
			the -t and -T settings on glftpd command line). If -1, it is disabled;
			if 0, it is the same as the idler flag.
	credits		Credits left to download.
	flags		+1ABC or +H or -3, type "site flags" for a list of flags.
	num_logins	# # : number of simultaneous logins allowed. The second
			number is number of sim. logins from the same IP.
	timeframe	# # : the hour from which to allow logins and the hour when logins from
	    		this user will start being rejected. This is set in a 24 hour format.
			If a user is online past his timeframe, he'll be disconnected the
			next time he does a 'CWD'.
	time_limit	Time limits, per LOGIN SESSION. (set in minutes. 0 = Unlimited)
	tagline		User's tagline.
	group_slots	Number of users a GADMIN is allowed to add.
			If you specify a second argument, it will be the
			number of leech accounts the gadmin can give (done by
			"site change user ratio 0") (2nd arg = leech slots)
	comment		Changes the user's comment (max 50 characters).
			Comments are displayed by the comment cookie (see below).
	max_dlspeed	Downstream bandwidth control (KBytes/sec) (0 = Unlimited)
	max_ulspeed	Same but for uploads
	max_sim_down	Maximum number of simultaneous downloads for this user
			(-1 = unlimited, 0 = zero [user can't download])
	max_sim_up	Maximum number of simultaneous uploads for this user
			(-1 = unlimited, 0 = zero [user can't upload])
	sratio		<SECTIONNAME> <#>
			This is to change the ratio of a section (other than default).
	
	Flags available:
	
	Flagname       	Flag	Description
	-------------------------------------------------------------
	SITEOP		1	User is siteop.
	GADMIN		2	User is Groupadmin of his/her first public
				group (doesn't work for private groups).
	GLOCK		3	User cannot change group.
	EXEMPT		4	Allows to log in when site is full. Also allows
				user to do "site idle 0", which is the same as
				having the idler flag. Also exempts the user
				from the sim_xfers limit in config file.
	COLOR		5	Enable/Disable the use of color (toggle with "site color").
	DELETED		6	User is deleted.
	USEREDIT	7	"Co-Siteop"
	ANON		8	User is anonymous (per-session like login).
	
	*NOTE* The 1 flag is not GOD mode, you must have the correct flags for the actions you wish to perform.
	*NOTE* If you have flag 1 then you DO NOT WANT flag 2
	
	Restrictions placed on users flagged ANONYMOUS.
		1.  '!' on login is ignored.
		2.  They cannot DELETE, RMDIR, or RENAME. 
		3.  Userfiles do not update like usual, meaning no stats will
	    	    be kept for these users.  The userfile only serves as a template for the starting 
		    environment of the logged in user. 
	    	    Use external scripts if you must keep records of their transfer stats.
	
	NUKE		A	User is allowed to use site NUKE.
	UNNUKE		B	User is allowed to use site UNNUKE.
	UNDUPE		C	User is allowed to use site UNDUPE.
	KICK		D	User is allowed to use site KICK.
	KILL		E	User is allowed to use site KILL/SWHO.
	TAKE		F	User is allowed to use site TAKE.
	GIVE		G	User is allowed to use site GIVE.
	USERS/USER	H	This allows you to view users ( site USER/USERS )
	IDLER		I	User is allowed to idle forever.
	CUSTOM1		J	Custom flag 1
	CUSTOM2		K	Custom flag 2
	CUSTOM3		L	Custom flag 3
	CUSTOM4		M	Custom flag 4
	CUSTOM5		N	Custom flag 5
	
	You can use custom flags in the config file to give some users access
	to certain things without having to use private groups.  These flags
	will only show up in "site flags" if they're turned on.
	
	ex. site change Archimede ratio 5
	
	This would set the ratio to 1:5 for the user 'Archimede'.
	
	ex. site change Archimede flags +2-AG
	
	This would make the user 'Archimede' groupadmin and remove his ability
	to use the commands site nuke and site give.
	
	NOTE: The flag DELETED can not be changed with site change, it
	      will change when someone does a site deluser/readd.
	 */
	public void doSITE_CHANGE(FtpRequest request, PrintWriter out) {
		final FtpResponse usageResponse =
			(FtpResponse) FtpResponse.RESPONSE_501_SYNTAX_ERROR.clone();
		usageResponse.addComment(
			"Valid fields: ratio idle_time credits num_logins_tot num_logins_ip");
		usageResponse.addComment(
			"              tagline max_sim_down max_sim_up");

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(usageResponse);
			return;
		}

		String command, commandArgument;
		User myUser;
		{
			String argument = request.getArgument();
			int pos1 = argument.indexOf(' ');
			if (pos1 == -1) {
				out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
				return;
			}
			String username = argument.substring(0, pos1);
			try {
				myUser = userManager.getUserByName(argument.substring(0, pos1));
			} catch (NoSuchUserException e) {
				out.print(
					new FtpResponse(
						550,
						"User " + username + " found: " + e.getMessage()));
				return;
			} catch (IOException e) {
				out.print(
					new FtpResponse(
						550,
						"Error loading user: " + e.getMessage()));
				logger.log(Level.WARNING, "Error loading user", e);
				return;
			}

			int pos2 = argument.indexOf(' ', pos1 + 1);
			if (pos2 == -1) {
				out.print(usageResponse);
				return;
			}
			command = argument.substring(pos1 + 1, pos2);
			commandArgument = argument.substring(pos2 + 1);
		}

		//		String args[] = request.getArgument().split(" ");
		//		String command = args[1].toLowerCase();
		// 0 = user
		// 1 = command
		// 2- = argument
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		if (command == null) {
			response.addComment(
				"Valid fields: ratio idle_time credits num_logins_tot num_logins_ip");
			response.addComment(
				"              tagline max_sim_down max_sim_up");
			return;
		} else if ("credits".equalsIgnoreCase(command)) {
			myUser.setCredits(Long.parseLong(command));
		} else if ("ratio".equalsIgnoreCase(command)) {
			myUser.setRatio(Float.parseFloat(commandArgument));
		} else if ("comment".equalsIgnoreCase(command)) {
			myUser.setComment(commandArgument);
		} else if ("idle_time".equalsIgnoreCase(command)) {
			myUser.setIdleTime(Integer.parseInt(commandArgument));
		} else if ("num_logins_tot".equalsIgnoreCase(command)) {
			myUser.setMaxLogins(Integer.parseInt(commandArgument));
		} else if ("num_logins_ip".equalsIgnoreCase(command)) {
			myUser.setMaxLoginsPerIP(Integer.parseInt(commandArgument));
		} else if ("max_dlspeed".equalsIgnoreCase(command)) {
			myUser.setRatio(Long.parseLong(commandArgument));
		} else if ("max_ulspeed".equals(command)) {
			myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
		}
		try {
			myUser.commit();
		} catch (UserFileException e) {
			response.addComment(e.getMessage());
		}
		out.print(response);
		return;
	}

	public void doSITE_CHECKSLAVES(FtpRequest request, PrintWriter out) {
		resetState();
		out.println(
			"200 Ok, " + slaveManager.verifySlaves() + " stale slaves removed");
	}

	public void doSITE_CHPASS(FtpRequest request, PrintWriter out) {
		resetState();

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

	}

	/**
	 * USAGE: site chgrp <user> <group> [<group>]
	    Adds/removes a user from group(s).
	
	    ex. site chgrp archimede ftp
	    This would change the group to 'ftp' for the user 'archimede'.
	
	    ex1. site chgrp archimede ftp
	    This would remove the group ftp from the user 'archimede'.
	
	    ex2. site chgrp archimede ftp eleet
	    This moves archimede from ftp group to eleet group.
	    
	 * @param request
	 * @param out
	 */
	public void doSITE_CHGRP(FtpRequest request, PrintWriter out) {
		resetState();

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
		} catch (NoSuchUserException e) {
			out.print(
				new FtpResponse(200, "User not found: " + e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.SEVERE, "IO error reading user", e);
			out.print(
				new FtpResponse(
					200,
					"IO error reading user: " + e.getMessage()));
			return;
		}

		FtpResponse response = new FtpResponse(200);
		for (int i = 1; i < args.length; i++) {
			String string = args[i];
			try {
				myUser.removeGroup(string);
				response.addComment(
					myUser.getUsername()+" removed from group "+string);
			} catch (NoSuchFieldException e1) {
				try {
					myUser.addGroup(string);
				} catch (DuplicateElementException e2) {
					logger.log(
						Level.SEVERE,
						"Error, user was not a member before",
						e2);
				}
				response.addComment(
					myUser.getUsername() + " added to group" + string);
			}
		}
		out.print(response);
		return;
	}

	/**
	 * USAGE: site delip <user> <ident@ip> ...
	 * @param request
	 * @param out
	 */
	public void doSITE_DELIP(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");

		System.out.println(args.length);
		if (args.length < 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(args[0]);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.SEVERE, "IO error", e.getMessage());
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}

		FtpResponse response = new FtpResponse(200);
		for (int i = 1; i < args.length; i++) {
			String string = args[i];
			try {
				myUser.removeIpMask(string);
				response.addComment("Removed " + string);
			} catch (NoSuchFieldException e1) {
				response.addComment(
					"Mask " + string + " not found: " + e1.getMessage());
				continue;
			}
		}
		out.print(response);
		return;
	}

	public void doSITE_DELUSER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String delUsername = request.getArgument();
		User delUser;
		try {
			delUser = this.userManager.getUserByName(delUsername);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(
				new FtpResponse(200, "Couldn't getUser: " + e.getMessage()));
			return;
		}

		delUser.setDeleted(true);
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}

	public void doSITE_GROUPS(FtpRequest request, PrintWriter out) {
		resetState();

		Collection groups;
		try {
			groups = userManager.getAllGroups();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "IO error from getAllGroups()", e);
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}
		FtpResponse response = new FtpResponse(200);
		response.addComment("All groups:");
		for (Iterator iter = groups.iterator(); iter.hasNext();) {
			String element = (String) iter.next();
			response.addComment(element);
		}

		out.print(response);
		return;
	}
	public void doSITE_KICK(FtpRequest request, PrintWriter out) {
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		String arg = request.getArgument();
		int pos = arg.indexOf(' ');

		String username;
		String message = "Kicked by " + getUser().getUsername();
		if (pos == -1) {
			username = arg;
		} else {
			username = arg.substring(0, pos);
			message = arg.substring(pos + 1);
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();

		for (Iterator iter = connManager.getConnections().iterator();
			iter.hasNext();
			) {
			BaseFtpConnection conn = (BaseFtpConnection) iter.next();
			if (conn.getUser().getUsername().equals(username)) {
				conn.stop(message);
			}
		}
		out.print(response);
		return;
	}

	public void doSITE_LIST(FtpRequest request, PrintWriter out) {
		resetState();
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		Map files = currentDirectory.getMap();
		for (Iterator iter = files.keySet().iterator(); iter.hasNext();) {
			String key = (String) iter.next();
			LinkedRemoteFile file = (LinkedRemoteFile) files.get(key);
			//response.addComment(key);
			response.addComment(file.toString());
		}
		out.print(response);
	}

	/**
	 * USAGE: site nuke <directory> <multiplier> <message>
	 * Nuke a directory
	 *
	 * ex. site nuke shit 2 CRAP
	 *
	 * This will nuke the directory 'shit' and remove x2 credits with the
	 * comment 'CRAP'.
	 *
	 * NOTE: You can enclose the directory in braces if you have spaces in the name
	 * ex. site NUKE {My directory name} 1 because_i_dont_like_it
	 * 
	 * Q)  What does the multiplier in 'site nuke' do?
	 * A)  Multiplier is a penalty measure. If it is 0, the user doesn't lose any
	 *     credits for the stuff being nuked. If it is 1, user only loses the
	 *     amount of credits he gained by uploading the files (which is calculated
	 *     by multiplying total size of file by his/her ratio). If multiplier is more
	 *     than 1, the user loses the credits he/she gained by uploading, PLUS some
	 *     extra credits. The formula is this: size * ratio + size * (multiplier - 1).
	 *     This way, multiplier of 2 causes user to lose size * ratio + size * 1,
	 *     so the additional penalty in this case is the size of nuked files. If the
	 *     multiplier is 3, user loses size * ratio + size * 2, etc.
	 */
	public void doSITE_NUKE(FtpRequest request, PrintWriter out) {
		//FtpResponse response = new FtpResponse();
		LinkedRemoteFile thisDir = currentDirectory;
		LinkedRemoteFile nukeDir;
		String arg = request.getArgument();
		int pos;
		int multiplier;
		String reason = "";

		String nukeDirname;
		String multiplierString;

		if (arg.charAt(0) == '{') {
			pos = arg.indexOf('}');
			nukeDirname = arg.substring(1, pos);
			pos += 1; // set pos to space
		} else {
			pos = arg.indexOf(' ');
			if (pos == -1) {
				//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
				out.print(new FtpResponse(501, "multiplier missing"));
				return;
			}
			nukeDirname = arg.substring(0, pos);
		}

		try {
			multiplier = Integer.parseInt(arg.substring(pos + 1));
		} catch (NumberFormatException ex) {
			ex.printStackTrace();
			out.print(
				new FtpResponse(501, "Invalid multiplier: " + ex.getMessage()));
			return;
		}

		try {
			nukeDir = thisDir.getFile(nukeDirname);
		} catch (FileNotFoundException e) {
			FtpResponse response = new FtpResponse(550, e.getMessage());
			out.print(response.toString());
			return;
		}

		if (!nukeDir.isDirectory()) {
			FtpResponse response =
				new FtpResponse(550, nukeDir.getName() + " is not a directory");
			out.print(response.toString());
			return;
		}

		Hashtable nukees = new Hashtable();
		nukeRemoveCredits(nukeDir, nukees);

		FtpResponse response = new FtpResponse(200, "NUKE suceeded");
		Hashtable nukees2 = new Hashtable(nukees.size());

		for (Iterator iter = nukees.keySet().iterator(); iter.hasNext();) {

			String username = (String) iter.next();
			User user;
			try {
				user = userManager.getUserByName(username);
			} catch (NoSuchUserException e1) {
				response.addComment(
					"Cannot remove credits from "
						+ username
						+ ": "
						+ e1.getMessage());
				e1.printStackTrace();
				user = null;
			} catch (IOException e1) {
				response.addComment(
					"Cannot read user data for "
						+ username
						+ ": "
						+ e1.getMessage());
				e1.printStackTrace();
				response.setMessage("NUKE failed");
				out.print(response);
				return;
			}
			// nukees contains credits as value
			if (user == null) {
				Long add = (Long) nukees2.get(null);
				if (add == null) {
					add = new Long(0);
				}
				nukees2.put(
					user,
					new Long(
						add.longValue()
							+ ((Long) nukees.get(username)).longValue()));
			} else {
				nukees2.put(user, nukees.get(username));
			}
		}
		String preNukePath = nukeDir.getPath();
		String to;
		try {
			to =
				nukeDir.getParentFile().getPath()
					+ "[NUKED]-"
					+ nukeDir.getName();
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
			out.print(FtpResponse.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		try {
			nukeDir.renameTo(to);
		} catch (IOException e1) {
			e1.printStackTrace();
			response.addComment(
				" cannot rename to \"" + to + "\": " + e1.getMessage());
			response.setCode(500);
			response.setMessage("NUKE failed");
			out.print(response);
			return;
		}

		for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
			AbstractUser nukee = (AbstractUser) iter.next();
			if (nukee == null)
				continue;
			Long size = (Long) nukees2.get(nukee);
			Long debt =
				new Long(
					(long) (size.longValue() * nukee.getRatio()
						+ size.longValue() * (multiplier - 1)));
			nukee.updateCredits(-debt.longValue());
			nukee.updateNukedBytes(debt.longValue());
			nukee.updateTimesNuked(1);
			nukee.setLastNuked(System.currentTimeMillis());
			try {
				nukee.commit();
			} catch (UserFileException e1) {
				response.addComment(
					"Error writing userfile: " + e1.getMessage());
				logger.log(Level.WARNING, "Error writing userfile", e1);
			}
			response.addComment(nukee.getUsername() + " -" + debt + " bytes");
		}
		NukeEvent nuke =
			new NukeEvent(
				getUser(),
				request.getCommand(),
				preNukePath,
				multiplier,
				reason,
				nukees);
		this.nukelog.add(nuke);
		connManager.dispatchFtpEvent(nuke);
		out.print(response);
	}

	private static void nukeRemoveCredits(
		LinkedRemoteFile nukeDir,
		Hashtable nukees) {
		for (Iterator iter = nukeDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String owner = file.getOwner();
			Long total = (Long) nukees.get(owner);
			if (total == null)
				total = new Long(0);
			total = new Long(total.longValue() + file.length());
			nukees.put(owner, total);
		}
	}

	public void doSITE_NUKES(FtpRequest request, PrintWriter out) {
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		for (Iterator iter = nukelog.getAll().iterator(); iter.hasNext();) {
			response.addComment(iter.next());
		}
		out.print(response);
	}

	public void doSITE_PASSWD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		getUser().setPassword(request.getArgument());
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_PRE(FtpRequest request, PrintWriter out) {
		//TODO implement me
		out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
		return;
	}
	public void doSITE_PURGE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String delUsername = request.getArgument();
		User delUser;
		try {
			delUser = this.userManager.getUserByName(delUsername);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(
				new FtpResponse(200, "Couldn't getUser: " + e.getMessage()));
			return;
		}

		delUser.purge();
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_RELOAD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		try {
			connManager.getConfig().loadConfig();
			slaveManager.reloadRSlaves();
			slaveManager.saveFilesXML();
		} catch (IOException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			logger.log(Level.SEVERE, "Error reloading config", e);
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_RENUSER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		String args[] = request.getArgument().split(" ");
		if (args.length != 2) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		try {
			userManager.getUserByName(args[0]).rename(args[1]);
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, "No such user: " + e.getMessage()));
			return;
		} catch (ObjectExistsException e) {
			out.print(new FtpResponse(200, "Target username is already taken"));
			return;
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		} catch (UserFileException e) {
			out.print(new FtpResponse(200, e.getMessage()));
		}
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_READD(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User myUser;
		try {
			myUser = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			return;
		}

		if (!myUser.isDeleted()) {
			out.print(new FtpResponse(200, "User wasn't deleted"));
			return;
		}
		myUser.setDeleted(false);
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}
	
	/**
	 * site replic <destslave> <path...>
	 * @param request
	 * @param out
	 */
// won't work due to the non-interactivitiy of ftp, and due to timeouts
//	public void doSITE_REPLIC(FtpRequest request, PrintWriter out) {
//		resetState();
//		if(!getUser().isAdmin()) {
//			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
//			return;
//		}
//		FtpResponse usage = new FtpResponse(501, "usage: SITE REPLIC <destsave> <path...>");
//		if(!request.hasArgument()) {
//			out.print(usage);
//			return;
//		}
//		String args[] = request.getArgument().split(" ");
//		
//		if(args.length < 2) {
//			out.print(usage);
//			return;
//		}
//		
//		RemoteSlave destRSlave;
//		try {
//			destRSlave = slaveManager.getSlave(args[0]);
//		} catch (ObjectNotFoundException e) {
//			out.print(new FtpResponse(200, e.getMessage()));
//			return;
//		}
//		//Slave destSlave = destRSlave.getSlave();
//		
//		for (int i = 1; i < args.length; i++) {
//			try {
//				String arg = args[i];
//				LinkedRemoteFile file = currentDirectory.lookupFile(arg);
//				String path = file.getPath();
//				RemoteSlave srcRSlave =
//					file.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD);
//
//				Transfer destTransfer =
//					destRSlave.getSlave().doListenReceive(
//						file.getParentFile().getPath(),
//						file.getName(),
//						0L);
//				Transfer srcTransfer =
//					srcRSlave.getSlave().doConnectSend(
//						file.getPath(),
//						'I',
//						0L,
//						destRSlave.getInetAddress(),
//						destTransfer.getLocalPort());
//				//TODO: these will block
//				TransferThread srcTransferThread = new TransferThread(srcRSlave, srcTransfer);
//				TransferThread destTransferThread = new TransferThread(destRSlave, destTransfer);
////				srcTransferThread.interruptibleSleepUntilFinished();
////				destTransferThread.interruptibleSleepUntilFinished();
//				while(true) {
//					out.print("200- "+srcTransfer.getTransfered()+" : "+destTransfer.getTransfered());
//				}
//			} catch (Exception e) {
//				// TODO: handle exception
//			}
//		}
//		
//	}

	public void doSITE_RESCAN(FtpRequest request, PrintWriter out) {
		resetState();
		LinkedRemoteFile directory = currentDirectory;
		LinkedRemoteFile sfvFile = null;
		//Map files = directory.getFiles();
		for (Iterator i = directory.getFiles().iterator(); i.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) i.next();
			String fileName = file.getName();
			if (fileName.endsWith(".sfv")) {
				if (sfvFile != null) {
					logger.warning(
						"Multiple SFV files in " + directory.getName());
					out.println(
						"200- Multiple SFV files found, using " + fileName);
				}
				sfvFile = file;
			}
		}
		if (sfvFile == null) {
			out.println("550 Sorry! no .sfv file found!");
			return;
		}
		SFVFile sfv;
		try {
			sfv = sfvFile.getSFVFile();
		} catch (IOException e) {
			out.println("550 " + e.getMessage());
			return;
		}
		for (Iterator i = sfv.getEntries().entrySet().iterator();
			i.hasNext();
			) {
			Map.Entry entry = (Map.Entry) i.next();
			String fileName = (String) entry.getKey();
			Long checkSum = (Long) entry.getValue();
			LinkedRemoteFile file;
			try {
				file = directory.lookupFile(fileName);
			} catch (FileNotFoundException ex) {
				out.println("200- " + fileName + " MISSING");
				continue;
			}
			boolean ok;
			ok = checkSum.longValue() == file.getCheckSum();
			out.println(
				"200- "
					+ fileName
					+ " "
					+ Long.toHexString(checkSum.longValue())
					+ " "
					+ (ok ? " OK" : " FAILED"));
			out.flush();
		}
		out.println("200 Command ok.");
	}

	/** Lists all slaves used by the master
	 * USAGE: SITE SLAVES
	 * 
	 */
	public void doSITE_SLAVES(FtpRequest request, PrintWriter out) {
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		Collection slaves = slaveManager.getSlaves();
		FtpResponse response =
			new FtpResponse(200, "OK, " + slaves.size() + " slaves listed.");

		for (Iterator iter = slaveManager.rslaves.iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			response.addComment(rslave.toString());
		}
		out.print(response);
	}

	public void doSITE_STAT(FtpRequest request, PrintWriter out) {
		resetState();
		if (request.hasArgument()) {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
			return;
		}
		out.print(new FtpResponse(200, status()));
		return;
	}

	public void doSITE_SEEN(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User user;
		try {
			user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.SEVERE, "", e);
			out.print(
				new FtpResponse(
					200,
					"Error reading userfile: " + e.getMessage()));
			return;
		}

		out.print(
			new FtpResponse(
				200,
				"User was last seen: " + new Date(user.getLastAccessTime())));
		return;
	}
	/**
	 * USAGE: site stats [<user>]
	    Display a user's upload/download statistics.
	
	    Definable in '/ftp-data/text/user.stats'
	
	    If you have multiple sections then this will display stats from 
	    all sections.  (But you have to copy this file to SECTIONuser.stats.
	    exmp: if you have a section called GAMES then glftpd will look
	    for the files user.stats and GAMESuser.stats in the /ftp-data/text dir.
	 */
	public void doSITE_STATS(FtpRequest request, PrintWriter out) {
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		User user;
		try {
			user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException e) {
			out.print(new FtpResponse(200, "No such user: " + e.getMessage()));
			return;
		} catch (IOException e) {
			logger.log(Level.WARNING, "", e);
			out.print(new FtpResponse(200, e.getMessage()));
			return;
		}

		FtpResponse response = new FtpResponse(200);
		response.addComment("bytes up, files up, bytes down, files down");
		response.addComment(
			"total: "
				+ user.getUploadedBytes()
				+ "b "
				+ user.getUploadedFiles()
				+ "f "
				+ user.getDownloadedBytes()
				+ "b "
				+ user.getDownloadedFiles()
				+ "f ");
		response.addComment(
			"month: "
				+ user.getUploadedBytesMonth()
				+ "b "
				+ user.getUploadedFilesMonth()
				+ "f "
				+ user.getDownloadedBytesMonth()
				+ "b "
				+ user.getDownloadedFilesMonth()
				+ "f ");
		response.addComment(
			"week: "
				+ user.getUploadedBytesWeek()
				+ "b "
				+ user.getUploadedFilesWeek()
				+ "f "
				+ user.getDownloadedBytesWeek()
				+ "b "
				+ user.getDownloadedFilesWeek()
				+ "f ");
		response.addComment(
			"today: "
				+ user.getUploadedBytesDay()
				+ "b "
				+ user.getUploadedFilesDay()
				+ "f "
				+ user.getDownloadedBytesDay()
				+ "b "
				+ user.getDownloadedFilesDay()
				+ "f ");

		out.print(response);
		return;
	}

	public void doSITE_TAGLINE(FtpRequest request, PrintWriter out) {
		resetState();

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		}

		getUser().setTagline(request.getArgument());
	}

	/**
	 * USAGE: site take <user> <kbytes> [<message>]
	 *        Removes credit from user
	 *
	 *        ex. site take Archimede 100000 haha
	 *
	 *        This will remove 100mb of credits from the user 'Archimede' and 
	 *        send the message haha to him.
	 */
	public void doSITE_TAKE(FtpRequest request, PrintWriter out) {
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}

		String args[] = request.getArgument().split(" ");
		User user2;
		long credits;

		try {
			user2 = userManager.getUserByName(args[0]);
			credits = Long.parseLong(args[1]) * 1000; // B, not KiB
			//			String message = args[3];
			user2.updateCredits(0 - credits); // adds - credits
		} catch (Exception ex) {
			out.println("200 " + ex.getMessage());
			return;
		}
		out.println(
			"200 OK, removed "
				+ credits
				+ "b from "
				+ user2.getUsername()
				+ ".");
	}

	public void doSITE_TIME(FtpRequest request, PrintWriter out) {
		resetState();
		if (request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		FtpResponse response = new FtpResponse(200);
		response.setMessage("Server time is: " + new Date());
		out.print(response);
		return;
	}

	/**
	 * USAGE: site unnuke <directory> <message>
	 * 	Unnuke a directory.
	 * 
	 * 	ex. site unnuke shit NOT CRAP
	 * 	
	 * 	This will unnuke the directory 'shit' with the comment 'NOT CRAP'.
	 * 
	 *         NOTE: You can enclose the directory in braces if you have spaces in the name
	 *         ex. site unnuke {My directory name} justcause
	 * 
	 * 	You need to configure glftpd to keep nuked files if you want to unnuke.
	 * 	See the section about glftpd.conf.
	 */
	public void doSITE_UNNUKE(FtpRequest request, PrintWriter out) {
		resetState();

		String argument = request.getArgument();

		int pos = argument.indexOf(' ');
		String toName;
		if (pos == -1) {
			toName = argument;
		} else {
			toName = argument.substring(0, pos);
		}
		String toPath = this.currentDirectory.getPath() + toName + "/";
		String nukeName = "[NUKED]" + toName;

		LinkedRemoteFile nukeDir;
		try {
			nukeDir = this.currentDirectory.getFile(nukeName);
		} catch (FileNotFoundException e2) {
			out.print(
				new FtpResponse(
					200,
					"nukeDir doesn't exist: " + e2.getMessage()));
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		NukeEvent nuke;
		try {
			nuke = nukelog.get(toPath);
		} catch (ObjectNotFoundException ex) {
			response.addComment(ex.getMessage());
			out.print(response);
			return;
		}

		//Map nukees2 = new Hashtable();
		for (Iterator iter = nuke.getNukees().entrySet().iterator();
			iter.hasNext();
			) {
			Map.Entry entry = (Map.Entry) iter.next();
			String nukeeName = (String) entry.getKey();
			Long amount = (Long) entry.getValue();
			User nukee;
			try {
				nukee = userManager.getUserByName(nukeeName);
			} catch (NoSuchUserException e) {
				response.addComment(nukeeName + ": no such user");
				continue;
			} catch (IOException e) {
				response.addComment(nukeeName + ": error reading userfile");
				logger.log(Level.SEVERE, "error reading userfile", e);
				continue;
			}

			nukee.updateCredits(amount.longValue());
			nukee.updateTimesNuked(1);
			try {
				nukee.commit();
			} catch (UserFileException e3) {
				logger.log(
					Level.SEVERE,
					"Eroror saveing userfile for " + nukee.getUsername(),
					e3);
				response.addComment(
					"Error saving userfile for " + nukee.getUsername());
			}

			response.addComment(nukeeName + ": restored " + amount + "bytes");
		}
		try {
			nukelog.remove(toPath);
		} catch (ObjectNotFoundException e) {
			response.addComment("Error removing nukelog entry");
		}
		try {
			nukeDir.renameTo(toPath);
		} catch (ObjectExistsException e1) {
			response.addComment(
				"Error renaming nuke, target dir already exists");
		} catch (IllegalTargetException e1) {
			response.addComment("Error: " + e1.getMessage());
			logger.log(
				Level.SEVERE,
				"Illegaltargetexception: means parent doesn't exist",
				e1);
		}
		out.print(response);
		return;
	}

	/**
	 * USAGE: site user [<user>]
	 * 	Lists users / Shows detailed info about a user.
	 * 
	 * 	ex. site user
	 * 
	 * 	This will display a list of all users currently on site.
	 * 
	 * 	ex. site user Archimede
	 * 
	 * 	This will show detailed information about user 'Archimede'.
	 */
	public void doSITE_USER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();

		User myUser;
		try {
			myUser = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			response.setMessage("User " + request.getArgument() + " not found");
			out.print(response.toString());
			//out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
			return;
		} catch (IOException ex) {
			out.print(new FtpResponse(200, ex.getMessage()));
			return;
		}

		response.addComment("comment: " + myUser.getComment());
		response.addComment("username: " + myUser.getUsername());
		int i = (int) (myUser.getTimeToday() / 1000);
		int hours = i / 60;
		int minutes = i - hours * 60;
		response.addComment(
			"last seen: " + new Date(myUser.getLastAccessTime()));
		response.addComment("time on today: " + hours + ":" + minutes);
		response.addComment("ratio: " + myUser.getRatio());
		response.addComment(
			"credits: " + Bytes.formatBytes(myUser.getCredits()));
		response.addComment("groups: " + myUser.getGroups());
		response.addComment("ip masks: " + myUser.getIpMasks());
		out.print(response);
	}

	public void doSITE_USERS(FtpRequest request, PrintWriter out) {
		resetState();

		if (request.hasArgument()) {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
			return;
		}
		FtpResponse response = new FtpResponse(200);
		Collection myUsers;
		try {
			myUsers = userManager.getAllUsers();
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
			logger.log(Level.SEVERE, "IO error reading all users", e);
			return;
		}
		for (Iterator iter = myUsers.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			response.addComment(myUser.getUsername());
		}
		out.print(response);
		return;
	}

	public void doSITE_VERS(FtpRequest request, PrintWriter out) {
		resetState();
		out.print(new FtpResponse(200, ConnectionManager.VERSION));
		return;
	}

	public void doSITE_WELCOME(FtpRequest request, PrintWriter out) {
		resetState();

		if (request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		FtpResponse response = new FtpResponse(200);

		try {
			response.addComment(
				new BufferedReader(
					new FileReader("ftp-data/text/welcome.txt")));
		} catch (IOException e) {
			out.print(new FtpResponse(200, "IO error: " + e.getMessage()));
		}
		out.print(response);
		return;
	}

	/**
	 * Lists currently connected users.
	 */
	public void doSITE_WHO(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		//FtpResponse response = new FtpResponse(200);
		FtpResponse response =
			(FtpResponse) FtpResponse.RESPONSE_200_COMMAND_OK.clone();
		for (Iterator i = connManager.getConnections().iterator();
			i.hasNext();
			) {
			response.addComment(((FtpConnection) i.next()).toString());
		}
		out.print(response.toString());
		//out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
	}
	/**
	 * USAGE: site wipe [-r] <file/directory>
	 *                                                                                 
	 *         This is similar to the UNIX rm command.
	 *         In glftpd, if you just delete a file, the uploader loses credits and
	 *         upload stats for it.  There are many people who didn't like that and
	 *         were unable/too lazy to write a shell script to do it for them, so I
	 *         wrote this command to get them off my back.
	 *                                                                                 
	 *         If the argument is a file, it will simply be deleted. If it's a
	 *         directory, it and the files it contains will be deleted.  If the
	 *         directory contains other directories, the deletion will be aborted.
	 *                                                                                 
	 *         To remove a directory containing subdirectories, you need to use
	 *         "site wipe -r dirname". BE CAREFUL WHO YOU GIVE ACCESS TO THIS COMMAND.
	 *         Glftpd will check if the parent directory of the file/directory you're
	 *         trying to delete is writable by its owner. If not, wipe will not
	 *         execute, so to protect directories from being wiped, make their parent
	 *         555.
	 *                                                                                 
	 *         Also, wipe will only work where you have the right to delete (in
	 *         glftpd.conf). Delete right and parent directory's mode of 755/777/etc
	 *         will cause glftpd to SWITCH TO ROOT UID and wipe the file/directory.
	 *         "site wipe -r /" will not work, but "site wipe -r /incoming" WILL, SO
	 *         BE CAREFUL.
	 *                                                                                 
	 *         This command will remove the deleted files/directories from the dirlog
	 *         and dupefile databases.
	 *                                                                                 
	 *         To give access to this command, add "-wipe -user flag =group" to the
	 *         config file (similar to other site commands).
	 * 
	 * @param request
	 * @param out
	 */
	public void doSITE_WIPE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!getUser().isAdmin())
			out.println("200 You need admin privileges to use SITE WIPE");

		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
		}

		String arg = request.getArgument();

		boolean recursive;
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
			recursive = true;
		} else {
			recursive = false;
		}

		LinkedRemoteFile wipeFile;
		try {
			wipeFile = currentDirectory.lookupFile(arg);
		} catch (FileNotFoundException e) {
			FtpResponse response =
				new FtpResponse(
					200,
					"Can't wipe: "
						+ arg
						+ " does not exist or it's not a plain file/directory");
			out.print(response);
			return;
		}
		if (wipeFile.isDirectory() && wipeFile.dirSize() != 0 && !recursive) {
			out.print(new FtpResponse(200, "Can't wipe, directory not empty"));
			return;
		}
		connManager.dispatchFtpEvent(
			new DirectoryFtpEvent(getUser(), "WIPE", wipeFile));
		wipeFile.delete();
		out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		return;
	}

	public void doSITE_XDUPE(FtpRequest request, PrintWriter out) {
		resetState();

		if (!request.hasArgument()) {
			if (this.xdupe == 0) {
				out.println("200 Extended dupe mode is disabled.");
			} else {
				out.println(
					"200 Extended dupe mode " + this.xdupe + " is enabled.");
			}
			return;
		}

		short myXdupe;
		try {
			myXdupe = Short.parseShort(request.getArgument());
		} catch (NumberFormatException ex) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (myXdupe > 0 || myXdupe < 4) {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
		this.xdupe = myXdupe;
		out.println("200 Activated extended dupe mode " + myXdupe + ".");
	}

	/**
	 * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * Returns the size of the file in bytes.
	 */
	public void doSIZE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		LinkedRemoteFile file;
		try {
			file = currentDirectory.lookupFile(request.getArgument());
			//file = getVirtualDirectory().lookupFile(request.getArgument());
		} catch (FileNotFoundException ex) {
			out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			return;
		}
		if (file == null) {
			System.out.println(
				"got null file instead of FileNotFoundException");
		}
		out.print(new FtpResponse(213, Long.toString(file.length())));
	}

	/**
	 * <code>STAT [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause a status response to be sent over
	 * the control connection in the form of a reply.
	 */
	/*
	 public void doSTAT(FtpRequest request, PrintWriter out) {
	     String args[] = {
	        mConfig.getSelfAddress().getHostAddress(),
	        mControlSocket.getInetAddress().getHostAddress(),
	        user.getName()
	     };
	   
	     out.write(ftpStatus.getResponse(211, request, user, args)); 
	 }
	*/

	/**
	 * <code>STOR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to accept the data
	 * transferred via the data connection and to store the data as
	 * a file at the server site.  If the file specified in the
	 * pathname exists at the server site, then its contents shall
	 * be replaced by the data being transferred.  A new file is
	 * created at the server site if the file specified in the
	 * pathname does not already exist.
	 * 
	 *                STOR
	              125, 150
	                 (110)
	                 226, 250
	                 425, 426, 451, 551, 552
	              532, 450, 452, 553
	              500, 501, 421, 530
	 * 
	 * ''zipscript´´ renames bad uploads to .bad, how do we handle this with resumes?
	 */
	public void doSTOR(FtpRequest request, PrintWriter out) {
		long resumePosition = this.resumePosition;
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		// get permission
		/*
				if (!user
					.getVirtualDirectory()
					.hasCreatePermission(physicalName, true)) {
					out.write(ftpStatus.getResponse(550, request, user, null));
					return;
				}
		*/

		// now transfer file data
		Object ret[] =
			currentDirectory.lookupNonExistingFile(request.getArgument());
		LinkedRemoteFile targetDir = (LinkedRemoteFile) ret[0];
		String targetFilename = (String) ret[1];
		LinkedRemoteFile targetFile;

		if (targetFilename == null) {
			// target exists, this could be overwrite or resume
			// if(resumePosition != 0) {} // resume
			//TODO overwrite & resume files.

			// file exists
			out.print(
				new FtpResponse(
					550,
					"Requested action not taken. File exists"));
			return;
			//targetFile = targetDir;
			//targetDir = targetDir.getParent();
			//			if(targetFile.getOwner().equals(getUser().getUsername())) {
			//				// allow overwrite/resume
			//			}
			//			if(directory.isDirectory()) {
			//				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			//			}
		}

		if (!VirtualDirectory.isLegalFileName(targetFilename)) {
			out.print(
				new FtpResponse(
					553,
					"Requested action not taken. File name not allowed."));
			return;
		}

		Transfer transfer;
		RemoteSlave rslave;

		// find a slave that works
		try {
			rslave = slaveManager.getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
		} catch (NoAvailableSlaveException ex) {
			logger.log(Level.SEVERE, "", ex);
			out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
			return;
		}
		List rslaves = Collections.singletonList(rslave);
		//		ArrayList rslaves = new ArrayList();
		//		rslaves.add(rslave);
		StaticRemoteFile uploadFile =
			new StaticRemoteFile(
				rslaves,
				targetFilename,
				getUser().getUsername(),
				getUser().getGroup(),
				0L,
				System.currentTimeMillis(),
				0L);
		targetFile = targetDir.addFile(uploadFile);

		try {
			transfer =
				rslave.getSlave().doConnectReceive(
					targetDir.getPath(),
					targetFilename,
					resumePosition,
					mAddress,
					miPort);

		} catch (RemoteException ex) {
			rslave.handleRemoteException(ex);
			targetFile.delete();
			out.print(new FtpResponse(451, ex.getMessage()));
			return;
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Error getting transfer", ex);
			targetFile.delete();
			out.println(new FtpResponse(451, "IO error: " + ex.getMessage()));
			return;
		}

		// say we are ready to start sending
		out.print(FtpResponse.RESPONSE_150_OK);
		out.flush();

		// connect and start transfer
		try {
			transfer.transfer();
		} catch (RemoteException ex) {
			out.print(new FtpResponse(426, ex.getMessage()));
			rslave.handleRemoteException(ex);
			targetFile.delete();
			return;
		} catch (IOException ex) {
			logger.log(
				Level.WARNING,
				"Transfer aborted, credits were not removed",
				ex);
			//TODO let user resume
			targetFile.delete();
			out.print(new FtpResponse(426, ex.getMessage()));
			return;
		}

		long transferedBytes;
		try {
			transferedBytes = transfer.getTransfered();
			// throws RemoteException
			if (resumePosition == 0) {
				targetFile.setCheckSum(transfer.getChecksum());
			}
			targetFile.setLastModified(System.currentTimeMillis());
			targetFile.setLength(transferedBytes);
		} catch (RemoteException ex) {
			rslave.handleRemoteException(ex);
			out.print(
				new FtpResponse(
					426,
					"Error communicationg with slave: " + ex.getMessage()));
			return;
		}

		FtpResponse response =
			(FtpResponse) FtpResponse
				.RESPONSE_226_CLOSING_DATA_CONNECTION
				.clone();
		try {
			long checksum;
			checksum = targetDir.lookupSFVFile().get(targetFilename);
			if (checksum == targetFile.getCheckSum(true)) {
				response.addComment("zipscript - checksum match");

				User user = getUser();
				user.updateCredits(
					(long) (getUser().getRatio() * transferedBytes));
				user.updateUploadedBytes(transferedBytes);
				try {
					user.commit();
				} catch (UserFileException e1) {
					response.addComment(
						"Error saving userfile: " + e1.getMessage());
				}

			} else {
				response.addComment(
					"zipscript - checksum mismatch, deleting file");
				targetFile.delete();

				//				getUser().updateCredits(
				//					- ((long) getUser().getRatio() * transferedBytes));
				//				getUser().updateUploadedBytes(-transferedBytes);
				response.addComment(status());
				out.print(response);
				return;
				//				String badtargetFilename = targetFilename + ".bad";
				//
				//				try {
				//					LinkedRemoteFile badtargetFile =
				//						targetDir.getFile(badtargetFilename);
				//					badtargetFile.delete();
				//					response.addComment(
				//						"zipscript - removing "
				//							+ badtargetFilename
				//							+ " to be replaced with new file");
				//				} catch (FileNotFoundException e2) {
				//					//good, continue...
				//					response.addComment(
				//						"zipscript - checksum mismatch, renaming to "
				//							+ badtargetFilename);
				//				}
				//				targetFile.renameTo(targetDir.getPath() + badtargetFilename);
			}
		} catch (NoAvailableSlaveException e) {
			response.addComment("zipscript - slave with .sfv file is offline");
		} catch (ObjectNotFoundException e) {
			response.addComment("zipscript - no .sfv file in directory");
		} catch (IOException e) {
			response.addComment(
				"zipscript - IO error parsing sfv file: " + e.getMessage());
		}
		connManager.dispatchFtpEvent(
			new DirectoryFtpEvent(getUser(), "STOR", targetFile));
		response.addComment(status());
		out.print(response);
		return;
	}

	/**
	 * <code>STOU &lt;CRLF&gt;</code><br>
	 *
	 * This command behaves like STOR except that the resultant
	 * file is to be created in the current directory under a name
	 * unique to that directory.  The 250 Transfer Started response
	 * must include the name generated.
	 */
	//TODO STOU
	/*
	public void doSTOU(FtpRequest request, PrintWriter out) {
	    
	    // reset state variables
	    resetState();
	    
	    // get filenames
	    String fileName = user.getVirtualDirectory().getAbsoluteName("ftp.dat");
	    String physicalName = user.getVirtualDirectory().getPhysicalName(fileName);
	    File requestedFile = new File(physicalName);
	    requestedFile = IoUtils.getUniqueFile(requestedFile);
	    fileName = user.getVirtualDirectory().getVirtualName(requestedFile.getAbsolutePath());
	    String args[] = {fileName};
	    
	    // check permission
	    if(!user.getVirtualDirectory().hasCreatePermission(fileName, false)) {
	        out.write(ftpStatus.getResponse(550, request, user, null));
	        return;
	    }
	    
	    // now transfer file data
	    out.print(FtpResponse.RESPONSE_150_OK);
	    InputStream is = null;
	    OutputStream os = null;
	    try {
	        Socket dataSoc = mDataConnection.getDataSocket();
	        if (dataSoc == null) {
	             out.write(ftpStatus.getResponse(550, request, user, args));
	             return;
	        }
	
	        
	        is = dataSoc.getInputStream();
	        os = user.getOutputStream( new FileOutputStream(requestedFile) );
	        
	        StreamConnector msc = new StreamConnector(is, os);
	        msc.setMaxTransferRate(user.getMaxUploadRate());
	        msc.setObserver(this);
	        msc.connect();
	        
	        if(msc.hasException()) {
	            out.write(ftpStatus.getResponse(451, request, user, null));
	            return;
	        }
	        else {
	            mConfig.getStatistics().setUpload(requestedFile, user, msc.getTransferredSize());
	        }
	        
	        out.write(ftpStatus.getResponse(226, request, user, null));
	        mDataConnection.reset();
	        out.write(ftpStatus.getResponse(250, request, user, args));
	    }
	    catch(IOException ex) {
	        out.write(ftpStatus.getResponse(425, request, user, null));
	    }
	    finally {
	       IoUtils.close(is);
	       IoUtils.close(os);
	       mDataConnection.reset(); 
	    }
	}
	*/

	/**
	 * <code>STRU &lt;SP&gt; &lt;structure-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * file structure.
	 */
	public void doSTRU(FtpRequest request, PrintWriter out) {
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		if (request.getArgument().equalsIgnoreCase("F")) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
		/*
				if (setStructure(request.getArgument().charAt(0))) {
					out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
				} else {
					out.print(FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
				}
		*/
	}

	/**
	 * <code>SYST &lt;CRLF&gt;</code><br> 
	 *
	 * This command is used to find out the type of operating
	 * system at the server.
	 */
	public void doSYST(FtpRequest request, PrintWriter out) {
		resetState();

		/*
		String systemName = System.getProperty("os.name");
		if(systemName == null) {
		    systemName = "UNKNOWN";
		}
		else {
		    systemName = systemName.toUpperCase();
		    systemName = systemName.replace(' ', '-');
		}
		String args[] = {systemName};
		*/
		out.print(FtpResponse.RESPONSE_215_SYSTEM_TYPE);
		//String args[] = { "UNIX" };
		//out.write(ftpStatus.getResponse(215, request, user, args));
	}

	/**
	 * <code>TYPE &lt;SP&gt; &lt;type-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument specifies the representation type.
	 */
	public void doTYPE(FtpRequest request, PrintWriter out) {
		resetState();

		// get type from argument
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}

		setType(request.getArgument().charAt(0));

		// set it
		if (setType(request.getArgument().charAt(0))) {
			out.print(FtpResponse.RESPONSE_200_COMMAND_OK);
		} else {
			out.print(
				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		}
	}

	/**
	 * <code>USER &lt;SP&gt; &lt;username&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string identifying the user.
	 * The user identification is that which is required by the
	 * server for access to its file system.  This command will
	 * normally be the first command transmitted by the user after
	 * the control connections are made.
	 */
	public void doUSER(FtpRequest request, PrintWriter out) {
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			return;
		}
		Ident id = new Ident(controlSocket);
		String ident = "";
		if (id.successful) {
			ident = id.userName;
		} else {
			System.out.println(
				"Failed to get ident response: " + id.errorMessage);
		}
		try {
			this.user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			out.print(new FtpResponse(530, ex.getMessage()));
			return;
		} catch (IOException ex) {
			ex.printStackTrace();
			out.print(new FtpResponse(530, "IOException: " + ex.getMessage()));
			return;
		}
		String masks[] =
			{
				ident + "@" + getClientAddress().getHostAddress(),
				ident + "@" + getClientAddress().getHostName()};
		if (!getUser().checkIP(masks)) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			return;
		}
		// check user login status
		//		mbUser = true;
		/*		if (user.hasLoggedIn()) {
					if (user.getUsername().equals(request.getArgument())) {
						out.write(ftpStatus.getResponse(230, request, user, null));
						return;
					}
					    else {
					        mConfig.getConnectionService().closeConnection(user.getSessionId());
					    }
				}
				*/

		// set user name and send appropriate message
		if (getUser() == null) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			//out.write(ftpStatus.getResponse(530, request, user, null));
		} else {
			//			user.setUsername(request.getArgument());
			//out.print(FtpResponse.RESPONSE_331_USERNAME_OK_NEED_PASS);
			FtpResponse response =
				new FtpResponse(
					331,
					"Password required for " + getUser().getUsername());
			out.print(response);
			//out.write(ftpStatus.getResponse(331, request, user, null));
		}
		/*
		if (user.isAnonymous()) {
			//if(mConfig.isAnonymousLoginAllowed()) { 
			FtpRequest anoRequest = new FtpRequest(user.getUsername());
			out.write(ftpStatus.getResponse(331, anoRequest, user, null));
			/*
			    }
			    else {
			        out.write(ftpStatus.getResponse(530, request, user, null));
			        ConnectionService conService = mConfig.getConnectionService();
			        if (conService != null) {
			           conService.closeConnection(user.getSessionId());
			        }
			    }
			*/
	}

	/**
	 * Get output stream. Returns <code>ftpserver.util.AsciiOutputStream</code>
	 * if the transfer type is ASCII.
	 */
	public OutputStream getOutputStream(OutputStream os) {
		//os = IoUtils.getBufferedOutputStream(os);
		if (type == 'A') {
			os = new AsciiOutputStream(os);
		}
		return os;
	}
	/**
	 * Get the user data type.
	 */
	public char getType() {
		return type;
	}

	//private FtpDataConnection mDataConnection;
	/**
	 * Set configuration file and the control socket. 
	 */
	/*
	public FtpConnection(FtpConfig cfg, Socket soc) {
	    super(cfg, soc);
	}
	*/

	/**
	 * prints a few lines of status such as credits, ratio, disk free, to the user
	 * @deprecated Use status(FtpResponse) instead
	 */
	protected void printStatus(PrintWriter out, String prefix) {
		out.println(
			prefix
				+ " [Credits: "
				+ getUser().getCredits()
				+ "B] [Ratio: 1:"
				+ getUser().getRatio()
				+ "]");
	}

	/** returns a one-line status line
	 */
	protected String status() {
		return " [Credits: "
			+ Bytes.formatBytes(getUser().getCredits())
			+ "B] [Ratio: 1:"
			+ getUser().getRatio()
			+ "]";
	}

	/**
	 * Is an anonymous user?
	 */
	//	public boolean getIsAnonymous() {
	//		return ANONYMOUS.equals(getUsername());
	//
	//	}

	/**
	 * Check the user permission to execute this command.
	 */
	/*
	protected boolean hasPermission(FtpRequest request) {
		String cmd = request.getCommand();
		return user.hasLoggedIn()
			|| cmd.equals("USER")
			|| cmd.equals("PASS")
			|| cmd.equals("HELP");
	}
	*/
	/**
	 * Reset temporary state variables.
	 * mstRenFr and resumePosition
	 */
	private void resetState() {
		mstRenFr = null;

		//		mbReset = false;
		resumePosition = 0;

		//mbUser = false;
		//mbPass = false;
	}

	/**
	 * Set the data type. Supported types are A (ascii) and I (binary).
	 * @return true if success
	 */
	public boolean setType(char type) {
		type = Character.toUpperCase(type);
		if ((type != 'A') && (type != 'I')) {
			return false;
		}
		this.type = type;
		return true;
	}
}
