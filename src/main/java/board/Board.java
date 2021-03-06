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
package board;

import io.ImageLoader;

import java.io.IOException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dozedoff.commonj.file.FileUtil;

import filter.Filter;
import filter.FilterItem;
import filter.FilterState;

/**
 * Represents a whole board.
 */
public class Board {
	private Timer pageAdder;
	private String boardId;
	private String lastRun ="";
	private boolean stoppped = true;
	final private URL boardUrl;
	final private SiteStrategy siteStartegy;
	final private Filter filter;
	final private ImageLoader imageLoader;
	private final int WAIT_TIME = 60 * 1000 * 60; // 1 hour
	
	private static final Logger logger = LoggerFactory.getLogger(Board.class);
	
	public Board(URL boardUrl, String boardId, SiteStrategy siteStrategy, Filter filter, ImageLoader imageLoader){
		this.boardUrl = boardUrl;
		this.boardId = boardId;
		this.siteStartegy = siteStrategy;
		this.filter = filter;
		this.imageLoader = imageLoader;
	}

	public void stop(){
		this.stoppped = true;
		if(pageAdder != null){
			pageAdder.cancel();
		}
		
		lastRun = ""; // looks a bit odd otherwise
		
		logger.info("Board {} is stopping...", boardId);
	}

	@Override
	public String toString() {
		return "/"+boardId+"/";
	}

	public String getStatus(){
		String status = "/"+boardId+"/";
		status += " "+lastRun+" ";

		if(stoppped){
			status += "idle";
		}else{
			status += "running";
		}
		return status;
	}

	public void start(){
		start(0);
	}

	public void start(int delay){
		logger.info("Starting board {} with a delay of {}", boardId, delay);
		pageAdder = new Timer("Board "+boardId+" worker", true);

		this.stoppped = false;
		pageAdder.schedule(new BoardWorker(delay), delay*60*1000, WAIT_TIME);
	}

	class BoardWorker extends TimerTask{
		public BoardWorker(int delay){
			setTime(delay);
		}

		@Override
		public void run() {
			//TODO add muli-queueing protection

			setTime(0);
			processBoard();
		}

		/**
		 * Set the time displayed in the GUI.
		 * @param delay delay in minutes.
		 */
		private void setTime(int delay){
			Calendar cal = new GregorianCalendar();
			cal.add(Calendar.MINUTE, delay);
			DateFormat df;
			df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
			lastRun = df.format(cal.getTime());
		}
		
		private void processBoard() {
			Document boardPage = loadPage(boardUrl);
			int numOfPages = siteStartegy.getBoardPageCount(boardPage);
			logger.info("Found {} pages on Board {}", numOfPages, boardId);
			ArrayList<URL> pageUrls = PageUrlFactory.makePages(boardUrl, numOfPages);
			List<URL> pageThreads = parsePages(pageUrls);
			logger.info("Parsing board {} pages resulted in {} thread links", boardId, pageThreads.size());
			
			filterPageThreads(pageThreads);
			logger.info("{} {} threads left after filtering", pageThreads.size(), boardId);
			processPageThreads(pageThreads);
		}
		
		private List<URL> parsePages(List<URL> pageUrls){
			logger.debug("Parsing pages for {}", boardId);
			LinkedList<URL> pageThreads = new LinkedList<>();
			
			for(URL page : pageUrls){
				if(stoppped){
					break;
				}
				Document pageDoc = loadPage(page);
				List<URL> threads = siteStartegy.parsePage(pageDoc);
				pageThreads.addAll(threads);
			}
			
			return pageThreads;
		}
		
		private void filterPageThreads(List<URL> pageThreads) {
			logger.debug("Filtering pages for {}", boardId);
			Iterator<URL> iterator = pageThreads.iterator();
			
			while(iterator.hasNext()){
				URL currentPageThread = iterator.next();
				
				if(isBlockedByFilter(currentPageThread)){
					iterator.remove();
				}
			}
		}
		
		private boolean isBlockedByFilter(URL currentPageThread){
			FilterState state = filter.getFilterState(currentPageThread);
			
			if(state == FilterState.DENY || state == FilterState.PENDING) {
				logger.info("{} is blocked by the filter", currentPageThread);
				return true;
			}else{
				return false;
			}
		}
		
		private void processPageThreads(List<URL> pageThreads) {
			for (URL thread : pageThreads) {
				if(stoppped){
					break;
				}
				Document threadPage = loadPage(thread);
				List<Post> posts = siteStartegy.parseThread(threadPage);
				String reason = filterPosts(posts);
				
				if (reason != null){
					logger.info("Suspending thread {} for {}", thread, reason);
					suspendThread(thread, reason);
					filter.downloadThumbs(thread.toString(), posts);
					continue;
				}
				
				filterImages(posts);
				logger.info("Queuing {} posts for download from thread {}", posts.size(), thread);
				queueForDownload(posts, siteStartegy.getThreadNumber(thread));
			}
		}
		
		private String filterPosts(List<Post> posts) {
			String reason = null;
			for (Post post : posts) {
				reason = filter.checkPost(post);
				
				if (reason != null){
					break;
				}
			}
			
			return reason;
		}
		
		private void suspendThread(URL threadUrl, String reason) {
			FilterItem filterItem = new FilterItem(threadUrl, boardId, reason, FilterState.PENDING);
			filter.reviewThread(filterItem);
		}
		
		private void filterImages(List<Post> posts) {
			Iterator<Post> iterator = posts.iterator();
			
			while(iterator.hasNext()){
				Post currentPost = iterator.next();
				
				if(currentPost.hasImage()){
					URL imageUrl = currentPost.getImageUrl();
					
					if(filter.isCached(imageUrl)) {
						iterator.remove();
					}
				}else{
					iterator.remove();
				}
			}
		}
		
		private void queueForDownload(List<Post> posts, int threadNumber) {
			for (Post post : posts) {
				String threadId = String.valueOf(threadNumber);
				String imageName = post.getImageName();
				
				try {
					if (!FileUtil.hasValidWindowsFilename(imageName)) {
						imageName = FileUtil.sanitizeFilenameForWindows(imageName);
					}

					String relativeImagePath = Paths.get(boardId, threadId,	imageName).toString();
					imageLoader.add(post.getImageUrl(), relativeImagePath);
				} catch (InvalidPathException ipe) {
					Object[] data = { post.getImageUrl(), post.getImageName(), ipe.getReason() };
					logger.warn("Failed to add image ({}) for download to {} - reason: {}",	data);
				}
			}
		}
		
		private Document loadPage(URL url) {
			try {
				return Jsoup.connect(url.toString()).userAgent("Mozilla").get();
			} catch (IOException e) {
				logger.warn("Failed to load page {} with error {}", url, e);
				return Jsoup.parse("");
			}
		}
	}
}
