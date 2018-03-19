package ca.orienteeringbc.nfctiming;


import android.app.AlertDialog;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public class StartFragment extends Fragment {
    private WjrDatabase database;

    // ArrayAdapter for start list
    private ArrayAdapter<Competitor> adapter;

    // The event id
    private int eventId;

    // Categories in use for event
    private List<WjrCategory> categories;

    // The fragment's view
    private View view;

    public StartFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_start, container, false);

        // Initialize shared prefs
        SharedPreferences sharedPrefs = getActivity().getPreferences(Context.MODE_PRIVATE);

        // Initialize database
        database = Room.databaseBuilder(getActivity().getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        eventId = sharedPrefs.getInt(MainActivity.SELECTED_EVENT_KEY, -1);

        if (eventId > 0) {
            new SetupEventName().execute();
            setupStartList();
        }

        return view;
    }

    // To be called from Activity when something changes
    protected void setupStartList() {
        new SetupStartListTask().execute();
    }

    // Fetches competitors and categories from database, initializes button
    private class SetupStartListTask extends AsyncTask<Void, Void, List<Competitor>> {
        @Override
        protected List<Competitor> doInBackground(Void... voids) {
            categories = database.daoAccess().getCategoryById(eventId);
            return database.daoAccess().getCompetitorsByEvent(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            // If categories.size() is 0, then we probably haven't fetched the event data
            if (categories.size() == 0) {
                Toast.makeText(getActivity(), R.string.no_categories, Toast.LENGTH_LONG).show();
            } else {
                ListView startList = view.findViewById(R.id.startlist_listview);
                adapter = new StartListArrayAdapter(getActivity(), competitors, categories);
                startList.setAdapter(adapter);

                // Setup add new person
                Button button = view.findViewById(R.id.add_new_person);
                button.setEnabled(true);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getActivity());
                        View mView = layoutInflaterAndroid.inflate(R.layout.add_new_competitor_dialog, null);
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                        alertDialogBuilder.setView(mView);

                        final EditText firstName = mView.findViewById(R.id.first_name_input);
                        final EditText lastName = mView.findViewById(R.id.last_name_input);
                        final Spinner spinner = mView.findViewById(R.id.new_person_category_spinner);
                        ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, categories);
                        spinner.setAdapter(catAdapter);
                        alertDialogBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                String firstNameString = firstName.getText().toString();
                                String lastNameString = lastName.getText().toString();
                                if (firstNameString.isEmpty() || lastNameString.isEmpty()) {
                                    // No name given, warn
                                    Toast.makeText(getActivity(), R.string.no_name, Toast.LENGTH_LONG).show();
                                } else {
                                    Competitor competitor = new Competitor(eventId, firstName.getText().toString(), lastName.getText().toString());
                                    WjrCategory category = (WjrCategory) spinner.getSelectedItem();
                                    competitor.wjrCategoryId = category.wjrCategoryId;
                                    new AddCompetitorTask().execute(competitor);
                                }
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
        }
    }

    // Async class to add a competitor to DB and ArrayAdapter
    private class AddCompetitorTask extends AsyncTask<Competitor, Void, Competitor> {
        @Override
        protected Competitor doInBackground(Competitor... competitors) {
            database.daoAccess().insertCompetitors(competitors[0]);
            return competitors[0];
        }

        @Override
        protected void onPostExecute(Competitor competitor) {
            adapter.add(competitor);
        }
    }

    // Sets the event name field
    private class SetupEventName extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            return database.daoAccess().getEventNameById(eventId);
        }

        @Override
        protected void onPostExecute(String name) {
            TextView eventName = view.findViewById(R.id.event_title);
            eventName.setText(name);
        }
    }
}
