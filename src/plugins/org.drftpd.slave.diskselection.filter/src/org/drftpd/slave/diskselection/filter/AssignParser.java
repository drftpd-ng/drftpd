package org.drftpd.slave.diskselection.filter;

public class AssignParser {
	private long _score;

	private int _root;
	
	private boolean _all = false;

	public AssignParser(String s) {
		if (s.equals("all")) {
			_all = true;
			_score = 0;
			return;
		}
		
		boolean positive;
		int pos = s.indexOf("+");

		if (pos != -1) {
			positive = true;
		} else {
			pos = s.indexOf("-");

			if (pos == -1) {
				_score = 0;
				_root = Integer.parseInt(s);
				return;
			}

			positive = false;
		}

		String root = s.substring(0, pos);
		String assign = s.substring(pos + 1);

		_root = Integer.parseInt(root);

		if (assign.equals("remove")) {
			_score = Integer.MIN_VALUE;
		} else {
			_score = Long.parseLong(assign);

			if (!positive) {
				_score = -_score;
			}
		}
	}

	public int getRoot() {
		return _root;
	}

	public long getScore() {
		return _score;
	}

	public String toString() {
		return getClass() + "@" + hashCode() + "[root=" + getRoot() + ",score="
				+ getScore() + "]";
	}
	
	public boolean allAssigned() {
		return _all;
	}
}
