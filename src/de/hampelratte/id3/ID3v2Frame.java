package de.hampelratte.id3;

import java.io.*;

/**	One frame of an id3v2.3 tag
 *	@author <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */
public class ID3v2Frame
{
	private MP3File mp3;
	private long startpos = 0;
	private byte[] header = new byte[10];
	private boolean[] flags = new boolean[8]; 
	private long size = 0;
	private byte[] content;
	private byte encoding = 0;
	private String ident = "";
	
	
	/** Constructor to create an empty frame */
	public ID3v2Frame(String ident)
	{
		this.ident = ident;	
	}
	
	
	/**	Constructor to read a frame
	 *	@param MP3File mp3
	 *	@param long startpos
	 *	@param String ident
	 *	@throws IOException
	 */
	public ID3v2Frame(MP3File mp3, long startpos, String ident) throws IOException
	{
		this.mp3 = mp3;
		this.startpos = startpos;
		this.ident = ident;
		if(startpos > -1)
		{
			readHeader();
			evaluateHeader();
			readContent();
		}
	}

	
	/** Reads the frame header from a mp3 file into a byte array
	 *	@throws IOException	
	 */
	private void readHeader() throws IOException
	{
		mp3.seek(startpos);
		mp3.read(header);
	}
	
	
	/** Reads the encoding type and frame content
	 *	@throws IOException
	 */
	private void readContent() throws IOException
	{
		byte[] temp = new byte[content.length+1];
		mp3.seek(startpos+10);
		mp3.read(temp);
		encoding = temp[0];
		for(int i=0; i<content.length; i++)
		{
			content[i] = temp[i+1];	
		}
	}
	
	
	/** Decodes the header.
	 *	This method detects the frame size, the frame flags and the frame content.
	 *	@throws IOException
	 */
	private void evaluateHeader() throws IOException
	{
		// decoding size
		byte[] sizeBytes = new byte[] { header[4], header[5], header[6], header[7] };
		size = SynchsafeInteger.decode(sizeBytes);
		
		// decode flags
		flags[0] = (header[8] & 0x40) != 0;
		flags[1] = (header[8] & 0x20) != 0;
		flags[2] = (header[8] & 0x10) != 0;
		flags[3] = (header[9] & 0x40) != 0;
		flags[4] = (header[9] & 0x08) != 0;
		flags[5] = (header[9] & 0x04) != 0;
		flags[6] = (header[9] & 0x02) != 0;
		flags[7] = (header[9] & 0x01) != 0;
		
		content = new byte[(int)size-1];
	}
	
	/**	Returns the frame content
	 *	@return Frame content as byte[]
	 */
	public byte[] getContent()
	{
		return content;	
	}
	
	/** Sets the frame content */
	public void setContent(byte[] content)
	{
		long oldsize = size;
		this.content = content;
		long newsize = content.length;
		size += (newsize - oldsize) + 1;
	}
	
	/** Returns the encoding of this frame
	 *	@return Encoding type as a byte
	 */
	public byte getEncoding()
	{
		return encoding;	
	}
	
	/** Sets the encoding type of this frame */
	public void setEncoding(byte encoding)
	{
		this.encoding = encoding;	
	}
	
	/** Sets the flags of this frame.
	 *	@param boolean tagAlter
	 *	@param boolean fileAlter
	 *	@param boolean readonly
	 *	@param boolean grouping
	 *	@param boolean compression
	 *	@param boolean encryption
	 *	@param boolean unsynchronisation
	 *	@param boolean dataLengthIndicator
	 */
	public void setFlags(	boolean tagAlter, boolean fileAlter, boolean readonly, boolean grouping,
							boolean compress, boolean encrypt,   boolean unsync,   boolean datalength)
	{
		flags[0] = tagAlter;
		flags[1] = fileAlter;
		flags[2] = readonly;
		flags[3] = grouping;
		flags[4] = compress;
		flags[5] = encrypt;
		flags[6] = unsync;
		flags[7] = datalength;
	}
	
	/** Returns the frame identifier. 
	 *	A four characters long String. Something like "XXXX".
	 *	@return Frame identifier as a String
	 */
	public String getIdent()
	{
		return ident;	
	}
	
	/** Converts the eight flag booleans to the according byte values
	 *	@return Flags as a byte[]
	 */
	private byte[] convertFlagsToByte()
	{	
		byte[] result = new byte[2];
	
		if(flags[0]) result[0] += 64;
		if(flags[1]) result[0] += 32;
		if(flags[2]) result[0] += 16;
		if(flags[3]) result[1] += 64;
		if(flags[4]) result[1] +=  8;
		if(flags[5]) result[1] +=  4;
		if(flags[6]) result[1] +=  2;
		if(flags[7]) result[1] +=  1;
		
		return result;
	}
	
	/**	Converts this frame to a byte[], so that we can write to a file or elsewhere 
	 *	@return Frame as a byte[]
	 *	@throws IOException
	 */
	public byte[] toByteArray() throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bos.write(ident.getBytes());
		bos.write(SynchsafeInteger.encode(size,4));
		bos.write(convertFlagsToByte());
		bos.write(encoding);
		bos.write(content);
		
		return bos.toByteArray();
	}
}
