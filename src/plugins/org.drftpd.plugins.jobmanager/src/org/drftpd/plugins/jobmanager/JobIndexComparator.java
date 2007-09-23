package org.drftpd.plugins.jobmanager;

import java.util.Comparator;

/**
 * 
 * @author zubov
 * @version $Id: JobIndexComparator.java 1621 2007-02-13 20:41:31Z djb61 $
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
