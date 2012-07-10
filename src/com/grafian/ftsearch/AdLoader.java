package com.grafian.ftsearch;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

public class AdLoader {
	private Context context;
	private View cache;

	public AdLoader(Context context) {
		this.context = context;
		preload();
	}

	private void preload() {
		cache = LayoutInflater.from(context).inflate(R.layout.main_ad, null);
	}

	public View nextAd() {
		View view = cache;
		preload();
		return view;
	}
}
