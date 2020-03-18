package org.drftpd.plugins.prebw;

import java.util.LinkedList;

/**
 * @author lh
 */
public class PreInfos {
    private static PreInfos ref;
    private LinkedList<PreInfo> _preInfos;

    private PreInfos() {
        _preInfos = new LinkedList<>();
    }

    public LinkedList<PreInfo> getPreInfos() {
        return _preInfos;
    }

    public void clearPreInfos() {
        _preInfos.clear();    
    }

    public static synchronized PreInfos getPreInfosSingleton() {
      if (ref == null)
          // it's ok, we can call this constructor
          ref = new PreInfos();
      return ref;
    }
}
