/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.plugins.prebw;

import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * @author lh
 */
public class PreInfo {
    private ArrayList<UserInfo> _leechers = new ArrayList<>();
    private ArrayList<String> _groups = new ArrayList<>();
    private ArrayList<Long> _bw = new ArrayList<>();
    private LinkedHashMap<String, String> _messures = new LinkedHashMap<>();
    private DirectoryHandle _dir;
    private SectionInterface _section;
    private int _mtime = 0;
    private TimeSetting _timeSetting;

    public PreInfo(DirectoryHandle dir, SectionInterface section, TimeSetting timeSetting) {
        _dir = dir;
        _section = section;
        _timeSetting = timeSetting;
    }

    public TimeSetting getTimeSetting() {
        return _timeSetting;
    }

    public ArrayList<UserInfo> getUsers() {
        return _leechers;
    }

    public UserInfo getUser(String userName) {
        for (UserInfo u : _leechers) {
            if (u.getName().equals(userName))
                return u;
        }
        return null;
    }

    public UserInfo getUser(int nr) {
        return _leechers.get(nr);
    }

    public void addUser(UserInfo u) {
        _leechers.add(u);
    }

    public ArrayList<String> getGroups() {
        return _groups;
    }

    public void addGroup(String group) {
        boolean foundGroup = false;
        for (String g : _groups) {
            if (g.equals(group)) {
                foundGroup = true;
                break;
            }
        }
        if (!foundGroup)
            _groups.add(group);
    }

    public LinkedHashMap<String,String> getMessures() {
        return _messures;
    }

    public void setMessures(String time, String bw) {
        _messures.put(time, bw);
    }

    public long getBWAvg() {
        long tmpSpeed = 0;
        Collections.sort(_bw);
        Collections.reverse(_bw);
        int countAvg;
        if (_timeSetting.getCountAvg().equals("*"))
            countAvg = Integer.MAX_VALUE;
        else
            countAvg = Integer.parseInt(_timeSetting.getCountAvg());
        int i = 0;
        for (long bw : _bw) {
            if (i == countAvg)
                break;
            tmpSpeed += bw;
            i++;
        }
        return tmpSpeed/i;
    }

    public long getBWTop() {
        long tmpSpeed = 0;
        for (Long bw : _bw) {
            if (bw > tmpSpeed) { tmpSpeed = bw; }
        }
        return tmpSpeed;
    }

    public void addBW(long bw) {
        _bw.add(bw);
    }

    public DirectoryHandle getDir() {
        return _dir;
    }

    public SectionInterface getSection() {
        return _section;
    }

    public long getBytes() {
        long tmpBytes = 0;
        for (UserInfo u : _leechers) {
            tmpBytes += u.getBytes();
        }
        return tmpBytes;
    }

    public int getMtime() {
        return _mtime;
    }
    
    public void setMtime(int mtime) {
        _mtime = mtime;
    }
}