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
package org.drftpd.slaveselection.filter;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;

import org.drftpd.GlobalContext;

import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.User;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class FilterChain {
    private SlaveSelectionManagerInterface _ssm;
    private String _cfgfileName;
    private ArrayList<Filter> _filters;

    protected FilterChain() {
    }

    public FilterChain(SlaveSelectionManagerInterface ssm, Properties p) {
        _ssm = ssm;
        reload(p);
    }

    public FilterChain(SlaveSelectionManagerInterface ssm, String cfgFileName)
        throws FileNotFoundException, IOException {
        _ssm = ssm;
        _cfgfileName = cfgFileName;
        reload();
    }

    public void filter(ScoreChart sc, User user, InetAddress peer, char direction, LinkedRemoteFileInterface file, RemoteSlave sourceSlave) throws NoAvailableSlaveException {
    	for (Filter filter : _filters) {
    		filter.process(sc, user, peer, direction, file, sourceSlave);
        }
	}

    public RemoteSlave getBestSlave(ScoreChart sc, User user, InetAddress peer,
        char direction, LinkedRemoteFileInterface file, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
    	filter(sc,user,peer,direction,file, sourceSlave);
        RemoteSlave rslave = sc.getBestSlave();
        rslave.setLastDirection(direction, System.currentTimeMillis());
        return rslave;
    }

    public void reload() throws FileNotFoundException, IOException {
        Properties p = new Properties();
        p.load(new FileInputStream(_cfgfileName));
        reload(p);
    }

    public void reload(Properties p) {
        ArrayList<Filter> filters = new ArrayList<Filter>();
        int i = 1;

        for (;; i++) {
            String type = p.getProperty(i + ".filter");

            if (type == null) {
                break;
            }

            if (type.indexOf('.') == -1) {
                type = "org.drftpd.slaveselection.filter." +
                    type.substring(0, 1).toUpperCase() + type.substring(1) +
                    "Filter";
            }

            try {
                Class[] SIG = new Class[] {
                        FilterChain.class, int.class, Properties.class
                    };

                Filter filter = (Filter) Class.forName(type).getConstructor(SIG)
                                              .newInstance(new Object[] {
                            this, new Integer(i), p
                        });
                filters.add(filter);
            } catch (Exception e) {
                throw new FatalException(i + ".filter = " + type, e);
            }
        }

        filters.trimToSize();
        _filters = filters;
    }

    public GlobalContext getGlobalContext() {
        return _ssm.getGlobalContext();
    }
}
