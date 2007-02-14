package org.drftpd.stats;

import java.util.Date;

/**
 * Set of resetable Stats.
 * The extended means that it also saves dayly/weekly/monthly stats.
 * @author fr0w
 */
public abstract class ExtendedTimedStats extends AbstractTimedStats {
	public static final int P_ALL = 0;
	public static final int P_MONTH = 1;
	public static final int P_WEEK = 2;
	public static final int P_DAY = 3;	
	public static final int P_SIZE = 4;
	
	private long[] _downloadedBytes = new long[P_SIZE];
	private int[] _downloadedFiles = new int[P_SIZE];
	private long[] _downloadedMilliSeconds = new long[P_SIZE];
	
	protected long[] _uploadedBytes = new long[P_SIZE];
	private int[] _uploadedFiles = new int[P_SIZE];
	private long[] _uploadedMilliSeconds = new long[P_SIZE];
	
	// -------------------------------------------
	
	public long getDownloadedBytes() {
		return _downloadedBytes[P_ALL];
	}

	public long getDownloadedBytesDay() {
		return _downloadedBytes[P_DAY];
	}	

	public long getDownloadedBytesWeek() {
		return _downloadedBytes[P_WEEK];
	}
	
	public long getDownloadedBytesMonth() {
		return _downloadedBytes[P_MONTH];
	}
	
