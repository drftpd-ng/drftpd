package net.sf.drftpd;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Write ASCII data. Before writing it filters the data.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public
class AsciiOutputStream extends OutputStream {
    
    private long    mlActualByteWritten = 0;
    private boolean mbIgnoreNonAscii    = true;
    private OutputStream mOutputStream;
    
    /**
     * Constructor.
     * @param os <code>java.io.OutputStream</code> to be filtered.
     */
    public AsciiOutputStream(OutputStream os) {
        mOutputStream = os;
    }
     
    /**
     * Write a single byte. 
     * ASCII characters are defined to be
     * the lower half of an eight-bit code set (i.e., the most
     * significant bit is zero). Change "\n" to "\r\n".
     */
    public void write(int i) throws IOException {
        
        if (mbIgnoreNonAscii && (i > 0x7F) ) {
            return;
        }
        
        if (i == '\r') {
            return;
        }
        if (i == '\n') {
            actualWrite('\r');
        }
        actualWrite(i);
        
    } 
    
    /**
     * Close stream
     */
    public void close() throws IOException {
        mOutputStream.close();
    }
    
    /**
     * Flush stream data
     */
    public void flush() throws IOException {
        mOutputStream.flush();
    }
    
    /**
     * write actual data.
     */
    private void actualWrite(int b) throws IOException {
        mOutputStream.write(b);
        ++mlActualByteWritten;
    }

        
    /**
     * Get actual byte written.
     */
    public long getByteWritten() {
        return mlActualByteWritten;
    }

    /**
     * Is non ascii character ignored. 
     * If true don't write non-ascii character.
     * Else first convert it to ascii by ANDing with 0x7F. 
     */
    public boolean getIsIgnoreNonAscii() {
        return mbIgnoreNonAscii;
    }

    /**
     * Set non-ascii ignore boolean value.
     */
    public void setIsIgnoreNonAscii(boolean ig) {
      mbIgnoreNonAscii = ig;  
    }
   
}
