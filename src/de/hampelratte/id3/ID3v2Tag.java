package de.hampelratte.id3;

import java.io.*;

/** This class represents a mp3 tag (id3v2.3.0)
 *
 *	@author <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */
public class ID3v2Tag
{
	private String[] frameIDs =
	{ 	
		"AENC", //  0
		"APIC", //  1 
		"ASPI", //  2
		"COMM", //  3
		"COMR", //  4
		"ENCR", //  5
		"EQU2", //  6
		"ETCO", //  7
		"GEOB", //  8
		"GRID", //  9
		"LINK", // 10 
		"MCDI", // 11 
		"MLLT", // 12 
		"OWNE", // 13 
		"PRIV", // 14 
		"PCNT", // 15 
		"POPM", // 16 
		"POSS", // 17 
		"RBUF", // 18 
		"RVA2", // 19 
		"RVRB", // 20 
		"SEEK", // 21 
		"SIGN", // 22 
		"SYLT", // 23 
		"SYTC", // 24 
		"TALB", // 25 
		"TBPM", // 26 
		"TCOM", // 27 
		"TCON", // 28 
		"TCOP", // 29 
		"TDEN", // 30 
		"TDLY", // 31 
		"TDOR", // 32 
		"TDRC", // 33 
		"TDRL", // 34 
		"TDTG", // 35 
		"TENC", // 36 
		"TEXT", // 37 
		"TFLT", // 38 
		"TIPL", // 39 
		"TIT1", // 40 
		"TIT2", // 41 
		"TIT3", // 42 
		"TKEY", // 43 
		"TLAN", // 44 
		"TLEN", // 45 
		"TMCL", // 46 
		"TMED", // 47 
		"TMOO", // 48 
		"TOAL", // 48 
		"TOFN", // 50 
		"TOLY", // 51 
		"TOPE", // 52 
		"TOWN", // 53 
		"TPE1", // 54 
		"TPE2", // 55 
		"TPE3", // 56 
		"TPE4", // 57 
		"TPOS", // 58 
		"TPRO", // 59 
		"TPUB", // 60 
		"TRCK", // 61 
		"TRSN", // 62 
		"TRSO", // 63 
		"TSOA", // 64 
		"TSOP", // 65 
		"TSOT", // 66 
		"TSRC", // 67 
		"TSSE", // 68 
		"UFID", // 69 
		"USER", // 70 
		"USLT", // 71 
		"WCOM", // 72 
		"WCOP", // 73 
		"WOAF", // 74 
		"WOAR", // 75 
		"WOAS", // 76 
		"WORS", // 77 
		"WPAY", // 78 
		"WPUB", // 79
		"WXXX", // 80
		"TYER"  // 81
	};
	private ID3v2Frame[] frames = new ID3v2Frame[frameIDs.length];
	private Header header;
	private MP3File mp3;
	private FileParser fp;
	
 	/** Constructor to create an empty tag -> insert your own data */
	public ID3v2Tag() {}
	
	
	/** Constructor to read a whole tag from a mp3 
	 *	@throws IOException
	 */
	ID3v2Tag(MP3File mp3) throws IOException
	{
		this.mp3 = mp3;
		fp = new FileParser(mp3);
		getFrames();
	}

	/** Returns the header of this tag 
	 *	@return Header as Header ;-)
	 */
	public Header getHeader()
	{
		return header;
	}
	
	/** Sets the header of this tag 
	 *
	 * With this method you can set a custom header. Be carefull: make sure you
	 * specify the rigth size, flags and so on. Make only use of this method if
	 * you know what you are doing
	 */
	public void setHeader(Header header)
	{
		this.header = header;	
	}

	
	/** Gets all frames of the tag 
	 *
	 *	Gets all frames of the tag and stores them in the field frames[] as ID3v2Frame(s)
	 *	@throws IOException
	 */
	private void getFrames() throws IOException
	{
		fp.searchTag();
		for(int i=0; i<frameIDs.length; i++)
		{
			frames[i] = getFrame(frameIDs[i]);	
		}
	}

	/** Gets one frame specified by String frameId from a mp3 file. 
	 *
	 *	This method is used by getFrames(). It gets one frame specified by the String
	 *	frameId from the mp3 file.	
	 *	@throws IOException
	 */
	private ID3v2Frame getFrame(String frameId) throws IOException
	{
		long pos = fp.searchFrame(frameId);
		if (pos != -1)
		{
			ID3v2Frame frame = new ID3v2Frame(mp3, pos, frameId);
			return frame;
		}
		else return null;
	}	
	
	
	public String getTitle()  
    {
        if(frames[41] == null) return "";
        else return new String(frames[41].getContent()); 
    }
	public void setTitle(String title) 
	{ 
		if(frames[41] == null) initFrame(41);
		frames[41].setContent(title.getBytes()); 
	}
	
	public String getArtist() 
    {
        if(frames[54] == null) return "";
        else return new String(frames[54].getContent()); 
    }
	public void setArtist(String artist) 
	{ 
		if(frames[54] == null) initFrame(54);
		frames[54].setContent(artist.getBytes()); 
	}
	
