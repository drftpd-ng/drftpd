/*
 * Created on Dec 10, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import java.util.Comparator;

/**
 * @author zubov
 * @version $Id: JobComparator.java,v 1.2 2003/12/23 13:38:21 mog Exp $
 *
 */
public class JobComparator implements Comparator {

	/**
	 * Compares Jobs
	 */
	public JobComparator() {
	}

	/* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Object arg0, Object arg1) {
		Job job1 = (Job) arg0;
		Job job2 = (Job) arg1;
		if (job1.getTimeCreated() < job2.getTimeCreated()) { //older
			return -1;
		}
		if (job1.getTimeCreated() > job2.getTimeCreated()) { //younger
			return 1;
		}
		return 0;
	}

}
