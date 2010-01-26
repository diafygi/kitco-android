package com.kitco.goldprices;

/* KITCO GOLD PRICES ANDROID APPLICATION
 * version 0.1
 * 
 * This is a simple application for Android devices
 * that downloads gold price data and charts from
 * Kitco.com and displays them on your Android
 * device.
 * 
 * The source code for this application is released
 * under the Gnu Public Licence version 2 (GPLv2).
 * Copyright 2010. Daniel Roesler (diafygi) diafygi
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
 * Fault errors
 * Image zoom on double tap
 */


import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class KitcoGoldPrices extends Activity {
	
	private String[] table_data = new String[13];
	private Bitmap[] charts = new Bitmap[8];
	private Integer loaded = 0;
	private Integer lockout = 0;
	
    /** Called when the activity is first created. */ 
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);
        
        //Update data on initial creation
        Log.v("kitco","calling updateDate from onCreate");
        updateData();
        
        //Define refresh button action
        Button refresh_button = (Button)findViewById(R.id.refresh);
        refresh_button.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		//Make progressbar visible
        		Log.v("kitco","refresh button clicked, making progress bar visible");
        		ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
        		pg.setVisibility(0);
        		
        		//Force reload data
        		Log.v("kitco","force reloading data");
        		loaded = 0;
        		updateData();
        	}
        });
    }
    
    //Don't kill activity when screen is rotated
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
	  setContentView(R.layout.main);
	  
      //Fill data on config change
      Log.v("kitco","calling updateDate from onConfigurationChanged");
	  updateData();
	  
	//Define refresh button action
      Button refresh_button = (Button)findViewById(R.id.refresh);
      refresh_button.setOnClickListener(new OnClickListener(){
      	public void onClick(View v) {
    		//Make progressbar visible
    		Log.v("kitco","refresh button clicked, making progress bar visible");
    		ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
    		pg.setVisibility(0);
    		
    		//Force reload data
    		Log.v("kitco","force reloading data");
    		loaded = 0;
    		updateData();
    	}
    });
	}
	
	//Handler that listens for when updates are complete
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			//Get progressbar widget
			Log.v("kitco","handler received message, finding progressbar widget");
			ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
			
			//Set progressbar to percent complete
			if(loaded<100) {
				Log.v("kitco","data "+loaded.toString()+"% loaded");
				pg.setProgress(loaded);
			}
			else {
				//100% loaded, make progressbar invisible
				Log.v("kitco","100% loaded, setting progressbar widget to invisible");
				pg.setVisibility(8);
				Log.v("kitco","updating layout to include table and chart data");
				updateViews();
			}
		}
	};
	
	//Thread that calls updates
	private void updateData() {
		Thread t = new Thread() {
			public void run() {
				//Create lockout to prevent multiple threads
				if(lockout != 1) {
					//Set lockout to prevent new thread from loading
					lockout = 1;
					
					//Check to see if data is already loaded
					Log.v("kitco","created new thread for updateData");
					if(loaded < 100) {
						//Get updates to table
						Log.v("kitco","updating table");
						table_data = updateTable();
						
						//Get updates to charts
						Log.v("kitco","updating charts");
						charts = updateCharts();
						
						//Mark as loaded
						Log.v("kitco","charts and tables updated");
						loaded = 100;
					}
					//Send signal if downloads complete
					Log.v("kitco","sending message to handler that updates are done");
					handler.sendMessage(Message.obtain());
					
					//free lockout so new threads can be created
					lockout = 0;
				}
			}
		};
		t.start();
	}

	//Updates table and charts in window
    private void updateViews() {
    	//Check to see if data was retrieved
    	if(table_data[0] == null && charts[0] == null) { 
    		TextView connection_info = (TextView)findViewById(R.id.market_status);
    		connection_info.setText("Unable to retreive data. Connection might be broken. Try clicking on the Kitco.com link above.");
    		return;
    	}
    	
    	//Find table label boxes
    	Log.v("kitco","finding table labels");
    	TextView bidask_label = (TextView)findViewById(R.id.bidask_label);
        TextView lowhigh_label = (TextView)findViewById(R.id.lowhigh_label);
        TextView change_label = (TextView)findViewById(R.id.change_label);
        TextView monthchange_label = (TextView)findViewById(R.id.monthchange_label);
        TextView yearchange_label = (TextView)findViewById(R.id.yearchange_label);
        
        //Set table labels
        Log.v("kitco","setting table label text");
        bidask_label.setText("Bid/Ask");
        lowhigh_label.setText("Low/High");
        change_label.setText("Change");
        monthchange_label.setText("30daychg");
        yearchange_label.setText("1yearchg");

        //Get table data boxes
        Log.v("kitco","finding table info");
        TextView market_status = (TextView)findViewById(R.id.market_status);
        TextView market_time = (TextView)findViewById(R.id.market_time);
        TextView time = (TextView)findViewById(R.id.time);
        TextView bid = (TextView)findViewById(R.id.bid);
        TextView ask = (TextView)findViewById(R.id.ask);
        TextView low = (TextView)findViewById(R.id.low);
        TextView high = (TextView)findViewById(R.id.high);
        TextView change = (TextView)findViewById(R.id.change);
        TextView change_percent = (TextView)findViewById(R.id.change_percent);
        TextView monthchange = (TextView)findViewById(R.id.monthchange);
        TextView monthchange_percent = (TextView)findViewById(R.id.monthchange_percent);
        TextView yearchange = (TextView)findViewById(R.id.yearchange);
        TextView yearchange_percent = (TextView)findViewById(R.id.yearchange_percent);

        //Set market status color
        if(table_data[0]=="SPOT MARKET IS OPEN")
        	market_status.setTextColor(0xff00af00);
        else
        	market_status.setTextColor(0xffff0000);
        
        //Set table data
        Log.v("kitco","checking to see if downloaded table info correctly");
        if (table_data[0] != null) {
        	Log.v("kitco","setting table info");
        	market_status.setText(table_data[0]);
        	market_time.setText(table_data[1]);
        	time.setText(table_data[2]);
        	bid.setText(table_data[3]);
        	ask.setText(table_data[4]);
        	low.setText(table_data[5]);
        	high.setText(table_data[6]);
        	change.setText(table_data[7]);
        	change_percent.setText(table_data[8]);
        	monthchange.setText(table_data[9]);
        	monthchange_percent.setText(table_data[10]);
        	yearchange.setText(table_data[11]);
        	yearchange_percent.setText(table_data[12]);
        }

        //Get chart boxes
        Log.v("kitco","finding chart locations");
        ImageView chart_3day = (ImageView)findViewById(R.id.chart_3day);
        ImageView chart_nyspot = (ImageView)findViewById(R.id.chart_nyspot);
        ImageView chart_30day = (ImageView)findViewById(R.id.chart_30day);
        ImageView chart_60day = (ImageView)findViewById(R.id.chart_60day);
        ImageView chart_6month = (ImageView)findViewById(R.id.chart_6month);
        ImageView chart_1year = (ImageView)findViewById(R.id.chart_1year);
        ImageView chart_5year = (ImageView)findViewById(R.id.chart_5year);
        ImageView chart_10year = (ImageView)findViewById(R.id.chart_10year);

        //Set charts
        Log.v("kitco","checking to see if charts downloaded");
        if(charts[0] != null) {
        	Log.v("kitco","setting chart images");
        	chart_3day.setImageBitmap(charts[0]);
        	chart_nyspot.setImageBitmap(charts[1]);
        	chart_30day.setImageBitmap(charts[2]);
        	chart_60day.setImageBitmap(charts[3]);
        	chart_6month.setImageBitmap(charts[4]);
        	chart_1year.setImageBitmap(charts[5]);
        	chart_5year.setImageBitmap(charts[6]);
        	chart_10year.setImageBitmap(charts[7]);
        }
    }
    
    //retrieves table data from internet
    private String[] updateTable() {
    	String[] tabledata = new String[13];

        //Get webpage data for prices
    	Log.v("kitco","grabbing webpage from kitco.com");
    	String webpage = downloadHTML("http://www.kitco.com/");
    	if(webpage.length() < 1) {
    		return tabledata;
    	}
    	else {
    		loaded = 7;
    		handler.sendMessage(Message.obtain());
    	}
    	
        //Parse webpage data for values
        //Find range of data
    	Log.v("kitco","finding location of table start and end on webpage");
        Integer start = webpage.indexOf("<!--status -->");
        Integer end = webpage.indexOf("<td colspan=\"4\"><a href=\"/charts/livegold.html\" target=\"_blank\">Charts...</a></td>");
        String data = webpage.substring(start,end);
        Integer i = 0;
        Integer o = 0;

        //Get market status
        Log.v("kitco","getting market status");
        if(data.contains("SPOT MARKET IS OPEN"))
        	tabledata[0]="SPOT MARKET IS OPEN";
        else
        	tabledata[0]="SPOT MARKET IS CLOSED";
    	loaded = 8;
    	handler.sendMessage(Message.obtain());

        //Get market time
        Log.v("kitco","getting market time");
        i = data.indexOf("SPOT MARKET IS", start);
        i = data.indexOf("<font face=\"Verdana, Arial, Helvetica, sans-serif\" size=\"1\">", i);
        o = data.indexOf("</font>",i);
        tabledata[1]=data.substring(i+60,o);
    	loaded = 9;
    	handler.sendMessage(Message.obtain());

        //Get time
        Log.v("kitco","getting current time");
        i = data.indexOf("<!--date -->");
        o = data.indexOf("</font>",i);
        tabledata[2]=data.substring(i+12,o);
    	loaded = 10;
    	handler.sendMessage(Message.obtain());

        //Get bid
        Log.v("kitco","getting bid price");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</td>",i);
        tabledata[3]=data.substring(i+37,o);
    	loaded = 11;
    	handler.sendMessage(Message.obtain());

        //Get ask
        Log.v("kitco","getting ask price");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</td>",i);
        tabledata[4]=data.substring(i+37,o);
    	loaded = 12;
    	handler.sendMessage(Message.obtain());

        //Get low
        Log.v("kitco","getting low price");
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</td>",i);
        tabledata[5]=data.substring(i+19,o);
    	loaded = 13;
    	handler.sendMessage(Message.obtain());

        //Get high
        Log.v("kitco","getting high price");
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</td>",i);
        tabledata[6]=data.substring(i+19,o);
    	loaded = 14;
    	handler.sendMessage(Message.obtain());

        //Get change
        Log.v("kitco","getting change price");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[7]=data.substring(i+57,o);
    	loaded = 15;
    	handler.sendMessage(Message.obtain());

        //Get change_percent
        Log.v("kitco","getting change percent");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[8]=data.substring(i+57,o);
    	loaded = 16;
    	handler.sendMessage(Message.obtain());

        //Get 30daychg
        Log.v("kitco","getting 30 day change");
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</font>",i);
        tabledata[9]=data.substring(i+39,o);
    	loaded = 17;
    	handler.sendMessage(Message.obtain());

        //Get 30daychg_percent
        Log.v("kitco","getting 30 day change percent");
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</font>",i);
        tabledata[10]=data.substring(i+39,o);
    	loaded = 18;
    	handler.sendMessage(Message.obtain());

        //Get 1yearchg
        Log.v("kitco","getting 1 year change");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[11]=data.substring(i+57,o);
    	loaded = 19;
    	handler.sendMessage(Message.obtain());

      	//Get 1yearchg_percent
        Log.v("kitco","getting 1 year change percent");
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[12]=data.substring(i+57,o);
    	loaded = 20;
    	handler.sendMessage(Message.obtain());
        
        return tabledata;
    }
    
    //retrieves chart images from internet
    private Bitmap[] updateCharts() {
    	Bitmap[] charts = new Bitmap[8];

        //Set chart urls
    	Log.v("kitco","set urls for chart images");
    	String url_chart_3day = "http://www.kitco.com/images/live/gold.gif";
        String url_chart_nyspot = "http://www.kitco.com/images/live/nygold.gif";
        String url_chart_30day = "http://www.kitco.com/LFgif/au0030lnb.gif";
        String url_chart_60day = "http://www.kitco.com/LFgif/au0060lnb.gif";
        String url_chart_6month = "http://www.kitco.com/LFgif/au0182nyb.gif";
        String url_chart_1year = "http://www.kitco.com/LFgif/au0365nyb.gif";
        String url_chart_5year = "http://www.kitco.com/LFgif/au1825nyb.gif";
        String url_chart_10year = "http://www.kitco.com/LFgif/au3650nyb.gif";

        //Download chart images
        Log.v("kitco","download chart images");
        charts[0] = downloadImage(url_chart_3day);
    	loaded = 30;
    	handler.sendMessage(Message.obtain());
        charts[1] = downloadImage(url_chart_nyspot);
    	loaded = 40;
    	handler.sendMessage(Message.obtain());
        charts[2] = downloadImage(url_chart_30day);
    	loaded = 50;
    	handler.sendMessage(Message.obtain());
        charts[3] = downloadImage(url_chart_60day);
    	loaded = 60;
    	handler.sendMessage(Message.obtain());
        charts[4] = downloadImage(url_chart_6month);
    	loaded = 70;
    	handler.sendMessage(Message.obtain());
        charts[5] = downloadImage(url_chart_1year);
    	loaded = 80;
    	handler.sendMessage(Message.obtain());
        charts[6] = downloadImage(url_chart_5year);
    	loaded = 90;
    	handler.sendMessage(Message.obtain());
        charts[7] = downloadImage(url_chart_10year);
    	loaded = 100;
    	handler.sendMessage(Message.obtain());
        
        Log.v("kitco","done downloading chart images");
        
        return charts;
    }
    
  //Downloads an html page from a specified url
    private String downloadHTML(String url) {
    	
    	Log.v("kitco","downloading html page from: "+url);
    	
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
    	
    	Log.v("kitco","finished downloading html page from: "+url);
    	
		return sb.toString();
    }
    
    //Downloads an image from a specified url
    private Bitmap downloadImage(String url) {
    	
    	Log.v("kitco","downloading chart image from: "+url);
    	
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
    	
    	Log.v("kitco","finished downloading chart image from: "+url);
    	
		return image;
    }
}