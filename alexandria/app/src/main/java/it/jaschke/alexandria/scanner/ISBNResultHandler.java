package it.jaschke.alexandria.scanner;

import android.app.Activity;

import com.google.zxing.Result;
import com.google.zxing.client.result.ISBNParsedResult;
import com.google.zxing.client.result.ParsedResult;

import it.jaschke.alexandria.R;

public final class ISBNResultHandler extends ResultHandler {
    private static final int[] buttons = {
            R.string.button_product_search,
            R.string.button_book_search,
            R.string.button_search_book_contents,
            R.string.button_custom_product_search
    };

    public ISBNResultHandler(Activity activity, ParsedResult result, Result rawResult) {
        super(activity, result, rawResult);
    }

    @Override
    public int getButtonCount() {
        return hasCustomProductSearch() ? buttons.length : buttons.length - 1;
    }

    @Override
    public int getButtonText(int index) {
        return buttons[index];
    }

    @Override
    public void handleButtonPress(int index) {
        ISBNParsedResult isbnResult = (ISBNParsedResult) getResult();
    }

    @Override
    public int getDisplayTitle() {
        return R.string.result_isbn;
    }
}