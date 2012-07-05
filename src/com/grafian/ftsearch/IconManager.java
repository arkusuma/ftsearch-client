package com.grafian.ftsearch;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

import android.graphics.drawable.Drawable;

public class IconManager {
	static private HashMap<String, SoftReference<Drawable>> mIcons = new HashMap<String, SoftReference<Drawable>>();
	static private Drawable mDefault;

	static public Drawable getIcon(String ext) {
		if (mIcons.containsKey(ext)) {
			Drawable icon = mIcons.get(ext).get();
			if (icon != null) {
				return icon;
			}
			mIcons.remove(ext);
		}

		try {
			Drawable icon = Drawable.createFromStream(App.getContext()
					.getAssets().open("icons/" + ext + ".png"), null);
			mIcons.put(ext, new SoftReference<Drawable>(icon));
			return icon;
		} catch (IOException e) {
			return getDefault();
		}
	}

	static public Drawable getDefault() {
		if (mDefault == null) {
			try {
				mDefault = Drawable.createFromStream(App.getContext().getAssets()
						.open("icons/default.png"), null);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return mDefault;
	}
}
