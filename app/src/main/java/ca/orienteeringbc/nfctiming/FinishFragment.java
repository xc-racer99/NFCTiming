package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addMigrations(WjrDatabase.MIGRATION_1_2)
                .build();

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

                            new UploadResultsTask(getActivity(), database, eventId,
                                    username.getText().toString(), password.getText().toString()).execute();
                        }
                    }).setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialogBox, int id) {
                                    dialogBox.cancel();
                                }
                    }).show();
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
    private class UploadResultsTask extends AsyncTask<Void, Void, Boolean> {
        private String res = null;
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;
        private int eventId;
        private final String basicAuth;

        UploadResultsTask(Activity activity, WjrDatabase database, int eventId, String username, String password) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;

            basicAuth = "Basic " + Base64.encodeToString(new String(username + ":" + password).getBytes(), Base64.NO_WRAP);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            OutputStream out;
            try {
                File mFile = new File(getActivity().getExternalFilesDir(null), eventId + ".xml");
                OutputStream out2 = new FileOutputStream(mFile);

                new UploadResultsXml().makeXml(out2, database, eventId);

                if (out2 != null)
                    out2.close();

                HttpURLConnection connection = uploadUrl("https://whyjustrun.ca/iof/3.0/events/" + eventId + "/result_list.xml", basicAuth, weakActivity);
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
