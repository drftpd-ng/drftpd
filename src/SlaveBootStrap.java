import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author mog
 *
 * Starts DrFTPD slave (net.sf.drftpd.slave.SlaveImpl) by loading a .jar file over the network.
 * Takes URL as first argument and passes the rest of the arguments to SlaveImpl.main()
 */
public class SlaveBootStrap {
	public static void main(String args[]) throws Throwable {
		URL urls[] = { new URL(args[0])};
		URLClassLoader cl = new URLClassLoader(urls);
		Method met =
			cl.loadClass("net.sf.drftpd.slave.SlaveImpl").getMethod(
				"main",
				new Class[] { String[].class });
		met.invoke(null, new Object[] { scrubArgs(args, 1)});
	}

	public static String[] scrubArgs(String args[], int scrub) {
		String ret[] = new String[args.length - scrub];
		System.arraycopy(args, scrub, ret, 0, ret.length);
		return ret;
	}
}
