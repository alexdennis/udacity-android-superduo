package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.MultiFormatReader;

import java.util.List;

import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.scanner.CaptureActivity;
import it.jaschke.alexandria.scanner.Intents;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = AddBook.class.toString();
    public static final int REQUEST_CODE = 0x0000c0de; // Only use bottom 16 bits

    private final int LOADER_ID = 1;
    private final String EAN_CONTENT = "eanContent";

    private EditText mEanView;
    private View mRootView;
    private boolean mShowSnackbar = false;
    private Snackbar mSnackbar;

    private final MultiFormatReader multiFormatReader;

    public AddBook() {
        multiFormatReader = new MultiFormatReader();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mEanView != null) {
            outState.putString(EAN_CONTENT, mEanView.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mRootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        mEanView = (EditText) mRootView.findViewById(R.id.ean);

        mEanView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                String ean = Utility.fixEanforISBN13(getActivity(), s.toString());
                if (ean.length() < 13) {
                    return;
                }
                searchBookByISBN(ean);

            }
        });

        final Fragment f = this;
        mRootView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This is the callback method that the system will invoke when your button is
                // clicked. You might do this by launching another app or by including the
                //functionality directly in this app.
                // Hint: Use a Try/Catch block to handle the Intent dispatch gracefully, if you
                // are using an external app.
                //when you're done, remove the toast below.

                Intent intent = new Intent(getActivity(), CaptureActivity.class);
//                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, REQUEST_CODE);

//                Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
//                intentScan.addCategory(Intent.CATEGORY_DEFAULT);
//                if (canSystemHandleIntent(intentScan)) {
//                    f.startActivityForResult(intentScan, REQUEST_CODE);
//                } else {
//                    Snackbar.make(mRootView, R.string.error_no_scanner_app, Snackbar.LENGTH_LONG)
//                            .show();
//                }

            }
        });

        mRootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEanView.setText("");
                clearFields();
            }
        });

        mRootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, mEanView.getText().toString());
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                clearFields();
                mEanView.setText("");
            }
        });

        if (savedInstanceState != null) {
            mEanView.setText(savedInstanceState.getString(EAN_CONTENT));
            mEanView.setHint("");
        }

        return mRootView;
    }

    private boolean canSystemHandleIntent(Intent intent) {
        PackageManager pm = getActivity().getPackageManager();
        List<ResolveInfo> availableApps = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return (availableApps != null && availableApps.size() > 0);
    }

    private void searchBookByISBN(String ean) {
        Context context = getActivity();

        mShowSnackbar = false;
        Utility.resetBookServiceStatus(context);

        Intent bookIntent = new Intent(context, BookService.class);
        bookIntent.putExtra(BookService.EAN, ean);
        bookIntent.setAction(BookService.FETCH_BOOK);
        context.startService(bookIntent);
        AddBook.this.restartLoader();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                mEanView.setText(data.getStringExtra(Intents.Scan.RESULT));
            }
        }
    }

    private void restartLoader() {
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mEanView == null || mEanView.getText().length() == 0) {
            return null;
        }
        String eanStr = Utility.fixEanforISBN13(getActivity(), mEanView.getText().toString());
        long eanLong = Utility.convertEanStringToLong(eanStr);
        if (eanLong == -1) {
            return null;
        }

        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(eanLong),
                null,
                null,
                null,
                null
        );

    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            mShowSnackbar = true;
            return;
        }

        mShowSnackbar = false;
        hideSnackbar();

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        ((TextView) mRootView.findViewById(R.id.bookTitle)).setText(bookTitle);

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        ((TextView) mRootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        String[] authorsArr = authors.split(",");
        ((TextView) mRootView.findViewById(R.id.authors)).setLines(authorsArr.length);
        ((TextView) mRootView.findViewById(R.id.authors)).setText(authors.replace(",", "\n"));
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if (Patterns.WEB_URL.matcher(imgUrl).matches()) {
            new DownloadImage((ImageView) mRootView.findViewById(R.id.bookCover)).execute(imgUrl);
            mRootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        ((TextView) mRootView.findViewById(R.id.categories)).setText(categories);

        mRootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        mRootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields() {
        ((TextView) mRootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) mRootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) mRootView.findViewById(R.id.authors)).setText("");
        ((TextView) mRootView.findViewById(R.id.categories)).setText("");
        mRootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        mRootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        activity.setTitle(R.string.scan);
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    private void showSnackbar() {
        if (mShowSnackbar) {
            int message = R.string.error_failed_scan;
            if (!Utility.isConnected(getActivity())) {
                message = R.string.error_no_network;
            } else {
                switch (Utility.getBookServiceStatus(getActivity())) {
                    case BookService.BOOK_SERVICE_STATUS_SERVER_INVALID:
                        message = R.string.error_server_invalid;
                        break;
                    case BookService.BOOK_SERVICE_STATUS_SERVER_DOWN:
                        message = R.string.error_server_down;
                        break;
                    case BookService.BOOK_SERVICE_STATUS_INVALID:
                        message = R.string.not_found;
                        break;
                }
            }
            mSnackbar = Snackbar.make(mRootView, message, Snackbar.LENGTH_LONG);
            mSnackbar.show();
        }
    }

    private void hideSnackbar() {
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_book_service_status_key))) {
            showSnackbar();
        }
    }
}
