package io;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import javax.activity.InvalidActivityException;

import net.PageLoadException;
import filter.Filter;
import gui.Stats;

public class ImageLoaderAid extends ImageLoader {
private Logger logger = Logger.getLogger(ImageLoaderAid.class.getName());

private FileWriter fileWriter;
private Filter filter;

private final int TIME_GRAPH_FACTOR = 1; // factor used for scaling DataGraph output

	public ImageLoaderAid(FileWriter fileWriter, Filter filter, File workingDir, int imageQueueWorkers) {
		super(workingDir, imageQueueWorkers);
		this.fileWriter = fileWriter;
		this.filter = filter;
	}

	@Override
	protected void beforeImageAdd(URL url, String fileName) {
		if(filter.isCached(url)){	// has the file been downloaded recently?
			filter.cache(url);		// if it has, update cache timestamp
			return;
		}
	}
	
	@Override
	protected void afterImageAdd(URL url, String fileName) {
		updateFileQueueState();
	}
	
	private void updateFileQueueState(){
		Stats.setFileQueueState("FileQueue: "+downloadList.size()+" - "+"? / "+imageQueueWorkers);
		// queue size  - active workers / pool size
	}
	
	@Override
	protected void afterClearQueue() {
		updateFileQueueState();
	}
	
	@Override
	protected void afterProcessItem(DownloadItem ii) {
		updateFileQueueState();
	}
	
	@Override
	protected void afterFileDownload(byte[] data, File fullpath, URL url) {
		if(data != null){
			try {
				fileWriter.add(fullpath, data.clone());
			} catch (InvalidActivityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			filter.cache(url);;	//add URL to cache
			Stats.addTimeGraphValue((int)((data.length/1024)*TIME_GRAPH_FACTOR)); // add data to the download graph
		}
	}
	
	@Override
	protected void onPageLoadException(PageLoadException ple) {
		int responseCode = Integer.parseInt(ple.getMessage());

		// the file was unavailable
		if(responseCode == 404 || responseCode == 500){
			logger.warning("got a 404 or 500 response for " + ple.getUrl()); // to prevent future attempts to load the file
			try {filter.cache(new URL(ple.getUrl()));
			} catch (MalformedURLException e) {
				logger.warning("could not add URL to cache " + ple.getMessage());
			} 
		}

		if(responseCode == 503){
			logger.severe("IP was banned for too many connections"); // :_(  thats what you get if you use too short intervals
			System.exit(3);
		}else{
			logger.info("GetBinary(size) http code "+ple.getMessage());
		}
	}
}
