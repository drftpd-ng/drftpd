/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.Checksum;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.NoSFVEntryException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SlaveUnavailableException;
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
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferFailedException;
import net.sf.drftpd.slave.TransferStatus;
import net.sf.drftpd.util.ListUtils;
import net.sf.drftpd.util.PortRange;
import net.sf.drftpd.util.SSLGetContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author mog
 * @author zubov
 * @version $Id: DataConnectionHandler.java,v 1.53 2004/04/27 19:57:19 mog Exp $
 */
public class DataConnectionHandler implements CommandHandler, Cloneable {
	private static final Logger logger =
		Logger.getLogger(DataConnectionHandler.class);
	private SSLContext _ctx;

	private boolean _encryptedDataChannel;
	protected boolean _isPasv = false;
	protected boolean _isPort = false;
	/**
	 * Holds the address that getDataSocket() should connect to in PORT mode.
	 */
	private InetSocketAddress _portAddress;
	private PortRange _portRange = new PortRange();
	protected boolean _preTransfer = false;
	private RemoteSlave _preTransferRSlave;

	private long _resumePosition = 0;
	private RemoteSlave _rslave;

	/**
	 * ServerSocket for PASV mode.
	 */
	private ServerSocket _serverSocket;
	private Transfer _transfer;
	private LinkedRemoteFileInterface _transferFile;

	private char type = 'A';
	public DataConnectionHandler() {
		super();
		try {
			_ctx = SSLGetContext.getSSLContext();
		} catch (Exception e) {
			_ctx = null;
			logger.warn("Couldn't load SSLContext, SSL/TLS disabled", e);
		}
	}

	private FtpReply doAUTH(BaseFtpConnection conn) {
		if (_ctx == null)
			return new FtpReply(500, "TLS not configured");
		Socket s = conn.getControlSocket();

		//reply success
		conn.getControlWriter().write(
			new FtpReply(
				234,
				conn.getRequest().getCommandLine() + " successfull")
				.toString());
		conn.getControlWriter().flush();

		try {
			SSLSocket s2;
			s2 =
				(SSLSocket)
					((SSLSocketFactory) _ctx.getSocketFactory()).createSocket(
					s,
					s.getInetAddress().getHostAddress(),
					s.getPort(),
					true);
			s2.setUseClientMode(false);
			s2.startHandshake();
			conn.setControlSocket(s2);
		} catch (IOException e) {
			logger.warn("", e);
			conn.stop(e.getMessage());
			return null;
		}
		return null;
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
		if (!_preTransfer) {
			return new FtpReply(
				500,
				"You need to use a client supporting PRET (PRE Transfer) to use PASV");
		}
		//reset();
		_preTransfer = false;
		assert isPort() == false;
		InetSocketAddress address;
		if (_preTransferRSlave == null) {
			try {
				address =
					new InetSocketAddress(
						conn.getControlSocket().getLocalAddress(),
						_portRange.getPort());
				_serverSocket =
					(_encryptedDataChannel
						? _ctx.getServerSocketFactory()
						: conn.getServerSocketFactory())
						.createServerSocket();
				_serverSocket.bind(address, 1);
				_serverSocket.setSoTimeout(60000);
				//				_address2 =
				//					new InetSocketAddress(
				//						_serverSocket.getInetAddress(),
				//						_serverSocket.getLocalPort());
				_isPasv = true;
			} catch (BindException ex) {
				_serverSocket = null;
				logger.warn("", ex);
				return new FtpReply(550, ex.getMessage());
			} catch (Exception ex) {
				logger.log(Level.WARN, "", ex);
				return new FtpReply(550, ex.getMessage());
			}
		} else {
			try {
				_transfer = _preTransferRSlave.getSlave().listen(false);
				address =
					new InetSocketAddress(
						_preTransferRSlave.getInetAddress(),
						_transfer.getLocalPort());
				_isPasv = true;
			} catch (RemoteException e) {
				_preTransferRSlave.handleRemoteException(e);
				return new FtpReply(450, "Remote error: " + e.getMessage());
			} catch (SlaveUnavailableException e) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			} catch (IOException e) {
				logger.log(Level.FATAL, "", e);
				return new FtpReply(450, e.getMessage());
			}
		}
		//InetAddress mAddress == getInetAddress();
		//miPort == getPort();

