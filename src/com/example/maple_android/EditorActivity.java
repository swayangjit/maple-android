package com.example.maple_android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.example.ad_creation.LogoActivity;
import com.example.ad_creation.TextActivity;
import com.example.maple_android.AdCreationManager.Filters;

import com.facebook.Session;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.*;

public class EditorActivity extends Activity implements OnItemSelectedListener {
	/* Global app data */
	MapleApplication mApp;

	private ImageView mPhoto;
	private Spinner mFilterSpinner;
	private boolean mInitializedView;

	/* for tagging a company */
	private String mCompanyTag;
	private AutoCompleteTextView mCompanySuggest;
	private ArrayList<String> mCompanySuggestions;
	private boolean mIsTagSet = false; // whether or not a company tag has been set
	private Session mSession;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_editor);
		
		mInitializedView = true;
		
		//init app data
		mApp = (MapleApplication) getApplication();

		
		mSession = Session.getActiveSession();
		// If user isn't logged in we need to redirect back to LoginActivity
		if (mSession == null) {
			Intent i = new Intent(this, LoginActivity.class);
			startActivity(i);
		}
		
		Bundle extras = getIntent().getExtras();
		
		mFilterSpinner = (Spinner) findViewById(R.id.filters);
		mFilterSpinner.setOnItemSelectedListener(this);

		// Create an ArrayAdapter using the string array and a default spinner
		// layout
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.filters_array,
				android.R.layout.simple_spinner_item);
		// Specify the layout to use when the list of choices appears
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		mFilterSpinner.setAdapter(adapter);
		mFilterSpinner.setSelection(adapter.getPosition(mApp.getAdCreationManager().getCurrentFilter().toString()));

		mPhoto = (ImageView) this.findViewById(R.id.photo);
		mPhoto.setImageBitmap(mApp.getAdCreationManager().getCurrentBitmap());

		/* Set up tools for tagging a company */
		mCompanySuggestions = CompanyList.getCompanyList(this);
		// link list of companies to text field for auto recommendations
		mCompanySuggest = (AutoCompleteTextView) findViewById(R.id.companySuggest);
		mCompanySuggest
				.setAdapter(new ArrayAdapter<String>(this,
						android.R.layout.simple_dropdown_item_1line,
						mCompanySuggestions));

		// check if a company tag has already been set
		String tag = mApp.getCurrentCompany();
		if (tag != null) {
			// if it was previously set, update the display to show this
			mCompanySuggest.setText(tag);
			tagPicture(mCompanySuggest);
		}

	}

	/** Returns to the main activity without saving any changes
	 * 
	 * @param view
	 */
	public void returnToMain(View view) {
		Intent i = new Intent(this, MainActivity.class);
		startActivity(i);
	}

	/** Saves the currently entered text as the company
	 * for the ad. The header text is changed to reflect the
	 * company, and additional editing options are revealed.
	 * 
	 * @param view The tag button that was clicked
	 */
	public void tagPicture(View view) {		
		// save company tag
		mCompanyTag = mCompanySuggest.getText().toString();
		mApp.setCurrentCompany(mCompanyTag);

		// update header
		((TextView) this.findViewById(R.id.header))
				.setText("Customize Your Ad");

		// set company Button
		((Button) findViewById(R.id.changeTag)).setText(mCompanyTag);

		toggleOptions();
	}

	/** This function toggles the visiblity of the tag
	 *  editor and the options to edit the ad. When one
	 *  is shown, the other is hidden. This way, a company
	 *  must be tagged before more advanced edits can be made 
	 */
	private void toggleOptions() {
		// toggle saved boolean
		mIsTagSet = !mIsTagSet;

		int taggingOptions;
		int editOptions;

		// if tagset is false, hide the edit options and show the tagging
		// options
		if (mIsTagSet) {
			taggingOptions = View.INVISIBLE;
			editOptions = View.VISIBLE;
		} else {
			taggingOptions = View.VISIBLE;
			editOptions = View.INVISIBLE;
		}

		// tagging options
		findViewById(R.id.companySuggest).setVisibility(taggingOptions);
		findViewById(R.id.tagButton).setVisibility(taggingOptions);

		// edit options
		mFilterSpinner.setVisibility(editOptions);
		findViewById(R.id.post).setVisibility(editOptions);
		findViewById(R.id.logo).setVisibility(editOptions);
		findViewById(R.id.text).setVisibility(editOptions);
		findViewById(R.id.changeTag).setVisibility(editOptions);
		findViewById(R.id.spinnerText).setVisibility(editOptions);

	}

	/** Called when the user clicks the the company button.
	 * The view is toggled to allow editing of which company
	 * has been tagged
	 * @param view The button that was clicked
	 */
	public void changeTag(View view) {
		toggleOptions();
	}

	/** Launches the LogoActivity, which allows
	 * the user to add a logo to the ad
	 * @param view The button that was clicked
	 */
	public void addLogo(View view) {
		Intent i = new Intent(this, LogoActivity.class);
		startActivity(i);
	}

	/** Launches the TextActivity, which allows
	 * the user to add a text to the ad
	 * @param view The button that was clicked
	 */
	public void addText(View view) {
		Intent i = new Intent(this, TextActivity.class);
		startActivity(i);
	}

	/** Posts the current ad to the server under the 
	 * currently logged in user. On success the user
	 * is returned to the MainActivity. On failure
	 * a toast is raised and the screen is not changed.
	 * @param view The button that was clicked
	 */
	public void postAd(View view) {
		Bitmap currBitmap = mApp.getAdCreationManager().getCurrentBitmap();
		Uri fileUri = mApp.getAdCreationManager().getFileUri();
		Utility.saveBitmap(fileUri, currBitmap, this);
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		currBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
		byte[] photoByteArray = stream.toByteArray();
		
		RequestParams params = new RequestParams();
		params.put("post[image]", new ByteArrayInputStream(photoByteArray), fileUri.getPath());
		params.put("post[title]", "Company: " + mCompanyTag);
		params.put("token", mSession.getAccessToken());

		MapleHttpClient.post("posts", params, new AsyncHttpResponseHandler(){
			@Override
			public void onSuccess(int statusCode, String response) {
				Intent i = new Intent(EditorActivity.this , MainActivity.class);
				i.putExtra("successMessage",
						"Posted picture successfully! Go to the website to check it out.");
				startActivity(i);
			}
			
			@Override
		    public void onFailure(Throwable error, String response) {
				Toast.makeText(getApplicationContext(), "Sugar! We ran into a problem!", Toast.LENGTH_LONG).show();
		    }
		});
		
		
	}

	/** Changes the currently applied filter when an item from 
	 * the filter spinner is selected.
	 */
	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos,
			long id) {

		if (mInitializedView) {
			mInitializedView = false;
			return;
		}

		String strFilter = mFilterSpinner.getSelectedItem().toString();
		
		mApp.getAdCreationManager().addFilter(strFilter);
		mPhoto.setImageBitmap(mApp.getAdCreationManager().getCurrentBitmap());


	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		// TODO Auto-generated method stub

	}

}
