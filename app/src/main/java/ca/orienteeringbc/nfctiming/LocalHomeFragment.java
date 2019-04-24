package ca.orienteeringbc.nfctiming;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;

public class LocalHomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    private static final String TAG = "LocalHomeFragment";

    /* For creating event and category IDs that won't conflict with WJR events for ~100 years */
    public static final String NEXT_LOCAL_EVENT_NUMBER_KEY = "next_local_event_#_key";
    public static final String NEXT_LOCAL_EVENT_CATEGORY_KEY = "next_local_event_cat_key";

    private WjrDatabase database;
    private SharedPreferences sharedPrefs;

    // The array adapter for the spinner
    ArrayAdapter<WjrEvent> eventAdapter;
    Spinner eventSpinner;

    // The event id
    private int eventId;

    // The main view
    private View view;

    // Callback for Activity
    HomeFragment.OnEventIdChangeListener mCallback;

    // Interface to notify Activity of change in eventId
    public interface OnEventIdChangeListener {
        void onEventIdChange(int wjrId);
    }

    @TargetApi(23)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof Activity)
            finishCallback((Activity) context);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Google changed onAttach in Fragments...
        // See https://stackoverflow.com/questions/32083053/android-fragment-onattach-deprecated#32088447
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finishCallback(activity);
        }
    }

    private void finishCallback(Activity activity) {
        try {
            mCallback = (HomeFragment.OnEventIdChangeListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnEventIdChangeListener");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_local_home, container, false);
        Button button;

        eventId = getArguments().getInt(MainActivity.SELECTED_EVENT_KEY);

        if (eventId < 0) {
            button = view.findViewById(R.id.add_category);
            button.setEnabled(false);
        }

        // Initialize DB
        database = WjrDatabase.getInstance(getActivity());

        // Initialize sharedPrefs
        sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Setup version info
        try {
            PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            String version = pInfo.versionName;
            TextView versionView = view.findViewById(R.id.version);
            versionView.setText(getString(R.string.version_string, version));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Button listeners
        button = view.findViewById(R.id.add_category);
        button.setOnClickListener(this);
        button = view.findViewById(R.id.add_new_event);
        button.setOnClickListener(this);
        button = view.findViewById(R.id.import_from_xml);
        button.setOnClickListener(this);

        eventSpinner = view.findViewById(R.id.local_event_spinner);
        eventSpinner.setOnItemSelectedListener(this);
        eventAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);
        eventSpinner.setAdapter(eventAdapter);

        // Populate spinner
        new PopulateEventSpinnerTask(database, getActivity(), eventId).execute();

        return view;
    }

    /**
     * The onClick listener for the buttons
     * @param view Which view was clicked
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.add_new_event: {
                addNewEvent();
                return;
            }

            case R.id.add_category: {
                addNewCategory();
                return;
            }

            case R.id.import_from_xml: {
                // TODO
            }
        }
    }

    /**
     * Callback for on item selected from spinner
     * @param parent The Spinner or other view that this adapter is for
     * @param view Unknown
     * @param position Which is selected
     * @param id Unknown
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() != R.id.local_event_spinner) {
            Log.e("NFCTiming","Unknown id of OnItemClick");
            return;
        }

        Spinner spinner = (Spinner) parent;
        WjrEvent event = (WjrEvent) spinner.getItemAtPosition(position);

        eventId = event.wjrId;

        Log.d(TAG, "Current eventId is " + eventId);

        // Callback to parent activity
        if (mCallback != null)
            mCallback.onEventIdChange(eventId);
    }

    /**
     * Boilerplate code to satisfy interface, should never happen
     * @param parent Adapterview that nothing is now selected on
     */
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Nothing selected -> do nothing
    }

    /**
     * Helper function to create a new event, calls AddEventTask to add
     * new event to DB
     *
     * Events will always be local (ie have wjrClubId of -1)
     */
    private void addNewEvent() {
        final EditText editText = new EditText(getActivity());

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.create_new_event)
                .setView(editText)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        WjrEvent newEvent;
                        Button button;

                        eventId = sharedPrefs.getInt(NEXT_LOCAL_EVENT_NUMBER_KEY, Integer.MAX_VALUE);
                        editor.putInt(NEXT_LOCAL_EVENT_NUMBER_KEY, eventId - 1);
                        editor.apply();

                        newEvent = new WjrEvent(eventId, -1, editText.getText().toString());
                        new AddEventTask(database).execute(newEvent);

                        // Add event to spinner
                        eventAdapter.add(newEvent);
                        eventSpinner.setSelection(eventAdapter.getCount() - 1);

                        button = view.findViewById(R.id.add_category);
                        button.setEnabled(true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Helper function to create a new category and save it to DB with help of AsyncTask
     */
    private void addNewCategory() {
        final EditText editText = new EditText(getActivity());

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.create_category)
                .setView(editText)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        WjrCategory newCategory;
                        int categoryId;

                        categoryId = sharedPrefs.getInt(NEXT_LOCAL_EVENT_CATEGORY_KEY, Integer.MAX_VALUE);
                        editor.putInt(NEXT_LOCAL_EVENT_CATEGORY_KEY, categoryId - 1);
                        editor.apply();

                        newCategory = new WjrCategory(categoryId, eventId, editText.getText().toString());
                        new AddCategoryTask(database).execute(newCategory);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Async task which adds a new event to the DB
     */
    private static class AddEventTask extends AsyncTask<WjrEvent, Void, Void> {
        WjrDatabase database;

        AddEventTask(WjrDatabase database) {
            this.database = database;
        }

        @Override
        public Void doInBackground(WjrEvent... event) {
            database.daoAccess().addEvent(event[0]);
            return null;
        }
    }

    /**
     * AsyncTask which adds a new category to an event
     */
    private static class AddCategoryTask extends AsyncTask<WjrCategory, Void, Void> {
        WjrDatabase database;

        AddCategoryTask(WjrDatabase database) {
            this.database = database;
        }

        @Override
        public Void doInBackground(WjrCategory... category) {
            database.daoAccess().addCategory(category[0]);
            return null;
        }
    }

    /**
     * AsyncTask to populate the Event spinner
     */
    private static class PopulateEventSpinnerTask extends AsyncTask<Void, Void, List<WjrEvent>> {
        final WeakReference<Activity> weakActivity;
        WjrDatabase database;
        int eventId;

        PopulateEventSpinnerTask(WjrDatabase database, Activity activity, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        public List<WjrEvent> doInBackground(Void... voids) {
            /* ClubId of -1 is local clubs */
            return database.daoAccess().getEventsByClub(-1);
        }

        @Override
        protected void onPostExecute(List<WjrEvent> events) {
            // Re-acquire a strong reference to the activity, and verify
            // that it still exists and is active.
            Activity activity = weakActivity.get();
            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()) {
                // activity is no longer valid, don't do anything!
                return;
            }

            // The activity is still valid
            MainActivity mainActivity = (MainActivity) weakActivity.get();
            if (mainActivity.currentFrame == MainActivity.FrameType.HomeFrag &&
                    mainActivity.currentMode == MainActivity.EventType.LocalEvent) {
                LocalHomeFragment fragment = (LocalHomeFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                // Setup spinners
                fragment.eventAdapter.addAll(events);

                for (int i = 0; i < events.size(); i++) {
                    if (events.get(i).wjrId == eventId) {
                        fragment.eventSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }
}
