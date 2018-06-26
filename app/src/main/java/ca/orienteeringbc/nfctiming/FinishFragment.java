package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
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

    // Sort types, must be synced with sort array strings
    private static final int SORT_STATUS = 0;
    private static final int SORT_CATEGORY = 1;
    private static final int SORT_NAME = 2;

    public FinishFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_finish, container, false);

        // Initialize shared prefs
        final SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize database
        database = WjrDatabase.getInstance(getActivity());

        // Determine event ID
        eventId = sharedPrefs.getInt(MainActivity.SELECTED_EVENT_KEY, -1);

        if (eventId > 0) {
            Button uploadResults = view.findViewById(R.id.upload_results);

            uploadResults.setEnabled(true);
            uploadResults.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getActivity());
                    View mView = layoutInflaterAndroid.inflate(R.layout.upload_results_dialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                    alertDialogBuilder.setView(mView);

                    final EditText username = mView.findViewById(R.id.username_input);
                    final EditText password = mView.findViewById(R.id.password_input);
                    final CheckBox save_pass = mView.findViewById(R.id.save_pass_checkbox);

                    username.setText(sharedPrefs.getString(MainActivity.WJR_USERNAME, null));
                    password.setText(sharedPrefs.getString(MainActivity.WJR_PASSWORD, null));
                    save_pass.setChecked(sharedPrefs.getBoolean(MainActivity.SAVE_WJR_CREDENTIALS, true));

                    alertDialogBuilder.setPositiveButton(R.string.upload_results, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            SharedPreferences.Editor editor = sharedPrefs.edit();
                            if (save_pass.isChecked()) {
                                // Save credentials
                                editor.putString(MainActivity.WJR_PASSWORD, password.getText().toString());
                            }
                            editor.putString(MainActivity.WJR_USERNAME, username.getText().toString());
                            editor.putBoolean(MainActivity.SAVE_WJR_CREDENTIALS, save_pass.isChecked());
                            editor.apply();

                            String basicAuth = "Basic " + Base64.encodeToString(
                                    new String(username.getText().toString() + ":" + password.getText().toString()).getBytes(),
                                    Base64.NO_WRAP);

                            new UploadResultsTask(getActivity(), database, eventId, basicAuth).execute(false);
                        }
                    }).setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogBox, int id) {
                                    dialogBox.cancel();
                                }
                    }).show();
                }
            });

            // Setup sort spinner
            Spinner sortSpinner = view.findViewById(R.id.results_sort);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(),
                    R.array.sorting_array, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sortSpinner.setAdapter(adapter);
            sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    setupResultList(i);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing
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
            setupResultList(SORT_STATUS);
        }
    }

    /**
     * Refreshes the results list based on current position of sort spinner
     */
    void refreshResultList() {
        Spinner spinner = view.findViewById(R.id.results_sort);
        setupResultList(spinner.getSelectedItemPosition());
    }

    private void setupResultList(int sort) {
        new SetupResultListTask(getActivity(), database, eventId).execute(sort);
    }

    private static class SetupResultListTask extends AsyncTask<Integer, Void, List<Competitor>> {
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;

        SetupResultListTask(Activity activity, WjrDatabase database, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        protected List<Competitor> doInBackground(Integer... sort) {
            switch (sort[0]) {
                case SORT_STATUS:
                    return database.daoAccess().getCompetitorsByEventTimed(eventId);
                case SORT_NAME:
                    return database.daoAccess().getCompetitorsByEventAlphabetically(eventId);
                case SORT_CATEGORY:
                    return database.daoAccess().getCompetitorsByEventCategory(eventId);
                default:
                    Log.e("Sort", "Unknown sort type, defaulting to by time");
            }
            return database.daoAccess().getCompetitorsByEventTimed(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            // Make sure we found some competitors
            if (competitors == null)
                return;

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
    private static class UploadResultsTask extends AsyncTask<Boolean, Void, Boolean> {
        private String res = null;
        private int resInt = -1;
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;
        private final String basicAuth;
        private File mFile;

        UploadResultsTask(Activity activity, WjrDatabase database, int eventId, String basicAuth) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
            this.basicAuth = basicAuth;
            mFile = new File(activity.getExternalFilesDir(null), eventId + ".xml");
        }

        @Override
        protected Boolean doInBackground(Boolean... compat) {
            OutputStream out;
            try {
                OutputStream fileOut = new FileOutputStream(mFile);

                // Save a copy to SD card
                new UploadResultsXml().makeXml(fileOut, database, eventId, false);

                HttpURLConnection connection = uploadUrl("https://whyjustrun.ca/iof/3.0/events/" + eventId + "/result_list.xml", basicAuth, weakActivity);
                if (connection == null)
                    return false;
                out = connection.getOutputStream();

                new UploadResultsXml().makeXml(out, database, eventId, compat[0]);

                res = connection.getResponseMessage();
                resInt = connection.getResponseCode();
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

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            if (success) {
                if (resInt == HttpURLConnection.HTTP_OK) {
                    // Success!
                    builder.setPositiveButton(R.string.ok, null)
                            .setMessage(R.string.success_uploading);
                } else if (resInt == HttpURLConnection.HTTP_BAD_REQUEST) {
                    // Presumably failed due to day-of registrants with two entries of their name online
                    builder.setMessage(activity.getString(R.string.bad_request_reply, res))
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    new UploadResultsTask(activity, database, eventId, basicAuth).execute(true);
                                }
                            }).setNegativeButton(R.string.cancel, null);
                } else {
                    // Unknown error, presumably due to incorrect password
                    builder.setMessage(activity.getString(R.string.internal_error_reply, res))
                            .setPositiveButton(R.string.ok, null);
                }
            } else {
                builder.setPositiveButton(R.string.ok, null)
                        .setMessage(R.string.error_uploading);
            }
            builder.show();
        }
    }

    // Given a string representation of a URL, sets up a connection and gets
    // an output stream
    private static HttpURLConnection uploadUrl(String urlString, String basicAuth, WeakReference<Activity> weakActivity) throws IOException {
        // Re-acquire a strong reference to the activity, and verify
        // that it still exists and is active.
        final Activity activity = weakActivity.get();
        if (activity == null
                || activity.isFinishing()
                || activity.isDestroyed()) {
            // activity is no longer valid, don't do anything!
            return null;
        }

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
