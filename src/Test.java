import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class Test {
	public static void test(ReferenceQueue queue) {
		Reference ref = new WeakReference(new MyObject("morgan"), queue);

		System.out.println("end of test");
	}
	public static void main(String[] args) {
		ReferenceQueue queue = new ReferenceQueue();
		Hashtable table = new Hashtable();

		WeakReference ref1, ref2;
		ref1 = new WeakReference(new MyObject("göran"), queue);
		ref2 = new WeakReference(new MyObject("mikaela"), queue);

		table.put("göran", ref1);
		table.put("mikaela", ref2);

		test(queue);
		//System.out.println(((WeakReference)table.get("mog")).);
		System.gc();
		//while(true) {
		System.out.println("waiting for queue");
		while (true) {
			Reference ref;
			try {
				ref = queue.remove(10000L);
			} catch (InterruptedException e) {
				System.out.println("continue");
				//System.gc();
				continue;
			}
			if (ref == null) {
				System.out.println("continue2");
				System.gc();
				continue;
			}

			System.out.println("ref.get(): " + ref.get());

			for (Iterator iter = table.values().iterator(); iter.hasNext();) {
				WeakReference refff = (WeakReference) iter.next();
				MyObject obj = (MyObject)refff.get();
				System.out.println("table: "+obj);
			}
		}
		//}
		/*
		Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
		String subj = "net.sf.drftpd.slave.SlaveImpl[RemoteStub [ref: [endpoint:[127.0.0.1:32907](local),objID:[1]]]]";
		Matcher m = p.matcher(subj);
		if(m.find()) {
			System.out.println(m.group(1));
		}
		*/
		/*
		File files[] = File.listMounts();
		for (int i = 0; i < files.length; i++) {
			System.out.println(files[i]+"\t"+files[i].getTotalDiskSpace());
		}
		*/
	}
}
class MyObject {
	String hej;
	public MyObject(String hej) {
		this.hej = hej;
	}
	/* (non-Javadoc)
	* @see java.lang.Object#finalize()
	*/
	protected void finalize() throws Throwable {
		super.finalize();
		System.out.println(hej + " finalized");
	}
}