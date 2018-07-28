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
    long getDownloadedBytes();
	
	/**
	 * Set the amount of data downloaded.
	 * @param bytes
	 */
    void setDownloadedBytes(long bytes);
	
	/**
	 * @return how many times it was/has downloaded.
	 */
    int getDownloadedFiles();
	
	/**
	 * Set how many times it was/has downloaded.
	 * @param files
	 */
    void setDownloadedFiles(int files) ;
	
	/**
	 * @return duration of downloads.
	 */
    long getDownloadedTime();
	
	/**
	 * Set the duration of the downloads.
	 * @param millis
	 */
    void setDownloadedTime(long millis);
	
	/**
	 * @return the amount of data uplaoded.
	 */
    long getUploadedBytes();
	
	/**
	 * Set the amount of data uploaded.
	 * @param bytes
	 */
    void setUploadedBytes(long bytes);
	
	/**
	 * @return how many times it was/has uploaded.
	 */
    int getUploadedFiles();
	
	/**
	 * Set how many times it was/has uploaded.
	 * @param files
	 */
    void setUploadedFiles(int files);
	
	/**
	 * @return duration of uploads.
	 */
    long getUploadedTime();
	
	/**
	 * Set the duration of the uploads.
	 * @param millis
	 */
    void setUploadedTime(long millis);
}
