package net.sf.drftpd.remotefile;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.List;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @author mog
 * @version $Id: JDOMSerialize.java,v 1.6 2004/01/22 21:49:21 mog Exp $
 */
public class JDOMSerialize {
	private static final Logger logger = Logger.getLogger(JDOMSerialize.class);

	public static LinkedRemoteFile unserialize(ConnectionManager cm, Reader in, List rslaves) throws FileNotFoundException {
		LinkedRemoteFile root;
		try {
			Document doc = new SAXBuilder().build(in);
			System.out.flush();
			JDOMRemoteFile xmlroot =
				new JDOMRemoteFile(doc.getRootElement(), rslaves);
			root =
				new LinkedRemoteFile(
					xmlroot,
					cm == null ? null : cm.getConfig());
		} catch (FileNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			logger.log(Level.FATAL, "Error loading \"files.xml\"", ex);
			throw new FatalException(ex);
		}
		return root;
	}
	public static Element serialize(LinkedRemoteFile file) {
		throw new UnsupportedOperationException("JDOMSerialize is deprecated");
	}
}
