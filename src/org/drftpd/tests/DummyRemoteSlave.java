package org.drftpd.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import net.sf.drftpd.ID3Tag;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveStatus;

import org.drftpd.GlobalContext;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.RemoteTransfer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.async.AsyncResponse;


/**
 * @author zubov
 * @version $Id: DummyRemoteSlave.java,v 1.6 2004/11/08 04:46:17 zubov Exp $
 */
public class DummyRemoteSlave extends RemoteSlave {
    public DummyRemoteSlave(String name, GlobalContext gctx) {
        super(name, gctx);
    }

    public InetAddress getInetAddress() {
        // TODO Auto-generated method stub
        return null;
    }

    public void fakeConnect() {
        _errors = 0;
        _lastNetworkError = System.currentTimeMillis();
    }

    public boolean isOnline() {
        // TODO Auto-generated method stub
        return false;
    }

    public LinkedRemoteFile getSlaveRoot()
        throws SlaveUnavailableException, IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public String moreInfo() {
        // TODO Auto-generated method stub
        return null;
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

    public SFVFile fetchSFVFileFromIndex(String index)
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
