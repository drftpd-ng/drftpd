package net.sf.drftpd.master;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.RemoteException;

import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Iterator;

import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.RemoteSlave;
import net.sf.drftpd.RemoteFile;

public class SlaveManagerImpl extends UnicastRemoteObject implements SlaveManager {
    protected Hashtable slaves = new Hashtable();
    //protected Hashtable filesystem = new Hashtable();
    protected RemoteFile root;
    
    public RemoteFile getRoot() {
	return root;
    }

    public SlaveManagerImpl(String url) throws RemoteException {
	super();
	try {
	    Naming.rebind(url, this);
	} catch(java.net.MalformedURLException ex) {
	    ex.printStackTrace();
	}
    }

    public void addSlave(String key, Slave slave, RemoteFile remoteroot) throws RemoteException {
	System.out.println("SlaveManager.addSlave(): "+slave);
	RemoteSlave rslave;
	rslave = (RemoteSlave) slaves.get(key);
	if(rslave != null) {
	    rslave.setSlave(slave);
	} else {
	    rslave = new RemoteSlave(slave);
	    slaves.put(key, rslave);
	}
	root = mergeRoot(rslave, root, remoteroot);
    }

    /**
     * mergeRoot merges two RemoteFile filesystems, it does addSlave(slave) on all RemoteFile object.
     *
     * If tofile is older than fromfile, it returns tofile, otherwise it returns fromfile.
     * The parent hashtable (or root RemoteFile reference) should be updated with this returned object.
     *
     * @return RemoteFile returns the RemoteFile object which has the smallest lastModified()
     */
    protected RemoteFile mergeRoot(RemoteSlave slave,
                                   RemoteFile srcdir,
                                   RemoteFile dstdir)
    {
        if(slave == null || srcdir == null) { throw new NullPointerException("slave or srcdir cannot be null"); }

        if(dstdir == null) System.out.println("Ja, dstdir är null");
        
        Hashtable srcmap = srcdir.getHashtable();
        Hashtable dstmap = dstdir.getHashtable();

	Iterator i = srcmap.entrySet().iterator();
	while(i.hasNext()) {
            //Map.Entry entry = (Map.Entry)i.next());
            //RemoteFile srcfile = (RemoteFile)entry.getValue();
            RemoteFile srcfile = (RemoteFile) ((Map.Entry)i.next()).getValue();
            RemoteFile dstfile = (RemoteFile)dstmap.get(srcdir.getName());
            if(srcfile.isDirectory()) {
                //let mergeRoot() merge fromfile's hashtable to tofile's hashtable
                //and put the reference mergeRoot() returns back into the table
                dstmap.put(srcfile.getName() ,mergeRoot(slave, srcfile, dstfile));
            }

            if(dstfile == null) {
                // dstfile has no entry for this file, addSlave() and add to dstmap
                srcfile.addSlave(slave);
                dstmap.put(srcfile.getName(), srcfile);
            } else {
                // file backdating
                
                // dstfile exists, if we're adding an older srcfile,
                // replace the RemoteFile and put it in the Hashtable and keep looping
                if(srcfile.lastModified() > dstfile.lastModified()) {
                    srcfile.getHashtable().putAll(dstfile.getHashtable());
                    dstmap.put(srcfile.getName(), srcfile);
                } else {
                    //just add the remoteslave to the target remotefile
                    dstfile.addSlave(slave);
                }
                //dst.addSlave(slave);
            }
	}
        
        // directory backdating
        if(srcdir.lastModified() > dstdir.lastModified()) {
            srcdir.getHashtable().putAll(dstdir.getHashtable());
            return srcdir;
        } else {
            return dstdir;
        }
    }

    /*
    public void addSlave(String key, Slave slave) {
	RemoteSlave rslave;
	rslave = (RemoteSlave) slaves.get(key);
	if(rslave != null) {
	    rslave.setSlave(slave);
	} else {
	    rslave = new RemoteSlave(slave);
	    slaves.put(key, rslave);
	}
    }
    */

    public RemoteSlave getRemoteSlave(String key) {
	return (RemoteSlave) slaves.get(key);
    }
}
