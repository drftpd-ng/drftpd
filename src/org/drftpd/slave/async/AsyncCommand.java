/*
 * AsyncCommand.java
 *
 * Created on May 20, 2004, 12:19 PM
 */

package org.drftpd.slave.async;

import java.util.Hashtable;

/**
 *
 * @author  jbarrett
 */
public class AsyncCommand {
    String      _name;
    String      _args;
    String      _chan;
    int         _stat;
    Hashtable   _data = new Hashtable();
    boolean     _done = false;
    AsyncSlave  _ownr;
    
    public AsyncCommand(String chan, String name, String args, AsyncSlave ownr) {
        _chan = chan;
        _name = name;
        _args = args;
        _ownr = ownr;
        _stat = -1;
    }
    public void abort()
    {
        _ownr.sendLine(_chan + " abrt");
    }
    public String getName() {
        return _name;
    }
    public String getArgs() {
        return _args;
    }
    public Hashtable getData() {
        return _data;
    }
    public int getStatus() {
        return _stat;
    }
    public void setStatus(int stat) {
        finished();
    }
    public void finished() {
        _ownr.releaseChan(_chan);
        _done = true;
    }
    public void waitForComplete() {
        while (!_done) {
            try { 
                java.lang.Thread.currentThread().sleep(100); 
            } catch (Exception e) {}
        }
    }   
}
