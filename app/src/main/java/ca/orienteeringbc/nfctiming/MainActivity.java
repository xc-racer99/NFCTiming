package ca.orienteeringbc.nfctiming;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements HomeFragment.OnEventIdChangeListener {

    // Shared prefs keys
    public static final String SELECTED_CLUB_KEY = "SELECTED_WJR_CLUB";
    public static final String SELECTED_EVENT_KEY = "SELECTED_WJR_EVENT";
    public static final String WJR_USERNAME = "WJR_USERNAME";
    public static final String WJR_PASSWORD = "WJR_PASSWORD";

    // Database name
    public static final String DATABASE_NAME = "wjr_database";

    // Mime type of assigned tags
    public static final String MIME_TEXT_PLAIN = "text/plain";

    // Counter to determine if we should reload the frame or not
    private enum FrameType {
        HomeFrag,
        StartFrag,
        FinishFrag,
    }

    FrameType currentFrame = FrameType.HomeFrag;

    // NFC related vars
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    // Database
    private WjrDatabase database;

    // Event Id
    private int eventId;

    // If we're waiting for an NFC tag to be accepted/denied
    private boolean queuedSwipe = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Determine eventId
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        eventId = preferences.getInt(SELECTED_EVENT_KEY, -1);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.bottombaritem_home:
                                if (currentFrame != FrameType.HomeFrag) {
                                    addHomeFragment();
                                    currentFrame = FrameType.HomeFrag;
                                }

                                return true;
                            case R.id.bottombaritem_start:
                                if (currentFrame != FrameType.StartFrag) {
                                    addStartFragment();
                                    currentFrame = FrameType.StartFrag;
                                }
                                return true;
                            case R.id.bottombaritem_config:
                                if (currentFrame != FrameType.FinishFrag) {
                                    addFinishFragment();
                                    currentFrame = FrameType.FinishFrag;
                                }
                                return true;
                        }
                        return false;
                    }
                });

        // Initialize to home view
        addHomeFragment();

        // Initialize database
        database = Room.databaseBuilder(getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();

        // Initialize NFC PendingIntent
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mAdapter == null) {
            // Warn about no NFC
            Toast.makeText(this, R.string.no_nfc, Toast.LENGTH_LONG).show();
        } else {
            if (!mAdapter.isEnabled()) {
                Toast.makeText(this, R.string.nfc_disabled, Toast.LENGTH_LONG).show();
            }
            mPendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

            // Setup an intent filter for all MIME based dispatches
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            try {
                ndef.addDataType(MIME_TEXT_PLAIN);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("Failed to add MIME", e);
            }
            IntentFilter td = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            mFilters = new IntentFilter[] { ndef, td };

            // Setup a tech list for all NFC tags
            mTechLists = new String[][] { new String[] {
                    NfcV.class.getName(),
                    NfcF.class.getName(),
                    NfcA.class.getName(),
                    NfcB.class.getName()
            } };
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mAdapter != null) {
            mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

        if (eventId == -1) {
            Toast.makeText(this, R.string.no_event, Toast.LENGTH_LONG).show();
            return;
        }

        // Skip if we're waiting
        if (queuedSwipe)
            return;
        else
            queuedSwipe = true;

        // Fetch the tag from the intent
        Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

        if (rawMessages != null && rawMessages.length > 0) {
            NdefMessage[] messages = new NdefMessage[rawMessages.length];
            for (int i = 0; i < rawMessages.length; i++) {
                messages[i] = (NdefMessage) rawMessages[i];
            }

            // Look for a WJR Id
            for (NdefMessage message : messages) {
                NdefRecord[] records = message.getRecords();
                for (NdefRecord record : records) {
                    if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
                        // See spec at http://bit.ly/2u2TzV5
                        byte[] payload = record.getPayload();
                        String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
                        int languageCodeLength = payload[0] & 0077;

                        try {
                            String string = new String(payload,
                                    languageCodeLength + 1,
                                    payload.length - languageCodeLength - 1,
                                    textEncoding);
                            Log.d("Payload string is", string);
                            if (string.startsWith("WjrId:")) {
                                // Remove WjrId: from start
                                int wjrId = Integer.parseInt(string.substring(6));
                                if (wjrId > 0) {
                                    new CompetitorFromWjrIdTask().execute(wjrId);
                                    return;
                                }
                            }
                        } catch (UnsupportedEncodingException e) {
                            Log.e("TextEncoding Exception", e.toString());
                        }
                    }
                }
            }
        }

        // Note, this is an attempt to convert the byte array into a long
        // BigInteger (which uses 2's complement), gives a different number
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        byte[] id_array  = t.getId();
        long value = 0;
        for (int i = 0; i < id_array.length; i++) {
            value += ((long) id_array[i] & 0xffL) << (8 * i);
        }

        new CompetitorFromNfcTagTask().execute(value);
    }

    private void addHomeFragment() {
        HomeFragment frag = new HomeFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commit();
    }

    private void addStartFragment() {
        StartFragment frag = new StartFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commit();
    }

    private void addFinishFragment() {
        FinishFragment frag = new FinishFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commit();
    }

    // Receive callbacks from HomeFragment about changes to the eventId
    public void onEventIdChange(int id) {
        eventId = id;
    }

    /**
     *  Tries to find a Competitor by their NFC tag number (for non-assigned cards)
     *  For pre-assigned cards, see @CompetitorFromWjrIdTask
     */
    private class CompetitorFromNfcTagTask extends AsyncTask<Long, Void, Competitor> {
        long nfcId;

        @Override
        protected Competitor doInBackground(Long... id) {
            nfcId = id[0];
            return database.daoAccess().getCompetitorByNfc(eventId, id[0]);
        }

        @Override
        protected void onPostExecute(Competitor competitor) {
            if (competitor == null) {
                // Un-assigned card, at the start
                new SelectCompetitorTask().execute(nfcId, Long.valueOf(-1));
            } else {
                // Un-assigned card, at the finish
                showFinish(competitor);
            }
        }
    }

    /**
     * Tries to find a competitor by their wjrId number from their pre-assigned card
     * For non-assigned cards, see @CompetitorFromNfcTagTask
     */
    private class CompetitorFromWjrIdTask extends AsyncTask<Integer, Void, Competitor> {
        int wjrId;

        @Override
        protected Competitor doInBackground(Integer... wjrId) {
            this.wjrId = wjrId[0];
            return database.daoAccess().getCompetitorByWjrId(eventId, wjrId[0]);
        }

        @Override
        protected void onPostExecute(Competitor competitor) {
            if (competitor == null) {
                // Pre-assigned card, but didn't pre-register
                new SelectCompetitorTask().execute(Long.valueOf(-1), Long.valueOf(wjrId));
            } else {
                // Yay!  Pre-assigned card, pre-registered.  Now check if finished or not
                if (competitor.startTime > 0)
                    showFinish(competitor);
                else
                    showStart(competitor);
            }
        }
    }

    /**
     * Gets all the competitors for selection
     */
    private class SelectCompetitorTask extends  AsyncTask<Long, Void, List<Competitor>> {
        private long nfcId;
        private int wjrId;
        private List<WjrCategory> categories;

        @Override
        protected List<Competitor> doInBackground(Long... ids) {
            nfcId = ids[0];
            wjrId = ids[1].intValue();
            categories = database.daoAccess().getCategoryById(eventId);
            return database.daoAccess().getUnstartedCompetitorsByEvent(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            LayoutInflater layoutInflaterAndroid = LayoutInflater.from(MainActivity.this);
            View mView = layoutInflaterAndroid.inflate(R.layout.alert_select_person, null);
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
            alertDialogBuilder.setView(mView);
            alertDialogBuilder.setNegativeButton(getText(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    queuedSwipe = false;
                }
            });
            final AlertDialog alertDialog = alertDialogBuilder.create();

            Button addNewCompetitor = mView.findViewById(R.id.add_new_person);
            addNewCompetitor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LayoutInflater layoutInflaterAndroid = LayoutInflater.from(MainActivity.this);
                    View mView = layoutInflaterAndroid.inflate(R.layout.add_new_competitor_dialog, null);
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                    alertDialogBuilder.setView(mView);

                    final EditText firstName = mView.findViewById(R.id.first_name_input);
                    final EditText lastName = mView.findViewById(R.id.last_name_input);
                    final Spinner spinner = mView.findViewById(R.id.new_person_category_spinner);
                    ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, categories);
                    spinner.setAdapter(catAdapter);
                    alertDialogBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String firstNameString = firstName.getText().toString();
                            String lastNameString = lastName.getText().toString();
                            if (firstNameString.isEmpty() || lastNameString.isEmpty()) {
                                // No name given, warn
                                Toast.makeText(MainActivity.this, R.string.no_name, Toast.LENGTH_LONG).show();
                                dialogInterface.cancel();
                            } else {
                                Competitor competitor = new Competitor(eventId, firstName.getText().toString(), lastName.getText().toString());
                                WjrCategory category = (WjrCategory) spinner.getSelectedItem();
                                competitor.wjrCategoryId = category.wjrCategoryId;
                                competitor.wjrId = wjrId;
                                competitor.nfcTagId = nfcId;
                                new AddCompetitorTask().execute(competitor);
                                showStart(competitor);

                                // Update start fragment if displayed
                                if (currentFrame == FrameType.StartFrag) {
                                    StartFragment fragment = (StartFragment) getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                                    if (fragment != null)
                                        fragment.setupStartList();
                                }

                                dialogInterface.dismiss();
                                alertDialog.dismiss();
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
            final ArrayAdapter adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_list_item_1,
                    competitors);
            final ListView listView = mView.findViewById(R.id.competitor_list);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    // Bring up start confirmation screen
                    Competitor competitor = (Competitor) adapterView.getItemAtPosition(i);
                    competitor.wjrId = wjrId;
                    competitor.nfcTagId = nfcId;
                    showStart(competitor);
                    alertDialog.dismiss();
                }
            });
            listView.setAdapter(adapter);

            alertDialog.show();
        }
    }

    /**
     * Adds a new competitor to the database
     */
    private class AddCompetitorTask extends AsyncTask<Competitor, Void, Void> {
        @Override
        protected Void doInBackground(Competitor... competitors) {
            database.daoAccess().insertCompetitors(competitors[0]);
            return null;
        }
    }

    /**
     * Updates a competitor in the database
     */
    private class UpdateCompetitorTask extends AsyncTask<Competitor, Void, Void> {
        @Override
        protected Void doInBackground(Competitor... competitors) {
            database.daoAccess().updateCompetitor(competitors[0]);
            return null;
        }
    }

    // Called when competitor has finished
    private void showFinish(final Competitor competitor) {
        competitor.endTime = System.currentTimeMillis() / 1000;
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilder.setTitle(R.string.confirm_finish)
                .setMessage(getString(R.string.confirm_finish_msg,
                        competitor.firstName + " " + competitor.lastName))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        new UpdateCompetitorTask().execute(competitor);
                        dialogInterface.dismiss();
                        updateFinishFrag();
                    }})
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        queuedSwipe = false;
                    }
                })
                .show();
    }

    // Called to confirm start time
    private void showStart(final Competitor competitor) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilder.setTitle(R.string.confirm_start)
                .setMessage(getString(R.string.confirm_start_msg,
                        competitor.firstName + " " + competitor.lastName))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        competitor.startTime = System.currentTimeMillis() / 1000;
                        new UpdateCompetitorTask().execute(competitor);
                        dialogInterface.dismiss();
                        updateFinishFrag();
                    }})
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        queuedSwipe = false;
                    }
                })
                .show();
    }

    // Updates the finish list if the finish fragment is displayed
    private void updateFinishFrag() {
        if (currentFrame == FrameType.FinishFrag) {
            FinishFragment fragment = (FinishFragment) getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
            if (fragment != null)
                fragment.setupResultList();
        }
    }
}
