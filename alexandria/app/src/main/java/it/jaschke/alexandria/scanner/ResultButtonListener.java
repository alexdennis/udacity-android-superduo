package it.jaschke.alexandria.scanner;

import android.view.View;

/**
 * Handles the result of barcode decoding in the context of the Android platform, by dispatching the
 * proper intents to open other activities like GMail, Maps, etc.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ResultButtonListener implements View.OnClickListener {
    private final ResultHandler resultHandler;
    private final int index;

    public ResultButtonListener(ResultHandler resultHandler, int index) {
        this.resultHandler = resultHandler;
        this.index = index;
    }

    @Override
    public void onClick(View view) {
        resultHandler.handleButtonPress(index);
    }
}
