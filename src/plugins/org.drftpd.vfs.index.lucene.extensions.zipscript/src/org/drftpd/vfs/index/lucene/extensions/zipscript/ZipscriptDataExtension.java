/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.vfs.index.lucene.extensions.zipscript;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.vfs.CaseInsensitiveTreeMap;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author scitz0
 * @version $Id$
 */
public class ZipscriptDataExtension implements IndexDataExtensionInterface {

	private static final NumericField FIELD_PRESENT = new NumericField("present", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_MISSING = new NumericField("missing", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_PERCENT = new NumericField("percent", Field.Store.YES, Boolean.TRUE);
	
	@Override
	public void initializeFields(Document doc) {
		doc.add(FIELD_PRESENT);
		doc.add(FIELD_MISSING);
		doc.add(FIELD_PERCENT);
	}

	@Override
	public void addData(Document doc, ImmutableInodeHandle inode) {
		SFVInfo sfvInfo = null;
		SFVStatus sfvStatus = null;
		DizInfo dizInfo = null;
		DizStatus dizStatus = null;
		if (inode.isDirectory()) {
			try {
				sfvInfo = inode.getPluginMetaData(SFVInfo.SFVINFO);
				sfvStatus = getSFVStatus(sfvInfo, new DirectoryHandle(inode.getPath()));
			} catch (KeyNotFoundException e) {
				// Fields will be cleared below
			} catch (FileNotFoundException e) {
				// Fields will be cleared below
			} catch (IOException e) {
				// Fields will be cleared below
			}
			try {
				dizInfo = inode.getPluginMetaData(DizInfo.DIZINFO);
				dizStatus = getDizStatus(dizInfo, new DirectoryHandle(inode.getPath()));
			} catch (KeyNotFoundException e) {
				// Fields will be cleared below
			} catch (FileNotFoundException e) {
				// Fields will be cleared below
			} catch (IOException e) {
				// Fields will be cleared below
			}
		}
		if (sfvStatus == null && dizStatus == null) {
			FIELD_PRESENT.setIntValue(-1);
			FIELD_MISSING.setIntValue(-1);
			FIELD_PERCENT.setIntValue(-1);
		} else if (sfvStatus != null) {
			FIELD_PRESENT.setIntValue(sfvStatus.getPresent());
			FIELD_MISSING.setIntValue(sfvStatus.getMissing());
			FIELD_PERCENT.setIntValue((sfvStatus.getPresent() * 100) / sfvInfo.getSize());
		} else {
			FIELD_PRESENT.setIntValue(dizStatus.getPresent());
			FIELD_MISSING.setIntValue(dizStatus.getMissing());
			FIELD_PERCENT.setIntValue((dizStatus.getPresent() * 100) / dizInfo.getTotal());
		}
	}

	private SFVStatus getSFVStatus(SFVInfo sfvInfo, DirectoryHandle dir)
	throws IOException, FileNotFoundException {
		int offline = 0;
		int present = 0;
		CaseInsensitiveTreeMap<String, Long> sfvEntries = sfvInfo.getEntries();
		for (FileHandle file : dir.getFilesUnchecked()) {
			if (file.isFile() && sfvEntries.containsKey(file.getName())) {
				if (!file.isUploading()) {
					present++;
				}
				if (!file.isAvailable()) {
					offline++;
				}
			}
		}
		return new SFVStatus(sfvEntries.size(), offline, present);
	}

	private DizStatus getDizStatus(DizInfo dizInfo, DirectoryHandle dir)
	throws IOException, FileNotFoundException {
		int offline = 0;
		int present = 0;
		for (FileHandle file : dir.getFilesUnchecked()) {
			if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
				if (!file.isUploading()) {
					present++;
				}
				if (!file.isAvailable()) {
					offline++;
				}
			}
		}
		return new DizStatus(dizInfo.getTotal(), offline, present);
	}
}
