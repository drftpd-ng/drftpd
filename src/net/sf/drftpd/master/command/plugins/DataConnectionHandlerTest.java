package net.sf.drftpd.master.command.plugins;

import java.io.ByteArrayOutputStream;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.tests.DummyBaseFtpConnection;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 * @version $Id: DataConnectionHandlerTest.java,v 1.1 2003/12/22 18:09:42 mog Exp $
 */
public class DataConnectionHandlerTest extends TestCase {
	public static TestSuite suite() {
		return new TestSuite(DataConnectionHandlerTest.class);
	}
	DummyBaseFtpConnection conn;
	DataConnectionHandler dch;
	public DataConnectionHandlerTest(String fName) {
		super(fName);
	}

	public void assertBetween(int val, int from, int to) {
		assertTrue(val + " is less than " + from, val >= from);
		assertTrue(val + " is more than " + from, val <= to);
	}
	private String list() {
		LIST list = (LIST) new LIST().initialize(null, null);

		ByteArrayOutputStream out =
			conn.getDummySSF().getDummySocket().getByteArrayOutputStream();

		conn.setRequest(new FtpRequest("LIST"));
		FtpReply reply = list.execute(conn);
		String replystr = conn.getDummyOut().getBuffer().toString();
		assertEquals(150, Integer.parseInt(replystr.substring(0, 3)));
		assertEquals(226, reply.getCode());
		//TODO verify LIST data
		String ret = out.toString();
		out.reset();
		return ret;
	}

	private String pasvList() throws UnhandledCommandException {
		conn.setRequest(new FtpRequest("PRET LIST"));

		FtpReply reply;
		reply = dch.execute(conn);
		assertEquals(reply.toString(), 200, reply.getCode());

		conn.setRequest(new FtpRequest("PASV"));
		reply = dch.execute(conn);
		assertEquals(reply.toString(), 227, reply.getCode());

		return list();
	}

	private String portList() throws UnhandledCommandException {
		conn.setRequest(new FtpRequest("PORT 127,0,0,1,0,0"));
		dch.execute(conn);

		return list();
	}

	protected void setUp() throws Exception {
		BasicConfigurator.configure();
		dch =
			(DataConnectionHandler) new DataConnectionHandler().initialize(
				null,
				null);
		conn = new DummyBaseFtpConnection(dch);
	}
	protected void tearDown() throws Exception {
		dch = null;
	}
	public void testMixedListEqual() throws UnhandledCommandException {
		String list = portList();
		assertEquals(list, pasvList());
		assertEquals(list, portList());
		assertEquals(list, pasvList());
		testPASVWithoutPRET();
		assertEquals(list, portList());
		testPASVWithoutPRET();
		assertEquals(list, portList());
	}
	public void testPasvList() throws UnhandledCommandException {
		pasvList();
	}

	public void testPASVWithoutPRET() throws UnhandledCommandException {
		conn.setRequest(new FtpRequest("PASV"));
		FtpReply reply = dch.execute(conn);
		assertBetween(reply.getCode(), 500, 599);
	}

	public void testPortList() throws UnhandledCommandException {
		portList();
	}
}
