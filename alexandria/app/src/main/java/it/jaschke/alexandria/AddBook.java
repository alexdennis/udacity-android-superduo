package it.jaschke.alexandria;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
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

    @Bind(R.id.ean) EditText mEanView;
    @Bind(R.id.message) TextView mMessageView;
    @Bind(R.id.buttons) View mButtonsView;
    @Bind(R.id.bookTitle) TextView mBookTitleView;
    @Bind(R.id.bookSubTitle) TextView mBookSubTitleView;
    @Bind(R.id.authors) TextView mAuthorsView;
    @Bind(R.id.bookCover) ImageView mBookCoverView;
    @Bind(R.id.categories) TextView mCategoriesView;

    private boolean mFoundBook = false;

    public AddBook() {
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

        View view = inflater.inflate(R.layout.fragment_add_book, container, false);
        ButterKnife.bind(this, view);

        if (savedInstanceState != null) {
            mEanView.setText(savedInstanceState.getString(EAN_CONTENT));
            mEanView.setHint("");
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @OnClick(R.id.scan_button)
    public void scanBarcode() {
        Intent intent = new Intent(getActivity(), CaptureActivity.class);
        startActivityForResult(intent, REQUEST_CODE);
    }

    @OnClick(R.id.save_button)
    public void saveBook() {
        mEanView.setText("");
        clearFields();
    }

    @OnClick(R.id.delete_button)
    public void deleteBook() {
        Intent bookIntent = new Intent(getActivity(), BookService.class);
        bookIntent.putExtra(BookService.EAN, mEanView.getText().toString());
        bookIntent.setAction(BookService.DELETE_BOOK);
        getActivity().startService(bookIntent);
        clearFields();
        mEanView.setText("");
    }

    @OnTextChanged(R.id.ean)
    public void searchBook(CharSequence s) {
        Context context = getActivity();
        String ean = Utility.fixEanforISBN13(getActivity(), s.toString());
        if (ean.length() < 13) {
            return;
        }

        mFoundBook = false;
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
            mFoundBook = false;
            return;
        }

        mFoundBook = true;
        clearMessage();

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        mBookTitleView.setText(bookTitle);

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        mBookSubTitleView.setText(bookSubTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        // Added check to ensure we have author text before trying a split.
        if (authors != null) {
            String[] authorsArr = authors.split(",");
            mAuthorsView.setLines(authorsArr.length);
            mAuthorsView.setText(authors.replace(",", "\n"));
        }
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if (Patterns.WEB_URL.matcher(imgUrl).matches()) {
            new DownloadImage(mBookCoverView).execute(imgUrl);
            mBookCoverView.setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        mCategoriesView.setText(categories);

        mButtonsView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields() {
        mBookTitleView.setText("");
        mBookSubTitleView.setText("");
        mAuthorsView.setText("");
        mCategoriesView.setText("");
        mBookCoverView.setVisibility(View.INVISIBLE);
        mButtonsView.setVisibility(View.INVISIBLE);
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

    private void displayMessage() {
        if (!mFoundBook) {
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

            clearFields();
            mMessageView.setVisibility(View.VISIBLE);
            mMessageView.setText(message);
        }
    }

    private void clearMessage() {
        mMessageView.setText("");
        mMessageView.setVisibility(View.GONE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_book_service_status_key))) {
            displayMessage();
        }
    }
}
