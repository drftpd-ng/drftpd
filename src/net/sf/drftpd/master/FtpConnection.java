package net.sf.drftpd.master;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.usermanager.GlftpdUserManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferImpl;
import socks.server.Ident;

/**
 * This class handles each ftp connection. Here all the ftp command
 * methods take two arguments - a FtpRequest and a PrintWriter object. 
 * This is the main backbone of the ftp server.
 * <br>
 * The ftp command method signature is: 
 * <code>public void doXYZ(FtpRequest request, PrintWriter out) throws IOException</code>.
 * <br>
 * Here <code>XYZ</code> is the capitalized ftp command. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */

public class FtpConnection extends BaseFtpConnection {
	private final static SimpleDateFormat DATE_FMT =
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");
	private static Logger logger =
		Logger.getLogger(FtpConnection.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	private boolean mbRenFr = false;
	private String mstRenFr = null;

	// command state specific temporary variables

	//unneeded? "mlSkipLen == 0" is almost the same thing.
	//private boolean mbReset = false;
	private long resumePosition = 0;

	//	public final static String ANONYMOUS = "anonymous";

	private char type = 'A';

	//	private boolean mbUser = false;
	//private boolean mbPass = false;
	private UserManager userManager;

	private VirtualDirectory virtualDirectory;

	private LinkedRemoteFile currentDirectory;

	public FtpConnection(
		Socket sock,
		UserManager userManager,
		SlaveManagerImpl slaveManager,
		LinkedRemoteFile root,
		ConnectionManager connManager) {

		super(connManager, sock);
		this.userManager = userManager;
		this.slaveManager = slaveManager;

		currentDirectory = root;
		virtualDirectory = new VirtualDirectory(root);
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
	/*
	 public void doABOR(FtpRequest request, PrintWriter out) throws IOException {
	     
	     // reset state variables
	     resetState();
	     mDataConnection.reset();
	     out.write(ftpStatus.getResponse(226, request, user, null));
	 }
	 */

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
	/*
	 public void doAPPE(FtpRequest request, PrintWriter out) throws IOException {
	    
	     // reset state variables
	     resetState();
	     
	     // argument check
	     if(!request.hasArgument()) {
	        out.write(ftpStatus.getResponse(501, request, user, null));
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
		LinkedRemoteFile newCurrentDirectory;
		try {
			newCurrentDirectory = currentDirectory.getParentFile();
		} catch (FileNotFoundException ex) {
			FtpResponse response = new FtpResponse(431, ex.getMessage());
			out.print(response.toString());
			return;
		}
		currentDirectory = newCurrentDirectory;
		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());
		out.print(response.toString());
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
		String dirName = "/";
		if (request.hasArgument()) {
			dirName = request.getArgument();
		}
		LinkedRemoteFile newCurrentDirectory;
		try {
			newCurrentDirectory =
				currentDirectory.lookupFile(dirName);
		} catch (FileNotFoundException ex) {
			FtpResponse response = new FtpResponse(431, ex.getMessage());
			out.print(response);
			return;
		}
		currentDirectory = newCurrentDirectory;
		FtpResponse response =
			new FtpResponse(
				200,
				"Directory changed to " + currentDirectory.getPath());
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
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		LinkedRemoteFile requestedFile;
		try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			requestedFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			out.println("500 Requested action not taken. " + ex.getMessage());
			return;
		}
		String[] args = { fileName };

		// check permission
//		if (!getVirtualDirectory().hasWritePermission(requestedFile)) {
//			out.write(ftpStatus.getResponse(450, request, user, args));
//			return;
//		}

		// now delete
		//try {
		requestedFile.delete();
		out.write(ftpStatus.getResponse(250, request, user, args));
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
		if (!request.hasArgument()) {
			out.write(ftpStatus.getResponse(214, null, user, null));
			return;
		}

		// print command specific help
		String ftpCmd = request.getArgument().toUpperCase();
		String args[] = null;
		FtpRequest tempRequest = new FtpRequest(ftpCmd);
		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
		return;
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
	 */
	public void doLIST(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		out.write(ftpStatus.getResponse(150, request, user, null));
		Writer os = null;
		try {
			Socket dataSocket = getDataSocket();
			if (dataSocket == null) {
				out.write(ftpStatus.getResponse(550, request, user, null));
				return;
			}

			if (mbPort) {
				os = new OutputStreamWriter(dataSocket.getOutputStream());

				if (!VirtualDirectory
					.printList(currentDirectory, request.getArgument(), os)) {
					out.write(ftpStatus.getResponse(501, request, user, null));
				} else {
					os.flush();
					FtpResponse response =
						new FtpResponse(226, "Closing data connection");
					response.addComment(status());
					//printStatus(out, "226-");
					out.print(response);
				}
			} else { //mbPasv
				//TODO passive transfer mode
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			out.write(ftpStatus.getResponse(425, request, user, null));
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
			out.print(new FtpResponse(213, DATE_FMT.format(new Date(reqFile.lastModified()))));
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
	 */
	public void doMKD(FtpRequest request, PrintWriter out) {

		// reset state variables
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		if (VirtualDirectory.isLegalFileName(fileName)) {
			out.println(
				"553 Requested action not taken. File name not allowed.");
			return;
		}

		LinkedRemoteFile file = currentDirectory;
		if (file.getHashtable().containsKey(fileName)) {
			out.println(
				"550 Requested action not taken. "
					+ fileName
					+ " already exists");
			return;
		}

		try {
			file.mkdir(user, fileName);
			out.write(
				ftpStatus.getResponse(
					250,
					request,
					user,
					new String[] { fileName }));
		} catch (FileExistsException ex) {
			out.println("550 directory " + fileName + " already exists");
			return;
		} catch (IOException ex) {
			out.write(ftpStatus.getResponse(550, request, user, null));
			ex.printStackTrace();
			return;
		}
		//		String args[] = { fileName };

		// check permission
		//		if (!getVirtualDirectory().hasCreatePermission(physicalName, true)) {
		//			out.write(ftpStatus.getResponse(450, request, user, args));
		//			return;
		//		}

		//getVirtualDirectory()

		/*
		// now create directory
		if(new File(physicalName).mkdirs()) {
		}
		else {
		}
		*/
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
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		if (request.getArgument().equalsIgnoreCase("S")) {
			out.write(ftpStatus.getResponse(200, request, user, null));
		} else {
			out.write(ftpStatus.getResponse(504, request, user, null));
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
	public void doNLST(FtpRequest request, PrintWriter out)
		throws IOException {
		out.print(
			new FtpResponse(
				500,
				"Temporarilrly out of order, file a bug at http://drftpd.sourceforge.net and say what client you are using"));
		/*
		 
		 // reset state variables
		 resetState();
		 
		 out.write(ftpStatus.getResponse(150, request, user, null));
		 Writer os = null;
		 try {
		     Socket dataSoc = mDataConnection.getDataSocket();
		     if (dataSoc == null) {
		          out.write(ftpStatus.getResponse(550, request, user, null));
		          return;
		     }
		     
		     os = new OutputStreamWriter(dataSoc.getOutputStream());
		     
		     if (!user.getVirtualDirectory().printNList(request.getArgument(), os)) {
		         out.write(ftpStatus.getResponse(501, request, user, null));
		     }
		     else {
		        os.flush();
		        out.write(ftpStatus.getResponse(226, request, user, null));
		     }
		 }
		 catch(IOException ex) {
		     out.write(ftpStatus.getResponse(425, request, user, null));
		 }
		 finally {
		 try {
		 os.close();
		 } catch(Exception ex) {
		 e.printStackTrace();
		 }
		     mDataConnection.reset();
		 }
		*/
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

		out.write(ftpStatus.getResponse(200, request, user, null));
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
		/*
		    if(!mbUser) {
		        out.write(ftpStatus.getResponse(500, request, user, null));
		        resetState();
		        return;
		    }
		*/
		resetState();
		//		mbPass = true;

		// set user password and login
		String pass = request.hasArgument() ? request.getArgument() : "";
		//user.login(pass);

		// login failure - close connection
		String args[] = { user.getUsername()};
		if (user.login(pass)) {
			out.write(ftpStatus.getResponse(230, request, user, args));
			authenticated = true;
		} else {
			out.write(ftpStatus.getResponse(530, request, user, args));
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

		// argument check
		if (!request.hasArgument()) {
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		// set state variables
		resetState();
		//mlSkipLen = 0; // already set by resetState()
		String skipNum = request.getArgument();
		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}
		if (resumePosition < 0) {
			//mlSkipLen = 0; // it is already 0
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}
		//		mbReset = true;
		out.write(ftpStatus.getResponse(350, request, user, null));
	}

	/**
	 * <code>RETR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to transfer a copy of the
	 * file, specified in the pathname, to the server- or user-DTP
	 * at the other end of the data connection.  The status and
	 * contents of the file at the server site shall be unaffected.
	 */

	public void doRETR(FtpRequest request, PrintWriter out) {
		// set state variables
		//		long skipLen = (mbReset) ? mlSkipLen : 0;
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
			//remoteFile = getVirtualDirectory().lookupFile(fileName);
			remoteFile = currentDirectory.lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			//out.write(ftpStatus.getResponse(550, request, user, null));
			out.println("550 " + fileName + ": No such file");
			return;
		}
		if (!remoteFile.isFile()) {
			out.println("550 " + remoteFile + ": not a plain file.");
			return;
		}
		//String args[] = { fileName };
		if (user.getCredits() < remoteFile.length() * user.getRatio()) {
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
		Transfer transfer;
		RemoteSlave slave;
		while (true) {
			try {
				slave = remoteFile.getASlave();
			} catch (NoAvailableSlaveException ex) {
				out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
				//out.println("550 " + ex.getMessage());
				return;
			}

			// get socket depending on the selection
			if (mbPort) {
				try {
					//TODO: prefixes
					transfer =
						slave.getSlave().doConnectSend(
							new StaticRemoteFile(remoteFile),
							getType(),
							resumePosition,
							mAddress,
							miPort);
				} catch (RemoteException ex) {
					slave.handleRemoteException(ex);
					continue;
				} catch (FileNotFoundException ex) {
					out.write(ftpStatus.getResponse(550, request, user, null));
					return;
				} catch (IOException ex) {
					ex.printStackTrace();
					return;
				}
				break;
			} else {
				out.println("502 Command not implemented. ");
				return;
			}
		}
		out.write(ftpStatus.getResponse(150, request, user, null));
		out.flush();
		try {
			transfer.transfer();
		} catch (RemoteException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		try {
			long transferedBytes = transfer.getTransfered();
			user.updateCredits((long) ( - transferedBytes * user.getRatio()));
			user.updateDownloadedBytes(transferedBytes);
		} catch (RemoteException ex) {
			slave.handleRemoteException(ex);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		out.write(ftpStatus.getResponse(226, request, user, null));
		reset();
	}

	public void doSITE_ADDIP(FtpRequest request, PrintWriter out) {
		resetState();
		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.println("200 USAGE: SITE ADDIP <username> <ident@ip>");
			return;
		}
		User user2;
		try {
			user2 = userManager.getUserByName(args[0]);
			user2.addIPMask(args[1]);
			userManager.save(user2);
		} catch (NoSuchUserException ex) {
			out.println("200 No such user: " + args[0]);
			return;
		} catch (IOException ex) {
			out.println("200 Caught IOException: " + ex.getMessage());
			return;
		}
		out.write(ftpStatus.getResponse(200, request, user, null));
	}

	public void doSITE_CHECKSLAVES(FtpRequest request, PrintWriter out) {
		out.println(
			"200 Ok, " + slaveManager.verifySlaves() + " stale slaves removed");
	}

	public void doSITE_LIST(FtpRequest request, PrintWriter out) {
		resetState();
		RemoteFile files[] = currentDirectory.listFiles();
		for (int i = 0; i < files.length; i++) {
			RemoteFile file = files[i];
			out.write("200- " + file.toString() + "\r\n");
		}
		out.println(
			"200 "
				+ currentDirectory.getPath()
				+ " contained "
				+ files.length
				+ " entries listed");
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
	//TODO {} directory argument syntax
	public void doSITE_NUKE(FtpRequest request, PrintWriter out) {
		//FtpResponse response = new FtpResponse();
		LinkedRemoteFile thisDir = currentDirectory;
		LinkedRemoteFile nukeDir;
		String arg = request.getArgument();
		int pos = arg.indexOf(' ');
		int multiplier; 
		try {
			multiplier = Integer.parseInt(arg.substring(pos + 1));
		} catch(NumberFormatException ex) {
			ex.printStackTrace();
			out.print(new FtpResponse(501, "Invalid multiplier: "+ex.getMessage()));
			return;
		}
		
		try {
			nukeDir = thisDir.getFile(arg.substring(0, pos));
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
			try {
				User user = userManager.getUserByName(username);
			} catch (NoSuchUserException e1) {
				response.addComment(
					"Cannot remove credits from "
						+ username
						+ ": "
						+ e1.getMessage());
				e1.printStackTrace();
			} catch (IOException e1) {
				response.addComment(
					"Cannot read user data for "
						+ username
						+ ": "
						+ e1.getMessage());
				e1.printStackTrace();
				response.setResponse("NUKE failed");
				out.print(response);
				return;
			}
			// nukees contains credits as value
			nukees2.put(user, nukees.get(username));
		}

		String to = "[NUKED]-" + nukeDir.getName();
		try {
			nukeDir.renameTo(to);
		} catch (IOException e1) {
			e1.printStackTrace();
			response.addComment(
				" cannot rename to \"" + to + "\": " + e1.getMessage());
			response.setCode(500);
			response.setResponse("NUKE failed");
			out.print(response);
			return;
		}

		for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
			User element = (User) iter.next();
			Integer size = (Integer) nukees2.get(element);
			Integer debt = new Integer( (int)(size.intValue() * user.getRatio() + size.intValue() * (multiplier - 1)));
			try {
				user.updateCredits(-debt.intValue());
			} catch (IOException e1) {
				String str = "updateCredits() failed for " + user.getUsername();
				response.addComment(str + ": " + e1.getMessage());
				logger.log(Level.WARNING, str, e1);
			}
		}

		out.print(response);
	}
	private void nukeRemoveCredits(
		LinkedRemoteFile nukeDir,
		Hashtable nukees) {
		for (Iterator iter = nukeDir.iterateFiles(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String owner = file.getOwner();
			Integer total = (Integer) nukees.get(owner);
			if (total == null)
				total = new Integer(0);
			total =
				new Integer(
					(int) (total.intValue() + file.length()));
			nukees.put(owner, total);
		}
	}
	public void doSITE_CHANGE(FtpRequest request, PrintWriter out) {
		out.print(new FtpResponse(500, "Temporarirly out of order"));
		/*
		if (!request.hasArgument() || !user.isAdmin()) {
			out.write(ftpStatus.getResponse(530, request, user, null));
			return;
		}
		
		Pattern p = new Perl5Compiler().compile("^(\\w+) (\\w+) (.*)$");
		request.getArgument()
		Matcher m = new Matcher();
		m.
		
		String command, arg;
		User user2;
		{
			String argument = request.getArgument();
			int l1 = argument.indexOf(" ");
		
			user2 = usermanager.getUserByName(argument.substring(0, l1));
			if (user2 == null) {
				out.write(ftpStatus.getResponse(200, request, user, null));
				return;
			}
			
			int i2 = argument.indexOf(" ", l1+1);
			
			
			
		}
		
		//		String args[] = request.getArgument().split(" ");
		//		String command = args[1].toLowerCase();
		// 0 = user
		// 1 = command
		// 2- = argument
		if (args[1] == null) {
			out.println("200- Fields:  ratio");
			out.println("200-  max_dlspeed, max_ulspeed");
			out.println("200-  max_sim_down, max_sim_up");
			out.println("200-  timeframe # #, credits, flags, homedir");
			out.println("200- idle_time, startup_dir, num_logins # [#]");
			out.println("200-  time_limit, tagline, comment, group_slots # [#]");
			return;
		} else if ("ratio".equalsIgnoreCase(command)) {
			user.setRatio(Float.parseFloat(args[2]));
		} else if ("max_dlspeed".equalsIgnoreCase(command)) {
			user.setRatio(Long.parseLong(command));
		} else if ("max_ulspeed".equals(command)) {
			user.setMaxUploadRate(Integer.parseInt(args[2]));
		}
		*/
	}
	public void doSITE_RESCAN(FtpRequest request, PrintWriter out) {
		LinkedRemoteFile directory = currentDirectory;
		LinkedRemoteFile sfvFile = null;
		Map files = directory.getFiles();
		for (Iterator i = files.keySet().iterator(); i.hasNext();) {
			String fileName = (String) i.next();
			if (fileName.endsWith(".sfv")) {
				try {
					if (sfvFile != null) {
						logger.warning(
							"Multiple SFV files in " + directory.getName());
						out.println(
							"200- Multiple SFV files found, using " + fileName);
					}
					sfvFile = directory.lookupFile(fileName);
				} catch (FileNotFoundException e) {
					out.println("550 " + e.getMessage());
					return;
				}
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
		for (Iterator i = sfv.entrySet().iterator(); i.hasNext();) {
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
			out.println("200- " + fileName + (ok ? " OK" : " FAILED"));
			out.flush();
		}
		out.println("200 Command ok.");
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
		GlftpdUserManager.GlftpdUser gluser = null;
		if (user instanceof GlftpdUserManager.GlftpdUser)
			gluser = (GlftpdUserManager.GlftpdUser) user;

		if (!user.isAdmin()
			&& !(gluser != null && gluser.getFlags().indexOf("F") != -1)) {
			out.println("200 Access denied.");
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
	//TODO UNNUKE
	public void doSITE_UNNUKE(FtpRequest request, PrintWriter out) {
		resetState();
		throw new NoSuchMethodError("TODO: UNNUKE");
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
		if (!user.isAdmin()) {
			out.print(new FtpResponse(530, "Access denied"));
			return;
		}
		FtpResponse response = new FtpResponse(200);
		//int pos = request.getArgument().indexOf(" ") + 1;
		User user2;
		try {
			user2 = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			response.setResponse(
				"User " + request.getArgument() + " not found");
			out.print(response.toString());
			//out.write(ftpStatus.getResponse(200, request, user, null));
			return;
		} catch (IOException ex) {
			//TODO No such user response code for SITE USER command?
			out.print(new FtpResponse(200, ex.getMessage()).toString());
			return;
		}

		//		//ugly hack to support glftpd flags
		//		GlftpdUserManager.GlftpdUser gluser = null;
		//		if (user instanceof GlftpdUserManager.GlftpdUser) {
		//			gluser = (GlftpdUserManager.GlftpdUser) user;
		//		}
		//		//out.write("200- " + user.getComment());
		//		response.addComment(user.getComment());
		//		//out.write(
		//		//	"200- +=======================================================================+\r\n");
		//		response.addComment("+=======================================================================+")
		//		//out.write(
		//		//	"200- | Username: "
		//		//		+ user.getUsername()
		//		//		+ " Created: UNKNOWN \r\n");
		//		response.addComment("| Username: "
		//				+ user.getUsername()
		//				+ " Created: UNKNOWN");
		//		int i = (int) (user.getTimeToday() / 1000);
		//		int hours = i / 60;
		//		int minutes = i - hours * 60;
		//		/*out.write(
		//			"200-*/ response.addComment("| Time On Today: "
		//				+ hours
		//				+ ":"
		//				+ minutes
		//				+ " Last seen: TODO"); //TODO Last seen
		//
		//		response.addComment(
		//			"| Flags: "
		//				+ (gluser != null ? gluser.getFlags() : "")
		//				+ "   Idle time: TODO"); //TODO idle time
		//		out.write(
		//			"200- | Ratio: 1:3                         Credits:    2868.8 MB              \r\n");
		//		out.write(
		//			"200- | Total Logins: 680                  Current Logins: 0                  \r\n");
		//		out.write(
		//			"200- | Max Logins: 3                      From same IP: Unlimited            \r\n");
		//		out.write(
		//			"200- | Max Sim Uploads: Unlimited         Max Sim Downloads: Unlimited       \r\n");
		//		out.write(
		//			"200- | Max Upload Speed:"
		//				+ (user.getMaxUploadRate() / 1000F)
		//				+ " K/s      Max Download Speed:"
		//				+ (user.getMaxDownloadRate() / 1000F)
		//				+ " K/s    \r\n");
		//		out.write(
		//			"200- | Times Nuked: "
		//				+ user.getTimesNuked()
		//				+ "    Bytes Nuked:    568 MB             \r\n");
		//		out.write(
		//			"200- | Weekly Allotment:     0 MB         Messages Waiting: Not implemented            \r\n");
		//		out.write(
		//			"200- | Time Limit: "
		//				+ user.getTimelimit()
		//				+ " minutes.          (0 = Unlimited)                    \r\n");
		//		if (gluser != null) {
		//			out.write("200- | " + gluser.getSlots() + " \r\n");
		//		}
		//		out.write(
		//			"200- | Gadmin/Leech Slots: -1 -1          (-1 = Unlimited, None)             \r\n");
		//		out.write("200- | Tagline: " + user.getTagline() + " \r\n");
		//		out.write("200- | Groups: " + user.getGroups() + " \r\n");
		//		if (user instanceof GlftpdUserManager.GlftpdUser) {
		//			out.write(
		//				"200- | Priv Groups: "
		//					+ ((GlftpdUserManager.GlftpdUser) user).getPrivateGroups()
		//					+ " \r\n");
		//		}
		//		out.write(
		//			"200- +-----------------------------------------------------------------------+\r\n");
		//		out.write("200- | IP0: IP1: \r\n");
		//		out.write("200- | IP2: IP3: \r\n");
		//		out.write("200- | IP4: IP5: \r\n");
		//		out.write("200- | IP6: IP7: \r\n");
		//		out.write("200- | IP8: IP9: \r\n");
		//		out.write(
		//			"200- +=======================================================================+\r\n");
		//		out.write(ftpStatus.getResponse(200, request, user, null));
	}

	/**
	 * Lists currently connected users.
	 */
	public void doSITE_WHO(FtpRequest request, PrintWriter out) {
		resetState();
		if (!user.isAdmin()) {
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
		//out.write(ftpStatus.getResponse(200, request, user, null));
	}

	public void doSITE_WIPE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!user.isAdmin())
			out.write("200 You need admin privileges to use SITE WIPE");
		String arg = request.getArgument();

		if (arg.charAt(0) == '-') {
			int pos = arg.indexOf(' ');
		}

	}

	/**
	 * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the directory specified in the pathname
	 * to be removed as a directory (if the pathname is absolute)
	 * or as a subdirectory of the current working directory (if
	 * the pathname is relative).
	 */
	/*
	 public void doRMD(FtpRequest request, PrintWriter out) throws IOException {
	    
	    // reset state variables
	    resetState(); 
	     
	    // argument check
	    if(!request.hasArgument()) {
	        out.write(ftpStatus.getResponse(501, request, user, null));
	        return;  
	    }
	    
	    // get file names
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
	    
	    // now delete
	    if(requestedFile.delete()) {
	       out.write(ftpStatus.getResponse(250, request, user, args)); 
	    }
	    else {
	       out.write(ftpStatus.getResponse(450, request, user, args));
	    }
	 }
	*/

	/**
	 * <code>RNFR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the old pathname of the file which is
	 * to be renamed.  This command must be immediately followed by
	 * a "rename to" command specifying the new file pathname.
	 */
	//TODO RNFR
	public void doRNFR(FtpRequest request, PrintWriter out)
		throws IOException {
		out.print(new FtpResponse(500, "Command not implemented").toString());
		/*     
		     // reset state variable
		     resetState();
		     
		     // argument check
		     if(!request.hasArgument()) {
		        out.write(ftpStatus.getResponse(501, request, user, null));
		        return;  
		     }
		     
		     // set state variable
		     mbRenFr = true;
		     
		     // get filenames
		     String fileName = request.getArgument();
		     fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
		     mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);
		     String args[] = {fileName};
		     
		     out.write(ftpStatus.getResponse(350, request, user, args));
		*/
	}

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	//TODO RNTO
	public void doRNTO(FtpRequest request, PrintWriter out)
		throws IOException {
		out.print(new FtpResponse(500, "Command not implemented").toString());
		/*
		// argument check
		if(!request.hasArgument()) {
		   resetState(); 
		   out.write(ftpStatus.getResponse(501, request, user, null));
		   return;  
		}
		
		// set state variables
		if((!mbRenFr) || (mstRenFr == null)) {
		     resetState();
		     out.write(ftpStatus.getResponse(100, request, user, null));
		     return;
		}
		
		// get filenames
		String fromFileStr = user.getVirtualDirectory().getVirtualName(mstRenFr);
		String toFileStr = request.getArgument();
		toFileStr = user.getVirtualDirectory().getAbsoluteName(toFileStr);
		String physicalToFileStr = user.getVirtualDirectory().getPhysicalName(toFileStr);
		File fromFile = new File(mstRenFr);
		File toFile = new File(physicalToFileStr);
		String args[] = {fromFileStr, toFileStr};
		
		resetState();
		
		// check permission
		if(!user.getVirtualDirectory().hasCreatePermission(physicalToFileStr, true)) {
		   out.write(ftpStatus.getResponse(553, request, user, null));
		   return;
		}
		
		// now rename
		if(fromFile.renameTo(toFile)) {
		    out.write(ftpStatus.getResponse(250, request, user, args));
		}
		else {
		    out.write(ftpStatus.getResponse(553, request, user, args));
		}
		*/
	}

	/**
	 * <code>SITE &lt;SP&gt; <string> &lt;CRLF&gt;</code><br>
	 *
	 * This command is used by the server to provide services
	 * specific to his system that are essential to file transfer
	 * but not sufficiently universal to be included as commands in
	 * the protocol.
	 */
	/*
	public void doSITE(FtpRequest request, PrintWriter out) throws IOException {
	     //SiteCommandHandler siteCmd = new SiteCommandHandler( mConfig, user );
	     SiteCommandHandler siteCmd = new SiteCommandHandler(user);
	     //out.write( siteCmd.getResponse(request) );
	     //siteCmd.do(request, out);
	}
	*/

	/**
	 * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * Returns the size of the file in bytes.
	 */
	public void doSIZE(FtpRequest request, PrintWriter out) {
		resetState();
		if (!request.hasArgument()) {
			out.write(ftpStatus.getResponse(501, request, user, null));
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
		if(file == null) {
			System.out.println("got null file instead of FileNotFoundException");
		}
		out.print(new FtpResponse(213, Long.toString(file.length())));
//		out.write(
//			ftpStatus.getResponse(
//				213,
//				request,
//				user,
//				new String[] { "" + file.length()}));
	}

	/**
	 * <code>STAT [&lt;SP&gt; &lt;pathname&gt;] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause a status response to be sent over
	 * the control connection in the form of a reply.
	 */
	/*
	 public void doSTAT(FtpRequest request, PrintWriter out) throws IOException {
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

		RemoteSlave slave;
		LinkedRemoteFile directory = currentDirectory;
		String fileName = request.getArgument();

		if (VirtualDirectory.isLegalFileName(fileName)) {
			out.print(
				new FtpResponse(
					553,
					"Requested action not taken. File name not allowed."));
			return;
		}

		try {
			//TODO overwrite & resume files.
			directory.lookupFile(fileName);
			// file exists
			out.print(
				new FtpResponse(
					550,
					"Requested action not taken. File exists"));
			return;
		} catch (FileNotFoundException ex) {
		} // file doesn't exist - good, continue.

		Transfer transfer;
		// find a slave that works
		while (true) {
			try {
				slave = slaveManager.getASlave(TransferImpl.TRANSFER_RECEIVING);
			} catch (NoAvailableSlaveException ex) {
				out.print(new FtpResponse(550, "No available slave"));
				return;
			}
			try {
				transfer =
					slave.getSlave().doConnectReceive(
						new StaticRemoteFile(directory),
						fileName,
						user,
						resumePosition,
						mAddress,
						miPort);
				break;
			} catch (RemoteException ex) {
				slave.handleRemoteException(ex);
				continue;
			} catch (IOException ex) {
				ex.printStackTrace();
				out.println("451 " + ex.getMessage());
				return;
			}
		}

		// say we are ready to start sending
		out.write(ftpStatus.getResponse(150, request, user, null));
		out.flush();

		// connect and start transfer
		try {
			transfer.transfer();
		} catch (RemoteException ex) {
			out.print(new FtpResponse(426, ex.getMessage()).toString());
			slave.handleRemoteException(ex);
			return; // does return prevent finally from being run?
		} catch (IOException ex) {
			out.print(new FtpResponse(426, ex.getMessage()).toString());
			return;
		}

		try {
			long transferedBytes = transfer.getTransfered();
			RemoteFile file =
				new StaticRemoteFile(
					fileName,
					user,
					transferedBytes,
					System.currentTimeMillis());
			directory.addFile(file);
			user.updateCredits((long) (user.getRatio() * transferedBytes));
			user.updateUploadedBytes(transferedBytes);
		} catch (RemoteException ex) {
			slave.handleRemoteException(ex);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		printStatus(out, "226");
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
	/*
	public void doSTOU(FtpRequest request, PrintWriter out) throws IOException {
	    
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
	    out.write(ftpStatus.getResponse(150, request, user, null));
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
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		if (request.getArgument().equalsIgnoreCase("F")) {
			out.write(ftpStatus.getResponse(200, request, user, null));
		} else {
			out.write(ftpStatus.getResponse(504, request, user, null));
		}
		/*
				if (setStructure(request.getArgument().charAt(0))) {
					out.write(ftpStatus.getResponse(200, request, user, null));
				} else {
					out.write(ftpStatus.getResponse(504, request, user, null));
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
			out.write(ftpStatus.getResponse(501, request, user, null));
			return;
		}

		setType(request.getArgument().charAt(0));

		// set it
		if (setType(request.getArgument().charAt(0))) {
			out.write(ftpStatus.getResponse(200, request, user, null));
		} else {
			out.write(ftpStatus.getResponse(504, request, user, null));
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
			out.write(ftpStatus.getResponse(501, request, user, null));
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
			user = userManager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			//out.print(new FtpResponse(530, "No such user"));
			return;
		} catch (IOException ex) {
			ex.printStackTrace();
			out.print(new FtpResponse(530, ex.getMessage()));
			return;
		}
		String masks[] =
			{
				ident + "@" + getClientAddress().getHostAddress(),
				ident + "@" + getClientAddress().getHostName()};
		user.checkIP(masks);
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
		if (user == null) {
			out.print(FtpResponse.RESPONSE_530_ACCESS_DENIED);
			//out.write(ftpStatus.getResponse(530, request, user, null));
		} else {
			//			user.setUsername(request.getArgument());
			out.print(FtpResponse.RESPONSE_331_USERNAME_OK_NEED_PASS);
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
	/**
	 * get user filesystem view
	 */
	public VirtualDirectory getVirtualDirectory() {
		return virtualDirectory;
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
				+ user.getCredits()
				+ "B] [Ratio: 1:"
				+ user.getRatio()
				+ "]");
	}

	protected String status() {
		return " [Credits: "
			+ user.getCredits()
			+ "B] [Ratio: 1:"
			+ user.getRatio()
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
	 */
	private void resetState() {
		mbRenFr = false;
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
