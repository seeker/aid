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
package io;

import java.awt.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import board.Post;

import com.github.dozedoff.commonj.net.GetBinary;

/**
 * Class for downloading and storing thumbnails.
 */
public class ThumbnailLoader {
	private static Logger logger = LoggerFactory.getLogger(ThumbnailLoader.class);
	private final int NUM_OF_THUMBS = 17;
	private AidDAO sql;
	public ThumbnailLoader(AidDAO sql){
		this.sql = sql;
	}
	/**
	 * Download thumbnails and store them in the database.
	 * @param url URL of the thread from which the thumbnails are loaded
	 * @param postList Posts from which thumbnails should be loaded.
	 */
	public void downloadThumbs(String url,List<Post> postList){
		//TODO add code to re-fetch thumbs?
		GetBinary gb = new GetBinary(2097152);  // 2 mb
		int counter = 0;
		logger.info("Fetching thumbs for {}", url);
		for(Post p : postList){

			if (! p.hasImage())
				continue;

			// Image / thumbnail addresses follow a distinct pattern
			String thumbUrl = p.getImageUrl().toString();
			thumbUrl = thumbUrl.replace("images", "thumbs");
			thumbUrl = thumbUrl.replace("src", "thumb");
			thumbUrl = thumbUrl.replace(".jpg", "s.jpg");

			try {
				byte data[] = gb.getViaHttp(thumbUrl); // get thumbnail

				int split = thumbUrl.lastIndexOf("/")+1;
				String filename = thumbUrl.substring(split); // get the filename (used for sorting)
				
				Object[] logData = {counter, thumbUrl, filename, url, data.length};
				logger.debug("Adding thumbnail({})  URL: {}, Filename: {}, Thread: {}, Size: {} to database", logData);
				sql.addThumb(url,filename, data); // add data to DB
				counter++;
			} catch (IOException e) {
				logger.warn("Could not load thumbnail {} -> {}", thumbUrl, e);		
			}
			// only the first few thumbs are needed for a preview
			if (counter > (NUM_OF_THUMBS-1)){
				break;
			}
		}
		
		logger.info("Loaded {} thumbnails for {}", counter, url);
	}

	/**
	 * Fetch thumbnail data from database.
	 * @param id URL of the page thumbs to load
	 * @return Array of Binary data
	 */
	public ArrayList<Image> getThumbs(String id){
		ArrayList<Image> images = new ArrayList<>(sql.getThumb(id));
		logger.info("Fetched {} thumbnails from the database for thread {}", images.size(), id);
		return images;
	}
}
