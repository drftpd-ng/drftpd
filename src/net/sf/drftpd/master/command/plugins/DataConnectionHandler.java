/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.drftpd.AsciiOutputStream;
import net.sf.drftpd.Bytes;
import net.sf.drftpd.Checksum;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class DataConnectionHandler implements CommandHandler, Cloneable {
	private static Logger logger =
		Logger.getLogger(DataConnectionHandler.class);
	private Socket _dataSocket;
	private RemoteSlave _rslave;
	private Transfer _transfer;
	private LinkedRemoteFile _transferFile;
	private InetAddress mAddress;
	public boolean mbPasv = false;

	public boolean mbPort = false;
	private int miPort = 0;
	private ServerSocket mServSoc;
	private boolean preTransfer = false;
	private RemoteSlave preTransferRSlave;

	private long resumePosition = 0;
	private char type = 'A';
	private short xdupe = 0;

	/**
	 * 
	 */
	public DataConnectionHandler() {
		super();
	}

	/**
	 * <code>MODE &lt;SP&gt; <mode-code> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * the data transfer modes described in the Section on
	 * Transmission Modes.
	 */
	private FtpReply doMODE(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		if (request.getArgument().equalsIgnoreCase("S")) {
			return FtpReply.RESPONSE_200_COMMAND_OK;
		} else {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
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
	private FtpReply doPASV(BaseFtpConnection conn) {
		conn.resetState();
		if (!preTransfer) {
			return new FtpReply(
				500,
				"You need to use a client supporting PRET (PRE Transfer) to use PASV");
		}

		if (preTransferRSlave == null) {
			if (!setPasvCommand(conn)) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		} else {
			try {
				_transfer = preTransferRSlave.getSlave().listen();
				mAddress = preTransferRSlave.getInetAddress();
				miPort = _transfer.getLocalPort();
				mbPasv = true;
			} catch (RemoteException e) {
				preTransferRSlave.handleRemoteException(e);
				return new FtpReply(450, "Remote error: " + e.getMessage());
			} catch (NoAvailableSlaveException e) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			} catch (IOException e) {
				logger.log(Level.FATAL, "", e);
				return new FtpReply(450, e.getMessage());
			}
		}
		//InetAddress mAddress == getInetAddress();
		//miPort == getPort();

		String addrStr =
			mAddress.getHostAddress().replace('.', ',')
				+ ','
				+ (miPort >> 8)
				+ ','
				+ (miPort & 0xFF);
		return new FtpReply(227, "Entering Passive Mode (" + addrStr + ").");
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
	private FtpReply doPORT(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// reset state variables
		conn.resetState();

		InetAddress clientAddr = null;
		int clientPort = 0;
		preTransfer = false;
		preTransferRSlave = null;
		// argument check
		if (!request.hasArgument()) {
			//Syntax error in parameters or arguments
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");
		if (st.countTokens() != 6) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
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
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		String portHostAddress = clientAddr.getHostAddress();
		String clientHostAddress =
			conn.getControlSocket().getInetAddress().getHostAddress();
		if ((portHostAddress.startsWith("192.168.")
			&& !clientHostAddress.startsWith("192.168.")) || (portHostAddress.startsWith("10.") && !clientHostAddress.startsWith("10."))) {
			FtpReply response = new FtpReply(501);
			response.addComment("==YOU'RE BEHIND A NAT ROUTER==");
			response.addComment(
				"Configure the firewall settings of your FTP client");
			response.addComment(
				"  to use your real IP: "
					+ conn.getControlSocket().getInetAddress().getHostAddress());
			response.addComment("And set up port forwarding in your router.");
			response.addComment(
				"Or you can just use a PRET capable client, see");
			response.addComment(
				"  http://drftpd.mog.se/ for PRET capable clients");
			return response;
		}

		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			//out.write(ftpStatus.getResponse(552, request, user, null));
		}

		setPortCommand(conn, clientAddr, clientPort);

		//Notify the user that this is not his IP.. Good for NAT users that aren't aware that their IP has changed.
		if (!clientAddr.equals(conn.getControlSocket().getInetAddress())) {
			return new FtpReply(
				200,
				"FXP allowed. If you're not FXPing and set your IP to "
					+ conn.getControlSocket().getInetAddress().getHostAddress()
					+ " (usually in firewall settings)");
		}
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	private FtpReply doPRET(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		FtpRequest ghostRequest = new FtpRequest(request.getArgument());
		String cmd = ghostRequest.getCommand();
		if (cmd.equals("LIST") || cmd.equals("NLST")) {
			preTransferRSlave = null;
			preTransfer = true;
			return new FtpReply(
				200,
				"OK, will use master for upcoming transfer");
		} else if (cmd.equals("RETR")) {
			try {
				preTransferRSlave =
					conn
						.getCurrentDirectory()
						.lookupFile(ghostRequest.getArgument())
						.getASlaveForDownload();
				preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ preTransferRSlave.getName()
						+ " for upcoming transfer");
			} catch (NoAvailableSlaveException e) {
				conn.reset();
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			} catch (FileNotFoundException e) {
				conn.reset();
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		} else if (cmd.equals("STOR")) {
			try {
				preTransferRSlave =
					conn.getSlaveManager().getASlave(
						Transfer.TRANSFER_RECEIVING_UPLOAD);
				preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ preTransferRSlave.getName()
						+ " for upcoming transfer");
			} catch (NoAvailableSlaveException e) {
				conn.reset();
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			}
		} else {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
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
	private FtpReply doREST(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// set state variables
		conn.resetState();

		String skipNum = request.getArgument();
		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		if (resumePosition < 0) {
			resumePosition = 0;
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		return FtpReply.RESPONSE_350_PENDING_FURTHER_INFORMATION;
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

	private FtpReply doRETR(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		// set state variables
		long resumePosition = this.resumePosition;
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// get filenames
		String fileName = request.getArgument();
		try {
			_transferFile = conn.getCurrentDirectory().lookupFile(fileName);
		} catch (FileNotFoundException ex) {
			return new FtpReply(550, fileName + ": No such file");
		}
		if (!conn
			.getConfig()
			.checkPrivPath(conn.getUserNull(), _transferFile)) {
			return new FtpReply(550, fileName + ": No such file");
		}
		if (!_transferFile.isFile()) {
			return new FtpReply(550, _transferFile + ": not a plain file.");
		}
		if (conn.getUserNull().getRatio() != 0
			&& conn.getUserNull().getCredits() < _transferFile.length()) {
			return new FtpReply(550, "Not enough credits.");
		}

		if (!conn
			.getConnectionManager()
			.getConfig()
			.checkDownload(conn.getUserNull(), _transferFile)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		//SETUP rslave
		if (mbPasv) {
			assert preTransfer == true;

			if (!_transferFile.getSlaves().contains(preTransferRSlave)) {
				return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
			}
			_rslave = preTransferRSlave;
			preTransferRSlave = null;
			//preTransfer = false;
		} else {

			try {
				_rslave =
					_transferFile.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD);
			} catch (NoAvailableSlaveException ex) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			}
		}

		// SETUP this.transfer
		if (mbPort) {
			try {
				_transfer =
					_rslave.getSlave().connect(getInetAddress(), getPort());
			} catch (RemoteException ex) {
				_rslave.handleRemoteException(ex);
				return new FtpReply(450, "Remote error: " + ex.getMessage());
			} catch (IOException ex) {
				logger.log(Level.FATAL, "rslave=" + _rslave, ex);
				return new FtpReply(
					450,
					ex.getClass().getName()
						+ " from slave: "
						+ ex.getMessage());
			}
		} else if (mbPasv) {

			// transfere was already set up in doPASV(..., ...)
			//			try {
			//				rslave.getSlave().doListenSend(
			//					remoteFile.getPath(),
			//					getType(),
			//					resumePosition);
			//			} catch (RemoteException ex) {
			//				preTransferRSlave.handleRemoteException(ex);
			//				out.print(
			//					new FtpResponse(450, "Remote error: " + ex.getMessage()));
			//				return;
			//			} catch (NoAvailableSlaveException e1) {
			//				out.print(FtpResponse.RESPONSE_450_SLAVE_UNAVAILABLE);
			//				return;
			//			} catch (IOException ex) {
			//				out.print(
			//					new FtpResponse(
			//						450,
			//						ex.getClass().getName()
			//							+ " from slave: "
			//							+ ex.getMessage()));
			//				logger.log(Level.FATAL, "rslave=" + rslave, ex);
			//				return;
			//			}
		} else {
			return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
		}
		assert _transfer != null;
		{
			PrintWriter out = conn.getControlWriter();
			out.print(
				new FtpReply(
					150,
					"File status okay; about to open data connection from "
						+ _rslave.getName()
						+ "."));
			out.flush();
		}
		try {
			_transfer.sendFile(
				_transferFile.getPath(),
				getType(),
				resumePosition,
				true);
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
			return new FtpReply(426, "Remote error: " + ex.getMessage());
		} catch (IOException ex) {
			logger.log(Level.WARN, "from " + _rslave.getName(), ex);
			return new FtpReply(426, "IO error: " + ex.getMessage());
		}
		//		TransferThread transferThread = new TransferThread(rslave, transfer);
		//		System.err.println("Calling interruptibleSleepUntilFinished");
		//		try {
		//			transferThread.interruptibleSleepUntilFinished();
		//		} catch (Throwable e1) {
		//			e1.printStackTrace();
		//		}
		//		System.err.println("Finished");

		FtpReply response =
			(FtpReply) FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION.clone();

		try {
			long checksum = _transfer.getChecksum();
			response.addComment(
				"Checksum: " + Checksum.formatChecksum(checksum));
			long cachedChecksum;

			cachedChecksum = _transferFile.getCheckSumCached();
			if (cachedChecksum == 0) {
				_transferFile.setCheckSum(_transfer.getChecksum());
			} else if (cachedChecksum != checksum) {
				response.addComment(
					"WARNING: checksum from transfer didn't match cached checksum");
				logger.info(
					"checksum from transfer "
						+ Checksum.formatChecksum(checksum)
						+ "didn't match cached checksum"
						+ Checksum.formatChecksum(cachedChecksum)
						+ " for "
						+ _transferFile.toString()
						+ " from slave "
						+ _rslave.getName(),
					new Throwable());
			}

			try {
				long sfvChecksum =
					_transferFile
						.getParentFileNull()
						.lookupSFVFile()
						.getChecksum(
						_transferFile.getName());
				if (sfvChecksum == checksum) {
					response.addComment(
						"checksum from transfer matched checksum in .sfv");
				} else {
					response.addComment(
						"WARNING: checksum from transfer didn't match checksum in .sfv");
				}
			} catch (NoAvailableSlaveException e1) {
				response.addComment(
					"slave with .sfv offline, checksum not verified");
			} catch (FileNotFoundException e1) {
				//continue without verification
			} catch (ObjectNotFoundException e1) {
				//file not found in .sfv, continue
			} catch (IOException e1) {
				logger.info(e1);
				response.addComment(
					"IO Error reading sfv file: " + e1.getMessage());
			}

			long transferedBytes = _transfer.getTransfered();

			float ratio =
				conn.getConfig().getCreditLossRatio(
					_transferFile,
					conn.getUserNull());
			if (ratio != 0) {
				conn.getUserNull().updateCredits(
					(long) (-transferedBytes * ratio));
			}
			conn.getUserNull().updateDownloadedBytes(transferedBytes);
			conn.getUserNull().commit();
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
		} catch (UserFileException e) {
		}
		conn.reset();
		return response;
	}

	private FtpReply doSITE_RESCAN(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		conn.resetState();
		boolean forceRescan =
			(request.hasArgument()
				&& request.getArgument().equalsIgnoreCase("force"));
		LinkedRemoteFile directory = conn.getCurrentDirectory();
		SFVFile sfv;
		try {
			sfv = conn.getCurrentDirectory().lookupSFVFile();
		} catch (Exception e) {
			return new FtpReply(
				200,
				"Error getting SFV File: " + e.getMessage());
		}
		PrintWriter out = conn.getControlWriter();
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
				out.println(
					"200- SFV: "
						+ Checksum.formatChecksum(checkSum.longValue())
						+ " SLAVE: "
						+ fileName
						+ " MISSING");
				continue;
			}
			String status;
			long fileCheckSum;
			try {
				if (forceRescan) {
					fileCheckSum = file.getCheckSumFromSlave();
				} else {
					fileCheckSum = file.getCheckSum();
				}
			} catch (NoAvailableSlaveException e1) {
				out.println(
					"200- "
						+ fileName
						+ "SFV: "
						+ Checksum.formatChecksum(checkSum.longValue())
						+ " SLAVE: OFFLINE");
				continue;
			} catch (IOException ex) {
				out.print(
					"200- "
						+ fileName
						+ " SFV: "
						+ Checksum.formatChecksum(checkSum.longValue())
						+ " SLAVE: IO error: "
						+ ex.getMessage());
				continue;
			}

			if (fileCheckSum == 0L) {
				status = "FAILED - failed to checksum file";
			} else if (checkSum.longValue() == fileCheckSum) {
				status = "OK";
			} else {
				status = "FAILED - checksum missmatch";
			}

			out.println(
				"200- "
					+ fileName
					+ " SFV: "
					+ Checksum.formatChecksum(checkSum.longValue())
					+ " SLAVE: "
					+ Checksum.formatChecksum(checkSum.longValue())
					+ " "
					+ status);
			return null;
		}
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	private FtpReply doSITE_XDUPE(BaseFtpConnection conn) {
		return FtpReply.RESPONSE_502_COMMAND_NOT_IMPLEMENTED;
		//		resetState();
		//
		//		if (!request.hasArgument()) {
		//			if (this.xdupe == 0) {
		//				out.println("200 Extended dupe mode is disabled.");
		//			} else {
		//				out.println(
		//					"200 Extended dupe mode " + this.xdupe + " is enabled.");
		//			}
		//			return;
		//		}
		//
		//		short myXdupe;
		//		try {
		//			myXdupe = Short.parseShort(request.getArgument());
		//		} catch (NumberFormatException ex) {
		//			out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
		//			return;
		//		}
		//
		//		if (myXdupe > 0 || myXdupe < 4) {
		//			out.print(
		//				FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
		//			return;
		//		}
		//		this.xdupe = myXdupe;
		//		out.println("200 Activated extended dupe mode " + myXdupe + ".");
	}
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
	private FtpReply doSTOR(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		long resumePosition = this.resumePosition;
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		//SETUP targetDir and targetFilename
		Object ret[] =
			conn.getCurrentDirectory().lookupNonExistingFile(
				request.getArgument());
		LinkedRemoteFile targetDir = (LinkedRemoteFile) ret[0];
		String targetFilename = (String) ret[1];

		if (targetFilename == null) {
			// target exists, this could be overwrite or resume
			// if(resumePosition != 0) {} // resume
			//TODO overwrite & resume files.

			return new FtpReply(550, "Requested action not taken. File exists");
			//_transferFile = targetDir;
			//targetDir = _transferFile.getParent();
			//			if(_transfereFile.getOwner().equals(getUser().getUsername())) {
			//				// allow overwrite/resume
			//			}
			//			if(directory.isDirectory()) {
			//				out.print(FtpResponse.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN);
			//			}
		}

		if (!LIST.isLegalFileName(targetFilename)
			|| !conn.getConfig().checkPrivPath(conn.getUserNull(), targetDir)) {
			return new FtpReply(
				553,
				"Requested action not taken. File name not allowed.");
		}

		if (!conn.getConfig().checkUpload(conn.getUserNull(), targetDir)) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		//SETUP rslave
		if (preTransfer) {
			assert preTransferRSlave != null : "preTransferRSlave";
			_rslave = preTransferRSlave;
			preTransferRSlave = null;
			preTransfer = false;
		} else {
			try {
				_rslave =
					conn.getSlaveManager().getASlave(
						Transfer.TRANSFER_RECEIVING_UPLOAD);
				assert _rslave != null : "_rslave";
			} catch (NoAvailableSlaveException ex) {
				logger.log(Level.FATAL, ex.getMessage());
				return FtpReply.RESPONSE_450_SLAVE_UNAVAILABLE;
			}
		}
		assert _rslave != null : "_rslave2";
		List rslaves = Collections.singletonList(_rslave);
		//		ArrayList rslaves = new ArrayList();
		//		rslaves.add(rslave);
		StaticRemoteFile uploadFile =
			new StaticRemoteFile(
				rslaves,
				targetFilename,
				conn.getUserNull().getUsername(),
				conn.getUserNull().getGroupName(),
				0L,
				System.currentTimeMillis(),
				0L);
		_transferFile = targetDir.addFile(uploadFile);

		//SETUP transfer
		if (mbPort) {
			try {
				_transfer =
					_rslave.getSlave().connect(getInetAddress(), getPort());
			} catch (RemoteException e1) {
				_rslave.handleRemoteException(e1);
				return new FtpReply(451, "Remote error: " + e1.getMessage());
			} catch (NoAvailableSlaveException e1) {
				return FtpReply.RESPONSE_450_SLAVE_UNAVAILABLE;
			}
		} else {
			assert mbPasv : "mbPasv";
			// _transfer is already set up
		}
		{
			PrintWriter out = conn.getControlWriter();
			// say we are ready to start sending
			out.print(
				new FtpReply(
					150,
					"File status okay, about to open data connection to "
						+ _rslave.getName()
						+ "."));
			out.flush();
		}
		// connect and start transfer
		try {
			_transfer.receiveFile(
				targetDir.getPath(),
				targetFilename,
				resumePosition);
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
			_transferFile.delete();
			return new FtpReply(
				426,
				"Remote error from " + _rslave + ": " + ex.getMessage());
		} catch (IOException ex) {
			//TODO let user resume
			_transferFile.delete();
			logger.log(Level.WARN, "from " + _rslave.getName(), ex);
			return new FtpReply(
				426,
				"IO Error from " + _rslave.getName() + ": " + ex.getMessage());
		}

		FtpReply response =
			(FtpReply) FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION.clone();

		long transferedBytes;
		long checksum;
		try {
			transferedBytes = _transfer.getTransfered();
			// throws RemoteException
			if (resumePosition == 0) {
				checksum = _transfer.getChecksum();
				_transferFile.setCheckSum(checksum);
			} else {
				try {
					checksum = _transferFile.getCheckSumFromSlave();
				} catch (NoAvailableSlaveException e) {
					response.addComment(
						"No available slaves when getting checksum from slave: "
							+ e.getMessage());
					logger.warn("", e);
					checksum = 0;
				} catch (IOException e) {
					response.addComment(
						"IO error getting checksum from slave: "
							+ e.getMessage());
					logger.warn("", e);
					checksum = 0;
				}
			}
			_transferFile.setLastModified(System.currentTimeMillis());
			_transferFile.setLength(transferedBytes);
			_transferFile.setXfertime(_transfer.getTransferTime());
		} catch (RemoteException ex) {
			_rslave.handleRemoteException(ex);
			return new FtpReply(
				426,
				"Error communicationg with slave: " + ex.getMessage());
		}

		response.addComment(Bytes.formatBytes(transferedBytes) + " transfered");
		if (!targetFilename.toLowerCase().endsWith(".sfv")) {
			try {
				long sfvChecksum =
					targetDir.lookupSFVFile().getChecksum(targetFilename);
				if (checksum == sfvChecksum) {
					response.addComment(
						"checksum match: SLAVE/SFV:"
							+ Long.toHexString(checksum));
				} else {
					response.addComment(
						"checksum mismatch: SLAVE: "
							+ Long.toHexString(checksum)
							+ " SFV: "
							+ Long.toHexString(sfvChecksum));
					response.addComment(" deleting file");
					response.setMessage("Checksum mismatch, deleting file");
					_transferFile.delete();

					//				getUser().updateCredits(
					//					- ((long) getUser().getRatio() * transferedBytes));
					//				getUser().updateUploadedBytes(-transferedBytes);
					response.addComment(conn.status());
					return response;
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
				response.addComment(
					"zipscript - SFV unavailable, slave with .sfv file is offline");
			} catch (ObjectNotFoundException e) {
				response.addComment(
					"zipscript - SFV unavailable, no .sfv file in directory");
			} catch (IOException e) {
				response.addComment(
					"zipscript - SFV unavailable, IO error: " + e.getMessage());
			}
		}
		//TODO creditcheck
		conn.getUserNull().updateCredits(
			(long) (conn.getUserNull().getRatio() * transferedBytes));
		conn.getUserNull().updateUploadedBytes(transferedBytes);
		conn.getUserNull().updateUploadedFiles(1);
		try {
			conn.getUserNull().commit();
		} catch (UserFileException e1) {
			response.addComment("Error saving userfile: " + e1.getMessage());
		}

		if (conn.getConfig().checkDirLog(conn.getUserNull(), _transferFile)) {
			conn.getConnectionManager().dispatchFtpEvent(
				new TransferEvent(
					conn.getUserNull(),
					"STOR",
					_transferFile,
					conn.getClientAddress(),
					_rslave.getInetAddress(),
					getType(),
					true));
		}

		response.addComment(conn.status());
		return response;
	}
	/**
	 * <code>STRU &lt;SP&gt; &lt;structure-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying
	 * file structure.
	 */
	private FtpReply doSTRU(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		if (request.getArgument().equalsIgnoreCase("F")) {
			return FtpReply.RESPONSE_200_COMMAND_OK;
		} else {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
		/*
				if (setStructure(request.getArgument().charAt(0))) {
					return FtpResponse.RESPONSE_200_COMMAND_OK);
				} else {
					return FtpResponse.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM);
				}
		*/
	}

	/**
	 * <code>SYST &lt;CRLF&gt;</code><br> 
	 *
	 * This command is used to find out the type of operating
	 * system at the server.
	 */
	private FtpReply doSYST(BaseFtpConnection conn) {
		conn.resetState();

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
		return FtpReply.RESPONSE_215_SYSTEM_TYPE;
		//String args[] = { "UNIX" };
		//out.write(ftpStatus.getResponse(215, request, user, args));
	}
	/**
	 * <code>TYPE &lt;SP&gt; &lt;type-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument specifies the representation type.
	 */
	private FtpReply doTYPE(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		conn.resetState();

		// get type from argument
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// set it
		if (setType(request.getArgument().charAt(0))) {
			return FtpReply.RESPONSE_200_COMMAND_OK;
		} else {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.command.CommandHandler#execute(net.sf.drftpd.master.BaseFtpConnection)
	 */
	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("MODE".equals(cmd))
			return doMODE(conn);
		if ("PASV".equals(cmd))
			return doPASV(conn);
		if ("PORT".equals(cmd))
			return doPORT(conn);
		if ("PRET".equals(cmd))
			return doPRET(conn);
		if ("REST".equals(cmd))
			return doREST(conn);
		if ("RETR".equals(cmd))
			return doRETR(conn);
		if ("SITE RESCAN".equals(cmd))
			return doSITE_RESCAN(conn);
		if ("SITE XDUPE".equals(cmd))
			return doSITE_XDUPE(conn);
		if ("STOR".equals(cmd))
			return doSTOR(conn);
		if ("STRU".equals(cmd))
			return doSTRU(conn);
		if ("SYST".equals(cmd))
			return doSYST(conn);
		if ("TYPE".equals(cmd))
			return doTYPE(conn);
		throw UnhandledCommandException.create(
			DataConnectionHandler.class,
			conn.getRequest());
	}

	/**
	 * Get the data socket. In case of error returns null.
	 * 
	 * Used by LIST and NLST.
	 */
	Socket getDataSocket() throws IOException {

		// get socket depending on the selection
		if (mbPort) {
			try {
				_dataSocket = new Socket(mAddress, getPort());
			} catch (IOException ex) {
				//mConfig.getLogger().warn(ex);
				logger.log(Level.WARN, "Error opening data socket", ex);
				_dataSocket = null;
				throw ex;
			}
		} else if (mbPasv) {
			try {
				_dataSocket = mServSoc.accept();
			} catch (IOException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} finally {
				mServSoc.close();
				mServSoc = null;
			}
		}
		_dataSocket.setSoTimeout(15000); // 15 seconds timeout
		//make sure it's available for garbage collection
		Socket sock = _dataSocket;
		_dataSocket = null;
		return sock;
	}

	public String[] getFeatReplies() {
		return new String[] { "PRET" };
	}
	/**
	 * Get client address from PORT command.
	 */
	private InetAddress getInetAddress() {
		return mAddress;
	}
	/**
	 * Get output stream. Returns <code>ftpserver.util.AsciiOutputStream</code>
	 * if the transfer type is ASCII.
	 * @deprecated unused, this is in slave anyways.
	 */
	private OutputStream getOutputStream(OutputStream os) {
		//os = IoUtils.getBufferedOutputStream(os);
		if (type == 'A') {
			os = new AsciiOutputStream(os);
		}
		return os;
	}
	/**
	 * Get port number.
	 * return miPort
	 */
	private int getPort() {
		return miPort;
	}

	/**
	 * @deprecated use getTransferSlave()
	 */
	public RemoteSlave getRSlave() {
		return _rslave;
	}

	/**
	 * 
	 */
	public RemoteSlave getTranferSlave() {
		return _rslave;
	}

	public Transfer getTransfer() {
		return _transfer;
	}

	public LinkedRemoteFile getTransferFile() {
		return _transferFile;
	}
	/**
		 * Get the user data type.
		 */
	public char getType() {
		return type;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		try {
			return (DataConnectionHandler) clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isTransfering() {
		return _transfer != null;
	}
	public void load(CommandManagerFactory initializer) {
	}

	/**
	 * Passive command. It returns the success flag.
	 */
	private boolean setPasvCommand(BaseFtpConnection conn) {
		try {
			conn.reset();
			mAddress = conn.getControlSocket().getLocalAddress();
			mServSoc = new ServerSocket(0, 1, mAddress);
			//mServSoc = new ServerSocket(0, 1);
			mServSoc.setSoTimeout(60000);
			miPort = mServSoc.getLocalPort();
			mbPasv = true;
			return true;
		} catch (Exception ex) {
			logger.log(Level.WARN, "", ex);
			return false;
		}
	}
	/**
	 * Port command.
	 */
	private void setPortCommand(
		BaseFtpConnection conn,
		InetAddress addr,
		int port) {
		conn.reset();
		mbPort = true;
		mAddress = addr;
		miPort = port;
	}

	/**
	  * Set the data type. Supported types are A (ascii) and I (binary).
	  * @return true if success
	  */
	private boolean setType(char type) {
		type = Character.toUpperCase(type);
		if ((type != 'A') && (type != 'I')) {
			return false;
		}
		this.type = type;
		return true;
	}
	public void unload() {
	}

}
