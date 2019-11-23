/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.trialmanager.types.grouptop;

import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Collections;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.plugins.trialmanager.TrialType;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.drftpd.util.GroupPosition;

/**
 * @author CyBeR
 * @version $Id: TopTrial.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class GroupTop extends TrialType {
	private int _list;
	private int _keep;	
	private long _min;
	private String _keyPrefix;
	private int _minPercent;
	
	public GroupTop(Properties p, int confnum, String type) {
		super(p, confnum, type);
		
		try {
			_minPercent = Integer.parseInt(p.getProperty(confnum + ".percent", "0").trim());
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid minimum percentange for " + confnum + ".percent - Skipping Config");
		}

		try {
			_list = Integer.parseInt(p.getProperty(confnum + ".list","").trim());
		} catch (NumberFormatException e) {
			_list = 10;
		}
		
		try {
        	_min = Long.parseLong(p.getProperty(confnum + ".min","-1").trim()) * 1000 * 1000 * 1000;
        	} catch (NumberFormatException e) {
        		throw new RuntimeException("Invalid Min for " + confnum + ".min - Skipping Config");        
        	}
		
		try {
			_keep = Integer.parseInt(p.getProperty(confnum + ".keep","").trim());
		} catch (NumberFormatException e) {
			_keep = 10;
		}

		_keyPrefix = this.getClass().getName() + ".";
	}

	
	public int getList() {
		return _list;
	}
	
	public int getKeep() {
		return _keep;
	}
	
	public long getMin() {
		return _min;
	}

	public int getMinPercent() {
		return _minPercent;
	}
	
	private void handlePassed(User user) {
		String[] commands = this.getPass().split(" ");

		if (commands.length > 1) {
			if (commands[0].equalsIgnoreCase("chgrp")) {
				for (int i=1;i<commands.length;i++) {
					user.toggleGroup(commands[i]);
                    logger.info("{} Toggled Group ('{}')", user.getName(), commands[i]);
				}
				return;
			}
			if (commands[0].equalsIgnoreCase("setgrp")) {
				user.setGroup(commands[1]);
                logger.info("{} Primary Group Set To ('{}')", user.getName(), commands[1]);
				return;
			}			
		}
        logger.info("No Mode found for {} - Passed - {}", getEventType(), getPass());
	}

	private void handleFailed(User user) {
		String[] commands = this.getFail().split(" ");

		if (commands.length > 0) {
			if (commands.length > 1) {
				if (commands[0].equalsIgnoreCase("chgrp")) {
					for (int i=1;i<commands.length;i++) {
						user.toggleGroup(commands[i]);
                        logger.info("{} Toggled Group ('{}')", user.getName(), commands[i]);
					}
					return;
				}
				if (commands[0].equalsIgnoreCase("setgrp")) {
					user.setGroup(commands[1]);
                    logger.info("{} Primary Group Set To ('{}')", user.getName(), commands[1]);
					return;
				}
			} else {
				if (commands[0].equalsIgnoreCase("delete")) {
					user.setDeleted(true);
                    logger.info("{} Deleted", user.getName());
					return;
				}
				if (commands[0].equalsIgnoreCase("purge")) {
					user.setDeleted(true);
                    logger.info("{} Purged", user.getName());
					user.purge();
					return;
				}
			}
		}
        logger.info("No Mode found for {} - Failed - {}", getEventType(), getFail());
	}	

	@Override
	public void doTrial() {
		int passed = 0;

		long minPercentage = getTop() / 100 * getMinPercent();

		ArrayList<User> users = getUsers();
		for (User user : users) {
			if ((user.getUploadedBytesForPeriod(getPeriod()) > this.getMin()) && (user.getUploadedBytesForPeriod(getPeriod()) >= minPercentage) && (passed < getKeep())) {
				passed++;
				handlePassed(user);
			} else {
				handleFailed(user);
			}
		}
		
		GlobalContext.getEventService().publish(new GroupTopEvent(getName(),users,getPeriod(),getKeep(),getMin()));
		
	}

	private long getTop() {
		long top=0;
		ArrayList<User> users = getUsers();
		for (User user : users) {
			if (user.getUploadedBytesForPeriod(getPeriod())>top) {
				top=user.getUploadedBytesForPeriod(getPeriod());
			}
		}
		return top;
	}	
	
	private CommandResponse doGroupTop(CommandRequest request, ResourceBundle bundle, CommandResponse response, boolean top) {
		User requestuser;
		try {
			requestuser = request.getUserObject();
		} catch (NoSuchUserException e1) {
			//No Such User Exists - return
			return response;
		} catch (UserFileException e1) {
			// User File Corrupt - return
			return response;
		}
		
		/*
		 * Gets the List Size
		 */
		int list = getList();
		if (request.hasArgument()) {
			try {
				list = Integer.parseInt(request.getArgument().trim());
				if ((list < 1) || (list > 100)) {
					list = getList();
				}
			} catch (NumberFormatException e) {
				list = getList();
			}
		}

		/*
		 * Gets the Groups and members
		 */
        MyGroupPosition stat = null;
        String groupname = "";
        ArrayList<User> users = getUsers();
        ArrayList<MyGroupPosition> grpList = new ArrayList<>();
        long minPercentage = getTop() / 100 * getMinPercent();
        for (User user : users) {
            groupname=user.getGroup();
            for (MyGroupPosition stat2 : grpList) {
                if (stat2.getGroupname().equals(groupname)) {
                    stat=stat2;
                    break;
                }
            }

            if (stat==null) {
                stat = new MyGroupPosition(groupname,0,0,0,0,0);
                grpList.add(stat);
            }

            stat.updateBytes(user.getUploadedBytesForPeriod(getPeriod()));
            stat.updateMembers(1);

            stat=null;
        }

        Collections.sort(grpList);

		ReplacerEnvironment env2 = new ReplacerEnvironment();
		env2.add("name", getName());
		env2.add("min", Bytes.formatBytes(getMin()));
		env2.add("period", getPeriodStr());
		env2.add("time",getRemainingTime());
		env2.add("keep",getKeep());
		env2.add("percent", getMinPercent());
        env2.add("grps", grpList.size());

		if (top) {
			if (getMin() > 0) {
				response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "top.header.min", env2, requestuser));
			} else {
				response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "top.header", env2, requestuser));
			}
		} else {
			if (getMin() > 0) {
				response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "cut.header.min", env2, requestuser));
			} else {
				response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "cut.header", env2, requestuser));
			}
		}

		int i=1;
		for (MyGroupPosition grp : grpList) {
			if (i>list) {
				break;
			}
			
			ReplacerEnvironment env = new ReplacerEnvironment();
			long uploaded=grp.getBytes();
			long avguploaded=uploaded/grp.getMembers();
			env.add("min",Bytes.formatBytes((getMin()*grp.getMembers())));
			env.add("grpbytes",Bytes.formatBytes(uploaded));
			env.add("avgbytes",Bytes.formatBytes(avguploaded));
			env.add("grpname",grp.getGroupname());
			env.add("grpsize",grp.getMembers());
			env.add("grprank",i);

			if (i < 10) {
				env.add("rank","0" + i);
			}
			
			if ((i < getKeep()) && (uploaded >= (getMin()*grp.getMembers())) && (uploaded >= minPercentage)) {
				//Passing
				if (top) {
					response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "top.passed", env, requestuser));
				}
			} else {
				//Failing
				if (top) {
					response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "top.failed", env, requestuser));
				} else {
					response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "cut.failed", env, requestuser));
				}
			}
			i++;
		}
		return response;
	}

	@Override
	public void doTop(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
		doGroupTop(request,bundle,response,true);
	}
 

	@Override
	public void doCut(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
		doGroupTop(request,bundle,response,false);
	}


	@Override
	public void doPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
		doGroupPassed(request,bundle,response);
	}

	
	private CommandResponse doGroupPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
		String group = request.getArgument();

		User requestuser = null;
		try {
			requestuser = request.getUserObject();
		} catch (NoSuchUserException e1) {
			return response;
		} catch (UserFileException e1) {
			return response;
		}

		if (group.equals("") || group == null) {
			group = requestuser.getGroup();
		}

		MyGroupPosition stat=null;
		String groupname="";
		ArrayList<User> users = getUsers();
		ArrayList<MyGroupPosition> grpList = new ArrayList<>();
		for (User user : users) {
			groupname = user.getGroup(); 
				for (MyGroupPosition stat2 : grpList) {
					if (stat2.getGroupname().equals(groupname)) {
						stat=stat2;
						break;
					}
				}
				if (stat==null) {
					stat=new MyGroupPosition(groupname,0,0,0,0,0);
					grpList.add(stat);
				}

				stat.updateBytes(user.getUploadedBytesForPeriod(getPeriod()));
				stat.updateMembers(1);

				stat=null;
		}

		Collections.sort(grpList);

		int i=1;
		MyGroupPosition grp = null;
		for (MyGroupPosition gr : grpList) {
			if (gr.getGroupname().equals(group)) {
				grp=gr;
				break;
			}
			i++;
		}

		ReplacerEnvironment env2 = new ReplacerEnvironment();
		env2.add("grpname",group);

		if (grp==null) {
			response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "passed.nosuchgroup", env2, requestuser));
			return response;
		}

	
		long uploaded=grp.getBytes();	
		env2.add("min",Bytes.formatBytes(getMin()*grp.getMembers()));
		env2.add("upBytes", Bytes.formatBytes(uploaded));
		env2.add("size", grp.getMembers());
		env2.add("upBytesPU", Bytes.formatBytes(grp.getBytes()/grp.getMembers()));
		env2.add("rank",i);
		env2.add("percent", getMinPercent());

		long minPercentage = getTop() / 100 * getMinPercent();

		if ((i < getKeep()) && (uploaded >= (getMin()*grp.getMembers())) && (uploaded >= minPercentage)) {
			response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "passed.passed.header", env2, requestuser));
		} else {
			response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "passed.failed.header", env2, requestuser));
		}

		int count = 0;
		for (User user : users) {
			if (user.isMemberOf(group)) {
				++count;
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("name", this.getName());
				env.add("usernick",user.getName());
				env.add("usergroup",user.getGroup());
				env.add("rank",count);
				uploaded = user.getUploadedBytesForPeriod(getPeriod());
				env.add("up",Bytes.formatBytes(uploaded));
				env.add("time", getRemainingTime());
				
				if ((count < getKeep()) && (uploaded >= getMin())) {
					response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "passed.passed", env, requestuser));
				} else {
					response.addComment(request.getSession().jprintf(bundle,_keyPrefix + "passed.failed", env, requestuser));
				}				
				
			}
		}
		return response;
	}
	
	static class MyGroupPosition extends GroupPosition {
		int members;

		public MyGroupPosition(String groupname, long bytes, int files,
				long xfertime, int members, int racesWon) {
			super(groupname, bytes, files, xfertime);
			this.members = members;
		}

		@Override
		public int compareTo(GroupPosition o) {
			MyGroupPosition mo = (MyGroupPosition) o;
            long thisVal = getBytes()/getMembers();
			long anotherVal = mo.getBytes()/mo.getMembers();
			return (Long.compare(anotherVal, thisVal));
		}


		public int getMembers() {
			return this.members;
		}
		public void updateMembers(int updatemembers) {
			members += updatemembers;
		}
	}
}
