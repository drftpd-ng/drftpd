package net.sf.drftpd;


/**
 * @author mog
 * @version $Id: Nukee.java,v 1.5 2004/01/05 00:14:19 mog Exp $
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

	public Nukee(String user, long amount) {
		_username = user;
		_amount = amount;
	}

	public String getUsername() {
		return _username;
	}

	/**
	 * Returns the amount nuked without multiplier.
	 * @return the amount nuked without multiplier.
	 */
	public long getAmount() {
		return _amount;
	}

}
