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

import java.net.URL;
import java.text.DateFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import thread.WorkQueue;

/**
 * Represents a whole board.
 */
public class Board {
	private AbstractList<Page> pages;
	private WorkQueue pageQueue;
	private Timer pageAdder;
	private String boardId;
	private String lastRun ="";
	private boolean stoppped = true;
	final private URL boardUrl;
	final private SiteStrategy siteStartegy;
	private final int WAIT_TIME = 60 * 1000 * 60; // 1 hour
	
	private static final Logger LOGGER = Logger.getLogger(Board.class.getName());
	
	public Board(URL boardUrl, String boardId, SiteStrategy siteStrategy){
		this.boardUrl = boardUrl;
		this.boardId = boardId;
		this.siteStartegy = siteStrategy;
	}

	public void stop(){
		this.stoppped = true;
		if(pageAdder != null)
			pageAdder.cancel();
		
		lastRun = ""; // looks a bit odd otherwise
		
		LOGGER.info("Board "+boardId+" is stopping...");
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
			int numOfPages = siteStartegy.getBoardPageCount(boardUrl);
			ArrayList<URL> pageUrls = PageUrlFactory.makePages(boardUrl, numOfPages);
			List<PageThread> pageThreads = parsePages(pageUrls);
			
			//TODO finish me
			
			// compare pageThreads against filter
			// parse pageThreads -> posts
			// compare posts against word filter
			// get image URLs
			// compare URLs against cache
			// add URLs for download
		}
		
		private List<PageThread> parsePages(List<URL> pageUrls){
			LinkedList<PageThread> pageThreads = new LinkedList<>();
			
			for(URL page : pageUrls){
				List<PageThread> threads = siteStartegy.parsePage(page);
				pageThreads.addAll(threads);
			}
			
			return pageThreads;
		}
	}
}
