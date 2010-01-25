package com.kitco.GoldPrices;

/* TODO
 * Fault errors
 * Prevent reloading on slid out
 * Image zoom on double tap
 * Refresh buttons
 */


import android.app.Activity;
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

public class GoldPrices extends Activity {
	
	String[] table_data = new String[13];
	Bitmap[] charts = new Bitmap[8];
	Integer loaded = null;
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			Log.v("kitco","handler received message, finding progressbar widget");
			
			ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
			
			Log.v("kitco","setting progressbar widget to invisible");
			
			pg.setVisibility(8);
			
			Log.v("kitco","updating layout to include table and chart data");
			
			updateViews();
		}
	};
	
	private void updateData() {
		Thread t = new Thread() {
			public void run() {
				
				Log.v("kitco","created new thread for updateData");
				
				//Check to see if data is already loaded
				if(loaded == null) {
					
					Log.v("kitco","updating table");
					
					//Get updates to table
					table_data = updateTable();
					
					Log.v("kitco","table updated");
					Log.v("kitco","updating charts");
					
					//Get updates to charts
					charts = updateCharts();
					
					Log.v("kitco","charts updated");
					
					//Mark as loaded
					loaded = 1;
				}
				
				Log.v("kitco","sending message to handler that updates are done");
				
				//Send signal if downloads complete
				handler.sendMessage(Message.obtain());
			}
		};
		t.start();
	}
	
    /** Called when the activity is first created. */ 
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);
        
        Log.v("kitco","calling updateDate from onCreate");
        updateData();
        
        Button refresh_button = (Button)findViewById(R.id.refresh);
        
        refresh_button.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		
        		Log.v("kitco","refresh button clicked, making progress bar visible");
        		
        		//Make progressbar visible
        		ProgressBar pg = (ProgressBar)findViewById(R.id.progressbar);
        		pg.setVisibility(0);
        		
        		Log.v("kitco","force reloading data");
        		
        		//Force reload data
        		loaded = null;
        		updateData();
        	}
        });
    }
    
    private void updateViews() {
    	
    	Log.v("kitco","finding table labels");
    	
    	//Find table label boxes
    	TextView bidask_label = (TextView)findViewById(R.id.bidask_label);
        TextView lowhigh_label = (TextView)findViewById(R.id.lowhigh_label);
        TextView change_label = (TextView)findViewById(R.id.change_label);
        TextView monthchange_label = (TextView)findViewById(R.id.monthchange_label);
        TextView yearchange_label = (TextView)findViewById(R.id.yearchange_label);
        
        Log.v("kitco","setting table label text");
        
        //Set table labels
        bidask_label.setText("Bid/Ask");
        lowhigh_label.setText("Low/High");
        change_label.setText("Change");
        monthchange_label.setText("30daychg");
        yearchange_label.setText("1yearchg");
        
        Log.v("kitco","finding table info");
        
        //Get table data boxes
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
        
        Log.v("kitco","setting table info");
        
        //Set market status color
        if(table_data[0]=="SPOT MARKET IS OPEN")
        	market_status.setTextColor(0xff00af00);
        else
        	market_status.setTextColor(0xffff0000);
        
        //Set table data
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
        
        Log.v("kitco","finding chart locations");
        
        //Get chart boxes
        ImageView chart_3day = (ImageView)findViewById(R.id.chart_3day);
        ImageView chart_nyspot = (ImageView)findViewById(R.id.chart_nyspot);
        ImageView chart_30day = (ImageView)findViewById(R.id.chart_30day);
        ImageView chart_60day = (ImageView)findViewById(R.id.chart_60day);
        ImageView chart_6month = (ImageView)findViewById(R.id.chart_6month);
        ImageView chart_1year = (ImageView)findViewById(R.id.chart_1year);
        ImageView chart_5year = (ImageView)findViewById(R.id.chart_5year);
        ImageView chart_10year = (ImageView)findViewById(R.id.chart_10year);
        
        Log.v("kitco","setting chart images");
        
        //Set charts
        chart_3day.setImageBitmap(charts[0]);
        chart_nyspot.setImageBitmap(charts[1]);
        chart_30day.setImageBitmap(charts[2]);
        chart_60day.setImageBitmap(charts[3]);
        chart_6month.setImageBitmap(charts[4]);
        chart_1year.setImageBitmap(charts[5]);
        chart_5year.setImageBitmap(charts[6]);
        chart_10year.setImageBitmap(charts[7]);     
    }

    
    private String[] updateTable() {
    	String[] tabledata = new String[13];
    	
    	Log.v("kitco","grabbing webpage from kitco.com");
    	
        //Get webpage data for prices
        String webpage = downloadHTML("http://www.kitco.com/");
        
        Log.v("kitco","finding location of table start and end on webpage");
        
        //Parse webpage data for values
        //Find range of data
        Integer start = webpage.indexOf("<!--status -->");
        Integer end = webpage.indexOf("<td colspan=\"4\"><a href=\"/charts/livegold.html\" target=\"_blank\">Charts...</a></td>");
        String data = webpage.substring(start,end);
        Integer b = 0;
        Integer i = 0;
        Integer o = 0;

        Log.v("kitco","getting market status");
        
        //Get market status
        if(data.contains("SPOT MARKET IS OPEN"))
        	tabledata[0]="SPOT MARKET IS OPEN";
        else
        	tabledata[0]="SPOT MARKET IS CLOSED";

        Log.v("kitco","getting market time");
        
        //Get market time
        i = data.indexOf("SPOT MARKET IS", start);
        i = data.indexOf("<font face=\"Verdana, Arial, Helvetica, sans-serif\" size=\"1\">", i);
        o = data.indexOf("</font>",i);
        tabledata[1]=data.substring(i+60,o);

        Log.v("kitco","getting current time");
        
        //Get time
        i = data.indexOf("<!--date -->");
        o = data.indexOf("</font>",i);
        tabledata[2]=data.substring(i+12,o);
        
        Log.v("kitco","getting bid price");
        
        //Get bid
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</td>",i);
        tabledata[3]=data.substring(i+37,o);
        
        Log.v("kitco","getting ask price");
        
        //Get ask
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</td>",i);
        tabledata[4]=data.substring(i+37,o);
        
        Log.v("kitco","getting low price");
        
        //Get low
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</td>",i);
        tabledata[5]=data.substring(i+19,o);
        
        Log.v("kitco","getting high price");
        
        //Get high
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</td>",i);
        tabledata[6]=data.substring(i+19,o);
        
        Log.v("kitco","getting change price");
        
        //Get change
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[7]=data.substring(i+57,o);
        
        Log.v("kitco","getting change percent");
        
        //Get change_percent
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[8]=data.substring(i+57,o);
        
        Log.v("kitco","getting 30 day change");
        
        //Get 30daychg
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</font>",i);
        tabledata[9]=data.substring(i+39,o);
        
        Log.v("kitco","getting 30 day change percent");
        
        //Get 30daychg_percent
        i = data.indexOf("<td align=\"center\">",o);
        o = data.indexOf("</font>",i);
        tabledata[10]=data.substring(i+39,o);
        
        Log.v("kitco","getting 1 year change");
        
        //Get 1yearchg
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[11]=data.substring(i+57,o);
        
        Log.v("kitco","getting 1 year change percent");
        
      	//Get 1yearchg_percent
        i = data.indexOf("<td align=\"center\" bgcolor=\"#f3f3e4\">",o);
        o = data.indexOf("</font>",i);
        tabledata[12]=data.substring(i+57,o);
        
        return tabledata;
    }
    
    private Bitmap[] updateCharts() {
    	Bitmap[] charts = new Bitmap[8];
    	
    	Log.v("kitco","set urls for chart images");
    	
        //Set chart urls
        String url_chart_3day = "http://www.kitco.com/images/live/gold.gif";
        String url_chart_nyspot = "http://www.kitco.com/images/live/nygold.gif";
        String url_chart_30day = "http://www.kitco.com/LFgif/au0030lnb.gif";
        String url_chart_60day = "http://www.kitco.com/LFgif/au0060lnb.gif";
        String url_chart_6month = "http://www.kitco.com/LFgif/au0182nyb.gif";
        String url_chart_1year = "http://www.kitco.com/LFgif/au0365nyb.gif";
        String url_chart_5year = "http://www.kitco.com/LFgif/au1825nyb.gif";
        String url_chart_10year = "http://www.kitco.com/LFgif/au3650nyb.gif";
        
        Log.v("kitco","download chart images");
        
        //Download chart images
        charts[0] = downloadImage(url_chart_3day);
        charts[1] = downloadImage(url_chart_nyspot);
        charts[2] = downloadImage(url_chart_30day);
        charts[3] = downloadImage(url_chart_60day);
        charts[4] = downloadImage(url_chart_6month);
        charts[5] = downloadImage(url_chart_1year);
        charts[6] = downloadImage(url_chart_5year);
        charts[7] = downloadImage(url_chart_10year);
        
        Log.v("kitco","done downloading chart images");
        
        return charts;
    }
    
    //Downloads an image from a specified url
    public Bitmap downloadImage(String url) {
    	
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
    
    //Downloads an html page from a specified url
    public String downloadHTML(String url) {
    	
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
}