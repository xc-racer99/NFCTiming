package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class FinishFragment extends Fragment {
    private WjrDatabase database;

    // The event id
    private int eventId;

    // View created
    private View view;

    public FinishFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_finish, container, false);

        // Initialize shared prefs
        SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize database
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        // Determine event ID
        eventId = sharedPrefs.getInt(MainActivity.SELECTED_EVENT_KEY, -1);

        if (eventId > 0) {
            Button uploadResults = view.findViewById(R.id.upload_results);

            String pass = sharedPrefs.getString(MainActivity.WJR_USERNAME, "");
            String user = sharedPrefs.getString(MainActivity.WJR_PASSWORD, "");
            if (!pass.isEmpty() && !user.isEmpty())
                uploadResults.setEnabled(true);

            uploadResults.setEnabled(true);
            uploadResults.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new UploadResultsTask(getActivity(), database, eventId).execute();
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (eventId > 0) {
            new SetupEventNameTask(getActivity(), database, eventId).execute();
            setupResultList();
        }
    }

    protected void setupResultList() {
        new SetupResultListTask(getActivity(), database, eventId).execute();
    }

    private static class SetupResultListTask extends AsyncTask<Void, Void, List<Competitor>> {
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;

        SetupResultListTask(Activity activity, WjrDatabase database, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        protected List<Competitor> doInBackground(Void... voids) {
            return database.daoAccess().getCompetitorsByEventTimed(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            // Re-acquire a strong reference to the activity, and verify
            // that it still exists and is active.
            final Activity activity = weakActivity.get();
            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()) {
                // activity is no longer valid, don't do anything!
                return;
            }

            // The activity is still valid
            MainActivity mainActivity = (MainActivity) weakActivity.get();
            if (mainActivity.currentFrame == MainActivity.FrameType.FinishFrag) {
                FinishFragment fragment = (FinishFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                ListView listView = fragment.view.findViewById(R.id.results_listview);
                FinishArrayAdapter adapter = new FinishArrayAdapter(activity, competitors);
                listView.setAdapter(adapter);
            }
        }
    }

    // Sets the event name field
    private static class SetupEventNameTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;

        SetupEventNameTask(Activity activity, WjrDatabase database, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        protected String doInBackground(Void... voids) {
            return database.daoAccess().getEventNameById(eventId);
        }

        @Override
        protected void onPostExecute(String name) {
            // Re-acquire a strong reference to the activity, and verify
            // that it still exists and is active.
            final Activity activity = weakActivity.get();
            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()) {
                // activity is no longer valid, don't do anything!
                return;
            }

            // The activity is still valid
            MainActivity mainActivity = (MainActivity) weakActivity.get();
            if (mainActivity.currentFrame == MainActivity.FrameType.FinishFrag) {
                FinishFragment fragment = (FinishFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                TextView eventName = fragment.view.findViewById(R.id.event_title);
                eventName.setText(name);
            }
        }
    }

    // Uploads a results xml
    private static class UploadResultsTask extends AsyncTask<Void, Void, Boolean> {
        private String res = null;
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;

        UploadResultsTask(Activity activity, WjrDatabase database, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            OutputStream out;
            try {
                HttpURLConnection connection = uploadUrl("https://whyjustrun.ca/iof/3.0/events/" + eventId + "/result_list.xml", weakActivity);
                if (connection == null)
                    return false;
                out = connection.getOutputStream();

                new UploadResultsXml().makeXml(out, database, eventId);

                res = connection.getResponseMessage();
            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            // Re-acquire a strong reference to the activity, and verify
            // that it still exists and is active.
            final Activity activity = weakActivity.get();
            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()) {
                // activity is no longer valid, don't do anything!
                return;
            }
            if (success) {
                Toast.makeText(activity, "Server Replied " + res, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, R.string.error_uploading, Toast.LENGTH_LONG).show();
            }
        }
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an output stream.
    private static HttpURLConnection uploadUrl(String urlString, WeakReference<Activity> weakActivity) throws IOException {
        // Re-acquire a strong reference to the activity, and verify
        // that it still exists and is active.
        final Activity activity = weakActivity.get();
        if (activity == null
                || activity.isFinishing()
                || activity.isDestroyed()) {
            // activity is no longer valid, don't do anything!
            return null;
        }
        SharedPreferences sharedPrefs = activity.getPreferences(Context.MODE_PRIVATE);
        String pass = sharedPrefs.getString(MainActivity.WJR_USERNAME, "");
        String user = sharedPrefs.getString(MainActivity.WJR_PASSWORD, "");
        final String basicAuth = "Basic " + Base64.encodeToString((pass + ":" + user).getBytes(), Base64.NO_WRAP);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);
        conn.setRequestProperty("Authorization", basicAuth);

        // Starts the connection
        conn.connect();
        return conn;
    }
}
