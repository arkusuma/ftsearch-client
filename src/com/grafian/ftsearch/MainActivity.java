package com.grafian.ftsearch;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {

	private static final String DONATION_URL = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=2D3BG2TWM5WJA";

	private ListView mResultList;
	private ResultAdapter mResultAdapter;
	private View mFooterView;
	private MySlidingDrawer mDrawer;

	private final Map<String, String> mQuery = new HashMap<String, String>();
	private int mTotal;
	private int mNextIndex;

	private SearchTask mSearchTask;
	private ExpandTask mExpandTask;

	private final ArrayList<SearchEngine.Item> mResult = new ArrayList<SearchEngine.Item>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mDrawer = (MySlidingDrawer) findViewById(R.id.drawer);
		mResultList = (ListView) findViewById(R.id.resultList);
		mResultList.setEmptyView(findViewById(R.id.intro));

		// Work around for ListView footer bug
		mFooterView = LayoutInflater.from(this).inflate(
				R.layout.main_list_footer, null);
		mResultList.addFooterView(mFooterView);
		mResultAdapter = new ResultAdapter(this, 0, mResult);
		mResultList.setAdapter(mResultAdapter);
		updateFooter();

		// Set event handler
		mResultList.setOnItemClickListener(mOnResultListItemClick);

		// Get the intent, verify the action and get the query
		handleIntent(getIntent());

		initFilter();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
		handleIntent(intent);
	}

	private void handleIntent(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			search(query);
		}
	}

	@TargetApi(11)
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getSupportMenuInflater().inflate(R.menu.main, menu);

		if (Build.VERSION.SDK_INT >= 11) {
			// Get the SearchView and set the searchable configuration
			SearchManager mgr = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
			SearchView view = (SearchView) menu.findItem(R.id.menu_search).getActionView();
			view.setSearchableInfo(mgr.getSearchableInfo(getComponentName()));
			view.setQueryHint(getString(R.string.search_hint));
			view.setOnSearchClickListener(new OnClickListener() {
				public void onClick(View v) {
					String query = "";
					if (mQuery.containsKey("q")) {
						query = mQuery.get("q");
					}
					((SearchView) v).setQuery(query, false);
				}
			});
		}
		return true;
	}

	/******************/
	/* Event Handlers */
	/******************/

	final private OnItemClickListener mOnResultListItemClick = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> adapter, View view,
				final int position, long id) {
			if (position < mResult.size()) {
				SearchEngine.Item item = mResult.get(position);
				if (item != null) {
					new DownloadTask().execute(item.getId());
				}
			}
		}
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_search:
			if (Build.VERSION.SDK_INT < 11) {
				String query = null;
				if (mQuery.containsKey("q")) {
					query = mQuery.get("q");
				}
				startSearch(query, true, null, false);
			}
			break;
		case R.id.clear:
			mQuery.clear();
			mResult.clear();
			mResultAdapter.notifyDataSetChanged();
			break;
		case R.id.rate:
			doRate();
			break;
		case R.id.share:
			doShare();
			break;
		case R.id.donate:
			doDonate();
			break;
		case R.id.about:
			doAbout();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		LinearLayout layout = (LinearLayout) findViewById(R.id.adContainer);
		layout.removeAllViews();
		LayoutInflater.from(this).inflate(R.layout.ad, layout);
		super.onConfigurationChanged(newConfig);
	}

	/********************/
	/* Helper functions */
	/********************/

	private static String formatNumber(int number) {
		NumberFormat fmt = NumberFormat.getInstance();
		fmt.setGroupingUsed(true);
		return fmt.format(number);
	}

	@SuppressWarnings("unchecked")
	private void search(String query) {
		if (mSearchTask == null) {
			if (query.length() > 0) {
				mQuery.remove("page");
				mQuery.put("q", query);
				mSearchTask = new SearchTask();
				mSearchTask.execute(mQuery);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void expand() {
		if (mSearchTask == null && mExpandTask == null && mNextIndex <= mTotal) {
			mQuery.put("page", Integer.toString(mNextIndex / 10 + 1));
			mExpandTask = new ExpandTask();
			mExpandTask.execute(mQuery);
		}
	}

	private void updateFooter() {
		View loading = mFooterView.findViewById(R.id.loading);
		if (mTotal > 0) {
			if (mResultList.getFooterViewsCount() == 0) {
				mResultList.addFooterView(mFooterView);
			}
		} else {
			if (mResultList.getFooterViewsCount() != 0) {
				mResultList.removeFooterView(mFooterView);
			}
		}
		if (mNextIndex > mTotal) {
			loading.setVisibility(View.GONE);
		} else {
			loading.setVisibility(View.VISIBLE);
		}
	}

	private void initFilter() {
		ArrayAdapter<CharSequence> adapter;

		final Spinner site = (Spinner) findViewById(R.id.filterSite);
		final Spinner ext = (Spinner) findViewById(R.id.filterExt);
		final Spinner date = (Spinner) findViewById(R.id.filterDate);
		final Spinner sort = (Spinner) findViewById(R.id.filterSort);
		final TextView sizeMin = (TextView) findViewById(R.id.filterSizeFrom);
		final TextView sizeMax = (TextView) findViewById(R.id.filterSizeTo);

		// Set site spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.sites_val,
				android.R.layout.simple_spinner_item);
		adapter.sort(new Comparator<CharSequence>() {
			public int compare(CharSequence lhs, CharSequence rhs) {
				boolean a = "All".equals(lhs);
				boolean b = "All".equals(rhs);
				if (a && !b) {
					return -1;
				} else if (!a && b) {
					return 1;
				}
				return lhs.toString().compareTo(rhs.toString());
			}
		});
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		site.setAdapter(adapter);

		// Set extension spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.exts,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		ext.setAdapter(adapter);

		// Set date spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.dates_val,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		date.setAdapter(adapter);

		// Set sort spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.sorts_val,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sort.setAdapter(adapter);

		OnClickListener onApply = new OnClickListener() {
			public void onClick(View v) {
				int val;
				String[] arr;
				String s;

				val = 0;
				s = (String) site.getSelectedItem();
				String[] vals = getResources()
						.getStringArray(R.array.sites_val);
				arr = getResources().getStringArray(R.array.sites_key);
				for (int i = 0; i < arr.length; i++) {
					if (vals[i].equals(s)) {
						val = i;
						break;
					}
				}
				if (val == 0) {
					mQuery.remove("hosting");
				} else {
					mQuery.put("hosting", arr[val]);
				}

				val = ext.getSelectedItemPosition();
				if (val == 0) {
					mQuery.remove("select");
				} else {
					arr = getResources().getStringArray(R.array.exts);
					mQuery.put("select", arr[val]);
				}

				val = date.getSelectedItemPosition();
				if (val == 0) {
					mQuery.remove("date");
				} else {
					arr = getResources().getStringArray(R.array.dates_key);
					mQuery.put("date", arr[val]);
				}

				val = sort.getSelectedItemPosition();
				if (val == 0) {
					mQuery.remove("sort");
				} else {
					arr = getResources().getStringArray(R.array.sorts_key);
					mQuery.put("sort", arr[val]);
				}

				s = sizeMin.getText().toString();
				if (s.length() == 0) {
					mQuery.remove("sizefrom");
				} else {
					mQuery.put("sizefrom", s);
				}

				s = sizeMax.getText().toString();
				if (s.length() == 0) {
					mQuery.remove("sizeto");
				} else {
					mQuery.put("sizeto", s);
				}

				mDrawer.animateClose();

				if (mQuery.containsKey("q")) {
					search(mQuery.get("q"));
				}
			}
		};

		OnClickListener onReset = new OnClickListener() {
			public void onClick(View v) {
				((Spinner) findViewById(R.id.filterSite)).setSelection(0);
				((Spinner) findViewById(R.id.filterExt)).setSelection(0);
				((Spinner) findViewById(R.id.filterDate)).setSelection(0);
				((Spinner) findViewById(R.id.filterSort)).setSelection(0);
				((TextView) findViewById(R.id.filterSizeFrom)).setText("");
				((TextView) findViewById(R.id.filterSizeTo)).setText("");
			}
		};

		findViewById(R.id.filterApply).setOnClickListener(onApply);
		findViewById(R.id.filterReset).setOnClickListener(onReset);
	}

	/******************/
	/* Helper classes */
	/******************/

	private class SearchTask extends
			AsyncTask<Map<String, String>, Void, ArrayList<SearchEngine.Item>> {

		private ProgressDialog mDialog;
		private SearchEngine mEngine = new SearchEngine();

		@Override
		protected void onPreExecute() {
			String message = getString(R.string.searching);
			mDialog = new ProgressDialog(MainActivity.this);
			mDialog.setCancelable(true);
			mDialog.setCanceledOnTouchOutside(false);
			mDialog.setIndeterminate(true);
			mDialog.setMessage(message);

			mDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					cancel(true);
				}
			});

			mDialog.show();
		}

		@Override
		protected ArrayList<SearchEngine.Item> doInBackground(
				Map<String, String>... params) {
			return mEngine.search(params[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<SearchEngine.Item> result) {
			mDialog.dismiss();

			mTotal = mEngine.getTotal();
			mNextIndex = mEngine.getIndex() + 10;
			updateFooter();

			if (mTotal == 0) {
				Toast.makeText(MainActivity.this, R.string.nothing_found,
						Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(
						MainActivity.this,
						String.format("%d of %s",
								mEngine.getIndex() + result.size() - 1, formatNumber(mTotal)),
						Toast.LENGTH_SHORT).show();
			}

			mResult.clear();
			mResult.addAll(result);
			mResultAdapter.notifyDataSetChanged();
			mResultList.setSelectionAfterHeaderView();

			mSearchTask = null;

			showSupport();
		}

		@Override
		protected void onCancelled() {
			mSearchTask = null;
		}
	}

	private class ExpandTask extends
			AsyncTask<Map<String, String>, Void, ArrayList<SearchEngine.Item>> {

		private SearchEngine mEngine = new SearchEngine();

		@Override
		protected ArrayList<SearchEngine.Item> doInBackground(
				Map<String, String>... params) {
			return mEngine.search(params[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<SearchEngine.Item> result) {
			Toast.makeText(
					MainActivity.this,
					String.format("%d of %s",
							mEngine.getIndex() + result.size() - 1, formatNumber(mTotal)),
					Toast.LENGTH_SHORT).show();

			mTotal = mEngine.getTotal();
			mNextIndex = mEngine.getIndex() + 10;
			updateFooter();

			mResult.addAll(result);
			mResultAdapter.notifyDataSetChanged();

			mExpandTask = null;
		}

		@Override
		protected void onCancelled() {
			mExpandTask = null;
		}
	}

	private class DownloadTask extends
			AsyncTask<String, Void, ArrayList<SearchEngine.Link>> {

		private ProgressDialog mDialog;

		@Override
		protected void onPreExecute() {
			String message = getString(R.string.fetching_download_link);
			mDialog = new ProgressDialog(MainActivity.this);
			mDialog.setCancelable(true);
			mDialog.setCanceledOnTouchOutside(false);
			mDialog.setIndeterminate(true);
			mDialog.setMessage(message);

			mDialog.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					cancel(true);
				}
			});

			mDialog.show();
		}

		@Override
		protected ArrayList<SearchEngine.Link> doInBackground(String... params) {
			return SearchEngine.fetchLink(params[0]);
		}

		@Override
		protected void onPostExecute(final ArrayList<SearchEngine.Link> result) {
			mDialog.dismiss();

			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					Uri uri = Uri.parse(result.get(which).getLink());
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					Intent chooser = Intent.createChooser(intent, getString(R.string.open_with));
					startActivity(chooser);
				}
			};

			LinkAdapter adapter = new LinkAdapter(MainActivity.this,
					R.layout.main_link_list_item, result);

			new AlertDialog.Builder(MainActivity.this)
					.setTitle(R.string.download)
					.setAdapter(adapter, listener)
					.show();
		}
	}

	public class LinkAdapter extends ArrayAdapter<SearchEngine.Link> {

		public LinkAdapter(Context context, int textViewResourceId,
				ArrayList<SearchEngine.Link> items) {
			super(context, textViewResourceId, items);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			SearchEngine.Link link = getItem(position);
			View view = convertView;
			if (convertView == null) {
				LayoutInflater inf = LayoutInflater.from(MainActivity.this);
				view = inf.inflate(R.layout.main_link_list_item, null);
			}
			ImageView iconView = (ImageView) view
					.findViewById(R.id.mainLinkIcon);
			TextView extView = (TextView) view.findViewById(R.id.mainLinkExt);
			TextView title = (TextView) view.findViewById(R.id.mainLinkTitle);
			TextView size = (TextView) view.findViewById(R.id.mainLinkSize);

			Drawable icon = IconManager.getIcon(link.getExt());
			iconView.setImageDrawable(icon);
			title.setText(link.getName());
			size.setText(link.getSize());

			if (icon == IconManager.getDefault()) {
				extView.setVisibility(View.VISIBLE);
				extView.setText(link.getExt().toUpperCase());
			} else {
				extView.setVisibility(View.GONE);
			}

			return view;
		}
	}

	private class ResultAdapter extends ArrayAdapter<SearchEngine.Item> {

		public ResultAdapter(Context context, int textViewResourceId,
				List<SearchEngine.Item> objects) {
			super(context, textViewResourceId, objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			SearchEngine.Item item = getItem(position);
			view = convertView;
			if (view == null) {
				LayoutInflater inf = LayoutInflater.from(MainActivity.this);
				view = inf.inflate(R.layout.main_list_item, null);
			}

			ImageView viewImage = (ImageView) view
					.findViewById(R.id.mainResultIcon);
			TextView viewTitle = (TextView) view
					.findViewById(R.id.mainResultTitle);
			TextView viewDate = (TextView) view
					.findViewById(R.id.mainResultDate);
			TextView viewSize = (TextView) view
					.findViewById(R.id.mainResultSize);
			TextView viewExt = (TextView) view.findViewById(R.id.mainResultExt);
			TextView viewSite = (TextView) view
					.findViewById(R.id.mainResultSite);

			Drawable icon = IconManager.getIcon(item.getExt());
			viewImage.setImageDrawable(icon);
			viewTitle.setText(item.getTitle());
			viewDate.setText(item.getDate());
			viewSite.setText(item.getSite());

			if (item.getParts() == 1) {
				viewSize.setText(item.getSize());
			} else {
				viewSize.setText(String.format("%s - %d parts", item.getSize(),
						item.getParts()));
			}
			if (icon == IconManager.getDefault()) {
				viewExt.setVisibility(View.VISIBLE);
				viewExt.setText(item.getExt().toUpperCase());
			} else {
				viewExt.setVisibility(View.GONE);
			}

			if (position == getCount() - 1) {
				expand();
			}

			return view;
		}

		@Override
		public int getItemViewType(int position) {
			return getItem(position) != null ? 0 : 1;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}
	}

	private int getRunCounter() {
		return getPreferences(MODE_PRIVATE).getInt("runCounter", 0);
	}

	private void setRunCounter(int counter) {
		Editor ed = getPreferences(MODE_PRIVATE).edit();
		ed.putInt("runCounter", counter);
		ed.commit();
	}

	private final DialogInterface.OnClickListener mSupportListener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case 0:
				doRate();
				break;
			case 1:
				doShare();
				break;
			case 2:
				doDonate();
				break;
			}
			dialog.dismiss();
		}
	};

	private void showSupport() {
		int counter = getRunCounter() + 1;
		setRunCounter(counter);
		if (counter % 20 == 0) {
			String items[] = {
					getString(R.string.rate),
					getString(R.string.share),
					getString(R.string.donate) };
			View title = getLayoutInflater().inflate(R.layout.support, null);
			new AlertDialog.Builder(this)
					.setCustomTitle(title)
					.setItems(items, mSupportListener)
					.setNeutralButton(R.string.not_now, mSupportListener)
					.setCancelable(true)
					.show();
		}
	}

	private void doRate() {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
		startActivity(intent);
	}

	private void doShare() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("plain/text");
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject));
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text));
		startActivity(Intent.createChooser(intent, getString(R.string.share)));
	}

	private void doDonate() {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL));
		startActivity(intent);
	}

	private void doAbout() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View about = getLayoutInflater().inflate(R.layout.about, null);
		builder.setCustomTitle(about);
		builder.setPositiveButton(R.string.visit_web, mAboutListener);
		builder.setNeutralButton(R.string.more_apps, mAboutListener);
		builder.setNegativeButton(R.string.okay, mAboutListener);
		builder.setCancelable(true);
		builder.show();
	}

	private final DialogInterface.OnClickListener mAboutListener = new DialogInterface.OnClickListener() {

		public void onClick(DialogInterface dialog, int which) {
			Intent intent;
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://grafian.com"));
				startActivity(intent);
				break;
			case DialogInterface.BUTTON_NEUTRAL:
				intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/search?q=pub:Grafian%20Software%20Crafter"));
				startActivity(intent);
				break;
			}
			dialog.dismiss();
		}
	};
}
