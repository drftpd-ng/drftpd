package net.sf.drftpd.master;

/**
 * Ftp command request class. We can access command, line and argument using 
 * <code>{CMD}, {ARG}</code> within ftp status file. This represents 
 * single Ftp request.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @version $Id: FtpRequest.java,v 1.6 2003/12/23 13:38:19 mog Exp $
 */
public class FtpRequest {

	private String line = null;
	private String command = null;
	private String argument = null;

	/**
	 * Constructor.
	 *
	 * @param commandLine ftp input command line.
	 */
	public FtpRequest(String commandLine) {
		line = commandLine.trim();
		parse();
	}

	/**
	 * Parse the ftp command line.
	 */
	private void parse() {
		int spInd = line.indexOf(' ');

		if (spInd != -1) {
			command = line.substring(0, spInd).toUpperCase();
			argument = line.substring(spInd + 1);					
			if (command.equals("SITE")) {
				spInd = line.indexOf(' ', spInd+1);
				if(spInd != -1) {
					command = line.substring(0, spInd).toUpperCase();
					argument = line.substring(spInd + 1);
				} else {
					command = line.toUpperCase();
					argument = null;
				}
			}
		} else {
			command = line.toUpperCase();
			argument = null;
		}

//		if ((command.length() > 0) && (command.charAt(0) == 'X')) {
//			command = command.substring(1);
//		}
	}

	/**
	 * Get the ftp command.
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get ftp input argument.  
	 */
	public String getArgument() {
		if(argument == null) throw new IllegalStateException();
		return argument;
	}

	/**
	 * Get the ftp request line.
	 */
	public String getCommandLine() {
		return line;
	}

	/**
	 * Has argument.
	 */
	public boolean hasArgument() {
		return argument != null;
	}

}
