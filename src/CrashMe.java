import com.thoughtworks.xstream.XStream;


public class CrashMe {
    private Test2 test = new Test2();

    public static void main(String[] args) {
        CrashMe t = (CrashMe) new XStream().fromXML(
                "<Test><test class=\"list\"><object/></test></Test>");
        t.test.tryMe();
    }


    public class Test2 {
        public void tryMe() {
        }
    }
}
