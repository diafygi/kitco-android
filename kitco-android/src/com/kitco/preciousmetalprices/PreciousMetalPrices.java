package com.kitco.preciousmetalprices;

/* KITCO PRECIOUS METAL PRICES ANDROID APPLICATION
 * version 0.2
 * 
 * This is a simple application for Android devices
 * that downloads precious metal price data and
 * charts from Kitco.com and displays them on your
 * Android device.
 * 
 * The source code for this application is released
 * under the Gnu Public Licence version 2 (GPLv2).
 * Copyright 2010. Daniel Roesler diafygi
 * @gmail.com
 * 
 * It is not an official app by Kitco Metals, Inc.
 * I wrote it because I am a fan of the website and
 * wanted to check it out easily from my Android
 * phone. I asked if I could make an app for Android
 * and Julian Dragut at Kitco.com said yes, as long
 * as I followed the guidelines at kitconet.com
 * 
 * Kitco.com is owned by Kitco Metals, Inc.
 * Table data and charts are downloaded from kitco.com
 * and are used within their acceptable use according
 * to http://www.kitconet.com/
 * 
 * This project is hosted at:
 * http://code.google.com/p/kitco-android/
 * Please post any issues there.
 */

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PreciousMetalPrices extends Activity {
	
	//Screen identifier
	//0=all metals price tables, 1=gold charts
	//2=silver charts, 3=platinum charts, 4=palladium charts
	private Integer screen = 0;
	
	//Table data
	//[0][y]=gold, [1][y]=silver, [2][y]=platinum, [3][y]=palladium
	//where:
	//[x][0]=metal name, [x][1]=time of update, [x][2]=bid, 
	//[x][3]=ask, [x][4]=change, [x][5]=change percent, [x][6]=low
	//[x][7]=high
	private String market_status = "";
	private String market_time = "";
	private String[][] data_table = new String[4][8];
	
	//Chart data
	//[0][y]=gold, [1][y]=silver, [2][y]=platinum, [3][y]=palladium
	//where:
	//[x][0]=3day, [x][1]=1day, [x][2]=30day, [x][3]=60day,
	//[x][4]=6month, [x][5]=1year, [x][6]=5year, [x][7]=10year
	private Bitmap[][] charts = new Bitmap[4][8];
	
	//Progress variables
	private Integer[] loaded = {0,0,0,0,0};
	private Integer refresh = 0;
	private Integer[] lockout = {0,0,0,0,0};
	static final boolean DEBUG = false;
	
    //Called when the activity is first created 
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        //Update data on initial creation
        if(DEBUG) Log.d("kitco","calling updateDate from onCreate");
        updateData();
        
        //Define refresh button action
        Button refresh_button = (Button)findViewById(R.id.refresh);
        refresh_button.setOnClickListener(refresh_onclick);
        
        //Add Kitco.com link to title
        TextView title_text = (TextView)findViewById(R.id.title);
        Linkify.addLinks(title_text, Pattern.compile("Kitco.com"), "http://www.");
    }
    
    //Doesn't kill activity when screen is rotated
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  setContentView(R.layout.main);
	  
      //Fill data on config change
      if(DEBUG) Log.d("kitco","calling updateDate from onConfigurationChanged");
	  updateData();
	  
	  //Define refresh button action
      Button refresh_button = (Button)findViewById(R.id.refresh);
      refresh_button.setOnClickListener(refresh_onclick);
      
      //Add Kitco.com link to title
      TextView title_text = (TextView)findViewById(R.id.title);
      Linkify.addLinks(title_text, Pattern.compile("Kitco.com"), "http://www.");
	}
	
	//Set refresh button click listener
	OnClickListener refresh_onclick = new OnClickListener() {
		public void onClick(View v) {
	    	//Set updateData() to refresh
	    	if(DEBUG) Log.d("kitco","refresh button clicked, calling up screen");
	    	refresh = 1;
	    	updateData();
		}
	};
	
	//Menu options
	public boolean onCreateOptionsMenu(Menu menu) {
	    menu.add(0,0,0,"Prices Index").setIcon(R.drawable.index);
	    menu.add(0,1,0,"Gold").setIcon(R.drawable.au_charts);
	    menu.add(0,2,0,"Silver").setIcon(R.drawable.ag_charts);
	    menu.add(0,3,0,"Platinum").setIcon(R.drawable.pt_charts);
	    menu.add(0,4,0,"Palladium").setIcon(R.drawable.pd_charts);
	    
	    return true;
	}

	//Menu call functions
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
	    	case 0:	Log.v("kitco","table menu item selected");
	    		screen=0; updateData(); return true;
	    	case 1:	Log.v("kitco","gold charts menu item selected");
	    		screen=1; updateData(); return true;
	    	case 2:	Log.v("kitco","silver charts menu item selected");
	    		screen=2; updateData(); return true;
	    	case 3:	Log.v("kitco","platinum menu item selected");
	    		screen=3; updateData(); return true;
	    	case 4:	Log.v("kitco","palladium menu item selected");
	    		screen=4; updateData(); return true;
	    }
	    return false;
	}
	
	//Receives messages from other threads
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			//Get progressbar widget
			ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
			
			//Set progressbar to percent complete
			if(loaded[screen] != 100) {
				if(DEBUG) Log.i("kitco","[Handler]: screen "+screen.toString()+" data is "+loaded[screen].toString()+"% loaded");
				pg.setVisibility(0);
				pg.setProgress(loaded[screen]);
				updateViews();
			}
			else {
				//100% loaded, make progressbar invisible
				if(DEBUG) Log.i("kitco","[Handler]: screen "+screen.toString()+" data is 100% loaded");
				pg.setVisibility(8);
				updateViews();
			}
		}
	};
	
	//Updates data from web in separate thread
	private void updateData() {
		Thread t = new Thread() {
			public void run() {
				Integer scr = screen;
				if(DEBUG) Log.d("kitco","updateData called, screen="+scr.toString()+", lockout="+lockout[scr]);
				
				//Set lockout
				if(lockout[scr] == 0) {
					lockout[scr] = 1;
					
				//Clear screen cache if refreshed
				if(refresh == 1) {
					if(DEBUG) Log.d("kitco","refresh enabled, purging cache for screen "+scr.toString());
					if (scr == 0)
						data_table = new String[4][8];
					else
						charts[scr-1] = new Bitmap[8];
				    loaded[scr] = 0;
				    refresh = 0;
				}
				
				//Update screen
				if(loaded[scr] != 100) {
					switch (scr) {
						//update view to show tables
						case 0: if(DEBUG) Log.v("kitco","all metal prices table screen selected");
							updateTables(scr); break;
					
						//default is to show charts
						default: if(DEBUG) Log.v("kitco","charts screen selected (default)");
							updateCharts(scr); break;
					}
				}
				lockout[scr] = 0;
				handler.sendMessage(Message.obtain());
				}
			}
		};
		t.start();
	}

	//Updates view widgets on screen with data
	private void updateViews() {
		TextView tv;
		TableLayout tbv;
		ImageView iv;
		
		//Set table view locations
    	int[][] table_view_ids = {
    			{R.id.gold_title,
    				R.id.gold_time,
    				R.id.gold_bidask_label,
    				R.id.gold_bid,
    				R.id.gold_ask,
    				R.id.gold_change_label,
    				R.id.gold_change,
    				R.id.gold_change_percent,
    				R.id.gold_lowhigh_label,
    				R.id.gold_low,
    				R.id.gold_high},
        		{R.id.silver_title,
        			R.id.silver_time,
        			R.id.silver_bidask_label,
        			R.id.silver_bid,
        			R.id.silver_ask,
        			R.id.silver_change_label,
        			R.id.silver_change,
        			R.id.silver_change_percent,
        			R.id.silver_lowhigh_label,
        			R.id.silver_low,
        			R.id.silver_high},
            	{R.id.platinum_title,
        			R.id.platinum_time,
        			R.id.platinum_bidask_label,
        			R.id.platinum_bid,
        			R.id.platinum_ask,
        			R.id.platinum_change_label,
        			R.id.platinum_change,
        			R.id.platinum_change_percent,
        			R.id.platinum_lowhigh_label,
        			R.id.platinum_low,
        			R.id.platinum_high},
            	{R.id.palladium_title,
        			R.id.palladium_time,
        			R.id.palladium_bidask_label,
        			R.id.palladium_bid,
        			R.id.palladium_ask,
        			R.id.palladium_change_label,
        			R.id.palladium_change,
        			R.id.palladium_change_percent,
        			R.id.palladium_lowhigh_label,
        			R.id.palladium_low,
        			R.id.palladium_high}};
    	
    	//Set label strings
    	String[][] table_view_label_titles = {
    			{"", "", "Bid/Ask", "", "", "Change", "", "", "Low/High", "", ""},
    			{"", "", "Bid/Ask", "", "", "Change", "", "", "Low/High", "", ""},
    			{"", "", "Bid/Ask", "", "", "Change", "", "", "Low/High", "", ""},
    			{"", "", "Bid/Ask", "", "", "Change", "", "", "Low/High", "", ""}};    	
    	
    	//Set chart view locations
    	int[] chart_view_ids = {
    			R.id.chart_title,
    			R.id.chart_3day,
    			R.id.chart_1day,
    			R.id.chart_30day,
    			R.id.chart_60day,
    			R.id.chart_6month,
    			R.id.chart_1year,
    			R.id.chart_5year,
    			R.id.chart_10year};
    	
    	//Set table views to hide if charts are displayed
		int[] hide_table_ids = {R.id.market_status, R.id.market_time,
				R.id.gold_title, R.id.gold_time, R.id.gold_table,
    			R.id.silver_title, R.id.silver_time, R.id.silver_table,
        		R.id.platinum_title, R.id.platinum_time, R.id.platinum_table,
        		R.id.palladium_title, R.id.palladium_time, R.id.palladium_table};
		
    	//Spacer view ids
    	int[] spacer_view_ids = {
    			R.id.spacer1,
    			R.id.spacer2,
    			R.id.spacer3,
    			R.id.spacer4,
    			R.id.spacer5};
    	
    	//Hide all views, so only those that are needed will be loaded
		//Hide chart views
		if(DEBUG) Log.v("kitco","hiding charts");
		for(int n = 0; n < 9; n++) {
			switch(n) {
				case 0:
					tv = (TextView)findViewById(chart_view_ids[n]);
					tv.setVisibility(8); break;
				default:
					iv = (ImageView)findViewById(chart_view_ids[n]);
	    			iv.setVisibility(8); break;
			}
		}
		//Hide table views
		if(DEBUG) Log.v("kitco","hiding tables");
		for(int n = 0; n < 14; n++) {
			switch(n) {
				case 4: case 7: case 10: case 13:
					tbv = (TableLayout)findViewById(hide_table_ids[n]);
					tbv.setVisibility(8); break;
				default:
					tv = (TextView)findViewById(hide_table_ids[n]);
	    			tv.setVisibility(8); break;
			}
		}
		//Hide spacers
		if(DEBUG) Log.v("kitco","hiding spacers");
		for(int n = 0; n < 5; n++) {
			tv = (TextView)findViewById(spacer_view_ids[n]);
			tv.setVisibility(8);
		}

    	//Set table data
    	if(screen == 0 && loaded[screen] == 100) {
    		//Making table views visible
    		if(DEBUG) Log.v("kitco","making tables visible");
    		for(int n = 0; n < 14; n++) {
    			switch(n) {
    				case 4: case 7: case 10: case 13:
    					tbv = (TableLayout)findViewById(hide_table_ids[n]);
    					tbv.setVisibility(0); break;
    				default:
    					tv = (TextView)findViewById(hide_table_ids[n]);
    	    			tv.setVisibility(0); break;
    			}
    		}
    		
    		//Make spacers visible
    		for(int n = 0; n < 5; n++) {
    			if(DEBUG) Log.v("kitco","making spacers visible");
    			tv = (TextView)findViewById(spacer_view_ids[n]);
    			tv.setVisibility(0);
    		}
    		
        	//Check to see if any data was retrieved
        	Integer isdata_table = 0;
        	if(screen == 0) {
        		for(int x = 0; x < 4; x++)
        			for(int y = 0; y < 8; y++)
        				if(data_table[x][y] != null)
        					isdata_table = 1;
        	}
        	
    		//Set market status
    		if(DEBUG) Log.v("kitco","setting market status ("+market_status+") color");
    		tv = (TextView)findViewById(R.id.market_status);
    		tv.setText(market_status);
    		if(market_status.contains("OPEN"))
    			tv.setTextColor(Color.GREEN);
    		else
    			tv.setTextColor(Color.RED);
    		tv = (TextView)findViewById(R.id.market_time);
    		tv.setText(market_time);
        	
        	//Display error if no data was retrieved
        	if(isdata_table == 0 && loaded[screen] == 100) {
        		tv = (TextView)findViewById(R.id.market_status);
        		tv.setTextColor(Color.RED);
        		tv.setText("Unable to retreive any data. Connection might be broken. Try clicking on the Kitco.com link above.");
        		tv = (TextView)findViewById(R.id.market_time);
        		tv.setText("");
        		if(DEBUG) Log.e("kitco","no table data retrieved");
        		return;
        	} else if(DEBUG) Log.v("kitco","at least some data was retrieved, continuing to update display");
    		
    		//Replace any missing data in partial tables with 'N/A'
    		if(DEBUG) Log.v("kitco","checking for partial tables");
    		for(int x = 0; x < 4; x++)
    			for(int y = 0; y < 8; y++)
    				if(data_table[x][y] == null)
    					data_table[x][y] = "N/A";
    		
    		//Populate tables
    		if(DEBUG) Log.v("kitco","setting table data");
    		for(int x = 0; x < 4; x++) {
    			for(int y = 0; y < 11; y++) {
    				tv = (TextView)findViewById(table_view_ids[x][y]);
    				switch(y) {
    					case 0:	tv.setText(data_table[x][0]); break;//Set metal name
    					case 1:	tv.setText(data_table[x][1]); break;//Set metal time
    					case 3:	tv.setText(data_table[x][2]); break;//Set bid
    					case 4:	tv.setText(data_table[x][3]); break;//Set ask
	    				case 6:	tv.setText(data_table[x][4]); break;//Set change
    					case 7:	tv.setText(data_table[x][5]); break;//Set change_percent
    					case 9:	tv.setText(data_table[x][6]); break;//Set low
    					case 10:tv.setText(data_table[x][7]); break;//Set high
    					default:tv.setText(table_view_label_titles[x][y]); break;//Set labels (default)
    				}
    			}
    		}
    	}
		

    	
        //Set charts
    	if(screen != 0) {    		
    		//Making chart views visible
    		if(DEBUG) Log.v("kitco","making charts visible");
    		for(int n = 0; n < 9; n++) {
    			switch(n) {
    				case 0:
    					tv = (TextView)findViewById(chart_view_ids[n]);
    					tv.setVisibility(0); break;
    				default:
    					iv = (ImageView)findViewById(chart_view_ids[n]);
    	    			iv.setVisibility(0); break;
    			}
    		}
    		
    		//Set title
    		if(DEBUG) Log.v("kitco","setting chart title");
    		tv = (TextView)findViewById(chart_view_ids[0]);
    		switch(screen) {
    			case 1: tv.setText("GOLD"); break;
    			case 2: tv.setText("SILVER"); break;
    			case 3: tv.setText("PLATINUM"); break;
    			case 4: tv.setText("PALLADIUM"); break;
    			default:tv.setText("ERROR"); break;
    		}
    		
    		//Set charts that are loaded
    		if(DEBUG) Log.v("kitco","setting charts");
    		for(int n = 0; n < 8; n++) {
    			iv = (ImageView)findViewById(chart_view_ids[n+1]);
    			if(charts[screen-1][n] != null) {
    				iv.setVisibility(0);
    				iv.setImageBitmap(charts[screen-1][n]);
    			}
    			else
    				iv.setVisibility(8);
    		}
    		
    		//Show error if no charts are loaded
    		if(loaded[screen] == 100) {
    			Integer isdata_charts = 0;
    			for(int n = 0; n < 8; n++)
    				if(charts[screen-1][n] != null)
    					isdata_charts = 1;
    			if(isdata_charts == 0) {
    				tv = (TextView)findViewById(chart_view_ids[0]);
    				tv.setText("Unable to retreive any data. Connection might be broken. Try clicking on the Kitco.com link above.");
    				if(DEBUG) Log.e("kitco","no chart data retrieved");
    			}
    		}
    	}
    }
	
	//Updates table view
	private void updateTables(Integer scr) {
		//Set initial progress at 5%
		loaded[scr]=5;
		handler.sendMessage(Message.obtain());
		
		//Get content from website for tables
		if(DEBUG) Log.d("kitco","grabbing webpage from kitco.com");
    	String webpage = downloadHTML("http://www.kitco.com/market");
    	
    	//Error if unable to download webpage
    	if(webpage.length() == 0) {
    		if(DEBUG) Log.e("kitco","unable to download kitco.com/market");
    		loaded[scr] = 100;
    		return;
    	}
    	
    	//Parse webpage data for values
        //Find range of data
    	if(DEBUG) Log.v("kitco","finding location of table start and end on webpage");
    	if(webpage.indexOf("<td align=\"center\" bgcolor=\"#000000\" colspan=\"8\">") > 0) {
    		Integer start = webpage.indexOf("<td align=\"center\" bgcolor=\"#000000\" colspan=\"8\">");
    		Integer end = webpage.indexOf("</table></td></tr></table><br><table");
    		String data = webpage.substring(start,end);
    		Integer i = 0;
    		Integer o = 0;

    		//Get market status
    		if(DEBUG) Log.v("kitco","getting market status");
    		i = data.indexOf("MARKET IS", 0);
    		o = data.indexOf("</font>", i);
    		market_status = data.substring(i,o);

    		//Get market time
    		if(DEBUG) Log.v("kitco","getting market time");
    		i = data.indexOf("<BR>", o);
    		o = data.indexOf("</b>",i);
    		market_time=data.substring(i+5,o-1);

    		for(int x=0; x<4; x++) {
    			//Get metal name
    			i = data.indexOf("<p>&nbsp;",o);
            	i = data.indexOf(">",i+1);
            	i = data.indexOf(">",i+1);
            	o = data.indexOf("</a>",i+1);
            	data_table[x][0]=data.substring(i+1,o);
            	if(DEBUG) Log.v("kitco","getting metal name ("+data_table[x][0]+")");

        	
            	//Get time
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i+1);
            	data_table[x][1]=data.substring(i+7,o);
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i);
            	data_table[x][1]=data.substring(i+7,o) + " " + data_table[x][1];
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " time ("+data_table[x][1]+")");
            
            	//Get bid
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i);
            	data_table[x][2]=data.substring(i+7,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " bid ("+data_table[x][2]+")");
            
            	//Get ask
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i);
            	data_table[x][3]=data.substring(i+7,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " ask ("+data_table[x][3]+")");
            
            	//Get change
            	i = data.indexOf("<td",o);
            	i = data.indexOf(">",i+1);
            	i = data.indexOf(">",i+1);
            	o = data.indexOf("</p>",i);
            	data_table[x][4]=data.substring(i+1,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " change ("+data_table[x][4]+")");
            
            	//Get change percent
            	i = data.indexOf("<td",o);
            	i = data.indexOf(">",i+1);
            	i = data.indexOf(">",i+1);
            	o = data.indexOf("</p>",i);
            	data_table[x][5]=data.substring(i+1,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " change percent ("+data_table[x][5]+")");
            
            	//Get low
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i);
            	data_table[x][6]=data.substring(i+7,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " low ("+data_table[x][6]+")");
            
            	//Get high
            	i = data.indexOf("<td",o);
            	o = data.indexOf("</td>",i);
            	data_table[x][7]=data.substring(i+7,o);
            	if(DEBUG) Log.v("kitco","getting " + data_table[x][0] + " high ("+data_table[x][7]+")");
    		}

    		//Set progress at 100%
    		loaded[scr] = 100;
    		handler.sendMessage(Message.obtain());
    	}
    	else{
    		//Note error
    		data_table[0][0]="Error reading table, please email the developer.";
    		loaded[scr] = 100;
    		handler.sendMessage(Message.obtain());
    	}

       	return;
	}
    
	//Grabs chart data from web
	private void updateCharts(Integer scr) {
        //Set chart urls
    	if(DEBUG) Log.v("kitco","set urls for chart images");
    	String[][] chart_urls = {
    		{"http://www.kitco.com/images/live/gold.gif",
    			"http://www.kitco.com/images/live/nygold.gif",
    			"http://www.kitco.com/LFgif/au0030lnb.gif",
    			"http://www.kitco.com/LFgif/au0060lnb.gif",
    			"http://www.kitco.com/LFgif/au0182nyb.gif",
    			"http://www.kitco.com/LFgif/au0365nyb.gif",
    			"http://www.kitco.com/LFgif/au1825nyb.gif",
    			"http://www.kitco.com/LFgif/au3650nyb.gif"},
    		{"http://www.kitco.com/images/live/silver.gif",
    			"http://www.kitco.com/images/live/nysilver.gif",
    			"http://www.kitco.com/LFgif/ag0030lnb.gif",
    			"http://www.kitco.com/LFgif/ag0060lnb.gif",
    			"http://www.kitco.com/LFgif/ag0182nyb.gif",
    			"http://www.kitco.com/LFgif/ag0365nyb.gif",
    			"http://www.kitco.com/LFgif/ag1825nyb.gif",
    			"http://www.kitco.com/LFgif/ag3650nyb.gif"},
    		{"http://www.kitco.com/images/live/plati.gif",
        		"N/A",
        		"http://www.kitco.com/LFgif/pt0030lnb.gif",
        		"http://www.kitco.com/LFgif/pt0060lnb.gif",
        		"http://www.kitco.com/LFgif/pt0182nyb.gif",
        		"http://www.kitco.com/LFgif/pt0365nyb.gif",
        		"http://www.kitco.com/LFgif/pt1825nyb.gif",
        		"N/A"},
        	{"http://www.kitco.com/images/live/plad.gif",
        		"N/A",
        		"http://www.kitco.com/LFgif/pd0030lnb.gif",
        		"http://www.kitco.com/LFgif/pd0060lnb.gif",
        		"http://www.kitco.com/LFgif/pd0182nyb.gif",
        		"http://www.kitco.com/LFgif/pd0365nyb.gif",
        		"http://www.kitco.com/LFgif/pd1825nyb.gif",
        		"N/A"}};
    	
    	//Check how many charts have been downloaded
    	for(int n = 0; n < 8; n++) {
    		if(charts[scr-1][n] != null) {
    			if(DEBUG) Log.v("kitco","chart "+n+" for screen "+scr.toString()+" is already downloaded");
    			chart_urls[scr-1][n] = "N/A";
    		}
    	}
    	handler.sendMessage(Message.obtain());
    	
    	
        //Download chart images based on url
        if(DEBUG) Log.v("kitco","downloading chart images for screen "+scr.toString());
        for(int n = 0; n < 8; n++) {
        	//Stop if screen changes
        	if(scr != screen) {
        		Log.d("kitco","stopping download for screen "+scr.toString()+" because current screen is "+screen.toString());
        		break;
        	}

        	//Download chart images if url is available
        	if(chart_urls[scr-1][n] != "N/A")
        		charts[scr-1][n] = downloadImage(chart_urls[scr-1][n]);
        	loaded[scr] = (n+1)*100/8;
        	handler.sendMessage(Message.obtain());
        }
        if(DEBUG) Log.v("kitco","done downloading chart images");
        
        return;
    }
    
	//Downloads html from specified url
	private String downloadHTML(String url) {
    	
    	if(DEBUG) Log.d("kitco","downloading html page from: "+url);
    	
    	URL ChartUrl =null;
    	String page = null;
    	StringBuffer sb = new StringBuffer();
    	try {ChartUrl= new URL(url);}
    	catch (MalformedURLException e) {e.printStackTrace();}
    	try {HttpURLConnection conn = (HttpURLConnection)ChartUrl.openConnection();
    		conn.setDoOutput(true);
    		conn.connect();
    		InputStream stream = conn.getInputStream();
    		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    		while ((page = reader.readLine()) != null) {
    			sb.append(page).append("\n");}
    		}
    	catch(IOException e) {e.printStackTrace();}
    	
		return sb.toString();
    }
    
	//Downloads image from specified url
	private Bitmap downloadImage(String url) {
    	
    	if(DEBUG) Log.d("kitco","downloading chart image from: "+url);
    	
    	URL ChartUrl =null;
    	Bitmap image = null;
    	try {ChartUrl= new URL(url);}
    	catch (MalformedURLException e) {e.printStackTrace();}
    	try {HttpURLConnection conn = (HttpURLConnection)ChartUrl.openConnection();
    		conn.setDoOutput(true);
    		conn.connect();
    		InputStream imagestream = conn.getInputStream();
    		image = BitmapFactory.decodeStream(imagestream);
    		}
    	catch(IOException e) {e.printStackTrace();}
    	
		return image;
    }
}