		String addrStr =
			address.getAddress().getHostAddress().replace('.', ',')
				+ ','
				+ (address.getPort() >> 8)
				+ ','
				+ (address.getPort() & 0xFF);
		return new FtpReply(227, "Entering Passive Mode (" + addrStr + ").");
	}

	private FtpReply doPBSZ(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getArgument();
		if (cmd == null || !cmd.equals("0"))
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		return FtpReply.RESPONSE_200_COMMAND_OK;
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
		reset();

		InetAddress clientAddr = null;
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
				"  http://drftpd.org/ for PRET capable clients");
			return response;
		}
		int clientPort;
		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			//out.write(ftpStatus.getResponse(552, request, user, null));
		}

		_isPort = true;
		_portAddress = new InetSocketAddress(clientAddr, clientPort);

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
		reset();
		FtpRequest request = conn.getRequest();
		FtpRequest ghostRequest = new FtpRequest(request.getArgument());
		String cmd = ghostRequest.getCommand();
		if (cmd.equals("LIST") || cmd.equals("NLST") || cmd.equals("MLSD")) {
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
						.getASlave(Transfer.TRANSFER_SENDING_DOWNLOAD, conn);
				_preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ _preTransferRSlave.getName()
						+ " for upcoming transfer");
			} catch (NoAvailableSlaveException e) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			} catch (FileNotFoundException e) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		} else if (cmd.equals("STOR")) {
			LinkedRemoteFile.NonExistingFile nef = conn.getCurrentDirectory().lookupNonExistingFile(ghostRequest.getArgument());
			if(!nef.hasPath()) {
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
			if(!ListUtils.isLegalFileName(nef.getPath())) {
				return FtpReply.RESPONSE_530_ACCESS_DENIED;
			}
			try {
				_preTransferRSlave =
					conn.getSlaveManager().getASlave(
						Transfer.TRANSFER_RECEIVING_UPLOAD, conn, nef.getFile());
				_preTransfer = true;
				return new FtpReply(
					200,
					"OK, will use "
						+ _preTransferRSlave.getName()
						+ " for upcoming transfer");
			} catch (NoAvailableSlaveException e) {
				return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
			}
		} else {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
	}

	private FtpReply doPROT(BaseFtpConnection conn)
		throws UnhandledCommandException {
		if (_ctx == null)
			return new FtpReply(500, "TLS not configured");
		FtpRequest req = conn.getRequest();
		if (!req.hasArgument() || req.getArgument().length() != 1)
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		switch (Character.toUpperCase(req.getArgument().charAt(0))) {
			case 'C' : //clear
				_encryptedDataChannel = false;
				return FtpReply.RESPONSE_200_COMMAND_OK;

			case 'P' : //private
				_encryptedDataChannel = true;
				return FtpReply.RESPONSE_200_COMMAND_OK;

			default :
				return FtpReply.RESPONSE_501_SYNTAX_ERROR;
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

	private FtpReply doSITE_RESCAN(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		boolean forceRescan =
			(request.hasArgument()
				&& request.getArgument().equalsIgnoreCase("force"));
		LinkedRemoteFileInterface directory = conn.getCurrentDirectory();
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
			LinkedRemoteFileInterface file;
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

	/**
	 * Get the data socket. In case of error returns null.
	 * 
	 * Used by LIST and NLST and MLST.
	 */
	public Socket getDataSocket(SocketFactory socketFactory)
		throws IOException {
		Socket dataSocket;
		// get socket depending on the selection
		if (isPort()) {
			try {
				SocketFactory ssf =
					_encryptedDataChannel
						? _ctx.getSocketFactory()
						: socketFactory;

				dataSocket = ssf.createSocket();
				//dataSocket.connect(_address, getPort());
				dataSocket.connect(_portAddress);
			} catch (IOException ex) {
				logger.warn("Error opening data socket", ex);
				dataSocket = null;
				throw ex;
			}
		} else if (isPasv()) {
			try {
				dataSocket = _serverSocket.accept();
			} finally {
				_serverSocket.close();
				_portRange.releasePort(_serverSocket.getLocalPort());
				_serverSocket = null;
			}
		} else {
			throw new IllegalStateException("Neither PASV nor PORT");
		}
		if (_encryptedDataChannel) {
			SSLSocket ssldatasocket = (SSLSocket) dataSocket;
			ssldatasocket.setUseClientMode(false);
			ssldatasocket.startHandshake();
		}
		dataSocket.setSoTimeout(15000); // 15 seconds timeout
		return dataSocket;
	}

	public String[] getFeatReplies() {
		if (_ctx != null)
			return new String[] { "PRET", "AUTH SSL", "PBSZ" };
		return new String[] { "PRET" };
	}
	/**
	 * Get client address from PORT command.
	 * @deprecated
	 */
	//	private InetAddress getInetAddress() {
	//		return _address;
	//	}

	/**
	 * Get port number.
	 * return miPort
	 */
	//	private int getPort() {
	//		return _port;
	//	}

	public RemoteSlave getTranferSlave() {
		return _rslave;
	}

	public Transfer getTransfer() {
		if (_transfer == null)
			throw new IllegalStateException();
		return _transfer;
	}

	public LinkedRemoteFileInterface getTransferFile() {
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

	public boolean isEncryptedDataChannel() {
		return _encryptedDataChannel;
	}

	/**
	 * Guarantes pre transfer is set up correctly.
	 */
	public boolean isPasv() {
		return _isPasv;
	}
	public boolean isPort() {
		return _isPort;
	}
	public boolean isPreTransfer() {
		return _preTransfer || isPasv();
	}

	public boolean isTransfering() {
		return _transfer != null;
	}
	public void load(CommandManagerFactory initializer) {
	}
	protected void reset() {
		_rslave = null;
		_transfer = null;
		_transferFile = null;
		_preTransfer = false;
		_preTransferRSlave = null;

		if (_serverSocket != null) { //isPasv() && _preTransferRSlave == null
			_portRange.releasePort(_serverSocket.getLocalPort());
		}
		_isPasv = false;
		_serverSocket = null;
		_isPort = false;
		_resumePosition = 0;
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
	private FtpReply transfer(BaseFtpConnection conn)
		throws UnhandledCommandException {
		if(!_encryptedDataChannel && conn.getConfig().checkDenyDataUnencrypted(conn.getUserNull())) {
			return new FtpReply(530, "USE SECURE DATA CONNECTION");
		}
		try {
			FtpRequest request = conn.getRequest();
			char direction = conn.getDirection();

			String cmd = conn.getRequest().getCommand();
			boolean isStor = cmd.equals("STOR");
			boolean isRetr = cmd.equals("RETR");
			boolean isAppe = cmd.equals("APPE");
			boolean isStou = cmd.equals("STOU");
			String eventType = isRetr ? "RETR" : "STOR";
			
			if (isAppe || isStou)
				throw UnhandledCommandException.create(
					DataConnectionHandler.class,
					conn.getRequest());

			// argument check
			if (!request.hasArgument()) {
				return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			}

			// get filenames
			LinkedRemoteFileInterface targetDir;
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
						conn.getConnectionManager().getConfig().getFileName(
							request.getArgument()));
				targetDir = ret.getFile();
				targetFileName = ret.getPath();

				if (ret.exists()) {
					// target exists, this could be overwrite or resume
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
				throw UnhandledCommandException.create(
					DataConnectionHandler.class,
					request);
			}

			//check access
			if (!conn
				.getConfig()
				.checkPrivPath(conn.getUserNull(), targetDir)) {
				return new FtpReply(
					550,
					request.getArgument() + ": No such file");
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
					throw UnhandledCommandException.create(
						DataConnectionHandler.class,
						request);
			}

			//check credits
			if (isRetr) {
				if (conn.getUserNull().getRatio() != 0
					&& conn.getUserNull().getCredits() < _transferFile.length()) {
					return new FtpReply(550, "Not enough credits.");
				}
			}

			//setup _rslave
			if (isPasv()) {
				//				isPasv() means we're setup correctly
				//				if (!_preTransfer || _preTransferRSlave == null)
				//					return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;

				//check pretransfer
				if (isRetr
					&& !_transferFile.getSlaves().contains(_preTransferRSlave)) {
					return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
				}
				_rslave = _preTransferRSlave;
				//_preTransferRSlave = null;
				//_preTransfer = false;
				//code above to be handled by reset()
			} else {

				try {
					if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
						_rslave = _transferFile.getASlave(direction, conn);
					} else if (
						direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
						_rslave = conn.getSlaveManager().getASlave(direction, conn, targetDir);
					} else {
						throw new RuntimeException();
					}
				} catch (NoAvailableSlaveException ex) {
					return FtpReply.RESPONSE_530_SLAVE_UNAVAILABLE;
				}
			}

			if (isStor) {
				//setup upload
				assert _rslave != null;
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
			if (isPort()) {
				try {
					_transfer =
						_rslave.getSlave().connect(
							_portAddress,
							_encryptedDataChannel);
				} catch (RemoteException ex) {
					_rslave.handleRemoteException(ex);
					return new FtpReply(
						450,
						"Remote error: " + ex.getMessage());
				} catch (Exception ex) {
					logger.fatal("rslave=" + _rslave, ex);
					return new FtpReply(
						450,
						ex.getClass().getName()
							+ " from slave: "
							+ ex.getMessage());
				}
			} else if (isPasv()) {
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
					status =
					_transferFile.sendFile(_transfer,getType(),_resumePosition);
				} else if (isStor) {
					status = _transferFile.receiveFile(_transfer,getType(),_resumePosition);
				} else {
					throw new RuntimeException();
				}
			} catch (RemoteException ex) {
				_rslave.handleRemoteException(ex);
				if (isStor) {
					_transferFile.delete();
					logger.error("RemoteException during transfer, deleting file"); 
					return new FtpReply(426, "RemoteException, deleting file");
				}
				else {
					logger.error("RemoteException during transfer");
					return new FtpReply(426, "RemoteException during transfer");
				} 
			} catch (FileExistsException ex) {
				// slave is unsync'd
				logger.warn("Slave is unsynchronized", ex);
				return new FtpReply(426, "FileExistsException, slave is unsynchronized: "+ex.getMessage());
			} catch (IOException ex) {
				if(ex instanceof TransferFailedException) {
					status = ((TransferFailedException)ex).getStatus();
					conn.getConnectionManager().dispatchFtpEvent(new TransferEvent(conn.getUserNull(), eventType, _transferFile, conn.getClientAddress(), _rslave, status.getPeer(), type, false));
					if(isRetr) {
						conn.getUserNull().updateCredits(-status.getTransfered());
					}
				}
				FtpReply reply = null;
				if (isStor) {
					_transferFile.removeSlave(_rslave);
					_transferFile.delete();
					logger.error("IOException during transfer, deleting file", ex); 
					reply =  new FtpReply(426, "IOException, deleting file");
				}
				else {
					logger.error("IOException during transfer");
					reply = new FtpReply(426, "IOException during transfer");
				} 
				reply.addComment(ex.getLocalizedMessage());
				return reply;
			}
			//		TransferThread transferThread = new TransferThread(rslave, transfer);
			//		System.err.println("Calling interruptibleSleepUntilFinished");
			//		try {
			//			transferThread.interruptibleSleepUntilFinished();
			//		} catch (Throwable e1) {
			//			e1.printStackTrace();
			//		}
			//		System.err.println("Finished");

			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("bytes", Bytes.formatBytes(status.getTransfered()));
			env.add("speed", Bytes.formatBytes(status.getXferSpeed()) + "/s");
			env.add("seconds", "" + status.getElapsed() / 1000);
			env.add("checksum", Checksum.formatChecksum(status.getChecksum()));
			FtpReply response =
				new FtpReply(
					226,
					conn.jprintf(
						DataConnectionHandler.class.getName(),
						"transfer.complete",
						env));

			if (isStor) {
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
				_transferFile.setLength(status.getTransfered());
				_transferFile.setXfertime(status.getElapsed());
			}

			boolean zipscript = zipscript(isRetr,
				isStor,
				status.getChecksum(),
				response,
				targetFileName,
				targetDir);
			if(zipscript) {

				//transferstatistics
				if (isRetr) {
					float ratio =
						conn.getConfig().getCreditLossRatio(
							_transferFile,
							conn.getUserNull());
					if (ratio != 0) {
						conn.getUserNull().updateCredits(
							(long) (-status.getTransfered() * ratio));
					}
					conn.getUserNull().updateDownloadedBytes(
						status.getTransfered());
					conn.getUserNull().updateDownloadedMilliseconds(
						status.getElapsed());
					conn.getUserNull().updateDownloadedFiles(1);
				} else {
					conn.getUserNull().updateCredits(
						(long) (status.getTransfered()
							* conn.getConfig().getCreditCheckRatio(
								_transferFile,
								conn.getUserNull())));
					conn.getUserNull().updateUploadedBytes(
						status.getTransfered());
					conn.getUserNull().updateUploadedMilliseconds(
						status.getElapsed());
					conn.getUserNull().updateUploadedFiles(1);
				}
				try {
					conn.getUserNull().commit();
				} catch (UserFileException e) {
					logger.warn("", e);
				}
			}
			//Dispatch for both STOR and RETR
			conn.getConnectionManager().dispatchFtpEvent(
				new TransferEvent(
					conn.getUserNull(),
					eventType,
					_transferFile,
					conn.getClientAddress(),
					_rslave,
					status.getPeer(),
					getType(),
					zipscript));
			return response;
		} finally {
			reset();
		}
	}

	public void unload() {
		if (isPasv()) {
			_portRange.releasePort(_serverSocket.getLocalPort());
		}
	}

	/**
	 * @param isRetr
	 * @param isStor
	 * @param status
	 * @param response
	 * @param targetFileName
	 * @param targetDir
	 * Returns true if crc check was okay, i.e, if credits should be altered
	 */
	private boolean zipscript(
		boolean isRetr,
		boolean isStor,
		long checksum,
		FtpReply response,
		String targetFileName,
		LinkedRemoteFileInterface targetDir) {
		//zipscript
		logger.debug(
			"Running zipscript on file "
				+ targetFileName
				+ " with CRC of "
				+ checksum);
		if (isRetr) {
			//compare checksum from transfer to cached checksum
			logger.debug("checksum from transfer = " + checksum);
			if (checksum != 0) {
				response.addComment(
					"Checksum from transfer: "
						+ Checksum.formatChecksum(checksum));
				long cachedChecksum;

				cachedChecksum = _transferFile.getCheckSumCached();
				if (cachedChecksum == 0) {
					_transferFile.setCheckSum(checksum);
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
				//compare checksum from transfer to checksum from sfv
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
				} catch (NoSFVEntryException e1) {
					//file not found in .sfv, continue
				} catch (IOException e1) {
					logger.info(e1);
					response.addComment(
						"IO Error reading sfv file: " + e1.getMessage());
				}
			} else { // slave has disabled download crc
				//response.addComment("Slave has disabled download checksum");
			}
		} else if (isStor) {
			if (!targetFileName.toLowerCase().endsWith(".sfv")) {
				try {
					long sfvChecksum =
						targetDir.lookupSFVFile().getChecksum(targetFileName);
					if (checksum == sfvChecksum) {
						response.addComment(
							"checksum match: SLAVE/SFV:"
								+ Long.toHexString(checksum));
					} else if (checksum == 0) {
						response.addComment(
							"checksum match: SLAVE/SFV: DISABLED");
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
						// response.addComment(conn.status());
						return false; // don't modify credits
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
						"zipscript - SFV unavailable, slave(s) with .sfv file is offline");
				} catch (NoSFVEntryException e) {
					response.addComment(
						"zipscript - no entry in sfv for file");
				} catch (IOException e) {
					response.addComment(
						"zipscript - SFV unavailable, IO error: "
							+ e.getMessage());
				}
			}
		}
		return true; // modify credits, transfer was okay
	}

}
