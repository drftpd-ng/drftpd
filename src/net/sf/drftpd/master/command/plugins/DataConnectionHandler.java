/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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
import net.sf.drftpd.slave.TransferStatus;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: DataConnectionHandler.java,v 1.14 2003/12/01 04:43:44 mog Exp $
 */
public class DataConnectionHandler implements CommandHandler, Cloneable {
	private static Logger logger =
		Logger.getLogger(DataConnectionHandler.class);
	private Socket _dataSocket;
	private RemoteSlave _rslave;
	private Transfer _transfer;
	private LinkedRemoteFile _transferFile;
	private InetAddress _address;
	public boolean mbPasv = false;

	public boolean mbPort = false;
	private int _port = 0;
	private ServerSocket _ServSoc;
	private boolean _preTransfer = false;
	private RemoteSlave _preTransferRSlave;

	private long _resumePosition = 0;
	private char type = 'A';
	private short xdupe = 0;

	public DataConnectionHandler() {
		super();
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
	//TODO implement APPE with transfer()
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
		if (!_preTransfer) {
			return new FtpReply(
				500,
				"You need to use a client supporting PRET (PRE Transfer) to use PASV");
		}

		if (_preTransferRSlave == null) {
			if (!setPasvCommand(conn)) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		} else {
			try {
				_transfer = _preTransferRSlave.getSlave().listen();
				_address = _preTransferRSlave.getInetAddress();
				_port = _transfer.getLocalPort();
				mbPasv = true;
			} catch (RemoteException e) {
				_preTransferRSlave.handleRemoteException(e);
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
			_address.getHostAddress().replace('.', ',')
				+ ','
				+ (_port >> 8)
				+ ','
				+ (_port & 0xFF);
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
		_preTransfer = false;
		_preTransferRSlave = null;
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
			&& !clientHostAddress.startsWith("192.168."))
			|| (portHostAddress.startsWith("10.")
				&& !clientHostAddress.startsWith("10."))) {
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

		if (portHostAddress.startsWith("127.")) {
			return new FtpReply(
				200,
				"Ok, but distributed transfers won't work with local addresses");
		}

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
			_preTransferRSlave = null;
			_preTransfer = true;
			return new FtpReply(
				200,
				"OK, will use master for upcoming transfer");
		} else if (cmd.equals("RETR")) {
			try {
				_preTransferRSlave =
					conn
						.getCurrentDirectory()
						.lookupFile(ghostRequest.getArgument())
						.getASlaveForDownload();
				_preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ _preTransferRSlave.getName()
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
				_preTransferRSlave =
					conn.getSlaveManager().getASlave(
						Transfer.TRANSFER_RECEIVING_UPLOAD);
				_preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ _preTransferRSlave.getName()
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
			_resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		if (_resumePosition < 0) {
			_resumePosition = 0;
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
	 * <p>
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
	 *				  125, 150
	 *					 (110)
	 *					 226, 250
	 *					 425, 426, 451, 551, 552
	 *				  532, 450, 452, 553
	 *				  500, 501, 421, 530
	 * 
	 * ''zipscript?? renames bad uploads to .bad, how do we handle this with resumes?
	 */
	//TODO add APPE support
	private FtpReply transfer(BaseFtpConnection conn) throws UnhandledCommandException {
		FtpRequest request = conn.getRequest();
		char direction = conn.getDirection();
		boolean isStor = conn.getRequest().getCommand().equals("STOR");
		boolean isRetr = conn.getRequest().getCommand().equals("RETR");
		boolean isAppe = conn.getRequest().getCommand().equals("APPE");
		boolean isStou = conn.getRequest().getCommand().equals("STOU");
		if(isAppe || isStou) throw UnhandledCommandException.create(DataConnectionHandler.class, conn.getRequest());
		// set state variables
		conn.resetState();

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		// get filenames
		LinkedRemoteFile targetDir;
		String targetFileName;
		if (isRetr) {
			try {
				_transferFile =
					conn.getCurrentDirectory().lookupFile(
						request.getArgument());
				if (!_transferFile.isFile()) {
					return new FtpReply(550, "Not a plain file");
				}
				targetDir = _transferFile.getParentFileNull();
				targetFileName = _transferFile.getName();
			} catch (FileNotFoundException ex) {
				return new FtpReply(550, ex.getMessage());
			}
		} else if (isStor) {
			LinkedRemoteFile.NonExistingFile ret =
				conn.getCurrentDirectory().lookupNonExistingFile(
					request.getArgument());
			targetDir = ret.getFile();
			targetFileName = ret.getPath();

			if (ret.exists()) {
				// target exists, this could be overwrite or resume
				// if(resumePosition != 0) {} // resume
				//TODO overwrite & resume files.

				return new FtpReply(
					550,
					"Requested action not taken. File exists.");
				//_transferFile = targetDir;
				//targetDir = _transferFile.getParent();
				//if(_transfereFile.getOwner().equals(getUser().getUsername())) {
				//	// allow overwrite/resume
				//}
				//if(directory.isDirectory()) {
				//	return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
				//}
			}

			if (!ListUtils.isLegalFileName(targetFileName)
				|| !conn.getConfig().checkPrivPath(
					conn.getUserNull(),
					targetDir)) {
				return new FtpReply(
					553,
					"Requested action not taken. File name not allowed.");
			}
		} else {
			throw new AssertionError();
		}

		//check access
		if (!conn.getConfig().checkPrivPath(conn.getUserNull(), targetDir)) {
			return new FtpReply(550, request.getArgument() + ": No such file");
		}

		switch (direction) {
			case Transfer.TRANSFER_SENDING_DOWNLOAD :
				if (!conn
					.getConnectionManager()
					.getConfig()
					.checkDownload(conn.getUserNull(), targetDir)) {
					return FtpReply.RESPONSE_530_ACCESS_DENIED;
				}
				break;
			case Transfer.TRANSFER_RECEIVING_UPLOAD :
				if (!conn
					.getConnectionManager()
					.getConfig()
					.checkUpload(conn.getUserNull(), targetDir)) {
					return FtpReply.RESPONSE_530_ACCESS_DENIED;
				}
				break;
			default :
				throw new AssertionError();
		}

		//check credits
		if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			if (conn.getUserNull().getRatio() != 0
				&& conn.getUserNull().getCredits() < _transferFile.length()) {
				return new FtpReply(550, "Not enough credits.");
			}
		}

		//setup _rslave
		if (mbPasv) {
			assert _preTransfer == true;

			if (!_transferFile.getSlaves().contains(_preTransferRSlave)) {
				return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
			}
			_rslave = _preTransferRSlave;
			_preTransferRSlave = null;
			_preTransfer = false;
			//TODO redundant code above to be handled by reset()
		} else {

			try {
				if (isRetr) {
					_rslave =
						_transferFile.getASlave(
							Transfer.TRANSFER_SENDING_DOWNLOAD);
				} else if (isStor) {
					_rslave =
						conn.getSlaveManager().getASlave(
							Transfer.TRANSFER_RECEIVING_UPLOAD);
				}
			} catch (NoAvailableSlaveException ex) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			}
		}
		if (isStor) {
			//setup upload
			List rslaves = Collections.singletonList(_rslave);
			StaticRemoteFile uploadFile =
				new StaticRemoteFile(
					rslaves,
					targetFileName,
					conn.getUserNull().getUsername(),
					conn.getUserNull().getGroupName(),
					0L,
					System.currentTimeMillis(),
					0L);
			_transferFile = targetDir.addFile(uploadFile);
		}

		// setup _transfer
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
			//_transfer is already set up by doPASV()
		} else {
			return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
		}
		assert _transfer != null;

		{
			PrintWriter out = conn.getControlWriter();
			out.write(
				new FtpReply(
					150,
					"File status okay; about to open data connection "
						+ (isRetr ? "from " : "to ")
						+ _rslave.getName()
						+ ".")
					.toString());
			out.flush();
		}
		TransferStatus status;

		//transfer

		try {
			//TODO ABORtable transfers
			if (isRetr) {
				_transfer.sendFile(
					_transferFile.getPath(),
					getType(),
					_resumePosition,
					true);
			} else if (isStor) {
				_transfer.receiveFile(
					targetDir.getPath(),
					'I',
					targetFileName, _resumePosition);
			}
			status = _transfer.getStatus();
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
			new FtpReply(
				226,
				"Transfer complete, "
					+ Bytes.formatBytes(status.getTransfered())
					+ " in "
					+ status.getElapsed() / 1000
					+ " seconds ("
					+ Bytes.formatBytes(status.getXferSpeed())
					+ "/s)");

		if (isStor) {
			long transferedBytes;
			try {
				transferedBytes = _transfer.getTransfered();
				// throws RemoteException
				if (_resumePosition == 0) {
					_transferFile.setCheckSum(status.getChecksum());
				} else {
					//						try {
					//							checksum = _transferFile.getCheckSumFromSlave();
					//						} catch (NoAvailableSlaveException e) {
					//							response.addComment(
					//								"No available slaves when getting checksum from slave: "
					//									+ e.getMessage());
					//							logger.warn("", e);
					//							checksum = 0;
					//						} catch (IOException e) {
					//							response.addComment(
					//								"IO error getting checksum from slave: "
					//									+ e.getMessage());
					//							logger.warn("", e);
					//							checksum = 0;
					//						}
				}
				_transferFile.setLastModified(System.currentTimeMillis());
				_transferFile.setLength(transferedBytes);
				_transferFile.setXfertime(status.getElapsed());
			} catch (RemoteException ex) {
				_rslave.handleRemoteException(ex);
				return new FtpReply(
					426,
					"Error communicationg with slave: " + ex.getMessage());
			}
		}

		//zipscript
		if (isRetr) {
			//compare checksum from transfer to cached checksum
			{
				response.addComment(
					"Checksum from transfer: "
						+ Checksum.formatChecksum(status.getChecksum()));
				long cachedChecksum;

				cachedChecksum = _transferFile.getCheckSumCached();
				if (cachedChecksum == 0) {
					_transferFile.setCheckSum(status.getChecksum());
				} else if (cachedChecksum != status.getChecksum()) {
					response.addComment(
						"WARNING: checksum from transfer didn't match cached checksum");
					logger.info(
						"checksum from transfer "
							+ Checksum.formatChecksum(status.getChecksum())
							+ "didn't match cached checksum"
							+ Checksum.formatChecksum(cachedChecksum)
							+ " for "
							+ _transferFile.toString()
							+ " from slave "
							+ _rslave.getName(),
						new Throwable());
				}
			}
			//compare checksum from transfer to checksum from sfv
			try {
				long sfvChecksum =
					_transferFile
						.getParentFileNull()
						.lookupSFVFile()
						.getChecksum(
						_transferFile.getName());
				if (sfvChecksum == status.getChecksum()) {
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
		} else if (isStor) {
			if (!targetFileName.toLowerCase().endsWith(".sfv")) {
				try {
					long sfvChecksum =
						targetDir.lookupSFVFile().getChecksum(targetFileName);
					if (status.getChecksum() == sfvChecksum) {
						response.addComment(
							"checksum match: SLAVE/SFV:"
								+ Long.toHexString(status.getChecksum()));
					} else {
						response.addComment(
							"checksum mismatch: SLAVE: "
								+ Long.toHexString(status.getChecksum())
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
						"zipscript - SFV unavailable, IO error: "
							+ e.getMessage());
				}
			}

		}
		//transferstatistics
		long transferedBytes = status.getTransfered();

		float ratio =
			conn.getConfig().getCreditLossRatio(
				_transferFile,
				conn.getUserNull());
		if (isRetr) {
			if (ratio != 0) {
				conn.getUserNull().updateCredits(
					(long) (-transferedBytes * ratio));
			}
			conn.getUserNull().updateDownloadedBytes(transferedBytes);
			conn.getUserNull().updateDownloadedFiles(1);
		} else {
			conn.getUserNull().updateCredits((long) (transferedBytes * ratio));
			conn.getUserNull().updateUploadedBytes(transferedBytes);
			conn.getUserNull().updateUploadedFiles(1);
		}
		try {
			conn.getUserNull().commit();
		} catch (UserFileException e) {
			logger.warn("", e);
		}

		if (isStor) {
			if (conn
				.getConfig()
				.checkDirLog(conn.getUserNull(), _transferFile)) {
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
		}

		conn.reset();

		return response;
	}
	private void reset() {
		_rslave = null;
		_transfer = null;
		_transferFile = null;
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
				out.write(
					"200- SFV: "
						+ Checksum.formatChecksum(checkSum.longValue())
						+ " SLAVE: "
						+ fileName
						+ " MISSING"
						+ BaseFtpConnection.NEWLINE);
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
			continue;
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
		if ("RETR".equals(cmd) || "STOR".equals(cmd) || "APPE".equals(cmd))
			return transfer(conn);
		if ("SITE RESCAN".equals(cmd))
			return doSITE_RESCAN(conn);
		if ("SITE XDUPE".equals(cmd))
			return doSITE_XDUPE(conn);
		if ("STRU".equals(cmd))
			return doSTRU(conn);
		if ("SYST".equals(cmd))
			return doSYST(conn);
		if ("TYPE".equals(cmd))
			return doTYPE(conn);
		if ("AUTH".equals(cmd))
			return doAUTH(conn);
		if ("PROT".equals(cmd))
			return doPROT(conn);
		if ("PBSZ".equals(cmd))
			return doPBSZ(conn);
		throw UnhandledCommandException.create(
			DataConnectionHandler.class,
			conn.getRequest());
	}

	private FtpReply doPBSZ(BaseFtpConnection conn)
		throws UnhandledCommandException {
		//TODO implement PBSZ
		throw UnhandledCommandException.create(
			DataConnectionHandler.class,
			conn.getRequest());
	}

	private FtpReply doPROT(BaseFtpConnection conn)
		throws UnhandledCommandException {
		//TODO implement PROT
		throw UnhandledCommandException.create(
			DataConnectionHandler.class,
			conn.getRequest());
	}

	//TODO error handling for AUTH cmd
	private FtpReply doAUTH(BaseFtpConnection conn) {
		Socket s = conn.getControlSocket();
		//reply success
		conn.getControlWriter().write(
			new FtpReply(
				234,
				conn.getRequest().getCommandLine() + " successfull")
				.toString());
		conn.getControlWriter().flush();
		SSLSocket s2;
		SSLContext ctx;

		try {

			ctx = SSLContext.getInstance("TLS");

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(new FileInputStream("drftpd.key"), "drftpd".toCharArray());

			kmf.init(ks, "drftpd".toCharArray());

			ctx.init(kmf.getKeyManagers(), null, null);
			//SecureRandom.getInstance("SHA1PRNG"));
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (CertificateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		try {
			s2 =
				(SSLSocket)
					((SSLSocketFactory) ctx.getSocketFactory()).createSocket(
					s,
					s.getInetAddress().getHostAddress(),
					s.getPort(),
					true);
			s2.setUseClientMode(false);
			logger.debug(Arrays.asList(s2.getEnabledCipherSuites()).toString());
			s2.startHandshake();
		} catch (IOException e) {
			logger.warn("", e);
			return new FtpReply(550, e.getMessage());
		}

		try {
			conn.setControlSocket(s2);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}

	/**
	 * Get the data socket. In case of error returns null.
	 * 
	 * Used by LIST and NLST and MLST.
	 */
	public Socket getDataSocket() throws IOException {

		// get socket depending on the selection
		if (mbPort) {
			try {
				_dataSocket = new Socket(_address, getPort());
			} catch (IOException ex) {
				logger.warn("Error opening data socket", ex);
				_dataSocket = null;
				throw ex;
			}
		} else if (mbPasv) {
			try {
				_dataSocket = _ServSoc.accept();
			} finally {
				_ServSoc.close();
				_ServSoc = null;
			}
		}
		_dataSocket.setSoTimeout(15000); // 15 seconds timeout
		//make sure it's available for garbage collection
		Socket sock = _dataSocket;
		_dataSocket = null;
		return sock;
	}

	public String[] getFeatReplies() {
		return new String[] { "PRET", "AUTH SSL" };
	}
	/**
	 * Get client address from PORT command.
	 */
	private InetAddress getInetAddress() {
		return _address;
	}

	/**
	 * Get port number.
	 * return miPort
	 */
	private int getPort() {
		return _port;
	}

	public RemoteSlave getTranferSlave() {
		return _rslave;
	}

	public Transfer getTransfer() {
		if (_transfer == null)
			throw new IllegalStateException();
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
			_address = conn.getControlSocket().getLocalAddress();
			
			_ServSoc = ServerSocketFactory.getDefault().createServerSocket(0, 1, _address);
			_ServSoc.setSoTimeout(60000);
			_port = _ServSoc.getLocalPort();
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
		_address = addr;
		_port = port;
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
