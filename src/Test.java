import se.mog.io.File;
/*
import java.util.regex.Matcher;
import java.util.regex.Pattern;
*/

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class Test {

	public static void main(String[] args) {
		/*
		Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
		String subj = "net.sf.drftpd.slave.SlaveImpl[RemoteStub [ref: [endpoint:[127.0.0.1:32907](local),objID:[1]]]]";
		Matcher m = p.matcher(subj);
		if(m.find()) {
			System.out.println(m.group(1));
		}
		*/
		File files[] = File.listMounts();
		for (int i = 0; i < files.length; i++) {
			System.out.println(files[i]+"\t"+files[i].getTotalDiskSpace());
		}
	}
}
