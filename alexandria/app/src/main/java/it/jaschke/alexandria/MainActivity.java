package it.jaschke.alexandria;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import butterknife.Bind;
import butterknife.ButterKnife;
import it.jaschke.alexandria.api.Callback;


public class MainActivity extends ActionBarActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks, Callback {

    private static final String TAG = MainActivity.class.getSimpleName();

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment navigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence title;

    @Nullable @Bind(R.id.right_container) View rightContainer;

    Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isTablet()) {
            setContentView(R.layout.activity_main_tablet);
        } else {
            setContentView(R.layout.activity_main);
        }

        ButterKnife.bind(this);

        navigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        title = getTitle();

        // Set up the drawer.
        navigationDrawerFragment.setUp(R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        if (savedInstanceState != null) {
            // If restoring state we may need to update right container visibility
            // if we had an orientation change.
            updateRightContainerVisibility(null);
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment nextFragment;

        switch (position) {
            default:
            case 0:
                nextFragment = new ListOfBooks();
                break;
            case 1:
                nextFragment = new AddBook();
                break;
            case 2:
                nextFragment = new About();
                break;

        }

        // Update right container visiblity depending on the upcoming
        // fragment transition.
        updateRightContainerVisibility(nextFragment);

        fragmentManager.beginTransaction()
                .replace(R.id.container, nextFragment)
                .addToBackStack((String) title)
                .commit();
    }

    /**
     * This method updates the right container visiblity depending on which
     * section we are viewing in tablet mode. It also takes care of orientation
     * changes. It does not preserve the detail view and always loads the master
     * list of books view when transitioning between landscape master/detail to portrait.
     *
     * @param leftFrag
     */
    private void updateRightContainerVisibility(Fragment leftFrag) {
        Log.d(TAG, "updateRightContainerVisibility() called.");

        clearMenu();

        // If we are in tablet mode then hide the right container for
        // AddBook and About fragments since we could use the full space
        if (rightContainer != null) {

            FragmentManager fm = getSupportFragmentManager();
            if (leftFrag == null) {
                leftFrag = fm.findFragmentById(R.id.container);
            }

            if (leftFrag instanceof About || leftFrag instanceof AddBook) {
                rightContainer.setVisibility(View.GONE);
                clearRightContainer(fm);

            } else if (leftFrag instanceof BookDetail) {
                // This means we just transitioned to master detail orientation
                // when book detail was displayed so replace it with with list of books.
                fm.beginTransaction()
                        .replace(R.id.container, new ListOfBooks())
                        .commit();
                clearRightContainer(fm);

                rightContainer.setVisibility(View.VISIBLE);

            } else {

                // We are probably display list of books, so make right side available to
                // display book details.

                rightContainer.setVisibility(View.VISIBLE);
            }
        } else {
            clearRightContainer(getSupportFragmentManager());
        }
    }

    private void clearRightContainer(FragmentManager fm) {
        // Remove fragment in right container if there is one.
        Fragment rightFrag = fm.findFragmentById(R.id.right_container);
        if (rightFrag != null) {
            Log.d(TAG, "Found right Fragment, removing..");
            fm.beginTransaction()
                    .remove(rightFrag)
                    .commit();
        }
    }

    public void setTitle(int titleId) {
        title = getString(titleId);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(title);
    }

    /**
     * Clear menu, useful to remove detail share menu option
     */
    private void clearMenu() {
        Log.d(TAG, "clearMenu() called");
        if (mMenu != null) {
            mMenu.clear();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        if (!navigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(String ean) {
        Bundle args = new Bundle();
        args.putString(BookDetail.EAN_KEY, ean);

        BookDetail fragment = new BookDetail();
        fragment.setArguments(args);

        int id = R.id.container;
        if (findViewById(R.id.right_container) != null) {
            id = R.id.right_container;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(id, fragment)
                .addToBackStack(getString(R.string.book_detail))
                .commit();

    }

    public void goBack(View view) {
        getSupportFragmentManager().popBackStack();
    }

    private boolean isTablet() {
        return (getApplicationContext().getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() < 2) {
            finish();
        }
        super.onBackPressed();

        // Update right container visibility based on fragment we are going back to
        updateRightContainerVisibility(null);
    }
}