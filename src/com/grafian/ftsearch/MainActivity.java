package com.grafian.ftsearch;

import java.util.ArrayList;
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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity {

	private ListView mResultList;
	private ResultAdapter mResultAdapter;
	private View mFooterView;

	private final Map<String, String> mQuery = new HashMap<String, String>();
	private int mTotal;
	private int mNextIndex;

	private SearchTask mSearchTask;
	private ExpandTask mExpandTask;

	private final ArrayList<SearchEngine.Item> mResult = new ArrayList<SearchEngine.Item>();
	private final SparseArray<View> mAds = new SparseArray<View>();
	private AdLoader mAdLoader;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mResultList = (ListView) findViewById(R.id.mainResultList);
		mAdLoader = new AdLoader(this);

		// Work around for ListView footer bug
		mFooterView = LayoutInflater.from(this).inflate(
				R.layout.main_list_footer, null);
		mResultList.addFooterView(mFooterView);
		mResultAdapter = new ResultAdapter(this, 0, mResult);
		mResultList.setAdapter(mResultAdapter);
		mResultList.removeFooterView(mFooterView);

		// Set event handler
		mResultList.setOnItemClickListener(mOnResultListItemClick);

		// Get the intent, verify the action and get the query
		handleIntent(getIntent());
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
		getSupportMenuInflater().inflate(R.menu.main, menu);

		if (Build.VERSION.SDK_INT >= 11) {
			// Get the SearchView and set the searchable configuration
			SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
			SearchView searchView = (SearchView) menu
					.findItem(R.id.menu_search).getActionView();
			searchView.setSearchableInfo(searchManager
					.getSearchableInfo(getComponentName()));
			searchView.setQueryHint(getString(R.string.search_hint));
			searchView.setOnSearchClickListener(new OnClickListener() {
				public void onClick(View v) {
					String query = "";
					if (mQuery.containsKey("q")) {
						query = mQuery.get("q");
					}
					((SearchView) v).setQuery(query, false);
				}
			});
		}

		return super.onCreateOptionsMenu(menu);
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
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_search:
			if (Build.VERSION.SDK_INT < 11) {
				String query = null;
				if (mQuery.containsKey("q")) {
					query = mQuery.get("q");
				}
				startSearch(query, true, null, false);
				return true;
			}
			break;
		case R.id.menu_filter:
			showFilter();
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	/********************/
	/* Helper functions */
	/********************/

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
		if (mNextIndex > mTotal) {
			if (mResultList.getFooterViewsCount() > 0) {
				mResultList.removeFooterView(mFooterView);
			}
		} else {
			if (mResultList.getFooterViewsCount() == 0) {
				mResultList.addFooterView(mFooterView);
			}
		}
	}

	private int findIndex(String s, int id) {
		String[] array = getResources().getStringArray(id);
		for (int i = 0; i < array.length; i++) {
			if (s.equals(array[i])) {
				return i;
			}
		}
		return 0;
	}

	private void showFilter() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.main_filter, null);
		ArrayAdapter<CharSequence> adapter;

		final Spinner site = (Spinner) view.findViewById(R.id.filterSite);
		final Spinner ext = (Spinner) view.findViewById(R.id.filterExt);
		final Spinner date = (Spinner) view.findViewById(R.id.filterDate);
		final Spinner sort = (Spinner) view.findViewById(R.id.filterSort);
		final TextView sizeMin = (TextView) view
				.findViewById(R.id.filterSizeFrom);
		final TextView sizeMax = (TextView) view
				.findViewById(R.id.filterSizeTo);

		// Set site spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.sites_val,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		site.setAdapter(adapter);
		if (mQuery.containsKey("hosting")) {
			String val = mQuery.get("hosting");
			site.setSelection(findIndex(val, R.array.sites_key));
		}

		// Set extension spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.exts,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		ext.setAdapter(adapter);
		if (mQuery.containsKey("select")) {
			String val = mQuery.get("select");
			ext.setSelection(findIndex(val, R.array.exts));
		}

		// Set date spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.dates_val,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		date.setAdapter(adapter);
		if (mQuery.containsKey("date")) {
			String val = mQuery.get("date");
			date.setSelection(findIndex(val, R.array.dates_key));
		}

		// Set sort spinner items
		adapter = ArrayAdapter.createFromResource(this, R.array.sorts_val,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		sort.setAdapter(adapter);
		if (mQuery.containsKey("sort")) {
			String val = mQuery.get("sort");
			sort.setSelection(findIndex(val, R.array.sorts_key));
		}

		if (mQuery.containsKey("sizefrom")) {
			sizeMin.setText(mQuery.get("sizefrom"));
		}

		if (mQuery.containsKey("sizeto")) {
			sizeMax.setText(mQuery.get("sizeto"));
		}

		builder.setView(view);
		builder.setTitle(R.string.filter_title);

		final AlertDialog dialog = builder.create();

		OnClickListener onApply = new OnClickListener() {
			public void onClick(View v) {
				int val;
				String[] arr;
				String s;

				val = site.getSelectedItemPosition();
				if (val == 0) {
					mQuery.remove("hosting");
				} else {
					arr = getResources().getStringArray(R.array.sites_key);
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

				dialog.dismiss();

				if (mQuery.containsKey("q")) {
					search(mQuery.get("q"));
				}
			}
		};

		OnClickListener onClear = new OnClickListener() {
			public void onClick(View v) {
				((Spinner) dialog.findViewById(R.id.filterSite))
						.setSelection(0);
				((Spinner) dialog.findViewById(R.id.filterExt)).setSelection(0);
				((Spinner) dialog.findViewById(R.id.filterDate))
						.setSelection(0);
				((Spinner) dialog.findViewById(R.id.filterSort))
						.setSelection(0);
				((TextView) dialog.findViewById(R.id.filterSizeFrom))
						.setText("");
				((TextView) dialog.findViewById(R.id.filterSizeTo)).setText("");
			}
		};

		OnClickListener onCancel = new OnClickListener() {
			public void onClick(View v) {
				dialog.dismiss();
			}
		};

		view.findViewById(R.id.filterApply).setOnClickListener(onApply);
		view.findViewById(R.id.filterClear).setOnClickListener(onClear);
		view.findViewById(R.id.filterCancel).setOnClickListener(onCancel);

		dialog.show();
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
						String.format("%d of %d",
								mEngine.getIndex() + result.size() - 1, mTotal),
						Toast.LENGTH_SHORT).show();
				result.add((int) (Math.random() * (result.size() + 1)), null);
			}

			mAds.clear();
			mResult.clear();
			mResult.addAll(result);
			mResultAdapter.notifyDataSetChanged();
			mResultList.setSelectionAfterHeaderView();

			mSearchTask = null;
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
					String.format("%d of %d",
							mEngine.getIndex() + result.size() - 1, mTotal),
					Toast.LENGTH_SHORT).show();

			if ((mNextIndex / 10) % 2 == 0) {
				result.add((int) (Math.random() * (result.size() + 1)), null);
			}

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
					startActivity(intent);
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder(
					MainActivity.this);
			builder.setTitle(R.string.download);
			builder.setAdapter(new LinkAdapter(MainActivity.this,
					R.layout.main_link_list_item, result), listener);
			builder.create().show();
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
			if (item == null) {
				if (mAds.get(position) != null) {
					view = mAds.get(position);
				} else {
					view = mAdLoader.nextAd();
					mAds.put(position, view);
				}
			} else {
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
				TextView viewExt = (TextView) view
						.findViewById(R.id.mainResultExt);
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
					viewSize.setText(String.format("%s - %d parts",
							item.getSize(), item.getParts()));
				}
				if (icon == IconManager.getDefault()) {
					viewExt.setVisibility(View.VISIBLE);
					viewExt.setText(item.getExt().toUpperCase());
				} else {
					viewExt.setVisibility(View.GONE);
				}
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
}