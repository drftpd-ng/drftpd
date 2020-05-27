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
package contrib;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager;
import org.drftpd.master.usermanager.javabeans.BeanGroup;
import org.drftpd.master.vfs.CommitManager;

/**
 * @author Mikevg
 * @version $Id$
 */
public class Upgrade {
    public static void main(String... args) throws Exception{
        if (args.length > 1) {
            System.out.println("We only accept 1 argument (the properties file for input)");
            System.exit(1);
        }
        String inputfile = "groups.properties";
        if(args.length == 1) {
            inputfile = args[0];
        }
        Properties props = new Properties();
        try {
            FileInputStream fin = new FileInputStream(inputfile);
            props.load(fin);
            fin.close();
        } catch(IOException e) {
            System.out.println("Unable to read [" + inputfile + "]");
            System.exit(1);
        }

        EncryptedBeanUserManager bu = new EncryptedBeanUserManager();
        bu.init();
        int i = 1;
        while(props.getProperty("group"+i) != null) {
            String group = props.getProperty("group"+i);
            System.out.println("Creating group [" + group + "]");
            BeanGroup g = (BeanGroup)bu.createGroup(group);
            if(props.getProperty("group"+i+".admin") != null) {
                g.addAdmin(bu.getUserByNameIncludeDeleted(props.getProperty("group"+i+".admin")));
            }
            if(props.getProperty("group"+i+".groupslots") != null) {
                g.setGroupSlots(Integer.parseInt(props.getProperty("group"+i+".groupslots")));
            }
            if(props.getProperty("group"+i+".leechslots") != null) {
                g.setLeechSlots(Integer.parseInt(props.getProperty("group"+i+".leechslots")));
            }
            if(props.getProperty("group"+i+".minratio") != null) {
                g.setMinRatio(Float.parseFloat(props.getProperty("group"+i+".minratio")));
            }
            if(props.getProperty("group"+i+".maxratio") != null) {
                g.setMaxRatio(Float.parseFloat(props.getProperty("group"+i+".maxratio")));
            }
            g.writeToDisk();
            i += 1;
        }
        System.out.println("We created [" + i + "] groups");
    }
}
