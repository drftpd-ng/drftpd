package de.hampelratte.id3;

import java.io.*;

/** Main class of this framework. You should start with this class. It provides all methods to
 *  handle id3 tags like reading, editing or removing tags...
 *
 * @author   <a href=mailto:henni@hampelratte.de>Henrik Niehaus</a>
 */

public class MP3File extends RandomAccessFile
{
	final static long TAGLENGTH = 128;
	private RandomAccessFile raf;
	public boolean hasID3v1Tag = false;
	public boolean hasID3v2Tag = false;
	private long v2Tagstart = -1;

	/** 
	 *	@throws FileNotFoundException
	 *	@throws IOException
	 *	@see java.io.RandomAccessFile
	 */
	public MP3File(String file, String mode) throws FileNotFoundException, IOException
	{ 
		super(file, mode);
		hasID3v1Tag = existsID3v1Tag();
		FileParser fp = new FileParser(this);
		v2Tagstart = fp.searchTag();
		if(v2Tagstart != -1) hasID3v2Tag = true;
	}

	/** 
	 *	@throws FileNotFoundException
	 *	@throws IOException
	 *	@see java.io.RandomAccessFile
	 */
	public MP3File(File file, String mode) throws FileNotFoundException, IOException
	{
		super(file, mode);
		hasID3v1Tag = existsID3v1Tag();
		FileParser fp = new FileParser(this);
		v2Tagstart = fp.searchTag();
		if(v2Tagstart != -1) hasID3v2Tag = true;
	}

	/**	Determines, if an id3v1 tag exists. 
	 *	@return true if an id3v1 tag exists.
	 */
	public boolean existsID3v1Tag()
	{
		boolean hasTag=false;
		try
		{
			seek(length()-TAGLENGTH);
			byte[] tag = new byte[128];
			read(tag);
			String tagString = new String(tag);
			if(tagString.substring(0,3).equals("TAG")) hasTag = true;
		}
		catch(IOException e) { System.out.println(e); }
		return hasTag;
	}

	/** Reads an ID3v1Tag
	 *	@return The ID3v1Tag read from file or an empty ID3v1Tag if no tag was found.
	 */
	public ID3v1Tag readID3v1Tag()
	{
		ID3v1Tag id3tag = new ID3v1Tag();
		if(hasID3v1Tag)
		{
			try
			{
				seek(length()-TAGLENGTH);
				byte[] tag = new byte[128];
				read(tag);
				String tagString = new String(tag);
	
				String title = tagString.substring(3,33);
				String artist = tagString.substring(33,63);
				String album = tagString.substring(63,93);
				String year = tagString.substring(93,97);
				String comment = tagString.substring(97,125);
				byte track = tag[126];
				byte genre = tag[127];
				
				id3tag.setTitle(title);
				id3tag.setArtist(artist);
				id3tag.setAlbum(album);
				id3tag.setYear(year);
				id3tag.setComment(comment);
				id3tag.setTrack(track);
				id3tag.setGenre(genre);
			}
			catch(IOException e) {System.out.println(e); }
		}
		
		return id3tag;
	}
	
	/**	Writes the given ID3v1Tag to the file.
	 *	@throws IOException
	 */
	public void writeID3v1Tag(ID3v1Tag tag) throws IOException
	{
		if(hasID3v1Tag)
		{
			seek(length()-TAGLENGTH);
			write(tag.toByteArray());
		}
		else
		{
			setLength(this.length()+128);
			seek(length()-TAGLENGTH);
			write(tag.toByteArray());
		}
	}
	
	/** Removes an ID3v1Tag 
	 *	@throws IOException
	 */
	public void removeID3v1Tag() throws IOException
	{
		if(hasID3v1Tag)
		{
			setLength(this.length()-128);
			hasID3v1Tag=false;
		}
	}
	
	/** Reads an ID3v2Tag
	 *	@throws IOException
	 *	@return ID3v2Tag read from file or an empty tag if no tag was found
	 */
	public ID3v2Tag readID3v2Tag() throws IOException
	{
		Header header=null;
		try
		{
			header = readHeader();
		}
		catch(IllegalHeaderException e) { System.out.println(e); }
		ID3v2Tag tag = new ID3v2Tag(this);
		tag.setHeader(header);
		return tag;
	}
	
	/** Writes the given ID3v2Tag to file
	 *	@throws IOException
	 */
	public void writeID3v2Tag(ID3v2Tag tag) throws IOException
	{
		try
		{
			byte file[] = null;
			if(hasID3v2Tag)
			{
				Header header = readHeader();
				long size = header.getSize();
				
				seek(size+10);
				file = new byte[(int)(this.length()-(size+10))];
			}
			else
			{
				file = new byte[(int)this.length()];
				seek(0);
			}

			read(file);
			byte[] tagBytes = tag.toByteArray();
			this.setLength(file.length + tagBytes.length);
			seek(0);
			write(tagBytes);
			write(file);
		}
		catch (Exception e) { System.out.println("oben Couldn't write tag:\n\n"+e); }
	}
	
	/**	Removes a ID3v2Tag from file */
	public void removeID3v2Tag()
	{
		if(hasID3v2Tag)
		{
			try
			{
				Header header = readHeader();
				long size = header.getSize();
				
				seek(size);
				byte[] file = new byte[(int)(this.length()-size)];
				read(file);
				this.setLength(this.length()-size);
				seek(0);
				write(file);
				hasID3v2Tag=false;
				v2Tagstart=-1L;
			}
			catch (Exception e) { System.out.println("Couldn't remove tag:\n\n"+e); }
		}
	}
	
	/** Reads the tag header from file.
	 *	@return Header as Header ;-)
	 *	@throws IOException
	 *	@throws IllegalHeaderException
	 */
	public Header readHeader() throws IOException, IllegalHeaderException
	{
		Header header = null;

		byte[] headerBytes = new byte[10];
		if(v2Tagstart >= 0)
		{
			seek(v2Tagstart);
			read(headerBytes);
			header = new Header();
			header.setHeaderBytes(headerBytes);
		}

		return header;
	}
	
	/* no good idea, cause you can specify wrong size and so on...
	public void writeHeader(Header header) throws IOException, IllegalHeaderException
	{
		byte[] headerBytes = header.toByteArray();
		if(headerBytes.length == 10)
		{
			seek(v2Tagstart);
			write(headerBytes);
		}
		else throw new IllegalHeaderException("Wrong header size!");
	}
	*/
}
