package com.kitco.goldprices;

/* KITCO GOLD PRICES ANDROID APPLICATION
 * version 0.4.2
 * 
 * This is a simple application for Android devices
 * that downloads gold price data and charts from
 * Kitco.com and displays them on your Android
 * device.
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

/* TODO
 * Image zoom on double tap, will probably require 1.5
 */

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.kitco.goldprices.R;

public class KitcoGoldPrices extends Activity {
	
	private String[] table_data = new String[13];
	private Bitmap[] charts = new Bitmap[8];
	private Integer loaded = 0;
	private Integer lockout = 0;
	static final boolean DEBUG = false;
	
    //Called when the activity is first created 
    @Override
    public void onCreate(Bundle icicle) {
        if(DEBUG) Log.v("kitco","created onCreate");

        super.onCreate(icicle);
        setContentView(R.layout.main);
        
		TextView obsolete_info = (TextView)findViewById(R.id.obsolete);
		obsolete_info.setText("This app is no longer supported. Please use the Gold Live! app by Kitco.");
        
        //Update data on initial creation
        if(DEBUG) Log.v("kitco","calling updateDate from onCreate");
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
      if(DEBUG) Log.v("kitco","calling updateDate from onConfigurationChanged");
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
	    	//Make progressbar visible
	    	if(DEBUG) Log.v("kitco","refresh button clicked, making progress bar visible");
	    	loaded = 0;
	    	ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
	    	pg.setVisibility(0);
	    	pg.setProgress(loaded);
	    	
	    	//Force reload data
	    	if(DEBUG) Log.v("kitco","force reloading data");
	    	for(int n = 0; n < 13; n++) {
	    			table_data[n] = null;
	    			if(n < 8) charts[n] = null;
	    		}
	    		updateData();
	    }
	};
	
	//Receives messages from other threads
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			//Get progressbar widget
			if(DEBUG) Log.v("kitco","handler received message, finding progressbar widget");
			ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
			
			//Set progressbar to percent complete
			if(loaded<100) {
				if(DEBUG) Log.v("kitco","data "+loaded.toString()+"% checked");
				pg.setProgress(loaded);
				updateViews();
			}
			else {
				//100% loaded, make progressbar invisible
				if(DEBUG) Log.v("kitco","100% checked, setting progressbar widget to invisible");
				pg.setVisibility(8);
				if(DEBUG) Log.v("kitco","updating layout to include table and chart data");
				updateViews();
			}
		}
	};
	
	//Updates data from web in separate thread
	private void updateData() {
		Thread t = new Thread() {
			public void run() {
				//Create lockout to prevent multiple threads
				if(lockout != 1) {
					//Set lockout to prevent new thread from loading
					lockout = 1;
					
					//Check to see if data is already loaded
					if(DEBUG) Log.v("kitco","created new thread for updateData");
					if(loaded < 100) {
						//Get updates to table
						if(DEBUG) Log.v("kitco","updating table");
						updateTable();
						
						//Get updates to charts
						if(DEBUG) Log.v("kitco","updating charts");
						updateCharts();
						
						//Mark as loaded
						if(DEBUG) Log.v("kitco","charts and tables updated");
						loaded = 100;
					}
					//Send signal if downloads complete
					if(DEBUG) Log.v("kitco","sending message to handler that updates are done");
					handler.sendMessage(Message.obtain());
					
					//free lockout so new threads can be created
					lockout = 0;
				}
			}
		};
		t.start();
	}

	//Updates view widgets on screen with data
	private void updateViews() {
    	//Set widget locations for data
    	int[] table_view_label_ids = { R.id.bidask_label, R.id.lowhigh_label,
    			R.id.change_label, R.id.monthchange_label, R.id.yearchange_label};
    	String[] table_view_label_titles = { "Bid/Ask", "Low/High",
    			"Change", "30daychg", "1yearchg"};
    	int[] table_view_ids = { R.id.market_status, R.id.market_time, R.id.time,
    			R.id.bid, R.id.ask, R.id.low, R.id.high, R.id.change, R.id.change_percent,
    			R.id.monthchange, R.id.monthchange_percent, R.id.yearchange, R.id.yearchange_percent};
    	int[] chart_view_ids = { R.id.chart_3day, R.id.chart_nyspot, R.id.chart_30day, R.id.chart_60day,
    			R.id.chart_6month, R.id.chart_1year, R.id.chart_5year, R.id.chart_10year};

    	//Check to see if any data was retrieved
    	Integer isdata_table = 0;
    	Integer isdata_charts = 0;
    	for(int n = 0; n < 13; n++)
    		if(table_data[n] != null)
    			isdata_table = 1;
    	for(int n = 0; n < 8; n++)
    		if(charts[n] != null)
    			isdata_charts = 1;
    	
    	//Display error if no data was retrieved
    	if(isdata_table == 0 && isdata_charts == 0 && loaded == 100) {
    		TextView connection_info = (TextView)findViewById(table_view_ids[0]);
    		connection_info.setText("Unable to retreive any data. Connection might be broken. Try clicking on the Kitco.com link above.");
    		if(DEBUG) Log.v("kitco","no data retrieved");
    		return;
    	} else if(DEBUG) Log.v("kitco","at least some data was retrieved, continuing to update display");
    	
    	//Set table labels
    	if(isdata_table == 1) {
    		if(DEBUG) Log.v("kitco","setting table labels");
    		TextView tv;
    		for(int n = 0; n < 5; n++) {
    			tv = (TextView)findViewById(table_view_label_ids[n]);
    			tv.setText(table_view_label_titles[n]);
    		}

    		//Get table data boxes
    		if(DEBUG) Log.v("kitco","setting table info");
    		for(int n = 0; n < 13; n++) {
    			tv = (TextView)findViewById(table_view_ids[n]);
    			if(table_data[n] != null)
    				tv.setText(table_data[n]);
    			else
    				tv.setText("N/A");
    		}
        
    		//Set market_status color
    		tv = (TextView)findViewById(table_view_ids[0]);
    		if(table_data[0]=="SPOT MARKET IS OPEN")
    			tv.setTextColor(0xff00af00);
    		else
    			tv.setTextColor(0xffff0000);
    	}
    	
        //Get chart boxes
    	if(isdata_charts == 1) {
    		if(DEBUG) Log.v("kitco","setting charts");
    		ImageView iv;
    		for(int n = 0; n < 8; n++) {
    			iv = (ImageView)findViewById(chart_view_ids[n]);
    			if(charts[n] != null)
    				iv.setImageBitmap(charts[n]);
    		}
    	}
    }
    
	//Grabs table data from web
	private void updateTable() {
        //Get webpage data for prices
    	if(DEBUG) Log.v("kitco","grabbing webpage from kitco.com");
    	String webpage = downloadHTML("http://www.kitco.com/");
    	
    	//Error if unable to download webpage
    	if(webpage.length() == 0) {
    		if(DEBUG) Log.v("kitco","unable to download kitco.com homepage");
    		return;
    	}
    	
        //Parse webpage data for values
        //Find range of data
    	if(DEBUG) Log.v("kitco","finding location of table start on webpage");
        Integer start = webpage.indexOf("<!-- LIVE SPOT GOLD -->");
        if(DEBUG) Log.v("kitco","finding location of table end on webpage ("+start+")");
        Integer end = webpage.indexOf("<td colspan=\"3\"><a href=\"/charts/livegold.html\" target=\"_blank\">Charts...</a></td>");
        if(DEBUG) Log.v("kitco","found location of table start and end on webpage ("+end+")");
        if(start < 0 || end < 0)
            return;
        String data = webpage.substring(start,end);
        Integer i = 0;
        Integer o = 0;

        //Get market status
        if(DEBUG) Log.v("kitco","getting market status");
        if(data.contains("OPEN"))
        	table_data[0]="SPOT MARKET IS OPEN";
        else
        	table_data[0]="SPOT MARKET IS CLOSED";

        //Get market time
        if(DEBUG) Log.v("kitco","getting market time");
        i = data.indexOf("Spot Market", start);
        i = data.indexOf("<font size=\"1\" face=\"Verdana, Arial, Helvetica, sans-serif\">", i);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[1]=data.substring(i+61,o);

        //Get time
        if(DEBUG) Log.v("kitco","getting current time");
        i = data.indexOf("<div class=\"market_date\">");
        o = data.indexOf("</div>",i);
        if(i < 0 || o < 0) return;
        table_data[2]=data.substring(i+25,o);

        //Get bid
        if(DEBUG) Log.v("kitco","getting bid price");
        o = data.indexOf("Bid/Ask",o);
        i = data.indexOf("<td>",o);
        o = data.indexOf(" -</td>",i);
        if(i < 0 || o < 0) return;
        table_data[3]=data.substring(i+4,o);

        //Get ask
        if(DEBUG) Log.v("kitco","getting ask price");
        i = data.indexOf("<td>",o);
        o = data.indexOf("</td>",i);
        if(i < 0 || o < 0) return;
        table_data[4]=data.substring(i+4,o);

        //Get low
        if(DEBUG) Log.v("kitco","getting low price");
        o = data.indexOf("Low/High",o);
        i = data.indexOf("<td>",o);
        o = data.indexOf(" -</td>",i);
        if(i < 0 || o < 0) return;
        table_data[5]=data.substring(i+4,o);

        //Get high
        if(DEBUG) Log.v("kitco","getting high price");
        i = data.indexOf("<td>",o);
        o = data.indexOf("</td>",i);
        if(i < 0 || o < 0) return;
        table_data[6]=data.substring(i+4,o);

        //Get change
        if(DEBUG) Log.v("kitco","getting change price");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[7]=data.substring(i+20,o);

        //Get change_percent
        if(DEBUG) Log.v("kitco","getting change percent");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[8]=data.substring(i+20,o);

        //Get 30daychg
        if(DEBUG) Log.v("kitco","getting 30 day change");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[9]=data.substring(i+20,o);

        //Get 30daychg_percent
        if(DEBUG) Log.v("kitco","getting 30 day change percent");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[10]=data.substring(i+20,o);

        //Get 1yearchg
        if(DEBUG) Log.v("kitco","getting 1 year change");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[11]=data.substring(i+20,o);

      	//Get 1yearchg_percent
        if(DEBUG) Log.v("kitco","getting 1 year change percent");
        i = data.indexOf("<font",o);
        o = data.indexOf("</font>",i);
        if(i < 0 || o < 0) return;
        table_data[12]=data.substring(i+20,o);
    	loaded = 20;
    	handler.sendMessage(Message.obtain());
        
        return;
    }
    
	//Grabs chart data from web
	private void updateCharts() {
    	String[] chart_urls = new String[8];

        //Set chart urls
    	if(DEBUG) Log.v("kitco","set urls for chart images");
    	chart_urls[0] = "http://www.kitco.com/images/live/gold.gif";
    	chart_urls[1] = "http://www.kitco.com/images/live/nygold.gif";
    	chart_urls[2] = "http://www.kitco.com/LFgif/au0030lnb.gif";
    	chart_urls[3] = "http://www.kitco.com/LFgif/au0060lnb.gif";
    	chart_urls[4] = "http://www.kitco.com/LFgif/au0182nyb.gif";
    	chart_urls[5] = "http://www.kitco.com/LFgif/au0365nyb.gif";
    	chart_urls[6] = "http://www.kitco.com/LFgif/au1825nyb.gif";
    	chart_urls[7] = "http://www.kitco.com/LFgif/au3650nyb.gif";

        //Download chart images
        if(DEBUG) Log.v("kitco","download chart images");
        for(int n = 0; n < 8; n++) {
        	charts[n] = downloadImage(chart_urls[n]);
        	loaded = 30 + (10 * n);
        	handler.sendMessage(Message.obtain());
        }
        if(DEBUG) Log.v("kitco","done downloading chart images");
        
        return;
    }
    
	//Downloads html from specified url
	private String downloadHTML(String url) {
    	
    	if(DEBUG) Log.v("kitco","downloading html page from: "+url);
    	
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
    	
    	if(DEBUG) Log.v("kitco","downloading chart image from: "+url);
    	
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
