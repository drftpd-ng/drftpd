/*
 * Created on 2004-jan-14
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.util.Comparator;

/**
 * @author mog
 * @version $Id: RemoteFileLastModifiedComparator.java,v 1.1 2004/01/14 14:16:41 mog Exp $
 */
public class RemoteFileLastModifiedComparator implements Comparator {

	private boolean _reverse;

	public RemoteFileLastModifiedComparator(boolean reverse) {
		_reverse = reverse;
	}

	public int compare(Object o1, Object o2) {
		long thisVal = ((LinkedRemoteFile)(_reverse ? o2 : o1)).length();
		long anotherVal = ((LinkedRemoteFile)(_reverse ? o1 : o2)).length();
		
		return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
	}

}
