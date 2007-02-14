package org.drftpd.stats;

/**
 * Stats interface.<br>
 * Simple interface, JavaBeans-ready, that helps storing some Stats data like
 * number of downloaded/uploaded files, amount of transfered data
 * etc...
 * @author fr0w
 */
public interface StatsInterface {
	/**
	 * @return the amount of data downloaded.
	 */
	public long getDownloadedBytes();
	
	/**
	 * Set the amount of data downloaded.
	 * @param bytes
	 */
	public void setDownloadedBytes(long bytes);
	
	/**
	 * @return how many times it was/has downloaded.
	 */
	public int getDownloadedFiles();
	
	/**
	 * Set how many times it was/has downloaded.
	 * @param files
	 */
	public void setDownloadedFiles(int files) ;
	
	/**
	 * @return duration of downloads.
	 */
	public long getDownloadedTime();
	
	/**
	 * Set the duration of the downloads.
	 * @param millis
	 */
	public void setDownloadedTime(long millis);
	
	/**
	 * @return the amount of data uplaoded.
	 */
	public long getUploadedBytes();
	
	/**
	 * Set the amount of data uploaded.
	 * @param bytes
	 */
	public void setUploadedBytes(long bytes);
	
	/**
	 * @return how many times it was/has uploaded.
	 */
	public int getUploadedFiles();
	
	/**
	 * Set how many times it was/has uploaded.
	 * @param files
	 */
	public void setUploadedFiles(int files);
	
	/**
	 * @return duration of uploads.
	 */
	public long getUploadedTime();
	
	/**
	 * Set the duration of the uploads.
	 * @param millis
	 */
	public void setUploadedTime(long millis);
}