	public long getDownloadedBytesForPeriod(int p) {
		try {
			return _downloadedBytes[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}

	//	 -------------------------------------------
	
	public int getDownloadedFiles() {
		return _downloadedFiles[P_ALL];
	}	

	public int getDownloadedFilesWeek() {
		return _downloadedFiles[P_WEEK];
	}

	public int getDownloadedFilesDay() {
		return _downloadedFiles[P_DAY];
	}
	
	public int getDownloadedFilesMonth() {
		return _downloadedFiles[P_MONTH];
	}
	
	public int getDownloadedFilesForPeriod(int p) {
		try {
			return _downloadedFiles[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}
	
	//	----------------------------------------------

	public long getDownloadedTime() {
		return _downloadedMilliSeconds[P_ALL];
	}
	
	public long getDownloadedTimeDay() {
		return _downloadedMilliSeconds[P_DAY];
	}
	
	public long getDownloadedTimeWeek() {
		return _downloadedMilliSeconds[P_WEEK];
	}
	
	public long getDownloadedTimeMonth() {
		return _downloadedMilliSeconds[P_MONTH];
	}

	public long getDownloadedTimeForPeriod(int p) {
		try {
			return _downloadedMilliSeconds[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}
	
	// -----------------------------------------------
	
	public long getUploadedBytes() {
		return _uploadedBytes[P_ALL];
	}

	public long getUploadedBytesDay() {
		return _uploadedBytes[P_DAY];
	}

	public long getUploadedBytesWeek() {
		return _uploadedBytes[P_WEEK];
	}
	
	public long getUploadedBytesMonth() {
		return _uploadedBytes[P_MONTH];
	}
	
	public long getUploadedBytesForPeriod(int p) {
		try {
			return _uploadedBytes[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}

	// ----------------------------------------------
	
	public int getUploadedFiles() {
		return _uploadedFiles[P_ALL];
	}

	public int getUploadedFilesDay() {
		return _uploadedFiles[P_DAY];
	}	

	public int getUploadedFilesWeek() {
		return _uploadedFiles[P_WEEK];
	}

	public int getUploadedFilesMonth() {
		return _uploadedFiles[P_MONTH];
	}

	public int getUploadedFilesForPeriod(int p) {
		try {
			return _uploadedFiles[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}

	// --------------------------------------------
	
	public long getUploadedTime() {
		return _uploadedMilliSeconds[P_ALL];
	}
	
	public long getUploadedTimeDay() {
		return _uploadedMilliSeconds[P_DAY];
	}
	
	public long getUploadedTimeWeek() {
		return _uploadedMilliSeconds[P_WEEK];
	}
	
	public long getUploadedTimeMonth() {
		return _uploadedMilliSeconds[P_MONTH];
	}
	
	public long getUploadedTimeForPeriod(int p) {
		try {
			return _uploadedMilliSeconds[p];
		} catch (Exception e) {
			throw new RuntimeException("Invalid period!", e);
		}
	}
	
	// --------------------------------------
	
	public void resetDay(Date resetDate) {
		setDownloadedFilesDay(0);
		setUploadedFilesDay(0);
		setDownloadedTimeDay(0);
		setUploadedTimeDay(0);
		setDownloadedBytesDay(0);
		setUploadedBytesDay(0);
	}

	public void resetMonth(Date resetDate) {
		setDownloadedFilesMonth(0);
		setUploadedFilesMonth(0);
		setDownloadedTimeMonth(0);
		setUploadedTimeMonth(0);
		setDownloadedBytesMonth(0);
		setUploadedBytesMonth(0);
	}

	public void resetWeek(Date resetDate) {
		setDownloadedFilesWeek(0);
		setUploadedFilesWeek(0);
		setDownloadedTimeWeek(0);
		setUploadedTimeWeek(0);
		setDownloadedBytesWeek(0);
		setUploadedBytesWeek(0);
	}	

	public void resetHour(Date d) {
		// do nothing for now
	}

	public void resetYear(Date d) {
		// do nothing for now
	}
	
	//  ------------------------------------------------
	
	public void setDownloadedBytes(long bytes) {
		_downloadedBytes[P_ALL] = bytes;
	}

	public void setDownloadedBytesDay(long bytes) {
		_downloadedBytes[P_DAY] = bytes;
	}
	
	public void setDownloadedBytesWeek(long bytes) {
		_downloadedBytes[P_WEEK] = bytes;
	}
	
	public void setDownloadedBytesMonth(long bytes) {
		_downloadedBytes[P_MONTH] = bytes;
	}

	public void setDownloadedBytesForPeriod(int p, long bytes) {
		try {
			_downloadedBytes[p] = bytes;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}
	
	// ------------------------------------------
	public void setDownloadedFiles(int files) {
		_downloadedFiles[P_ALL] = files;
	}

	public void setDownloadedFilesDay(int files) {
		_downloadedFiles[P_DAY] = files;
	}
	
	public void setDownloadedFilesWeek(int files) {
		_downloadedFiles[P_WEEK] = files;
	}
	
	public void setDownloadedFilesMonth(int files) {
		_downloadedFiles[P_MONTH] = files;
	}
	
	public void setDownloadedFilesForPeriod(int p, int files) {
		try {
			_downloadedFiles[p] = files;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}	

	// --------------------------------------------
	
	public void setDownloadedTime(long millis) {
		_downloadedMilliSeconds[P_ALL] = millis;
	}

	public void setDownloadedTimeDay(long millis) {
		_downloadedMilliSeconds[P_DAY] = millis;
	}

	public void setDownloadedTimeWeek(long millis) {
		_downloadedMilliSeconds[P_WEEK] = millis;
	}
	
	public void setDownloadedTimeMonth(long millis) {
		_downloadedMilliSeconds[P_MONTH] = millis;
	}
	
	public void setDownloadedTimeForPeriod(int p, long time) {
		try {
			_downloadedMilliSeconds[p] = time;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}
	
	// ------------------------------------------------
	
	public void setUploadedBytes(long bytes) {
		_uploadedBytes[P_ALL] = bytes;
	}

	public void setUploadedBytesDay(long bytes) {
		_uploadedBytes[P_DAY] = bytes;
	}	

	public void setUploadedBytesWeek(long bytes) {
		_uploadedBytes[P_WEEK] = bytes;
	}
	
	public void setUploadedBytesMonth(long bytes) {
		_uploadedBytes[P_MONTH] = bytes;
	}
	
	public void setUploadedBytesForPeriod(int p, long bytes) {
		try {
			_uploadedBytes[p] = bytes;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}

	// 	--------------------------
	
	public void setUploadedFiles(int files) {
		_uploadedFiles[P_ALL] = files;
	}

	public void setUploadedFilesDay(int files) {
		_uploadedFiles[P_DAY] = files;
	}
	
	public void setUploadedFilesWeek(int files) {
		_uploadedFiles[P_WEEK] = files;
	}
	
	public void setUploadedFilesMonth(int files) {
		_uploadedFiles[P_MONTH] = files;
	}

	public void setUploadedFilesForPeriod(int p, int files) {
		try {
			_uploadedFiles[p] = files;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}	
	
	// ---------------------------------------
	
	public void setUploadedTime(long millis) {
		_uploadedMilliSeconds[P_ALL] = millis;
	}

	public void setUploadedTimeDay(long millis) {
		_uploadedMilliSeconds[P_DAY] = millis;
	}
	
	public void setUploadedTimeWeek(long millis) {
		_uploadedMilliSeconds[P_WEEK] = millis;
	}
	
	public void setUploadedTimeMonth(long millis) {
		_uploadedMilliSeconds[P_MONTH] = millis;
	}
	
	public void setUploadedTimeForPeriod(int p, long time) {
		try {
			_uploadedMilliSeconds[p] = time;
		} catch (Exception e) {
			throw new RuntimeException("Invalid period", e);
		}
	}
	
	// ----------------------------------------
	
	public void updateDownloadedBytes(long bytes) {
		_downloadedBytes[P_ALL] += bytes;
		_downloadedBytes[P_DAY] += bytes;
		_downloadedBytes[P_WEEK] += bytes;
		_downloadedBytes[P_MONTH] += bytes;
	}

	public void updateDownloadedFiles(int i) {
		_downloadedFiles[P_ALL] += i;
		_downloadedFiles[P_DAY] += i;
		_downloadedFiles[P_WEEK] += i;
		_downloadedFiles[P_MONTH] += i;
	}

	public void updateDownloadedTime(long millis) {
		_downloadedMilliSeconds[P_ALL] += millis;
		_downloadedMilliSeconds[P_DAY] += millis;
		_downloadedMilliSeconds[P_WEEK] += millis;
		_downloadedMilliSeconds[P_MONTH] += millis;
	}
	
	//  --------------------------------------------
	
	public void updateUploadedBytes(long bytes) {
		_uploadedBytes[P_ALL] += bytes;
		_uploadedBytes[P_DAY] += bytes;
		_uploadedBytes[P_WEEK] += bytes;
		_uploadedBytes[P_MONTH] += bytes;
	}

	public void updateUploadedFiles(int i) {
		_uploadedFiles[P_ALL] += i;
		_uploadedFiles[P_DAY] += i;
		_uploadedFiles[P_WEEK] += i;
		_uploadedFiles[P_MONTH] += i;
	}

	public void updateUploadedTime(long millis) {
		_uploadedMilliSeconds[P_ALL] += millis;
		_uploadedMilliSeconds[P_DAY] += millis;
		_uploadedMilliSeconds[P_WEEK] += millis;
		_uploadedMilliSeconds[P_MONTH] += millis;
	}

}
