package net.sf.drftpd;


/**
 * @author mog
 * @version $Id: Nukee.java,v 1.4 2003/12/23 13:38:18 mog Exp $
 */
public class Nukee implements Comparable {
	private String _username;
	private long _amount;

	public long getBytes() {
		return _amount;
	}

	public int compareTo(Object o) {
		return compareTo((Nukee) o);
	}

	public int compareTo(Nukee o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();
		return (
			thisVal < anotherVal ? 1 : (thisVal == anotherVal ? 0 : -1));
	}

	public Nukee(String user, long l) {
		_username = user;
		_amount = l;
	}

	public String getUsername() {
		return _username;
	}

	public long getAmount() {
		return _amount;
	}

}
