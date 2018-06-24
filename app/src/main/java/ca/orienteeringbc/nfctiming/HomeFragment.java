package ca.orienteeringbc.nfctiming;

import android.annotation.TargetApi;
import android.app.Activity;
import android.arch.persistence.room.Room;
import android.content.Context;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static ca.orienteeringbc.nfctiming.MainActivity.SELECTED_CLUB_KEY;
import static ca.orienteeringbc.nfctiming.MainActivity.SELECTED_EVENT_KEY;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomeFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "HomeFragment";

    // A list of all clubs in WJR database
    private Spinner clubSpinner;
    private List<WjrClub> mClubList = new ArrayList<>();
    private ArrayAdapter<WjrClub> clubAdapter;

    // All events for the club
    private Spinner eventSpinner;
    private List<WjrEvent> mEventList = new ArrayList<>();
    private ArrayAdapter<WjrEvent> eventAdapter;

    // Buttons
    private Button getCompetitors;
    private Button getEvents;

    // The WJR id of the chosen club and event
    private int clubId = -1;
    private int eventId = -1;

    // SharedPrefs
    private SharedPreferences sharedPref;

    // Initialize database
    private WjrDatabase database;

    // Callback for Activity
    OnEventIdChangeListener mCallback;

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
            mCallback = (OnEventIdChangeListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnEventIdChangeListener");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize sharedPrefs
        sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize DB
        database =  Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addMigrations(WjrDatabase.MIGRATION_1_2, WjrDatabase.MIGRATION_2_3)
                .build();

        // Setup version info
        try {
            PackageInfo pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            String version = pInfo.versionName;
            TextView versionView = view.findViewById(R.id.version);
            versionView.setText(getString(R.string.version_string, version));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Set button listener
        Button getClubs = view.findViewById(R.id.get_clubs);
        getEvents = view.findViewById(R.id.get_events);
        getCompetitors = view.findViewById(R.id.get_competitors);

        getClubs.setOnClickListener(this);
        getEvents.setOnClickListener(this);
        getCompetitors.setOnClickListener(this);

        // Setup club spinner
        clubSpinner = view.findViewById(R.id.club_spinner);
        clubAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mClubList);
        clubSpinner.setAdapter(clubAdapter);
        clubSpinner.setOnItemSelectedListener(this);
        clubId = sharedPref.getInt(SELECTED_CLUB_KEY, -1);

        // Setup event spinner
        eventSpinner = view.findViewById(R.id.event_spinner);
        eventAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, mEventList);
        eventSpinner.setAdapter(eventAdapter);
        eventSpinner.setOnItemSelectedListener(this);
        eventId = sharedPref.getInt(SELECTED_EVENT_KEY, -1);

        // Fetch clubs/events from DB and setup spinners
        new InitialSpinnerSetupTask(getActivity(), database).execute(clubId);

        Log.d(TAG, "Initial club ID is " + clubId + ", initial event ID is " + eventId);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.get_clubs:
                Log.d(TAG, "Get clubs pushed");
                new DownloadClubTask(getActivity(), database).execute("https://whyjustrun.ca/iof/3.0/organization_list.xml");
                break;
            case R.id.get_events:
                Log.d(TAG, "Get events pushed");
                if (clubId > 0) {
                    Calendar temp = Calendar.getInstance();
                    long now = temp.getTimeInMillis() / 1000;
                    // 604800 is one week in seconds
                    long start = now - 604800;
                    long end = now + 604800;
                    String url = "https://whyjustrun.ca/events.xml?iof_version=3.0&start=" + start + "&end=" + end + "&club_id=" + clubId;
                    new DownloadEventTask(getActivity(), database, clubId).execute(url);
                }
                break;
            case R.id.get_competitors:
                Log.d(TAG, "Get competitors pushed");
                if (eventId > 0) {
                    String url = "https://whyjustrun.ca/iof/3.0/events/" + eventId + "/entry_list.xml";
                    Log.e("Test", "Url is " + url);
                    new DownloadEntryListTask(getActivity(), database).execute(url);
                }
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        SharedPreferences.Editor editor = sharedPref.edit();
        switch (parent.getId())
        {
            case R.id.club_spinner:
                Log.d(TAG, "Club spinner item selected");
                clubId = mClubList.get(position).wjrId;
                Log.d(TAG, "New club ID is " + clubId);
                editor.putInt(SELECTED_CLUB_KEY, clubId);
                updateClubSpinner();

                // Update events list
                eventSpinner.setEnabled(false);
                new UpdateEventSpinnerTask(getActivity(), database).execute(clubId);
                break;
            case R.id.event_spinner:
                Log.d(TAG, "Event spinner item selected");
                eventId = mEventList.get(position).wjrId;
                Log.d(TAG, "New event ID is " + eventId);
                editor.putInt(SELECTED_EVENT_KEY, eventId);
                getCompetitors.setEnabled(true);

                // Callback to parent activity
                if (mCallback != null)
                    mCallback.onEventIdChange(eventId);

                break;
        }
        editor.apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Nothing selected -> do nothing :)
    }

    // Returns position in ArrayList by id
    private static int findClubPositionById(List<WjrClub> entries, int id) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).wjrId == id)
                return i;
        return -1;
    }

    // Returns position in ArrayList by id
    private static int findEventPositionById(List<WjrEvent> entries, int id) {
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).wjrId == id)
                return i;
        return -1;
    }

    /**
     * Fetch all clubs and events for the chosen club (if applicable)
     * ids[0] is club id
     */
    private static class InitialSpinnerSetupTask extends  AsyncTask<Integer, Void, Void> {
        private final WeakReference<Activity> weakActivity;
        private final WjrDatabase database;
        private List<WjrClub> clubs;
        private List<WjrEvent> events;

        InitialSpinnerSetupTask(Activity activity, WjrDatabase database) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
        }

        @Override
        protected Void doInBackground(Integer... ids) {
            clubs = database.daoAccess().getAllClubs();
            events = database.daoAccess().getEventsByClub(ids[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
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
            if (mainActivity.currentFrame == MainActivity.FrameType.HomeFrag) {
                HomeFragment fragment = (HomeFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                // Re-setup spinners
                fragment.mClubList = clubs;
                fragment.mEventList = events;

                fragment.updateClubSpinner();
                fragment.updateEventSpinner();
            }
        }
    }

    /**
     * Fetches the events from the database for a specific club
     * ids[0] is club id
     */
    private static class UpdateEventSpinnerTask extends  AsyncTask<Integer, Void, Void> {
        private final WeakReference<Activity> weakActivity;
        private final WjrDatabase database;
        private List<WjrEvent> events;

        UpdateEventSpinnerTask(Activity activity, WjrDatabase database) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
        }

        @Override
        protected Void doInBackground(Integer... ids) {
            events = database.daoAccess().getEventsByClub(ids[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
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
            if (mainActivity.currentFrame == MainActivity.FrameType.HomeFrag) {
                HomeFragment fragment = (HomeFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                // Setup event spinner
                fragment.mEventList = events;
                fragment.eventAdapter.notifyDataSetChanged();
                fragment.eventSpinner.setEnabled(true);

                fragment.updateEventSpinner();
            }
        }
    }

    /**
     * Moves club spinner to correct position, enables get events
     */
    private void updateClubSpinner() {
        Log.d(TAG, "Update club spinner called");
        clubAdapter.clear();
        clubAdapter.addAll(mClubList);
        int pos = findClubPositionById(mClubList, clubId);
        if (pos < 0) {
            clubId = -1;
            eventId = -1;
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(SELECTED_CLUB_KEY, clubId);
            editor.putInt(SELECTED_EVENT_KEY, eventId);
            editor.apply();

            // Disable get events
            getEvents.setEnabled(false);
        } else {
            clubSpinner.setSelection(pos);

            // Enable get events
            getEvents.setEnabled(true);
        }
    }

    /**
     * Moves event spinner to correct position, enables get competitors
     */
    private void updateEventSpinner() {
        Log.d(TAG, "Update event spinner called");
        eventAdapter.clear();
        eventAdapter.addAll(mEventList);
        int pos = findEventPositionById(mEventList, eventId);
        if (pos < 0) {
            // Set eventId to -1
            eventId = -1;
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(SELECTED_EVENT_KEY, eventId);
            editor.apply();

            // Disable download event data button
            getCompetitors.setEnabled(false);
        } else {
            eventSpinner.setSelection(pos);

            // Enable download event data button
            getCompetitors.setEnabled(true);
        }
    }

    // Implementation of AsyncTask used to download XML from WJR for Clubs
    private static class DownloadClubTask extends AsyncTask<String, Void, List<WjrClub>> {
        private final WeakReference<Activity> weakActivity;
        private final WjrDatabase database;

        DownloadClubTask(Activity activity, WjrDatabase database) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
        }

        @Override
        protected List<WjrClub> doInBackground(String... urls) {
            try {
                return updateClubs(urls[0], database);
            } catch (IOException e) {
                return null;
            } catch (XmlPullParserException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<WjrClub> clubs) {
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
            if (mainActivity.currentFrame == MainActivity.FrameType.HomeFrag) {
                HomeFragment fragment = (HomeFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                // Do whatever with fragment
                if (clubs != null) {
                    fragment.mClubList = clubs;
                    fragment.updateClubSpinner();
                } else {
                    Toast.makeText(activity.getApplicationContext(), R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // Implementation of AsyncTask used to download XML from WJR for Clubs
    private static class DownloadEventTask extends AsyncTask<String, Void, List<WjrEvent>> {
        private final WeakReference<Activity> weakActivity;
        private final WjrDatabase database;
        private final int clubId;

        DownloadEventTask(Activity activity, WjrDatabase database, int clubId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.clubId = clubId;
        }

        @Override
        protected List<WjrEvent> doInBackground(String... urls) {
            try {
                return updateEvents(urls[0], database, clubId);
            } catch (IOException e) {
                return null;
            } catch (XmlPullParserException e) {
                return null;
            }
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
            if (mainActivity.currentFrame == MainActivity.FrameType.HomeFrag) {
                HomeFragment fragment = (HomeFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                if (events != null) {
                    fragment.mEventList = events;

                    // Enable event spinner
                    fragment.eventSpinner.setEnabled(true);

                   fragment.updateEventSpinner();
                } else {
                    Toast.makeText(activity.getApplicationContext(), R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // Implementation of AsyncTask used to download entry list XML from WJR
    private static class DownloadEntryListTask extends AsyncTask<String, Void, Boolean> {
        private final WeakReference<Activity> weakActivity;
        private final WjrDatabase database;

        DownloadEntryListTask(Activity activity, WjrDatabase database) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            return loadEntryList(urls[0], database);
        }

        @Override
        protected void onPostExecute(Boolean success) {
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
            if (success == null || !success) {
                Toast.makeText(activity.getApplicationContext(), R.string.entry_list_failure, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity.getApplicationContext(), R.string.entry_list_success, Toast.LENGTH_LONG).show();
            }
        }
    }

    // Downloads club xml and saves to DB
    private static List<WjrClub> updateClubs(String urlString, WjrDatabase database)
            throws XmlPullParserException, IOException {
        InputStream stream = null;
        List<WjrClub> clubs= null;
        WjrClubParser xmlParser = new WjrClubParser();

        try {
            stream = downloadUrl(urlString);
            if (stream != null) {
                clubs = xmlParser.parse(stream);

                // Save to DB, removing all old entries
                if (clubs.size() > 0) {
                    database.daoAccess().deleteAllClubs();
                    database.daoAccess().addClubsList(clubs);
                }
            } else {
                Log.e("UpdateClubs", "Failed due to null stream");
            }

            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return clubs;
    }

    // Downloads club xml and parses
    private static List<WjrEvent> updateEvents(String urlString, WjrDatabase database, int clubId)
            throws XmlPullParserException, IOException {
        InputStream stream = null;
        List<WjrEvent> events = null;
        WjrEventParser xmlParser = new WjrEventParser();

        try {
            stream = downloadUrl(urlString);
            if (stream != null) {
                List<Integer> curEvents = new ArrayList<>();
                events = xmlParser.parse(stream);

                // Update DB, clearing out old data
                for (int i = 0; i < events.size(); i++) {
                    curEvents.add(events.get(i).wjrId);
                }
                // Delete competitors from events not current
                int[] eventsForRemoval = database.daoAccess().getEventsToRemove(clubId, curEvents);
                database.daoAccess().cleanupOldCompetitors(eventsForRemoval);

                // Delete old categories
                database.daoAccess().cleanupOldCategories(eventsForRemoval);

                // Delete old events
                database.daoAccess().deleteOldEvents(clubId, curEvents);

                // Add new events
                database.daoAccess().addEventsList(events);
                Log.d(TAG, "Successfully removed old events and added new ones");
            } else {
                Log.e("UpdateEvents", "Couldn't get network stream");
            }

            // Makes sure that the streams are closed after the app is
            // finished using it.
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return events;
    }

    /**
     *
     *
     * @param urlString String representation of URL to download
     * @param database Database to store info in
     * @return Success on downloading entries
     */
    private static Boolean loadEntryList(String urlString, WjrDatabase database) {
        InputStream stream = null;
        WjrCompetitorParser xmlParser = new WjrCompetitorParser();
        EventInfo info;

        try {
            stream = downloadUrl(urlString);
            info = xmlParser.parse(stream);
            // Makes sure that the streams are closed after the app is
            // finished using it.
        } catch (IOException e) {
            Log.e("LoadEntryList", "Caught IOException");
            return false;
        } catch (XmlPullParserException e) {
            Log.e("LoadEntryList", "Caught XmlPullParserException");
            return false;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                // Do nothing
            }
        }
        
        if (info != null) {
            // Add info to DB
            database.daoAccess().addCategories(info.categories);
            database.daoAccess().insertCompetitorList(info.competitors);
            return true;
        } else {
            Log.d("LoadEntryList", "Return event info was null");
            return false;
        }
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an input stream.
    private static InputStream downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // Starts the query
        conn.connect();
        return conn.getInputStream();
    }

    // This class represents an event and its categories and competitors
    static class EventInfo {
        WjrEvent event;
        List<WjrCategory> categories;
        List<Competitor> competitors;

        EventInfo() {
            categories = new ArrayList<>();
            competitors = new ArrayList<>();
        }
    }
}
