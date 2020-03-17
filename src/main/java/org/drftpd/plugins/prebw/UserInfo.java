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

import java.util.ArrayList;

/**
 * @author lh
 */
public class UserInfo {
    private String _userName;
    private String _group;
    private int _files;
    private long _bytes;
    private ArrayList<Long> _speeds = new ArrayList<>();

    public UserInfo(String userName, String group, int files, long bytes, long speed) {
        _userName = userName;
        _group = group;
        _files = files;
        _bytes = bytes;
        _speeds.add(speed);
    }

    public String getName() {
        return _userName;
    }

    public String getGroup() {
        return _group;
    }

    public int getFiles() {
        return _files;
    }

    public void addFiles(int files) {
        _files += files;
    }

    public long getBytes() {
        return _bytes;
    }
    
    public void addBytes(long bytes) {
        _bytes += bytes;
    }

    public void addSpeed(long speed) {
        _speeds.add(speed);
    }

    public long getAvgSpeed() {
        if (_speeds.isEmpty())
            return 0L;
        long total = 0L;
        for (long speed : _speeds) {
            total += speed;
        }
        return total/_speeds.size();
    }

    public long getTopSpeed() {
        long top = 0L;
        for (long speed : _speeds) {
            if (speed > top)
                top = speed;
        }
        return top; 
    }
}