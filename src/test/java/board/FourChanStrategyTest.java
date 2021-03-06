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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.matchers.JUnitMatchers.hasItems;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Server;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.dozedoff.commonj.file.BinaryFileReader;
import com.github.dozedoff.commonj.io.TextFileReader;

public class FourChanStrategyTest {
	SiteStrategy strategy;
	static Server server;
	static Document mainBoard, boardPage, threadPage, invalidPage, pagesFragment;
	static URL threadUrl, invalidUrl;
	
	@BeforeClass
	static public void before() throws Exception{
		InputStream is = ClassLoader.getSystemResourceAsStream("HtmlData/mainPage.html");
		mainBoard = Jsoup.parse(is, null, "http://boards.4chan.org/");
		
		is = ClassLoader.getSystemResourceAsStream("HtmlData/pageTestData");
		boardPage = Jsoup.parse(is, null, "http://boards.4chan.org/htmlnew");
		
		is = ClassLoader.getSystemResourceAsStream("HtmlData/threadData.html");
		threadPage = Jsoup.parse(is, null, "http://boards.4chan.org/p/res/57867301");
		threadUrl = new URL("http://boards.4chan.org/p/res/57867301");
		
		invalidPage = Jsoup.parse("");
		invalidUrl = new URL("http://foobar");
		
		is = ClassLoader.getSystemResourceAsStream("HtmlData/numberOfPagesFragmentData");
		TextFileReader tfr = new TextFileReader();
		String htmlFragment = tfr.read(is);
		pagesFragment = Jsoup.parseBodyFragment(htmlFragment);
	}

	@Before
	public void setUp() throws Exception {
		strategy = new FourChanStrategy();
	}

	@Test
	public void testValidSiteStrategy() throws MalformedURLException {
		boolean isValid = strategy.validSiteStrategy(new URL("http://www.4chan.org"));
		assertTrue(isValid);
	}

	@Test
	public void testFindBoards() throws Exception {
		Map<String, URL> foundBoards;

		foundBoards = strategy.findBoards(mainBoard);
		
		assertThat(foundBoards.get("Photography"), is(new URL("http://boards.4chan.org/p/")));
		assertThat(foundBoards.get("Music"), is(new URL("http://boards.4chan.org/mu/")));
		assertThat(foundBoards.get("Fashion"), is(new URL("http://boards.4chan.org/fa/")));
		assertThat(foundBoards.get("Sports"), is(new URL("http://boards.4chan.org/sp/")));
		
		assertThat(foundBoards.size(), is(59));
	}
	
	@Test
	public void testFindBoardsInvalid() {
		Map<String, URL> foundBoards;
		foundBoards = strategy.findBoards(invalidPage);
		assertThat(foundBoards.size(),is(0));
	}

	@Test
	public void testGetBoardPageCount() {
		assertThat(strategy.getBoardPageCount(pagesFragment), is(10));
	}
	
	@Test
	public void testGetBoardPageCountInvalid() {
		assertThat(strategy.getBoardPageCount(invalidPage), is(0));
	}
	
	@Test
	public void testParsePage() throws Exception {
		List<URL> pageUrls = strategy.parsePage(boardPage);
		LinkedList<URL> correctUrls = new LinkedList<>();
		
		int replyNr[] = {1418, 7897, 7910, 1461, 1454, 1456, 1450, 1449, 1448, 1447};
		
		for(int i : replyNr){
			correctUrls.add(new URL("http://localhost/newhtml/res/" + String.valueOf(i)));
		}
		
		URL correctArray[] = new URL[0];
		
		correctUrls.toArray(correctArray);
		assertThat(pageUrls, hasItems(correctArray));
	}
	
	@Test
	public void testParsePageInvalid() {
		List<URL> pageUrls = strategy.parsePage(invalidPage);
		assertThat(pageUrls.size(), is(0));
	}

	@Test
	public void testParseThread() {
		List<Post> posts = strategy.parseThread(threadPage);
		int images = 0, comments = 0;
		
		assertThat(posts.size(), is(13));
		
		for(Post p : posts){
			if(p.hasComment()){
				comments++;
			}
			
			if(p.hasImage()){
				images++;
			}
		}
		
		assertThat(images, is(8));
		assertThat(comments, is(13));
	
	}
	
	@Test
	public void testParseThreadInvalid() {
		List<Post> posts = strategy.parseThread(invalidPage);
		assertThat(posts.size(), is(0));
	}
	
	@Test
	public void testGetThreadNumber() {
		int threadNumber = strategy.getThreadNumber(threadUrl);
		assertThat(threadNumber, is(57867301));
	}
	
	@Test
	public void testGetThreadNumberInvalid() {
		int threadNumber = strategy.getThreadNumber(invalidUrl);
		assertThat(threadNumber, is(0));
	}
	
	@Test
	public void testGetBoardShortcutsP() throws MalformedURLException {
		String threadLetters = strategy.getBoardShortcut(new URL("http://boards.4chan.org/p/"));
		assertThat(threadLetters, is("p"));
	}
	
	@Test
	public void testGetBoardShortcutsWg() throws MalformedURLException {
		String threadLetters = strategy.getBoardShortcut(new URL("http://boards.4chan.org/wg/"));
		assertThat(threadLetters, is("wg"));
	}
	
	@Test
	public void testGetBoardShortcutsInvalid() {
		String threadLetters = strategy.getBoardShortcut(invalidUrl);
		assertThat(threadLetters, is(""));
	}
}
