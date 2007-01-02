package org.drftpd.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import net.sf.drftpd.SlaveUnavailableException;

import org.drftpd.GlobalContext;
import org.drftpd.id3.ID3Tag;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.async.AsyncResponse;


/**
 * @author zubov
 * @version $Id$
 */
public class DummyRemoteSlave extends RemoteSlave {
    public DummyRemoteSlave(String name, GlobalContext gctx) {
        super(name, gctx);
    }

	public int getPort() {
		return 10;
	}
    public void fakeConnect() {
        _errors = 0;
        _lastNetworkError = System.currentTimeMillis();
    }

    public boolean isOnline() {
        // TODO Auto-generated method stub
        return false;
    }

    public void listenForInfoFromSlave() {
        // TODO Auto-generated method stub
    }

    public void connect(Socket socket, BufferedReader in)
        throws IOException {
    }

    public void disconnect() {
        // TODO Auto-generated method stub
    }

    public int fetchMaxPathFromIndex(String maxPathIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return 0;
    }

    public AsyncResponse fetchResponse(String index)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public SlaveStatus fetchStatusFromIndex(String statusIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public void fetchAbortFromIndex(String abortIndex)
        throws IOException, SlaveUnavailableException {
        // TODO Auto-generated method stub
    }

    public long fetchChecksumFromIndex(String index)
        throws RemoteIOException, SlaveUnavailableException {
        // TODO Auto-generated method stub
        return 0;
    }

    public ID3Tag fetchID3TagFromIndex(String index)
        throws RemoteIOException, SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueChecksumToSlave(String string)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueConnectToSlave(InetSocketAddress address,
        boolean encryptedDataChannel) throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueID3TagToSlave(String string)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueSFVFileToSlave(String string)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueListenToSlave(boolean encryptedDataChannel)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueMaxPathToSlave() throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issuePingToSlave() throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueReceiveToSlave(String name)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueTransferStatusToSlave(String transferIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueAbortToSlave(RemoteTransfer transferIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueSendToSlave(String name)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueStatusToSlave() throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public RemoteTransfer fetchTransferIndexFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean transferIsUpdated(RemoteTransfer transferIndex) {
        // TODO Auto-generated method stub
        return false;
    }

    public void run() {
        // TODO Auto-generated method stub
    }

    public String issueDeleteToSlave(String sourceFile)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueRenameToSlave(String from, String toDirPath,
        String toName) throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueTransferStatusToSlave(RemoteTransfer transferIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public void issueAbortToSlave(TransferIndex transferIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
    }

    public String issueReceiveToSlave(String name, char c, long position,
        TransferIndex index) throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueSendToSlave(String name, char c, long position,
        TransferIndex index) throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public String issueTransferStatusToSlave(TransferIndex transferIndex)
        throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean transferIsUpdated(TransferIndex transferIndex) {
        // TODO Auto-generated method stub
        return false;
    }

    public void connect(Socket socket, BufferedReader reader, PrintWriter writer)
        throws IOException {
        // TODO Auto-generated method stub
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.RemoteSlave#issueRemergeToSlave()
     */
    public String issueRemergeToSlave() throws SlaveUnavailableException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.RemoteSlave#fetchRemergeResponseFromIndex(java.lang.String)
     */
    public void fetchRemergeResponseFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        // TODO Auto-generated method stub
    }

    public void commit() {
        // TODO Auto-generated method stub
    }
}
