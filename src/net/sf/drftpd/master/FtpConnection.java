package net.sf.drftpd.master;

import java.net.Socket;

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
//	private static Logger logger =
//		Logger.getLogger(FtpConnection.class.getName());


	public FtpConnection(Socket sock, ConnectionManager connManager) {

		super(connManager, sock);

		
	}

	////////////////////////////////////////////////////////////
	/////////////////   all the FTP handlers   /////////////////
	////////////////////////////////////////////////////////////

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











	/**
	 * site replic <destslave> <path...>
	 * @param request
	 * @param out
	 */
	// won't work due to the non-interactivitiy of ftp, and due to timeouts
	//	public void doSITE_REPLIC(FtpRequest request, PrintWriter out) {
	//		resetState();
	//		if(!_user.isAdmin()) {
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
	//				TransferThread srcTransferThread = new TransferThread(srcRSlave, srcTransfer);
	//				TransferThread destTransferThread = new TransferThread(destRSlave, destTransfer);
	////				srcTransferThread.interruptibleSleepUntilFinished();
	////				destTransferThread.interruptibleSleepUntilFinished();
	//				while(true) {
	//					out.print("200- "+srcTransfer.getTransfered()+" : "+destTransfer.getTransfered());
	//				}
	//			} catch (Exception e) {
	//				// Handle exception
	//			}
	//		}
	//		
	//	}
}
