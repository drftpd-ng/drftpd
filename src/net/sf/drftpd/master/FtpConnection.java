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

import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import com.sun.jndi.ldap.ManageReferralControl;

import net.sf.drftpd.*;
import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.StaticRemoteFile;
import net.sf.drftpd.master.usermanager.GlftpdUserManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Slave;
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

	// command state specific temporary variables

	//unneeded? mlSkipLen = 0 is about the same
	//private boolean mbReset = false;
	private long resumePosition = 0;

	private boolean mbRenFr = false;
	private String mstRenFr = null;

	//	private boolean mbUser = false;
	//private boolean mbPass = false;
	private UserManager usermanager;

	//private FtpDataConnection mDataConnection;
	/**
	 * Set configuration file and the control socket. 
	 */
	/*
	public FtpConnection(FtpConfig cfg, Socket soc) {
	    super(cfg, soc);
	}
	*/
	public FtpConnection(
		Socket soc,
		UserManager usermanager,
		SlaveManagerImpl slavemanager,
		LinkedRemoteFile root,
		ConnectionManager connManager) {

		super(connManager, soc);
		this.usermanager = usermanager;
		this.slavemanager = slavemanager;
		this.virtualDirectory = new VirtualDirectory(root);
	}

	private VirtualDirectory virtualDirectory;
	/**
	 * get user filesystem view
	 */
	public VirtualDirectory getVirtualDirectory() {
		return virtualDirectory;
	}

	//	public final static String ANONYMOUS = "anonymous";

	private char type = 'A';

	/**
	 * Get the user data type.
	 */
	public char getType() {
		return type;
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
		type = type;
		return true;
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
	     out.write(mFtpStatus.getResponse(226, request, mUser, null));
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
	        out.write(mFtpStatus.getResponse(501, request, mUser, null));
	        return;  
	     }
	     
	     // get filenames
	     String fileName = request.getArgument();
	     fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
	     String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
	     File requestedFile = new File(physicalName);
	     String args[] = {fileName};
	     
	     // check permission
	     if(!mUser.getVirtualDirectory().hasWritePermission(physicalName, true)) {
	         out.write(mFtpStatus.getResponse(450, request, mUser, args));
	         return;
	     }
	     
	     // now transfer file data
	     out.write(mFtpStatus.getResponse(150, request, mUser, args));
	     InputStream is = null;
	     OutputStream os = null;
	     try {
	         Socket dataSoc = mDataConnection.getDataSocket();
	         if (dataSoc == null) {
	              out.write(mFtpStatus.getResponse(550, request, mUser, args));
	              return;
	         }
	         
	         is = dataSoc.getInputStream();
	         RandomAccessFile raf = new RandomAccessFile(requestedFile, "rw");
	         raf.seek(raf.length());
	         os = mUser.getOutputStream( new FileOutputStream(raf.getFD()) );
	         
	         StreamConnector msc = new StreamConnector(is, os);
	         msc.setMaxTransferRate(mUser.getMaxUploadRate());
	         msc.setObserver(this);
	         msc.connect();
	         
	         if(msc.hasException()) {
	             out.write(mFtpStatus.getResponse(451, request, mUser, args));
	         }
	         else {
	             mConfig.getStatistics().setUpload(requestedFile, mUser, msc.getTransferredSize());
	         }
	         
	         out.write(mFtpStatus.getResponse(226, request, mUser, args));
	     }
	     catch(IOException ex) {
	         out.write(mFtpStatus.getResponse(425, request, mUser, args));
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
		if (getVirtualDirectory().changeDirectory("..")) {
			String args[] = { getVirtualDirectory().getCurrentDirectory()};
			out.write(mFtpStatus.getResponse(200, request, user, args));
		} else {
			out.write(mFtpStatus.getResponse(431, request, user, null));
		}
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

		// change directory
		if (getVirtualDirectory().changeDirectory(dirName)) {
			String args[] = { getVirtualDirectory().getCurrentDirectory()};
			out.write(mFtpStatus.getResponse(200, request, user, args));
		} else {
			out.write(mFtpStatus.getResponse(431, request, user, null));
		}
	}

	/**
	 * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the file specified in the pathname to be
	 * deleted at the server site.
	 */
	/*
	 public void doDELE(FtpRequest request, PrintWriter out) throws IOException {
	    
	    // reset state variables
	    resetState();  
	     
	    // argument check
	    if(!request.hasArgument()) {
	       out.write(mFtpStatus.getResponse(501, request, mUser, null));
	       return;  
	    }    
	    
	    // get filenames
	    String fileName = request.getArgument();
	    fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
	    String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
	    File requestedFile = new File(physicalName);
	    String[] args = {fileName};
	    
	    // check permission
	    if(!mUser.getVirtualDirectory().hasWritePermission(physicalName, true)) {
	        out.write(mFtpStatus.getResponse(450, request, mUser, args));
	        return;
	    }
	    
	    // now delete
	    if(requestedFile.delete()) {
	       out.write(mFtpStatus.getResponse(250, request, mUser, args)); 
	       //mConfig.getStatistics().setDelete(requestedFile, mUser); 
	    }
	    else {
	       out.write(mFtpStatus.getResponse(450, request, mUser, args));
	    }
	 }
	*/

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
			out.write(mFtpStatus.getResponse(214, null, user, null));
			return;
		}

		// print command specific help
		String ftpCmd = request.getArgument().toUpperCase();
		String args[] = null;
		FtpRequest tempRequest = new FtpRequest(ftpCmd);
		out.write(mFtpStatus.getResponse(214, tempRequest, user, args));
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

		out.write(mFtpStatus.getResponse(150, request, user, null));
		Writer os = null;
		try {
			Socket dataSoc = getDataSocket();
			if (dataSoc == null) {
				out.write(mFtpStatus.getResponse(550, request, user, null));
				return;
			}

			if (!mbPasv) {
				os = new OutputStreamWriter(dataSoc.getOutputStream());

				if (!getVirtualDirectory()
					.printList(request.getArgument(), os)) {
					out.write(mFtpStatus.getResponse(501, request, user, null));
				} else {
					os.flush();
					out.write(mFtpStatus.getResponse(226, request, user, null));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			out.write(mFtpStatus.getResponse(425, request, user, null));
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

	public void doNLST(FtpRequest request, PrintWriter out) {
		// reset state variables
		resetState();

		out.write(mFtpStatus.getResponse(150, request, user, null));
		Writer os = null;
		try {
			Socket dataSoc = getDataSocket();
			if (dataSoc == null) {
				out.write(mFtpStatus.getResponse(550, request, user, null));
				return;
			}

			if (!mbPasv) {
				os = new OutputStreamWriter(dataSoc.getOutputStream());

				if (!getVirtualDirectory()
					.printNList(request.getArgument(), os)) {
					out.write(mFtpStatus.getResponse(501, request, user, null));
				} else {
					os.flush();
					out.write(mFtpStatus.getResponse(226, request, user, null));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			out.write(mFtpStatus.getResponse(425, request, user, null));
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

	/*
		public void doSITECHANGE(FtpRequest request, PrintWriter out) {
			if (!request.hasArgument() || !user.isAdmin()) {
				out.write(mFtpStatus.getResponse(530, request, user, null));
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
					out.write(mFtpStatus.getResponse(200, request, user, null));
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
		}
	*/

	public void doSITEADDIP(FtpRequest request, PrintWriter out) {
		resetState();
		String args[] = request.getArgument().split(" ");
		if (args.length < 2) {
			out.println("200 USAGE: SITE ADDIP <username> <ident@ip>");
			return;
		}
		User user2;
		try {
			user2 = usermanager.getUserByName(args[0]);
		} catch (NoSuchUserException ex) {
			out.println("200 No such user: " + args[0]);
			return;
		}
		user2.addIPMask(args[1]);
		usermanager.save(user2);
		out.write(mFtpStatus.getResponse(200, request, user, null));
	}

	/**
	 * Lists currently connected users.
	 */
	public void doSITEWHO(FtpRequest request, PrintWriter out) {
		resetState();
		if (!user.isAdmin()) {
			out.write(mFtpStatus.getResponse(530, request, user, null));
			return;
		}
		for (Iterator i = connManager.getConnections().iterator();
			i.hasNext();
			) {
			out.println("200- " + i.next());
		}
		out.write(mFtpStatus.getResponse(200, request, user, null));
	}

	public void doSITELIST(FtpRequest request, PrintWriter out) {
		resetState();
		RemoteFile files[] =
			getVirtualDirectory().getCurrentDirectoryFile().listFiles();
		for (int i = 0; i < files.length; i++) {
			RemoteFile file = files[i];
			out.write("200- " + file.toString() + "\r\n");
		}
		out.write(mFtpStatus.getResponse(200, request, user, null));
	}

	public void doSITEUSER(FtpRequest request, PrintWriter out) {
		resetState();
		if (!user.isAdmin()) {
			out.write(mFtpStatus.getResponse(530, request, user, null));
			return;
		}
		//int pos = request.getArgument().indexOf(" ") + 1;
		try {
			User user = usermanager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			out.println("200- user " + request.getArgument() + " not found");
			out.write(mFtpStatus.getResponse(200, request, user, null));
			return;
		}
		GlftpdUserManager.GlftpdUser gluser = null;
		if (user instanceof GlftpdUserManager.GlftpdUser) {
			gluser = (GlftpdUserManager.GlftpdUser) user;
		}
		out.write("200- " + user.getComment());
		out.write(
			"200- +=======================================================================+\r\n");
		out.write(
			"200- | Username: "
				+ user.getUsername()
				+ " Created: UNKNOWN \r\n");
		int i = (int) (user.getTimeToday() / 1000);
		int hours = i / 60;
		int minutes = i - hours * 60;
		out.write(
			"200- | Time On Today: "
				+ hours
				+ ":"
				+ minutes
				+ " Last seen: Wed Jul 17 13:19:56 2002\r\n");

		out.write(
			"200- | Flags: "
				+ (gluser != null ? gluser.getFlags() : "")
				+ "   Idle time: 10                      \r\n");
		out.write(
			"200- | Ratio: 1:3                         Credits:    2868.8 MB              \r\n");
		out.write(
			"200- | Total Logins: 680                  Current Logins: 0                  \r\n");
		out.write(
			"200- | Max Logins: 3                      From same IP: Unlimited            \r\n");
		out.write(
			"200- | Max Sim Uploads: Unlimited         Max Sim Downloads: Unlimited       \r\n");
		out.write(
			"200- | Max Upload Speed:"
				+ (user.getMaxUploadRate() / 1000F)
				+ " K/s      Max Download Speed:"
				+ (user.getMaxDownloadRate() / 1000F)
				+ " K/s    \r\n");
		out.write(
			"200- | Times Nuked: "
				+ user.getTimesNuked()
				+ "    Bytes Nuked:    568 MB             \r\n");
		out.write(
			"200- | Weekly Allotment:     0 MB         Messages Waiting: Not implemented            \r\n");
		out.write(
			"200- | Time Limit: "
				+ user.getTimelimit()
				+ " minutes.          (0 = Unlimited)                    \r\n");
		if (gluser != null) {
			out.write("200- | " + gluser.getSlots() + " \r\n");
		}
		out.write(
			"200- | Gadmin/Leech Slots: -1 -1          (-1 = Unlimited, None)             \r\n");
		out.write("200- | Tagline: " + user.getTagline() + " \r\n");
		out.write("200- | Groups: " + user.getGroups() + " \r\n");
		if (user instanceof GlftpdUserManager.GlftpdUser) {
			out.write(
				"200- | Priv Groups: "
					+ ((GlftpdUserManager.GlftpdUser) user).getPrivateGroups()
					+ " \r\n");
		}
		out.write(
			"200- +-----------------------------------------------------------------------+\r\n");
		out.write("200- | IP0: IP1: \r\n");
		out.write("200- | IP2: IP3: \r\n");
		out.write("200- | IP4: IP5: \r\n");
		out.write("200- | IP6: IP7: \r\n");
		out.write("200- | IP8: IP9: \r\n");
		out.write(
			"200- +=======================================================================+\r\n");
		out.write(mFtpStatus.getResponse(200, request, user, null));
	}
	
	public void doSITECHECKSLAVES(FtpRequest request, PrintWriter out) {
		out.println("200 Ok, "+slavemanager.verifySlaves()+" stale slaves removed");
	}
	/**
	 * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 * 
	 * Returns the date and time of when a file was modified.
	 */
	/*
	 public void doMDTM(FtpRequest request, PrintWriter out) throws IOException {
	     
	     // argument check
	     if(!request.hasArgument()) {
	        out.write(mFtpStatus.getResponse(501, request, mUser, null));
	        return;  
	     }
	    
	     // reset state variables
	     resetState();
	    
	     // get filenames
	     String fileName = request.getArgument();
	     fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
	     String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
	     File reqFile = new File(physicalName);
	
	     // now print date
	     if(reqFile.exists()) {
	         String args[] = {DATE_FMT.format(new Date(reqFile.lastModified()))};
	         out.write(mFtpStatus.getResponse(213, request, mUser, args));
	     }
	     else {
	         out.write(mFtpStatus.getResponse(550, request, mUser, null));
	     }
	 }
	*/

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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		// get filenames
		String fileName = getVirtualDirectory().getAbsoluteName(request.getArgument());
		LinkedRemoteFile file = getVirtualDirectory().getCurrentDirectoryFile();
		try {
			file.mkdir(fileName);
			out.write(mFtpStatus.getResponse(250, request, user, new String[] {fileName})); 			
		} catch(IOException ex) {
			out.write(mFtpStatus.getResponse(550, request, user, null));
			ex.printStackTrace();
		}
//		String args[] = { fileName };

		// check permission
//		if (!getVirtualDirectory().hasCreatePermission(physicalName, true)) {
//			out.write(mFtpStatus.getResponse(450, request, user, args));
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		if(request.getArgument().equalsIgnoreCase("S")) {
			
		} else {
			out.write(mFtpStatus.getResponse(504, request, user, null));
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
	/*
	 public void doNLST(FtpRequest request, PrintWriter out) throws IOException {
	     
	     // reset state variables
	     resetState();
	     
	     out.write(mFtpStatus.getResponse(150, request, mUser, null));
	     Writer os = null;
	     try {
	         Socket dataSoc = mDataConnection.getDataSocket();
	         if (dataSoc == null) {
	              out.write(mFtpStatus.getResponse(550, request, mUser, null));
	              return;
	         }
	         
	         os = new OutputStreamWriter(dataSoc.getOutputStream());
	         
	         if (!mUser.getVirtualDirectory().printNList(request.getArgument(), os)) {
	             out.write(mFtpStatus.getResponse(501, request, mUser, null));
	         }
	         else {
	            os.flush();
	            out.write(mFtpStatus.getResponse(226, request, mUser, null));
	         }
	     }
	     catch(IOException ex) {
	         out.write(mFtpStatus.getResponse(425, request, mUser, null));
	     }
	     finally {
	     try {
		 os.close();
	     } catch(Exception ex) {
		 e.printStackTrace();
	     }
	         mDataConnection.reset();
	     }
	 }
	*/

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

		out.write(mFtpStatus.getResponse(200, request, user, null));
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
		        out.write(mFtpStatus.getResponse(500, request, mUser, null));
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
			out.write(mFtpStatus.getResponse(230, request, user, args));
			authenticated = true;
		} else {
			out.write(mFtpStatus.getResponse(530, request, user, args));
			/*
			ConnectionService conService = mConfig.getConnectionService();
			if (conService != null) {
			    conService.closeConnection(mUser.getSessionId());
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
	/*
	 public void doPASV(FtpRequest request, PrintWriter out) throws IOException {
	     if (!setPasvCommand()) {
	         out.write(mFtpStatus.getResponse(550, request, mUser, null));
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
	     
	     out.write(mFtpStatus.getResponse(227, request, mUser, args));
	     if (!listenPasvConnection()) {
	        out.write(mFtpStatus.getResponse(425, request, mUser, args));
	     }
	 }
	*/

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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");
		if (st.countTokens() != 6) {
			out.write(mFtpStatus.getResponse(510, request, user, null));
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
			out.write(mFtpStatus.getResponse(553, request, user, null));
			return;
		}

		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			out.write(mFtpStatus.getResponse(552, request, user, null));
			return;
		}
		setPortCommand(clientAddr, clientPort);
		out.write(mFtpStatus.getResponse(200, request, user, null));
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
		String args[] = { getVirtualDirectory().getCurrentDirectory()};
		out.write(mFtpStatus.getResponse(257, request, user, args));
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
		out.write(mFtpStatus.getResponse(221, request, user, null));

		stop();
		/*
		    ConnectionService conService = mConfig.getConnectionService();
		    if (conService != null) {
		        conService.closeConnection(mUser.getSessionId());
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		// set state variables
		resetState();
		//mlSkipLen = 0; // already set by resetState()
		String skipNum = request.getArgument();
		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}
		if (resumePosition < 0) {
			//mlSkipLen = 0; // it is already 0
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}
		//		mbReset = true;
		out.write(mFtpStatus.getResponse(350, request, user, null));
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
				System.out.println("type = "+type);
		System.out.println("getType() = "+getType());

		// set state variables
		//		long skipLen = (mbReset) ? mlSkipLen : 0;
		long resumePosition = this.resumePosition;
		resetState();

		// argument check
		if (!request.hasArgument()) {
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		//fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
		//String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
		//File requestedFile = new File(physicalName);
		LinkedRemoteFile remoteFile;
		RemoteFile staticRemoteFile;
		try {
			remoteFile = getVirtualDirectory().getAbsoluteFile(fileName);
		} catch (FileNotFoundException ex) {
			//out.write(mFtpStatus.getResponse(550, request, user, null));
			out.println("550 " + fileName + ": No such file");
			return;
		}
		if (!remoteFile.isFile()) {
			out.println("550 " + fileName + ": not a plain file.");
			return;
		}
		//String args[] = { fileName };
		if (user.getCredits() < remoteFile.length()) {
			out.println("550 Not enough credits.");
			return;
		}
		/*
		// check permission
	    if(!mUser.getVirtualDirectory().hasReadPermission(physicalName, true)) {
	        out.write(mFtpStatus.getResponse(550, request, mUser, args));
	        return;
	    }
		*/
		Transfer transfer;
		while (true) {
			RemoteSlave slave = remoteFile.getAnySlave();

			// get socket depending on the selection
			if (mbPort) {
				try {
					transfer = slave.getSlave().doConnectSend(
							new StaticRemoteFile(remoteFile),
							getType(),
							resumePosition,
							mAddress,
							miPort);
				} catch (RemoteException ex) {
					slavemanager.handleRemoteException(ex, slave);
					continue;
				} catch (FileNotFoundException ex) {
					out.write(mFtpStatus.getResponse(550, request, user, null));
					return;
				} catch (IOException ex) {
					ex.printStackTrace();
					return;
				}
				break;
			}
			//TODO: passive transfer
		}
		out.write(mFtpStatus.getResponse(150, request, user, null));
		out.flush();
		try {
			transfer.transfer();
		} catch(RemoteException ex) {
			ex.printStackTrace();
		} catch(IOException ex) {
			ex.printStackTrace();
		}

		out.write(mFtpStatus.getResponse(226, request, user, null));
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
	/*
	 public void doRMD(FtpRequest request, PrintWriter out) throws IOException {
	    
	    // reset state variables
	    resetState(); 
	     
	    // argument check
	    if(!request.hasArgument()) {
	        out.write(mFtpStatus.getResponse(501, request, mUser, null));
	        return;  
	    }
	    
	    // get file names
	    String fileName = request.getArgument();
	    fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
	    String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
	    File requestedFile = new File(physicalName);
	    String args[] = {fileName};
	    
	    // check permission
	    if(!mUser.getVirtualDirectory().hasWritePermission(physicalName, true)) {
	        out.write(mFtpStatus.getResponse(450, request, mUser, args));
	        return;
	    }
	    
	    // now delete
	    if(requestedFile.delete()) {
	       out.write(mFtpStatus.getResponse(250, request, mUser, args)); 
	    }
	    else {
	       out.write(mFtpStatus.getResponse(450, request, mUser, args));
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
	/*
	 public void doRNFR(FtpRequest request, PrintWriter out) throws IOException {
	     
	     // reset state variable
	     resetState();
	     
	     // argument check
	     if(!request.hasArgument()) {
	        out.write(mFtpStatus.getResponse(501, request, mUser, null));
	        return;  
	     }
	     
	     // set state variable
	     mbRenFr = true;
	     
	     // get filenames
	     String fileName = request.getArgument();
	     fileName = mUser.getVirtualDirectory().getAbsoluteName(fileName);
	     mstRenFr = mUser.getVirtualDirectory().getPhysicalName(fileName);
	     String args[] = {fileName};
	     
	     out.write(mFtpStatus.getResponse(350, request, mUser, args));
	 }
	*/

	/**
	 * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command specifies the new pathname of the file
	 * specified in the immediately preceding "rename from"
	 * command.  Together the two commands cause a file to be
	 * renamed.
	 */
	/*
	 public void doRNTO(FtpRequest request, PrintWriter out) throws IOException {
	     
	     // argument check
	     if(!request.hasArgument()) {
	        resetState(); 
	        out.write(mFtpStatus.getResponse(501, request, mUser, null));
	        return;  
	     }
	     
	     // set state variables
	     if((!mbRenFr) || (mstRenFr == null)) {
	          resetState();
	          out.write(mFtpStatus.getResponse(100, request, mUser, null));
	          return;
	     }
	     
	     // get filenames
	     String fromFileStr = mUser.getVirtualDirectory().getVirtualName(mstRenFr);
	     String toFileStr = request.getArgument();
	     toFileStr = mUser.getVirtualDirectory().getAbsoluteName(toFileStr);
	     String physicalToFileStr = mUser.getVirtualDirectory().getPhysicalName(toFileStr);
	     File fromFile = new File(mstRenFr);
	     File toFile = new File(physicalToFileStr);
	     String args[] = {fromFileStr, toFileStr};
	     
	     resetState();
	     
	     // check permission
	     if(!mUser.getVirtualDirectory().hasCreatePermission(physicalToFileStr, true)) {
	        out.write(mFtpStatus.getResponse(553, request, mUser, null));
	        return;
	     }
	     
	     // now rename
	     if(fromFile.renameTo(toFile)) {
	         out.write(mFtpStatus.getResponse(250, request, mUser, args));
	     }
	     else {
	         out.write(mFtpStatus.getResponse(553, request, mUser, args));
	     }
	 } 
	*/

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
	     //SiteCommandHandler siteCmd = new SiteCommandHandler( mConfig, mUser );
	     SiteCommandHandler siteCmd = new SiteCommandHandler(mUser);
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}
		RemoteFile file;
		try {
			file = getVirtualDirectory().getAbsoluteFile(request.getArgument());
		} catch (FileNotFoundException ex) {
			out.write(mFtpStatus.getResponse(550, request, user, null));
			return;
		}
		out.write(
			mFtpStatus.getResponse(
				213,
				request,
				user,
				new String[] { "" + file.length()}));
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
	        mUser.getName()
	     };
	   
	     out.write(mFtpStatus.getResponse(211, request, mUser, args)); 
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		// get filenames
		String fileName = request.getArgument();
		fileName = getVirtualDirectory().getAbsoluteName(fileName);
//		String physicalName = getVirtualDirectory().getPhysicalName(fileName);
		
		
		//		File requestedFile = new File(physicalName);

		// get permission
		/*
				if (!mUser
					.getVirtualDirectory()
					.hasCreatePermission(physicalName, true)) {
					out.write(mFtpStatus.getResponse(550, request, mUser, null));
					return;
				}
		*/

		// now transfer file data

		out.write(mFtpStatus.getResponse(150, request, user, null));
		RemoteSlave slave;
		try {
			slave =
				slavemanager.getASlave(TransferImpl.TRANSFER_RECEIVING);
		} catch (NoAvailableSlaveException ex) {
			//TODO: send correct error to client
			out.write(mFtpStatus.getResponse(530, request, user, null));
			ex.printStackTrace();
			return;
		}
		
		
		/*
				InputStream is = null;
				OutputStream os = null;
				try {
					Socket dataSoc = mDataConnection.getDataSocket();
					if (dataSoc == null) {
						out.write(mFtpStatus.getResponse(550, request, mUser, null));
						return;
					}
		
					is = dataSoc.getInputStream();
		
					RandomAccessFile raf = new RandomAccessFile(requestedFile, "rw");
					raf.seek(skipLen);
					os = mUser.getOutputStream(new FileOutputStream(raf.getFD()));
		
					StreamConnector msc = new StreamConnector(is, os);
					msc.setMaxTransferRate(mUser.getMaxUploadRate());
					msc.setObserver(this);
					msc.connect();
		
					if (msc.hasException()) {
						out.write(mFtpStatus.getResponse(451, request, mUser, null));
						return;
					} else {
						mConfig.getStatistics().setUpload(
							requestedFile,
							mUser,
							msc.getTransferredSize());
					}
		
					out.write(mFtpStatus.getResponse(226, request, mUser, null));
				} catch (IOException ex) {
					out.write(mFtpStatus.getResponse(425, request, mUser, null));
				} finally {
					try {
						is.close();
						os.close();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					mDataConnection.reset();
				}
		*/
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
	    String fileName = mUser.getVirtualDirectory().getAbsoluteName("ftp.dat");
	    String physicalName = mUser.getVirtualDirectory().getPhysicalName(fileName);
	    File requestedFile = new File(physicalName);
	    requestedFile = IoUtils.getUniqueFile(requestedFile);
	    fileName = mUser.getVirtualDirectory().getVirtualName(requestedFile.getAbsolutePath());
	    String args[] = {fileName};
	    
	    // check permission
	    if(!mUser.getVirtualDirectory().hasCreatePermission(fileName, false)) {
	        out.write(mFtpStatus.getResponse(550, request, mUser, null));
	        return;
	    }
	    
	    // now transfer file data
	    out.write(mFtpStatus.getResponse(150, request, mUser, null));
	    InputStream is = null;
	    OutputStream os = null;
	    try {
	        Socket dataSoc = mDataConnection.getDataSocket();
	        if (dataSoc == null) {
	             out.write(mFtpStatus.getResponse(550, request, mUser, args));
	             return;
	        }
	
	        
	        is = dataSoc.getInputStream();
	        os = mUser.getOutputStream( new FileOutputStream(requestedFile) );
	        
	        StreamConnector msc = new StreamConnector(is, os);
	        msc.setMaxTransferRate(mUser.getMaxUploadRate());
	        msc.setObserver(this);
	        msc.connect();
	        
	        if(msc.hasException()) {
	            out.write(mFtpStatus.getResponse(451, request, mUser, null));
	            return;
	        }
	        else {
	            mConfig.getStatistics().setUpload(requestedFile, mUser, msc.getTransferredSize());
	        }
	        
	        out.write(mFtpStatus.getResponse(226, request, mUser, null));
	        mDataConnection.reset();
	        out.write(mFtpStatus.getResponse(250, request, mUser, args));
	    }
	    catch(IOException ex) {
	        out.write(mFtpStatus.getResponse(425, request, mUser, null));
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}
		
		if(request.getArgument().equalsIgnoreCase("F")) {
			out.write(mFtpStatus.getResponse(200, request, user, null));
		} else {
			out.write(mFtpStatus.getResponse(504, request, user, null));
		}
/*
		if (setStructure(request.getArgument().charAt(0))) {
			out.write(mFtpStatus.getResponse(200, request, user, null));
		} else {
			out.write(mFtpStatus.getResponse(504, request, user, null));
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

		String args[] = { "UNIX" };
		out.write(mFtpStatus.getResponse(215, request, user, args));
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}

		setType(request.getArgument().charAt(0));

		// set it
		if (setType(request.getArgument().charAt(0))) {
			out.write(mFtpStatus.getResponse(200, request, user, null));
		} else {
			out.write(mFtpStatus.getResponse(504, request, user, null));
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
			out.write(mFtpStatus.getResponse(501, request, user, null));
			return;
		}
		Ident id = new Ident(mControlSocket);
		String ident = "";
		if (id.successful) {
			ident = id.userName;
		} else {
			System.out.println(
				"Failed to get ident response: " + id.errorMessage);
		}
		try {
			user = usermanager.getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			// TODO: what error code for "no such user" ???
			out.write(mFtpStatus.getResponse(530, request, user, null));
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
						out.write(mFtpStatus.getResponse(230, request, user, null));
						return;
					}
					    else {
					        mConfig.getConnectionService().closeConnection(mUser.getSessionId());
					    }
				}
				*/

		// set user name and send appropriate message
		if (user == null) {
			out.write(mFtpStatus.getResponse(530, request, user, null));
		} else {
			//			user.setUsername(request.getArgument());
			out.write(mFtpStatus.getResponse(331, request, user, null));
		}
		/*
		if (user.isAnonymous()) {
			//if(mConfig.isAnonymousLoginAllowed()) { 
			FtpRequest anoRequest = new FtpRequest(user.getUsername());
			out.write(mFtpStatus.getResponse(331, anoRequest, user, null));
			/*
			    }
			    else {
			        out.write(mFtpStatus.getResponse(530, request, mUser, null));
			        ConnectionService conService = mConfig.getConnectionService();
			        if (conService != null) {
			           conService.closeConnection(mUser.getSessionId());
			        }
			    }
			*/
	}
}
