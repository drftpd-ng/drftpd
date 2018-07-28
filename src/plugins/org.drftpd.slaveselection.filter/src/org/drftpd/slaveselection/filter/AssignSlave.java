package org.drftpd.slaveselection.filter;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class AssignSlave {
	private static GlobalContext _gctx;
	
	/**
	 * This method is needed by the TestCases.
	 * @param gctx
	 */
	public static void setGlobalContext(GlobalContext gctx) {
		_gctx = gctx;
	}
	
	/**
	 * This method is needed by the TestCases.
	 */
	public static GlobalContext getGlobalContext() {
		if (_gctx == null)
			_gctx = GlobalContext.getGlobalContext();
		return _gctx;
	}
	
	public static ArrayList<AssignParser> parseAssign(String p) throws ObjectNotFoundException {
		StringTokenizer st = new StringTokenizer(p.replaceAll(",", ""));
		ArrayList<AssignParser> list = new ArrayList<>();

		while (st.hasMoreTokens()) {
			String toParse = st.nextToken();
			AssignParser ap = new AssignParser(toParse);
			list.add(ap);
		}
				
		list.trimToSize();
		return list;
	}
	
	public static void addScoresToChart(ArrayList<AssignParser> aps, ScoreChart sc) {
		for (AssignParser ap : aps) {
			// assign all slaves.
			if (ap.allAssigned()) {
				for (SlaveScore score : sc.getSlaveScores()) {
					score.addScore(ap.getScore());
				}

				return;
			}
			
			try {
				if (ap.isRemoved()) {
					sc.removeSlaveFromChart(ap.getRSlave());
				} else {
					sc.addScoreToSlave(ap.getRSlave(), ap.getScore());
				}
			} catch (ObjectNotFoundException e) {
				// slave is not in the scorechart, but that's np.
			}
		}
	}
}

class AssignParser {
	private long _score;

	private RemoteSlave _rslave;
	
	private boolean _all = false;
	
	private boolean _removed = false;

	public AssignParser(String s) throws ObjectNotFoundException{
		boolean positive;
		int pos = s.indexOf("+");

		if (pos != -1) {
			positive = true;
		} else {
			pos = s.indexOf("-");

			if (pos == -1) {
				throw new IllegalArgumentException(s+ " is not a valid assign slave expression");
			}

			positive = false;
		}
		
		String assign = s.substring(pos + 1);		
		String slavename = s.substring(0, pos);
		
		if (slavename.equalsIgnoreCase("all")) {
			_all = true;
			_score = Long.parseLong(assign);
			return;
		}

		try {
			_rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
		} catch (ObjectNotFoundException e) {
			throw new ObjectNotFoundException(slavename + " does not exist.", e);
		}

		if (assign.equals("remove")) {
			_score = Integer.MIN_VALUE;
			_removed = true;
			positive = false;
		} else {
			_score = Long.parseLong(assign);
			if (!positive) {
				_score = -_score;
			}
		}
	}
	
	public boolean isRemoved() {
		return _removed;
	}

	public RemoteSlave getRSlave() {
		return _rslave;
	}

	public long getScore() {
		return _score;
	}

	public String toString() {
		return getClass() + "@" + hashCode() + "[rslave=" + getRSlave().getName() + ",score="+ getScore() + "]";
	}
	
	public boolean allAssigned() {
		return _all;
	}
}
