package de.hampelratte.id3;

/** Encodes and decodes synchsafe integers
 *
 * @author   <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */
 
public class SynchsafeInteger
{
	/** Decodes a synchsafe integer
	 *	@return Decoded integer as long
	 */
	public static long decode (byte[] b) 
	{
		long result = 0;
		// to clarify, a temp var
		long temp = 0;
		// to clarify, a position marker. This denotes the significance of the
		// currently processed byte.
		long pos = 0;
		for (long i = (b.length-1); i >= 0; i--)
		{
			temp = (long)b[(int)i];
			result += (temp * (pow(2,pos*7)));
			pos++;
		}
		return result;
	}
	
	/** Encodes a synchsafe integer
	 *	@return Encoded integer as byte[]
	 */
	public static byte[] encode(long value, int length)
	{
		byte[] b = new byte[length];
		int j=0;
		for (int i = length - 1;i >= 0; i--) 
		{
			b [i] = (byte)((value >> (j * 7)) & 0x7f);
			j++;
		}
		return b;
	}
	
	/** Power function */
	private static long pow (long number, long exp) 
	{
        long result=1;
        for (long i=1;i<=exp;i++) 
            result*=number;
        return result;
    }
}
