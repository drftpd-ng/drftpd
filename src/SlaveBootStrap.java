/*
 * Created on 2003-sep-14
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class SlaveBootStrap {
	public static void main(String args[]) throws Throwable {
		URL urls[] = {new URL("http://mog.se/slave.jar")};
		URLClassLoader cl = new URLClassLoader(urls);
		Method met = cl.loadClass("net.sf.drftpd.slave.SlaveImpl").getMethod("main", new Class[] {String[].class});
		met.invoke(null, new Object[] {args});
	}
}
