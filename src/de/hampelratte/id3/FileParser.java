package de.hampelratte.id3;

import java.io.*;
import de.hampelratte.id3.MP3File;

/** This class parses a mp3 file for tags and frames
 *
 *	@author <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */
public class FileParser
{
	private byte[] file;
	private MP3File mp3;
	private long tagOffset=-1L;
	private long tagsize=0L;
	
	
	/** Constructor to create a FileParser
	 *	@param MP3File mp3
	 *	@throws IOException
	 *
	 */
	public FileParser(MP3File mp3) throws IOException
	{
		this.mp3 = mp3;
		
		long length = mp3.length();
		file = new byte[(int)length];
		mp3.seek(0L);
		mp3.read(file);
	}
	
	 
	/** Searches the file for a tag.
	 *
	 *	@return tag offset as a long
	 */ 
	public long searchTag()
	{	
		long offset = -1;
		offset = parseFileForTag();
		
		if(offset != -1)
		{
			byte[] size = new byte[4];
			size[0] = file[(int)offset+6];
			size[1] = file[(int)offset+7];
			size[2] = file[(int)offset+8];
			size[3] = file[(int)offset+9];
			tagsize = SynchsafeInteger.decode(size);
		}
		if(offset == 0 | offset == file.length - tagsize | offset == file.length - tagsize - 128)
		{
			tagOffset = offset;
			return offset;
		}
		else
		{
			tagOffset = -1L;
			return -1L;
		}
	}
	
	/** Parses the file for a tag.
	 *
	 *	This method is used by searchTag()
	 *
	 *	@return tag offset as long
	 *	@see #searchTag()
	 */
	private long parseFileForTag()
	{
		/*
		 *	An ID3v2 tag can be detected with the following hex pattern:
		 *  49 44 33 YY YY XX ZZ ZZ ZZ ZZ
		 *  Where YY is less than 0xFF, XX is the 'flags' byte and ZZ is less than 0x80.
		 *  Source: www.id3.org
		*/
		
		for(int i=0; i<file.length; i++)
		{
			if(	file[i]==0x49 )
			{
				if (file[i+1]==0x44)
				{
					if(file[i+2]==0x33)
					{
						if( file[i+3]<0xFF && file[i+4]<0xFF && file[i+6]<0x80 &&
							file[i+7]<0x80 && file[i+8]<0x80 && file[i+9]<0x80)
						return i;
					}
				}
			}
		}
		return -1;
	}
	
	/** Searches the file for a frame
	 *
	 *	@return frame offset as a long
	 */ 
	public long searchFrame(String ident)
	{
		if(tagOffset == -1L) return -1L;
		else
		{
			long offset = -1;
			offset = parseFileForFrame(ident);
			return offset;
		}
	}
	
	/** Parses the file for a frame.
	 *
	 *	This method is used by searchFrame()
	 *
	 *	@return frame offset as long
	 *	@see #searchFrame(String ident)
	 */
	private long parseFileForFrame(String ident)
	{
		try
		{
			byte c1 = (byte)ident.charAt(0);
			byte c2 = (byte)ident.charAt(1);
			byte c3 = (byte)ident.charAt(2);
			byte c4 = (byte)ident.charAt(3);
			
			for(int i=(int)tagOffset; i < (int)tagOffset+(int)tagsize ; i++)
			{
				if(	file[i]==c1)
				{
					if (file[i+1]==c2)
					{
						if(file[i+2]==c3)
						{
							if(file[i+3]==c4) return i;
						}
					}
				}
			}
		}
		catch(Exception e) {}
		return -1;
	}
}