	public String getAlbum() 
    {
        if(frames[25] == null) return "";
        else return new String(frames[25].getContent()); 
    }
	public void setAlbum(String album) 
	{
		if(frames[25] == null) initFrame(25);
		frames[25].setContent(album.getBytes()); 
	}
	
	public String getYear() 
    {
        if(frames[81] == null) return "";
        else return new String(frames[81].getContent()); 
    }
	public void setYear(String year) 
	{ 
		if(frames[81] == null) initFrame(81);
		frames[81].setContent(year.getBytes()); 
	}
	
	public String getGenre() 
    {
        if(frames[28] == null) return "";
        else return new String(frames[28].getContent()); 
    }
	public void setGenre(String genre) 
	{ 
		if(frames[28] == null) initFrame(28);
		frames[28].setContent(genre.getBytes()); 
	}
	
	public String getTrack() 
    {
        if(frames[61] == null) return "";
        else return new String(frames[61].getContent()); 
    }
	//public int getTrack { return Integer.parseInt(frames[61]); }
	public void setTrack(String track) 
	{ 
		if(frames[61] == null) initFrame(61);
		frames[61].setContent(track.getBytes()); 
	}
	public void setTrack(int track) 
	{ 
		if(frames[61] == null) initFrame(61);
		frames[61].setContent((""+track).getBytes()); 
	}
	
	public String getComment() 
	{ 
        if(frames[3] == null) return "";
        else
        {
            String content = new String(frames[3].getContent());
            return content.substring(4, content.length());
        }
	}
	public void setComment(String comment) 
	{
		try
		{
			if(frames[3] == null) initFrame(3);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write("eng".getBytes());
			bos.write((byte)0);
			bos.write(comment.getBytes());
			byte[] content = bos.toByteArray();
			frames[3].setContent(content);
		}
		catch(Exception e) { System.out.println("Couldn't set comment:\n\n"+e); }
	}
	
	public String getComposer() 
    {
        if(frames[27] == null) return "";
        else return new String(frames[27].getContent()); 
    }
	public void setComposer(String composer) 
	{ 
		if(frames[27] == null) initFrame(27);
		frames[27].setContent(composer.getBytes()); 
	}
	
	public String getOrigArtist()  
    {
        if(frames[52] == null) return "";
        else return new String(frames[52].getContent()); 
    }
	public void setOrigArtist(String artist) 
	{ 
		if(frames[52] == null) initFrame(52);
		frames[52].setContent(artist.getBytes()); 
	}
	
	public String getCopyright() 
    {
        if(frames[29] == null) return "";
        else return new String(frames[29].getContent()); 
    }
	public void setCopyright(String copyright) 
	{ 
		if(frames[29] == null) initFrame(29);
		frames[29].setContent(copyright.getBytes()); 
	}
	
	public String getURL()  
    {
        if(frames[80] == null) return "";
        else return new String(frames[80].getContent()); 
    }
	public void setURL(String url) 
	{ 	
		try
		{
			if(frames[80] == null) initFrame(80);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write((byte)0);
			bos.write(url.getBytes());
			byte[] content = bos.toByteArray();
			frames[80].setContent(content);
		}
		catch(Exception e) { System.out.println("Couldn't set URL:\n\n"+e); }
	}
	
	public String getEncodedBy() 
    {
        if(frames[36] == null) return "";
        else return new String(frames[36].getContent()); 
    }
	public void setEncodedBy(String enc) 
	{ 
		if(frames[36] == null) initFrame(36);
		frames[36].setContent(enc.getBytes()); 
	}
	
	public String getAudioEncryption() 
    {
        if(frames[0] == null) return "";
        else return new String(frames[0].getContent()); 
    }
	public void setAudioEncryption(String enc) 
	{ 
		if(frames[0] == null) initFrame(0);
		frames[0].setContent(enc.getBytes()); 
	}
	
	
	/** Prints the content of all frames to stdout */ 
	public void printIt()
	{
		for(int i=0; i<frames.length; i++)
		{
			if(frames[i] != null)
			{
				System.out.println(frameIDs[i]+"="+new String(frames[i].getContent()));
			}
		}
	}
	
	
	private void initFrame(int number)
	{
		frames[number] = new ID3v2Frame(frameIDs[number]);
		frames[number].setEncoding((byte)0);
		frames[number].setFlags(false,false,false,false,false,false,false,false);
	}
	
	
	/** Converts the whole tag to a byte array, so we can write it to a file or elsewhere...
	 *	@return ID3v2Tag as a byte[]
	 */
	public byte[] toByteArray()
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		for(int i=0; i<frameIDs.length; i++)
		{
			if(frames[i] != null)
			{
				try
				{
					bos.write(frames[i].toByteArray());
				}
				catch (IOException e) { System.out.println("Couldn't create ByteArray :-(\n\n"+e); }
			}
		}
		byte[] data = bos.toByteArray();

		byte[] result = null;
		try
		{
			ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
			if(header==null)
			{
				header = new Header();
				header.setFlags(false,false,false,false);
				header.setMajorVersion((byte)3);
				header.setMinorVersion((byte)0);
			}
			header.setSize(data.length);
			bos2.write(header.toByteArray());
			bos2.write(data);
			result = bos2.toByteArray();
		}
		catch(Exception e) { System.out.println("Couldn't create ByteArray :-(\n\n"+e); }
		
		return result;	
	}
}
