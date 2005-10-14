package net.sf.drftpd.mirroring;

import java.util.Comparator;
/**
 * 
 * @author zubov
 * @version $Id$
 */
public class JobIndexComparator implements Comparator<Job> {
	public int compare(Job arg0, Job arg1) {
		long diff = arg0.getIndex() - arg1.getIndex();
		if (diff < 0) {
			return -1;
		} else if (diff > 0) {
			return 1;
		}
		return 0;
	}
}
