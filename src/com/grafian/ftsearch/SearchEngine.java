package com.grafian.ftsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;

public class SearchEngine {

	final private static String API_BASE = "http://ftsearchapp.appspot.com/api/";

	private int mIndex;
	private int mTotal;

	private static String inputStreamToString(InputStream in)
			throws IOException {
		Reader reader = new InputStreamReader(in);
		StringBuilder sb = new StringBuilder();
		char[] buf = new char[8192];
		int len;
		while ((len = reader.read(buf)) != -1) {
			sb.append(buf, 0, len);
		}
		return sb.toString();
	}

	public ArrayList<Item> search(Map<String, String> query) {
		ArrayList<Item> items = new ArrayList<Item>(10);
		try {
			URL url = new URL(buildSearchUrl(query));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			InputStream in = conn.getInputStream();
			String json = inputStreamToString(in);
			in.close();
			conn.disconnect();

			JSONTokener tokener = new JSONTokener(json);
			JSONObject root = (JSONObject) tokener.nextValue();
			mTotal = root.getInt("total");
			mIndex = 0;
			if (mTotal > 0) {
				mIndex = root.getInt("index");
				JSONArray array = root.getJSONArray("items");
				for (int i = 0; i < array.length(); i++) {
					JSONObject item = array.getJSONObject(i);
					Item r = new Item(item.getString("id"),
							item.getString("title"), item.getString("size"),
							item.getString("date"), item.getString("site"),
							item.getString("ext"), item.getInt("parts"));
					items.add(r);
				}
			}
		} catch (Exception e) {
			Log.d("ftsearch", e.toString());
		}
		return items;
	}

	public static ArrayList<Link> fetchLink(String id) {
		ArrayList<Link> items = new ArrayList<Link>();
		try {
			URL url = new URL(API_BASE + "link/" + id);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			InputStream in = conn.getInputStream();
			String json = inputStreamToString(in);
			in.close();
			conn.disconnect();

			JSONTokener tokener = new JSONTokener(json);
			JSONArray array = (JSONArray) tokener.nextValue();
			for (int i = 0; i < array.length(); i++) {
				JSONObject item = array.getJSONObject(i);
				Link link = new Link(item.getString("name"),
						item.getString("size"), item.getString("link"));
				items.add(link);
			}
		} catch (Exception e) {
			Log.d("ftsearch", e.toString());
		}
		return items;
	}

	public String buildSearchUrl(Map<String, String> query) {
		StringBuilder sb = new StringBuilder();
		sb.append(API_BASE);
		sb.append("search?");
		boolean first = true;
		for (String key : query.keySet()) {
			if (first) {
				first = false;
			} else {
				sb.append('&');
			}
			try {
				sb.append(URLEncoder.encode(key, "UTF-8"));
				sb.append('=');
				sb.append(URLEncoder.encode(query.get(key), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

	public int getIndex() {
		return mIndex;
	}

	public int getTotal() {
		return mTotal;
	}

	public static class Item {
		private String mId;
		private String mTitle;
		private String mSize;
		private String mDate;
		private String mSite;
		private String mExt;
		private int mParts;

		public Item(String id, String title, String size, String date,
				String site, String ext, int parts) {
			mId = id;
			mTitle = title;
			mSize = size;
			mDate = date;
			mSite = site;
			mExt = ext;
			mParts = parts;
		}

		public String getId() {
			return mId;
		}

		public String getTitle() {
			return mTitle;
		}

		public String getSize() {
			return mSize;
		}

		public String getDate() {
			return mDate;
		}

		public String getSite() {
			return mSite;
		}

		public String getExt() {
			return mExt;
		}

		public int getParts() {
			return mParts;
		}
	}

	public static class Link {
		private String mName;
		private String mSize;
		private String mLink;

		public Link(String name, String size, String link) {
			mName = name;
			mSize = size;
			mLink = link;
		}

		public String getName() {
			return mName;
		}

		public String getSize() {
			return mSize;
		}

		public String getLink() {
			return mLink;
		}
	}
}
