package de.hampelratte.id3;


/** The header of an id3v2.3 tag
 *
 * @author <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */
public class Header
{
	private byte[] headerBytes = new byte[10];
	private byte major = 0;
	private byte minor = 0;
	private byte flagByte = 0;
	private boolean[] flags = new boolean[4];
	private long size = 0L;
	
	/** Constructor to create an empty header -> insert your own data */
	public Header() {}
	
	/** Returns the header as a byte[] 
	 *	@return Header as a byte[]
	 */
	public byte[] getHeaderBytes()
	{
		return headerBytes;	
	}
	
	/** Replaces current header data with bytes of given byte[].
	 *	Don't make use of this method until you know what you are doing!
	 *	@param byte[] headerBytes
	 *	@throws IllegalHeaderException
	 *	@see #evaluateHeaderBytes()
	 */
	public void setHeaderBytes(byte[] headerBytes) throws IllegalHeaderException
	{
		if(headerBytes.length == 10)
		{
			this.headerBytes = headerBytes;
			evaluateHeaderBytes();
		}
		else throw new IllegalHeaderException("Wrong header size!");
	}
	
	/** Sets the major version number for the tag/header.
	 *	@param byte major
	 */
	public void setMajorVersion(byte major)
	{
		this.major = major;	
	}
	
	/** Gets the major version number of the tag/header.
	 *	@return Major version number as a byte
	 */
	public byte getMajorVersion()
	{
		return major;
	}
	
	/** Sets the minor version number for the tag/header.
	 *	@param byte minor
	 */
	public void setMinorVersion(byte minor)
	{
		this.minor = minor;	
	}
	
	/** Gets the minor version number of the tag/header.
	 *	@return Minor version number as a byte
	 */
	public byte getMinorVersion()
	{
		return minor;
	}
	
	/** Sets the flags of the tag/header.
	 *  Due to synchronisation issues this method calls convertToBoolean().
	 *	@param byte flagByte
	 *	@see #convertToBoolean()
	 */
	public void setFlagByte(byte flagByte)
	{
		this.flagByte = flagByte;
		convertToBoolean();
	}
	
	/** Returns the flags as a byte.
	 * 	This is the size exclusive the header -> total size - 10.
	 *	@return Flags as a byte.
	 */
	public byte getFlagByte()
	{
		return flagByte;
	}
	
	/** Sets the size of the tag.
	 * 	Notice: Don't include the header -> total size - 10
	 *	@param long size
	 */
	public void setSize(long size)
	{
		this.size = size;	
	}
	
	/** Returns the size as a long
	 *	@return Size as a long
	 */
	public long getSize()
	{
		return size;
	}
	
	/** Sets the flags of the tag/header.
	 *  Due to synchronisation issues this method calls convertToByte()
	 *	@param boolean unsync
	 *	@param boolean exthead
	 *	@param boolean exp
	 *	@param boolean footer
	 *	@see #convertToByte()
	 */
	public void setFlags(boolean unsync, boolean exthead, boolean exp, boolean footer)
	{
		flags[0] = unsync;
		flags[1] = exthead;
		flags[2] = exp;
		flags[3] = footer;
		
		convertToByte();
	}
	
	/** Returns the flags as a boolean[]
	 *	@return Flags as a boolean[]
	 */
	public boolean[] getFlags()
	{
		return flags;
	}
	
	/** Converts the whole header to a byte[], so we can write to a file or elsewhere.
	 *	@return Header as a byte[].
	 */
	public byte[] toByteArray()
	{
		byte[] result = new byte[10];
		
		result[0] = 'I';
		result[1] = 'D';
		result[2] = '3';
		result[3] = major;
		result[4] = minor;
		result[5] = flagByte;
		
		byte[] sizeBytes = SynchsafeInteger.encode(size, 4);
		for(int i=6; i<10; i++)
		{
			result[i] = sizeBytes[i-6];
		}
		
		return result;
	}
	
	/** Converts the four flag booleans to the flagByte.
	 *  Through this, we try to avoid synchronisation errors.
	 *	@see #setFlags(boolean unsync, boolean exthead, boolean exp, boolean footer)
	 */
	private void convertToByte()
	{	
		byte result = 0;
		if(flags[0]) result = -128;
		for(int i=1; i<4 ; i++)
		{
			byte value = bytePow((byte)2,(byte)(7-i));
			if(flags[i]) result += value;	
		}
		flagByte = result;
	}
	
	/** Converts the flagByte to the four flag booelans.
	 *  Through this, we try to avoid synchronisation errors.
	 *	@see #setFlagByte(byte flagByte)
	 */
	private void convertToBoolean()
	{	
		flags[0] = (flagByte & 0x80) != 0;
		flags[1] = (flagByte & 0x40) != 0;
		flags[2] = (flagByte & 0x20) != 0;
		flags[3] = (flagByte & 0x10) != 0;
	}
	
	/** Synchronizes all variables to avoid snchronisation errors.
	 *	This method is called by setHeaderBytes().
	 *  @see #setHeaderBytes(byte[] headerBytes)
	 */
	private void evaluateHeaderBytes()
	{			
		major = headerBytes[3];
		minor = headerBytes[4];
		flagByte = headerBytes[5];
		
		// decode flags
		flags[0] = (flagByte & 0x80) != 0;
		flags[1] = (flagByte & 0x40) != 0;
		flags[2] = (flagByte & 0x20) != 0;
		flags[3] = (flagByte & 0x10) != 0;
		
		// calculate tag size
		byte[] sizeBytes = new byte[] { headerBytes[6], headerBytes[7], 
										headerBytes[8], headerBytes[9] };
		
		size = SynchsafeInteger.decode(sizeBytes);	
	}
	
	/** Returns this Header as a String.
	 *	@return Header as a String
	 */
	public String toString()
	{
		return	"Tag version: 2."+major+"."+minor+
				"\nTag size: "+size+
				"\nFlagByte: "+flagByte+
				"\nUnsynchronisation: "+flags[0]+
				"\nExtended Header:   "+flags[1]+
				"\nExperimental:      "+flags[2]+
				"\nFooter present:    "+flags[3];
	}
	
	/** Implements power function for bytes.
	 *	Needed, cause "^" seems to cause a casting fault
	 */
	private byte bytePow(byte base, byte exp)
	{			
		byte b = base;
		for(byte i=1; i<exp; i++)
		{
			base *= b;	
		}
		return base;
	}
}
