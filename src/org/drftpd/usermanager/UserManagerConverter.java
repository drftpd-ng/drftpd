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
package org.drftpd.usermanager;

import net.sf.drftpd.DuplicateElementException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.drftpd.plugins.Trial;

import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.Iterator;


/**
 * Usage: java net.sf.drftpd.master.usermanager.UserManagerConverter
 * net.sf.drftpd.master.usermanager.glftpd.GlftpdUserManager
 * net.sf.drftpd.master.usermanager.JSXUserManager
 *
 * @author mog
 * @version $Id$
 */
public class UserManagerConverter {
    private static final Logger logger = Logger.getLogger(UserManagerConverter.class);

    public static void convert(User from, User to) throws UserFileException {
        logger.debug("Converting " + from.getName());

        for (Iterator iter = from.getGroups().iterator(); iter.hasNext();) {
            try {
                to.addSecondaryGroup((String) iter.next());
            } catch (DuplicateElementException e) {
                logger.warn("", e);
            }
        }

        to.getKeyedMap().setAllObjects(from.getKeyedMap());
        to.addAllMasks(from.getHostMaskCollection());

        to.setCredits(from.getCredits());

        to.setDeleted(from.isDeleted());

        to.setGroup(from.getGroup());

        to.setGroupLeechSlots(from.getGroupLeechSlots());

        to.setGroupSlots(from.getGroupLeechSlots());

        to.setIdleTime(from.getIdleTime());

        to.setLastAccessTime(from.getLastAccessTime());

        //to.setLastNuked(from.getLastNuked());
        //to.putObject(Nuke.LASTNUKED, new Long(from
        //		.getObjectLong(Nuke.LASTNUKED)));
        to.setLogins(from.getLogins());

        to.setMaxLogins(from.getMaxLogins());

        to.setMaxLoginsPerIP(from.getMaxLoginsPerIP());

        //to.setMaxSimDownloads(from.getMaxSimDownloads());
        //to.setMaxSimUploads(from.getMaxSimUploads());
        //to.putObject(Nuke.NUKEDBYTES, new Long(from
        //		.getObjectLong(Nuke.NUKEDBYTES)));
        if (from instanceof PlainTextPasswordUser) {
            to.setPassword(((PlainTextPasswordUser) from).getPassword());
        } else if (from instanceof UnixPassword && to instanceof UnixPassword) {
            ((UnixPassword) to).setUnixPassword(((UnixPassword) from).getUnixPassword());
        } else {
            logger.warn("Don't know how to convert password from " +
                from.getName());
        }

        //to.putObject(UserManagment.RATIO, new
        // Float(from.getObjectFloat(UserManagment.RATIO)));
        //to.putObject(UserManagment.TAGLINE,
        // from.getObjectString(UserManagment.TAGLINE));
        //to.putObject(Nuke.NUKED, new Integer(from.getObjectInt(Nuke.NUKED)));
        int[] periods = new int[] {
                Trial.PERIOD_ALL, Trial.PERIOD_DAILY, Trial.PERIOD_MONTHLY,
                Trial.PERIOD_WEEKLY
            };

        for (int i = 0; i < periods.length; i++) {
            int period = periods[i];
            to.setUploadedTimeForTrialPeriod(period,
                from.getUploadedTimeForTrialPeriod(period));

            to.setDownloadedTimeForTrialPeriod(period,
                from.getDownloadedTimeForTrialPeriod(period));

            to.setUploadedBytesForTrialPeriod(period,
                from.getUploadedBytesForTrialPeriod(period));

            to.setDownloadedBytesForTrialPeriod(period,
                from.getDownloadedBytesForTrialPeriod(period));

            to.setUploadedFilesForTrialPeriod(period,
                from.getUploadedFilesForTrialPeriod(period));

            to.setDownloadedFilesForTrialPeriod(period,
                from.getDownloadedFilesForTrialPeriod(period));
        }

        to.commit();
    }

    public static void main(String[] args)
        throws InstantiationException, IllegalAccessException, 
            ClassNotFoundException, IOException, UserFileException {
        BasicConfigurator.configure();

        if (args.length != 2) {
            System.out.println(
                "arguments: <from usermanager class> <to usermanager class>");

            return;
        }

        UserManager from = (UserManager) Class.forName(args[0]).newInstance();
        UserManager to;

        try {
            Constructor c = Class.forName(args[1]).getConstructor(new Class[] {
                        boolean.class
                    });
            to = (UserManager) c.newInstance(new Object[] { new Boolean(false) });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        logger.debug(from.getAllUsers());

        for (Iterator iter = from.getAllUsers().iterator(); iter.hasNext();) {
            User user = (User) iter.next();
            convert(user, to.create(user.getName()));
        }
    }
}
