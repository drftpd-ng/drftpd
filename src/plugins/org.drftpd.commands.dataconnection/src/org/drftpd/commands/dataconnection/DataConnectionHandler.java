/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands.dataconnection;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.PassiveConnection;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.event.SlowTransferEvent;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.*;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.master.*;
import org.drftpd.slave.*;
import org.drftpd.usermanager.User;
import org.drftpd.util.FtpRequest;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ListUtils;
import org.drftpd.vfs.ObjectNotValidException;
import org.tanesha.replacer.ReplacerEnvironment;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;


/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class DataConnectionHandler extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(DataConnectionHandler.class);

	public static final Key<Long> CHECKSUM = new Key<>(DataConnectionHandler.class, "checksum");

	public static final Key<FileHandle> TRANSFER_FILE = new Key<>(DataConnectionHandler.class, "transfer_file");
	
	public static final Key<RemoteSlave> TRANSFER_SLAVE = new Key<>(DataConnectionHandler.class, "transfer_slave");
	
	public static final Key<InetAddress> TRANSFER_SLAVE_INET_ADDRESS =
            new Key<>(DataConnectionHandler.class, "transfer_slave_inetAddress");
	
	public static final Key<Character> TRANSFER_TYPE = new Key<>(DataConnectionHandler.class, "transfer_type");

	public static final Key<String> INET_ADDRESS = new Key<>(DataConnectionHandler.class, "inetAddress");

	public static final Key<TransferStatus> XFER_STATUS = new Key<>(DataConnectionHandler.class, "transferStatus");
	
	public static final Key<Long> MIN_XFER_SPEED = new Key<>(DataConnectionHandler.class, "minTransferSpeed");
	
	public static final Key<Long> MAX_XFER_SPEED = new Key<>(DataConnectionHandler.class, "maxTransferSpeed");

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
		_featReplies = populateFeat(method);
	}

	public CommandResponse doAUTH(CommandRequest request) {
		SSLContext ctx = GlobalContext.getGlobalContext().getSSLContext();
		if (ctx == null) {
			return new CommandResponse(400, "SSL not configured");
		}

		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		Socket s = conn.getControlSocket();

		//reply success
		conn.printOutput(new FtpReply(234, request.getCommand()
				+ " " + request.getArgument() + " successful").toString());
		SSLSocket s2 = null;
		try {
			s2 = (SSLSocket) ctx.getSocketFactory().createSocket(s,
					s.getInetAddress().getHostAddress(), s.getPort(), true);
			conn.setControlSocket(s2);
			s2.setUseClientMode(false);
			s2.setSoTimeout(10000);
			String[] cipherSuites = GlobalContext.getConfig().getCipherSuites();
			if (cipherSuites != null && cipherSuites.length > 0) {
				s2.setEnabledCipherSuites(GlobalContext.getConfig().getCipherSuites());
			}
			String[] sslProtocols = GlobalContext.getConfig().getSSLProtocols();
			if (sslProtocols != null && sslProtocols.length > 0) {
				s2.setEnabledProtocols(GlobalContext.getConfig().getSSLProtocols());
			}
			s2.startHandshake();
			conn.authDone();
		} catch (IOException e) {
			logger.warn("", e);
			if (s2 != null) {
				try {
					s2.close();
				} catch (IOException e2){
					logger.debug("error closing SSLSocket connection");
				}
			}
			conn.stop(e.getMessage());

			return null;
		}
		s2 = null;

		return null;
	}

	/**
	 * <code>MODE &lt;SP&gt; <mode-code> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying the data
	 * transfer modes described in the Section on Transmission Modes.
	 */
	public CommandResponse doMODE(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		if (request.getArgument().equalsIgnoreCase("S")) {
			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		}

		return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
	}

	/**
	 * <code>PASV &lt;CRLF&gt;</code><br>
	 *
	 * This command requests the server-DTP to "listen" on a data port (which is
	 * not its default data port) and to wait for a connection rather than
	 * initiate one upon receipt of a transfer command. The response to this
	 * command includes the host and port address this server is listening on.
	 */
	public CommandResponse doPASVandCPSV(CommandRequest request) {

		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		TransferState ts = conn.getTransferState();
		ts.setPasv(true);
		if (!ts.isPreTransfer()) {
			reset(conn);
			return new CommandResponse(500,
			"You need to use a client supporting PRET (PRE Transfer) to use PASV");
		}

		if (request.getCommand().equalsIgnoreCase("CPSV")) {
			conn.getTransferState().setSSLHandshakeClientMode(true);
		}

		InetSocketAddress address = null;

		if (ts.isLocalPreTransfer()) {
			// setup a PassiveConnection for a local transfer, LIST/NLST
			PassiveConnection pc;
			try {
				pc = new PassiveConnection(ts.getSendFilesEncrypted() ? conn.getGlobalContext().getSSLContext() : null, conn.getGlobalContext().getPortRange(), false, ConnectionManager.getBindIP()); 
			} catch (IOException e1) {
				reset(conn);
				return new CommandResponse(500, e1.getMessage());
			}
			try {
				address = new InetSocketAddress(GlobalContext.getConfig().getPasvAddress(), pc.getLocalPort());
			} catch (NullPointerException e) {
				address = new InetSocketAddress(conn.getControlSocket()
						.getLocalAddress(), pc.getLocalPort());
			}
			ts.setLocalPassiveConnection(pc);
		} else {
			RemoteSlave slave = null;
			ConnectInfo ci;
			if (ts.isPASVDownload()) {
				while (slave == null) {
					try {
						slave = conn.getGlobalContext()
						.getSlaveSelectionManager().getASlave(conn,
								Transfer.TRANSFER_SENDING_DOWNLOAD,
								ts.getTransferFile());
						String index = SlaveManager.getBasicIssuer().issueListenToSlave(slave,
								ts.getSendFilesEncrypted(), ts
								.getSSLHandshakeClientMode());
						ci = slave.fetchTransferResponseFromIndex(index);
						ts.setTransfer(slave.getTransfer(ci.getTransferIndex()));
						address = new InetSocketAddress(slave.getPASVIP(),ts.getAddress().getPort());
					} catch (NoAvailableSlaveException e) {
						reset(conn);
						return StandardCommandManager.genericResponse("RESPONSE_450_SLAVE_UNAVAILABLE");
					} catch (SlaveUnavailableException e) {
						// make it loop till it finds a good one
						slave = null;
					} catch (RemoteIOException e) {
						slave.setOffline("Slave could not listen for a connection");
						logger.error("Slave could not listen for a connection", e);
						// make it loop until it finds a good one
						slave = null;
					} catch (SSLUnavailableException e) {
						return new CommandResponse(421, e.getMessage());
					}
				}
			} else if (ts.isPASVUpload()) {
				while (slave == null) {
					try {
						slave = conn.getGlobalContext()
						.getSlaveSelectionManager().getASlave(
								conn,
								Transfer.TRANSFER_RECEIVING_UPLOAD,
								ts.getTransferFile());
						String index = SlaveManager.getBasicIssuer().issueListenToSlave(slave,
								ts.getSendFilesEncrypted(), ts
								.getSSLHandshakeClientMode());
						ci = slave.fetchTransferResponseFromIndex(index);
						ts.setTransfer(slave.getTransfer(ci.getTransferIndex()));
						address = new InetSocketAddress(slave.getPASVIP(),ts.getAddress().getPort());
					} catch (NoAvailableSlaveException e) {
						reset(conn);
						return StandardCommandManager.genericResponse("RESPONSE_450_SLAVE_UNAVAILABLE");
					} catch (SlaveUnavailableException e) {
						// make it loop till it finds a good one
						slave = null;
					} catch (RemoteIOException e) {
						slave.setOffline("Slave could not listen for a connection");
						logger.error("Slave could not listen for a connection",
								e);
						// make it loop until it finds a good one
						slave = null;
					} catch (SSLUnavailableException e) {
						return new CommandResponse(421, e.getMessage());
					}
				}
			} else {
				return StandardCommandManager.genericResponse("RESPONSE_502_COMMAND_NOT_IMPLEMENTED");
			}
			ts.setTransferSlave(slave);
		}

		if (conn.getRequest().getCommand().equals("CPSV")) {
			// can only reset it if the transfer was setup with CPSV
			ts.setSSLHandshakeClientMode(false);
		}

		if (address.getAddress() == null || address.getAddress().getHostAddress() == null) {
			return new CommandResponse(500,
					"Address is unresolvable, check pasv_addr setting on " + ts.getTransferSlave().getName());
		}

		String addrStr = address.getAddress().getHostAddress().replace('.', ',') +
		',' + (address.getPort() >> 8) + ',' + (address.getPort() & 0xFF);
		CommandResponse response = new CommandResponse(227, "Entering Passive Mode (" + addrStr + ").");
		response.addComment("Using " + (ts.isLocalPreTransfer() ? "master" : ts.getTransferSlave().getName()) + " for upcoming transfer");
		return response;
	}

	public CommandResponse doPBSZ(CommandRequest request) {
		String cmd = request.getArgument();

		if ((cmd == null) || !cmd.equals("0")) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	/**
	 * <code>PORT &lt;SP&gt; <host-port> &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a HOST-PORT specification for the data port to be used in
	 * data connection. There are defaults for both the user and server data
	 * ports, and under normal circumstances this command and its reply are not
	 * needed. If this command is used, the argument is the concatenation of a
	 * 32-bit internet host address and a 16-bit TCP port address. This address
	 * information is broken into 8-bit fields and the value of each field is
	 * transmitted as a decimal number (in character string representation). The
	 * fields are separated by commas. A port command would be:
	 *
	 * PORT h1,h2,h3,h4,p1,p2
	 *
	 * where h1 is the high order 8 bits of the internet host address.
	 */
	public CommandResponse doPORT(CommandRequest request) {
		InetAddress clientAddr;

		// argument check
		if (!request.hasArgument()) {
			//Syntax error in parameters or arguments
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		StringTokenizer st = new StringTokenizer(request.getArgument(), ",");

		if (st.countTokens() != 6) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// get data server
		String dataSrvName = st.nextToken() + '.' + st.nextToken() + '.' +
		st.nextToken() + '.' + st.nextToken();

		try {
			clientAddr = InetAddress.getByName(dataSrvName);
		} catch (UnknownHostException ex) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		String portHostAddress = clientAddr.getHostAddress();
		String clientHostAddress = conn.getControlSocket().getInetAddress()
		.getHostAddress();

		if ((portHostAddress.startsWith("192.168.") &&
				!clientHostAddress.startsWith("192.168.")) ||
				(portHostAddress.startsWith("10.") &&
						!clientHostAddress.startsWith("10."))) {
			CommandResponse response = new CommandResponse(501);
			response.addComment("==YOU'RE BEHIND A NAT ROUTER==");
			response.addComment(
			"Configure the firewall settings of your FTP client");
			response.addComment("  to use your real IP: " +
					conn.getControlSocket().getInetAddress().getHostAddress());
			response.addComment("And set up port forwarding in your router.");
			response.addComment(
			"Or you can just use a PRET capable client, see");
			response.addComment("  https://github.com/drftpd-ng/drftpd3/wiki for PRET capable clients");

			reset(conn);
			return response;
		}

		int clientPort;

		// get data server port
		try {
			int hi = Integer.parseInt(st.nextToken());
			int lo = Integer.parseInt(st.nextToken());
			clientPort = (hi << 8) | lo;
		} catch (NumberFormatException ex) {
			reset(conn);
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		conn.getTransferState().setPortAddress(new InetSocketAddress(clientAddr, clientPort));


		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		if (portHostAddress.startsWith("127.")) {
			response.addComment(
			"Ok, but distributed transfers won't work with local addresses");
		}

		//Notify the user that this is not his IP.. Good for NAT users that
		// aren't aware that their IP has changed.
		if (!clientAddr.equals(conn.getControlSocket().getInetAddress())) {
			response.addComment(
					"FXP allowed. If you're not FXPing then set your IP to " +
					conn.getControlSocket().getInetAddress().getHostAddress() +
			" (usually in firewall settings)");
		}

		return response;
	}

	public CommandResponse doPRET(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		reset(conn);
		TransferState ts = conn.getTransferState();
		ts.setPreTransferRequest(new FtpRequest(request.getArgument()));

		if (ts.isLocalPreTransfer()) {
			return new CommandResponse(200, "OK, planning to use master for upcoming LIST transfer");
		}
		return setTransferFileFromPRETRequest(request);
	}

	private CommandResponse setTransferFileFromPRETRequest(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		TransferState ts = conn.getTransferState();
		FtpRequest ghostRequest = ts.getPretRequest();
		if (ghostRequest == null) {
			throw new IllegalStateException("PRET was not called before setTransferFileFromPRETRequest()");
		}
		String cmd = ghostRequest.getCommand();
		User user = request.getSession().getUserNull(request.getUser());
		if (user == null) {
			reset(conn);
			return new CommandResponse(550, "Unable to get correct User file");			
		}
		
		if (ts.getPretRequest().getArgument() == null) {
			reset(conn);
			return new CommandResponse(550, "Unable to get PretRequest Argument");			
		}

        switch (cmd) {
            case "RETR": {
                FileHandle file;
                try {
                    file = conn.getCurrentDirectory().getFile(ts.getPretRequest().getArgument(), user);
                } catch (FileNotFoundException e) {
                    reset(conn);
                    return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
                } catch (ObjectNotValidException e) {
                    reset(conn);
                    return new CommandResponse(550, "Requested target is not a file");
                }
                ts.setTransferFile(file);
                return new CommandResponse(200, "OK, planning for upcoming download");
            }
            case "STOR": {
                FileHandle file = null;
                try {
                    file = conn.getCurrentDirectory().getFile(ts.getPretRequest().getArgument(), user);
                } catch (FileNotFoundException e) {
                    // this is good, do nothing
                } catch (ObjectNotValidException e) {
                    // this is not good, file exists
                    // until we can upload multiple instances of files
                    reset(conn);
                    return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
                }
                if (file != null) {
                    // this is not good, file exists
                    // until we can upload multiple instances of files
                    reset(conn);
                    return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
                }
                file = conn.getCurrentDirectory().getNonExistentFileHandle(ts.getPretRequest().getArgument());

                if (!ListUtils.isLegalFileName(file.getName())) {
                    reset(conn);
                    return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN");
                }
                ts.setTransferFile(file);
                return new CommandResponse(200, "OK, planning for upcoming upload");
            }
            default:
                return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
        }
	}

	public CommandResponse doSSCN(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		if (conn.getGlobalContext().getSSLContext() == null) {
			return new CommandResponse(500, "TLS not configured");
		}

		if (!(conn.getControlSocket() instanceof SSLSocket)) {
			return new CommandResponse(500, "You are not on a secure channel");
		}

		if (!conn.getTransferState().getSendFilesEncrypted()) {
			return new CommandResponse(500, "SSCN only works for encrypted transfers");
		}

		if (conn.getRequest().hasArgument()) {
			if (conn.getRequest().getArgument().equalsIgnoreCase("ON")) {
				conn.getTransferState().setSSLHandshakeClientMode(true);
			}
			if (conn.getRequest().getArgument().equalsIgnoreCase("OFF")) {
				conn.getTransferState().setSSLHandshakeClientMode(false);
			}
		}
		return new CommandResponse(220,  "SSCN:"
				+ (conn.getTransferState().getSSLHandshakeClientMode() ? "CLIENT" : "SERVER") + " METHOD");
	}

	public CommandResponse doPROT(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		if (conn.getGlobalContext().getSSLContext() == null) {
			return new CommandResponse(500, "TLS not configured");
		}

		if (!(conn.getControlSocket() instanceof SSLSocket)) {
			return new CommandResponse(500, "You are not on a secure channel");
		}

		if (!request.hasArgument()) {
			//clear
			conn.getTransferState().setSendFilesEncrypted(false);

			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		}

		if (!request.hasArgument() || (request.getArgument().length() != 1)) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		switch (Character.toUpperCase(request.getArgument().charAt(0))) {
		case 'C':

			//clear
			conn.getTransferState().setSendFilesEncrypted(false);

			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		case 'P':

			//private
			conn.getTransferState().setSendFilesEncrypted(true);

			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		default:
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
	}

	/**
	 * <code>REST &lt;SP&gt; <marker> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field represents the server marker at which file transfer is
	 * to be restarted. This command does not cause file transfer but skips over
	 * the file to the specified data checkpoint. This command shall be
	 * immediately followed by the appropriate FTP service command which shall
	 * cause file transfer to resume.
	 */
	public CommandResponse doREST(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();

		// argument check
		if (!request.hasArgument()) {
			reset(conn);
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		String skipNum = request.getArgument();
		long resumePosition = 0L;

		try {
			resumePosition = Long.parseLong(skipNum);
		} catch (NumberFormatException ex) {
			reset(conn);
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		if (resumePosition < 0) {
			resumePosition = 0;
			reset(conn);
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
		conn.getTransferState().setResumePosition(resumePosition);

		return StandardCommandManager.genericResponse("RESPONSE_350_PENDING_FURTHER_INFORMATION");
	}

	/**
	 * <code>STRU &lt;SP&gt; &lt;structure-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument is a single Telnet character code specifying file structure.
	 */
	public CommandResponse doSTRU(CommandRequest request) {

		// argument check
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		if (request.getArgument().equalsIgnoreCase("F")) {
			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		}

		return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
	}

	/**
	 * <code>SYST &lt;CRLF&gt;</code><br>
	 *
	 * This command is used to find out the type of operating system at the
	 * server.
	 */
	public CommandResponse doSYST(CommandRequest request) {
		/*
		 * String systemName = System.getProperty("os.name"); if(systemName ==
		 * null) { systemName = "UNKNOWN"; } else { systemName =
		 * systemName.toUpperCase(); systemName = systemName.replace(' ', '-'); }
		 * String args[] = {systemName};
		 */
		return StandardCommandManager.genericResponse("RESPONSE_215_SYSTEM_TYPE");

		//String args[] = { "UNIX" };
		//out.write(ftpStatus.getResponse(215, request, user, args));
	}

	/**
	 * <code>TYPE &lt;SP&gt; &lt;type-code&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument specifies the representation type.
	 */
	public CommandResponse doTYPE(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();

		// get type from argument
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// set it
		if (conn.getTransferState().setType(request.getArgument().charAt(0))) {
			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		}

		return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
	}

	private String[] populateFeat(String method) {
		boolean sslContext = GlobalContext.getGlobalContext().getSSLContext() != null;
		if ("doAUTH".equals(method)) {
			return sslContext ? new String[] { "AUTH TLS" } : null;
		}
		if ("doPASVandCPSV".equals(method)) {
			return sslContext ? new String[] { "CPSV" } : null;
		}
		if ("doPBSZ".equals(method)) {
			return sslContext ? new String[] { "PBSZ" } : null;
		}
		if ("doSSCN".equals(method)) {
			return sslContext ? new String[] { "SSCN" } : null;
		}
		if ("doPRET".equals(method)) {
			return new String[] { "PRET" };
		}
		return null;
	}

	public String getHelp(String cmd) {
		ResourceBundle bundle = ResourceBundle.getBundle(DataConnectionHandler.class.getName());
		if ("".equals(cmd))
			return bundle.getString("help.general")+"\n";
		else if("rescan".equals(cmd))
			return bundle.getString("help.rescan")+"\n";
		else
			return "";
	}

	protected synchronized void reset(BaseFtpConnection conn) {
		conn.getTransferState().reset();
	}

	/**
	 * <code>STOU &lt;CRLF&gt;</code><br>
	 *
	 * This command behaves like STOR except that the resultant file is to be
	 * created in the current directory under a name unique to that directory.
	 * The 250 Transfer Started response must include the name generated.
	 */

	//TODO STOU

	/*
	 * public void doSTOU(FtpRequest request, PrintWriter out) {
	 *  // reset state variables resetState();
	 *  // get filenames String fileName =
	 * user.getVirtualDirectory().getAbsoluteName("ftp.dat"); String
	 * physicalName = user.getVirtualDirectory().getPhysicalName(fileName); File
	 * requestedFile = new File(physicalName); requestedFile =
	 * IoUtils.getUniqueFile(requestedFile); fileName =
	 * user.getVirtualDirectory().getVirtualName(requestedFile.getAbsolutePath());
	 * String args[] = {fileName};
	 *  // check permission
	 * if(!user.getVirtualDirectory().hasCreatePermission(fileName, false)) {
	 * out.write(ftpStatus.getResponse(550, request, user, null)); return; }
	 *  // now transfer file data out.print(FtpResponse.RESPONSE_150_OK);
	 * InputStream is = null; OutputStream os = null; try { Socket dataSoc =
	 * mDataConnection.getDataSocket(); if (dataSoc == null) {
	 * out.write(ftpStatus.getResponse(550, request, user, args)); return; }
	 *
	 *
	 * is = dataSoc.getInputStream(); os = user.getOutputStream( new
	 * FileOutputStream(requestedFile) );
	 *
	 * StreamConnector msc = new StreamConnector(is, os);
	 * msc.setMaxTransferRate(user.getMaxUploadRate()); msc.setObserver(this);
	 * msc.connect();
	 *
	 * if(msc.hasException()) { out.write(ftpStatus.getResponse(451, request,
	 * user, null)); return; } else {
	 * mConfig.getStatistics().setUpload(requestedFile, user,
	 * msc.getTransferredSize()); }
	 *
	 * out.write(ftpStatus.getResponse(226, request, user, null));
	 * mDataConnection.reset(); out.write(ftpStatus.getResponse(250, request,
	 * user, args)); } catch(IOException ex) {
	 * out.write(ftpStatus.getResponse(425, request, user, null)); } finally {
	 * IoUtils.close(is); IoUtils.close(os); mDataConnection.reset(); } }
	 */

	/**
	 * <code>RETR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to transfer a copy of the file,
	 * specified in the pathname, to the server- or user-DTP at the other end of
	 * the data connection. The status and contents of the file at the server
	 * site shall be unaffected.
	 *
	 * RETR 125, 150 (110) 226, 250 425, 426, 451 450, 550 500, 501, 421, 530
	 * <p>
	 * <code>STOR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
	 *
	 * This command causes the server-DTP to accept the data transferred via the
	 * data connection and to store the data as a file at the server site. If
	 * the file specified in the pathname exists at the server site, then its
	 * contents shall be replaced by the data being transferred. A new file is
	 * created at the server site if the file specified in the pathname does not
	 * already exist.
	 *
	 * STOR 125, 150 (110) 226, 250 425, 426, 451, 551, 552 532, 450, 452, 553
	 * 500, 501, 421, 530
	 *
	 * ''zipscript?? renames bad uploads to .bad, how do we handle this with
	 * resumes?
	 */
	//TODO add APPE support
	private CommandResponse transfer(CommandRequest request) {
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		TransferState ts = conn.getTransferState();
		ReplacerEnvironment env = new ReplacerEnvironment();
		if (!ts.getSendFilesEncrypted() &&
				GlobalContext.getConfig().checkPermission("denydatauncrypted", conn.getUserNull())) {
			reset(conn);
			return new CommandResponse(530, "USE SECURE DATA CONNECTION");
		}

		User user = request.getSession().getUserNull(request.getUser());
		String cmd = request.getCommand();
		char direction = TransferState.getDirectionFromRequest(new FtpRequest(cmd));
		boolean isStor = cmd.equalsIgnoreCase("STOR");
		boolean isRetr = cmd.equalsIgnoreCase("RETR");
		boolean isAppe = cmd.equalsIgnoreCase("APPE");
		boolean isStou = cmd.equalsIgnoreCase("STOU");

		try {
			if (isAppe || isStou) {
				return StandardCommandManager.genericResponse("RESPONSE_502_COMMAND_NOT_IMPLEMENTED");
			}

			if (isStor && !ts.isPort() && !ts.isPasv()) {
				// reset(); already done in finally block
				return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
			}

			// argument check
			if (!request.hasArgument()) {
				// reset(); already done in finally block
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}

			//          Checks maxsim up/down
			// _simup OR _simdown = 0, exempt
			int comparison = 0;
			int count = BaseFtpConnection.countTransfersForUser(conn.getUserNull(), direction);
			env.add("maxsim", count);

			if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
				comparison =  conn.getUserNull().getMaxSimUp();
				env.add("direction", "upload");
			} else {
				comparison =  conn.getUserNull().getMaxSimDown();
				env.add("direction", "download");
			}

			if (comparison != 0 && count >= comparison) {
				return new CommandResponse(550,
						conn.jprintf(_bundle, _keyPrefix+"transfer.err.maxsim", env, request.getUser()));
			}

			// get filenames

			if (isRetr && ts.isPort()) {
				if (ts.getTransferFile() == null) {
					try {
						ts.setTransferFile(conn.getCurrentDirectory().getFile(
								request.getArgument(), user));
					} catch (FileNotFoundException e) {
						return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
					} catch (ObjectNotValidException e) {
						return new CommandResponse(550, "Argument is not a file");
					}
				} // else { ts.getTransferFile() is set, this is a PRET action
			}


			if (isStor && !ts.isPasv() && !ts.isPreTransfer()) {
				//setup upload
				FileHandle fh = ts.getTransferFile();
				// cannot use this FileHandle for anything but name, parent, and path
				// it doesn't exist in the VFS yet!
				try {
					fh = conn.getCurrentDirectory().getFile(conn.getRequest().getArgument(), user);
				} catch (FileNotFoundException e) {
					// this is good, do nothing
					// should be null already, but just for my (current) sanity
					fh = null;
				} catch (ObjectNotValidException e) {
					// this is not good, file exists
					// until we can upload multiple instances of files
					return StandardCommandManager.genericResponse(
					"RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
				}
				if (fh != null) {
					// this is not good, file exists
					// until we can upload multiple instances of files
					return StandardCommandManager.genericResponse(
					"RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
				}
				fh = conn.getCurrentDirectory().getNonExistentFileHandle(conn.getRequest().getArgument());

				if (!ListUtils.isLegalFileName(fh.getName())) {
					reset(conn);
					return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN");
				}
				ts.setTransferFile(fh);
			}

			//setup _rslave
			//if (isCpsv)
			if (ts.isPASVDownload()) {
				//check pretransfer
				try {
					if (!ts.getTransferFile().getSlaves().contains(ts.getTransferSlave())) {
						// reset(); already done in finally block
						return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
					}
				} catch (FileNotFoundException e) {
					// reset(); already done in finally block
					return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
				}
				// reset(); already done in finally block
			} else if (ts.isPASVUpload()) {
				// do nothing at this point
			} else if (ts.isPort()) {
				try {
					if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
						ts.setTransferSlave(conn.getGlobalContext()
								.getSlaveSelectionManager().getASlave(
										conn,
										Transfer.TRANSFER_SENDING_DOWNLOAD,
										ts.getTransferFile()));
					} else if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
						ts.setTransferSlave(conn.getGlobalContext()
								.getSlaveSelectionManager().getASlave(
										conn,
										Transfer.TRANSFER_RECEIVING_UPLOAD,
										ts.getTransferFile()));
					} else {
						// reset(); already done in finally block
						throw new RuntimeException();
					}
				} catch (NoAvailableSlaveException ex) {
					// TODO Might not be good to 450 reply always
					// from rfc: 450 Requested file action not taken. File
					// unavailable (e.g., file busy).
					// reset(); already done in finally block
					return StandardCommandManager.genericResponse("RESPONSE_450_SLAVE_UNAVAILABLE");
				}
			}

			if (isStor) {
				//setup upload
				FileHandle fh = ts.getTransferFile();
				// cannot use this FileHandle for anything but name, parent, and path
				// it doesn't exist in the VFS yet!
				try {
					ts.setTransferFile(fh.getParent().createFile(conn.getUserNull(), fh.getName(), ts.getTransferSlave()));
					ts.setTransferFileCreated(true);
				} catch (FileExistsException e) {
					// reset is handled in finally
					return StandardCommandManager.genericResponse(
					"RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
				} catch (FileNotFoundException e) {
					// reset is handled in finally
					logger.debug("",e);
					return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
				} catch (PermissionDeniedException e) {
					return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
				}
			}

			// setup _transfer

			if (ts.isPort()) {
				String index;
				try {
					index = SlaveManager.getBasicIssuer().issueConnectToSlave(ts.getTransferSlave(), ts.getPortAddress()
							.getAddress().getHostAddress(), ts.getPortAddress()
							.getPort(), ts.getSendFilesEncrypted(), ts.getSSLHandshakeClientMode());
					ConnectInfo ci = ts.getTransferSlave().fetchTransferResponseFromIndex(index);
					ts.setTransfer(ts.getTransferSlave().getTransfer(ci.getTransferIndex()));
				} catch (SlaveUnavailableException e) {
					return StandardCommandManager.genericResponse("RESPONSE_450_SLAVE_UNAVAILABLE");
				} catch (RemoteIOException e) {
					// couldn't talk to the slave, this is bad
					ts.getTransferSlave().setOffline(e);
					return StandardCommandManager.genericResponse("RESPONSE_450_SLAVE_UNAVAILABLE");
				} catch (SSLUnavailableException e) {
					return new CommandResponse(421, e.getMessage());
				}
			} else if (ts.isPASVDownload() || ts.isPASVUpload()) {
				//_transfer is already set up by doPASV()
			} else {
				// reset(); already done in finally block
				return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
			}

			{
				PrintWriter out = conn.getControlWriter();
				out.write(new FtpReply(150,
						"File status okay; about to open data connection " +
						(isRetr ? "from " : "to ") + ts.getTransferSlave().getName() + ".").toString());
				out.flush();
			}

			TransferStatus status = null;
			CommandResponse response = null;
			boolean transferEnded = false;
			boolean slowDenied = false;

			//transfer
			try {
				String address = request.getSession().getObject(INET_ADDRESS, "*@*");           	

				if (isRetr) {
					ts.sendFile(ts.getTransferFile().getPath(), ts.getType(),
							ts.getResumePosition(), address,
							request.getObjectLong(MIN_XFER_SPEED),
							request.getObjectLong(MAX_XFER_SPEED));

					while (true) {
						synchronized(ts) {
							status = ts.getTransferStatus();
						}	
						if (status.isFinished()) {
							break;
						}

						try {
							Thread.sleep(100);
						} catch (InterruptedException e1) {
						}
					}
				} else if (isStor) {
					ts.receiveFile(ts.getTransferFile().getPath(), ts.getType(),
							ts.getResumePosition(), address,
							request.getObjectLong(MIN_XFER_SPEED),
							request.getObjectLong(MAX_XFER_SPEED));

					while (true) {
						synchronized(ts) {
							status = ts.getTransferStatus();
						}
						try {
							ts.getTransferFile().setSize(status.getTransfered());
						} catch (FileNotFoundException e) {
							// Will happen if aborted and delete on abort set
							// Check this and rethrow if needed
							if (!conn.isAborted() || !Boolean.parseBoolean(GlobalContext.getConfig()
									.getMainProperties().getProperty("delete.upload.on.abort", "false"))) {
								throw e;
							}
						}
						if (status.isFinished()) {
							break;
						}
						try {
							Thread.sleep(100);
						} catch (InterruptedException e1) {
						}
					}
				} else {
					throw new RuntimeException();
				}
				if (conn.isAborted()) {
					if (isStor && !ts.getTransferFile().exists()) {
						response = new CommandResponse(226,"Transfer aborted - deleting file");
					} else {
						response = new CommandResponse(226,"Transfer aborted");
					}
				}
				transferEnded = true;
			} catch (IOException ex) {          	                
				boolean fxpDenied = false;

				if (ex instanceof TransferFailedException) {
					if (ex.getCause() instanceof TransferSlowException) {
						TransferSlowException tse = (TransferSlowException) ex.getCause();
						status = tse.getStatus();
						slowDenied = true;
						transferEnded = true;
						response = new CommandResponse(426, "You are transfering too slow");	
					} else if (ex.getCause() instanceof TransferDeniedException) {
						fxpDenied = true;
						response = new CommandResponse(426, "You are not allowed to FXP from here.");
					}
				}

				if ((!fxpDenied) && (!slowDenied)) {
					logger.debug("IOException during transfer", ex);
					response = new CommandResponse(426, "Transfer failed");
				}

				if (isStor) {
					conn.abortCommand();
					if (!fxpDenied && !ts.getTransferFile().exists() && !slowDenied) {
						response = new CommandResponse(426, "Transfer failed, deleting file");
					}
				}
				
				response.addComment(ex.getMessage());
			} catch (SlaveUnavailableException e) {
				logger.error("Slave went offline during transfer", e);
				response = new CommandResponse(426,	"Slave went offline during transfer");

				if (isStor) {
					conn.abortCommand();
					if (!ts.getTransferFile().exists()) {
						response = new CommandResponse(426, "Slave went offline during transfer, deleting file");
					}
				}

				response.addComment(e.getLocalizedMessage());
			}

			if (transferEnded && !conn.isAborted()) {
				env = new ReplacerEnvironment();
				env.add("bytes", Bytes.formatBytes(status.getTransfered()));
				env.add("speed", Bytes.formatBytes(status.getXferSpeed()) + "/s");
				env.add("seconds", "" + (status.getElapsed() / 1000F));
				env.add("checksum", Checksum.formatChecksum(status.getChecksum()));

				response = new CommandResponse(226, conn.jprintf(_bundle,
						_keyPrefix+"transfer.complete", env, request.getUser()));
			}

			response.setObject(CHECKSUM,status == null ? 0L : status.getChecksum());
			response.setObject(TRANSFER_FILE,ts.getTransferFile());
			response.setObject(TRANSFER_SLAVE,ts.getTransferSlave());
			response.setObject(TRANSFER_SLAVE_INET_ADDRESS, ts.getAddress().getAddress());
			response.setObject(TRANSFER_TYPE, ts.getType());
			if (status != null) {
				response.setObject(XFER_STATUS, status);
			}

			if (isStor && transferEnded) {
				try {
					if (ts.getResumePosition() == 0) {
						ts.getTransferFile().setCheckSum(status.getChecksum());
					} // else, fetch checksum from slave when resumable
					// uploads are implemented.

					ts.getTransferFile().setSize(status.getTransfered());
					ts.getTransferFile().setLastModified(System.currentTimeMillis());
					ts.getTransferFile().setXfertime(ts.getElapsed());
				} catch (FileNotFoundException e) {
					// this is kindof odd
					// it was a successful transfer, yet the file is gone
					// lets just return the response
				}
			}

			if (slowDenied) {
				GlobalContext.getEventService().publish(new SlowTransferEvent(user,ts.getTransferFile(),isStor,conn,request.getObjectLong(MIN_XFER_SPEED),status.getXferSpeed(), status.getTransfered(),ts.getTransferSlave().getName()));
			}
			
			return response;
		} finally {
			// A simple catch all to delete any 0-byte uploaded files as these could be left around if
			// settings like delete on abort are disabled
			try {
				if (isStor && ts.getTransferFileCreated() && ts.getTransferFile().getSize() == 0L) {
					ts.getTransferFile().deleteUnchecked();
				}
			} catch (FileNotFoundException e) {
				// File already gone which is fine
			}
			reset(conn);
		}
	}

	/* Add separate methods for STOR/RETR which will allow us to hook them separately
	 * , for now they will just in turn call the old method but this may change later
	 */
	public CommandResponse doRETR(CommandRequest request) {
		return transfer(request);
	}

	public CommandResponse doSTOR(CommandRequest request) {
		return transfer(request);
	}
}
