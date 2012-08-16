/*  Copyright (C) 2012  Nicholas Wright
	
	part of 'Aid', an imageboard downloader.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.tables;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.j256.ormlite.field.DatabaseField;

import file.FileInfo;
import file.FileUtil;

public abstract class FileRecord {
	@DatabaseField(id=true)
	private String id;
	@DatabaseField
	private long size;
	@DatabaseField(canBeNull = false, foreign = true, columnName="dir", foreignAutoRefresh=true, foreignAutoCreate=true)
	private DirectoryPathRecord directory = new DirectoryPathRecord();
	@DatabaseField(canBeNull = false, foreign = true, columnName="filename", foreignAutoRefresh=true, foreignAutoCreate=true)
	private FilePathRecord file = new FilePathRecord();
	@DatabaseField(canBeNull = false, foreign = true, columnName="location", foreignAutoRefresh=true, foreignAutoCreate=true)
	private LocationRecord location = new LocationRecord();
	
	public FileRecord() {}
	
	public FileRecord(FileInfo info, LocationRecord location) {
		setId(info.getHash());
		setLocation(location);
		Path relativePath = FileUtil.removeDriveLetter(info.getFilePath());
		setRelativePath(relativePath);
		setSize(info.getSize());
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public Path getRelativePath() {
		String directory = this.directory.getDirpath();
		String filename = file.getFilename();
		return Paths.get(directory, filename);
	}

	public void setRelativePath(Path relativePath) {
		String directory = relativePath.getParent().toString() + "\\";
		String filename = relativePath.getFileName().toString();
		
		this.directory.setDirpath(directory);
		this.file.setFilename(filename);
	}

	public String getLocation() {
		return location.getLocation();
	}

	public void setLocation(LocationRecord location) {
		this.location = location;
	}
}