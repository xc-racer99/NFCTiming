package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
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
import android.provider.Settings;
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
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements HomeFragment.OnEventIdChangeListener {

    // Shared prefs keys
    public static final String SELECTED_CLUB_KEY = "SELECTED_WJR_CLUB";
    public static final String SELECTED_EVENT_KEY = "SELECTED_WJR_EVENT";
    public static final String WJR_USERNAME = "WJR_USERNAME";
    public static final String WJR_PASSWORD = "WJR_PASSWORD";
    public static final String SAVE_WJR_CREDENTIALS = "WJR_SAVE";
    public static final String WJR_PEOPLE_LAST_UPDATED = "WJR_PEOPLE_UPDATE";

    // Mime type of assigned tags
    public static final String MIME_TEXT_PLAIN = "text/plain";

    // Saved frame in bundle
    private static final String STATE_CURRENT_FRAME = "currentFrame";

    // Counter to determine if we should reload the frame or not
    enum FrameType {
        HomeFrag,
        StartFrag,
        FinishFrag,
    }

    // To determine what card swipe result is
    enum SwipeType {
        StartAssigned,
        StartUnassigned,
        Finish,
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

        if (savedInstanceState != null) {
            currentFrame = (FrameType) savedInstanceState.get(STATE_CURRENT_FRAME);
            if (currentFrame == null) {
                addHomeFragment();
            } else {
                switch (currentFrame) {
                    case HomeFrag:
                        addHomeFragment();
                        break;
                    case StartFrag:
                        addStartFragment();
                        break;
                    case FinishFrag:
                        addFinishFragment();
                        break;
                }
            }
        } else {
            // Initialize to home view
            addHomeFragment();
        }

        // Initialize database
        database = WjrDatabase.getInstance(this);

        // Initialize NFC PendingIntent
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mAdapter == null) {
            // Warn about no NFC
            new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(R.string.no_nfc)
                    .setMessage(R.string.no_nfc_limitations)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
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
            if (!mAdapter.isEnabled()) {
                new AlertDialog.Builder(this).setTitle(R.string.enable_nfc)
                        .setMessage(R.string.enable_nfc_reason)
                        .setPositiveButton(R.string.go, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent setNfc = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                                startActivity(setNfc);
                                dialogInterface.dismiss();
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create().show();
            }

            mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save currently viewed pane
        savedInstanceState.putSerializable(STATE_CURRENT_FRAME, currentFrame);

        super.onSaveInstanceState(savedInstanceState);
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
        int wjrId = 0;

        if (eventId == -1) {
            Toast.makeText(this, R.string.no_event, Toast.LENGTH_LONG).show();
            return;
        }

        // Skip if we're waiting
        if (queuedSwipe)
            return;
        else
            queuedSwipe = true;

        // Note, this is an attempt to convert the byte array into a long
        // BigInteger (which uses 2's complement), gives a different number
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        byte[] id_array  = t.getId();
        long nfcId = 0;
        for (int i = 0; i < id_array.length; i++) {
            nfcId += ((long) id_array[i] & 0xffL) << (8 * i);
        }

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
                                wjrId = Integer.parseInt(string.substring(6));
                            }
                        } catch (UnsupportedEncodingException e) {
                            Log.e("TextEncoding Exception", e.toString());
                        }
                    }
                }
            }
        }
        Log.d("NFC", "Tag # is " + nfcId + " WJR ID is " + wjrId);
        new HandleCompetitorTask(this, database, eventId).execute(nfcId, Long.valueOf(wjrId));
    }

    private void addHomeFragment() {
        HomeFragment frag = new HomeFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commitNow();
    }

    private void addStartFragment() {
        StartFragment frag = new StartFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commitNow();
    }

    private void addFinishFragment() {
        FinishFragment frag = new FinishFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frame_fragmentholder, frag);

        transaction.commitNow();
    }

    // Receive callbacks from HomeFragment about changes to the eventId
    public void onEventIdChange(int id) {
        eventId = id;
    }

    /**
     *  Tries to find a Competitor by their NFC tag number
     *  If it fails, tried to find via WjrId (if valid)
     *  Shows select competitor, start, or finish depending on result of DB query
     */
    private static class HandleCompetitorTask extends AsyncTask<Long, Void, SwipeType> {
        private final WeakReference<Activity> weakActivity;
        WjrDatabase database;
        int eventId;
        long nfcId;
        Competitor competitor;
        List<WjrCategory> categories;
        List<Competitor> competitors = null;

        HandleCompetitorTask(Activity activity, WjrDatabase database, int eventId) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
            this.eventId = eventId;
        }

        @Override
        protected SwipeType doInBackground(Long... id) {
            SwipeType ret;
            nfcId = id[0];
            categories = database.daoAccess().getCategoryById(eventId);
            competitor = database.daoAccess().getCompetitorByNfc(eventId, id[0]);
            if (competitor == null && id[1] > 0) {
                Log.d("HandleCompetitor", "Didn't find Competitor by NFC tag #");
                // Try and get competitor (maybe multiple times) based on WJR ID
                List<Competitor> thisCompetitors = database.daoAccess().getCompetitorsByWjrId(eventId, id[1].intValue());
                if (thisCompetitors == null) {
                    Log.d("HandleCompetitor", "Didn't find Competitor by WJR ID");
                    // Assigned card, at start of first loop
                    WjrPerson person = database.daoAccess().getPersonById(id[1].intValue());
                    if (person == null) {
                        Log.w("HandleCompetitor", "Didn't find WJR ID in DB");
                        competitors = database.daoAccess().getUnstartedCompetitorsByEvent(eventId);
                        ret = SwipeType.StartUnassigned;
                    } else {
                        competitor = new Competitor(eventId, person.firstName, person.lastName);
                        competitor.nfcTagId = nfcId;
                        competitor.wjrId = person.wjrId;
                        competitor.wjrCategoryId = categories.get(0).wjrCategoryId;
                        ret = SwipeType.StartAssigned;
                    }
                } else {
                    Log.d("HandleCompetitor", "Found " + thisCompetitors.size() + " competitor(s) by WJR ID");

                    if (thisCompetitors.get(0).endTime > 0) {
                        Log.d("HandleCompetitor", "Found pre-registered, assigned card at 2+ start");
                        /* Assigned card, at start for next loop/course
                         * Create duplicate competitor with default start/finish times
                         */
                        competitor = new Competitor(eventId, thisCompetitors.get(0).firstName, thisCompetitors.get(0).lastName);
                        competitor.nfcTagId = nfcId;
                        if (thisCompetitors.get(0).wjrId > 0)
                            competitor.wjrId = thisCompetitors.get(0).wjrId;
                        // Set category to first one competitor hasn't run
                        for (WjrCategory category : categories) {
                            boolean found = true;
                            for (Competitor comp : thisCompetitors) {
                                if (comp.wjrCategoryId == category.wjrCategoryId) {
                                    found = false;
                                    break;
                                }
                            }

                            if (found) {
                                competitor.wjrCategoryId = category.wjrCategoryId;
                                break;
                            }
                        }
                        // If we didn't find an unused category, use the first category
                        if (competitor.wjrCategoryId < 0)
                            competitor.wjrCategoryId = categories.get(0).wjrCategoryId;
                    } else {
                        Log.d("HandleCompetitor", "Found pre-registered, assigned card at first start");
                        competitor = thisCompetitors.get(0);
                        competitor.nfcTagId = nfcId;
                    }

                    ret = SwipeType.StartAssigned;
                }
            } else if (competitor == null) {
                competitors = database.daoAccess().getUnstartedCompetitorsByEvent(eventId);
                ret = SwipeType.StartUnassigned;
            } else {
                // Found competitor's NFC, do finish
                ret = SwipeType.Finish;
            }

            return ret;
        }

        @Override
        protected void onPostExecute(SwipeType swipeType) {
            // Re-acquire a strong reference to the activity, and verify
            // that it still exists and is active.
            Activity activity = weakActivity.get();
            if (activity == null
                    || activity.isFinishing()
                    || activity.isDestroyed()) {
                // activity is no longer valid, don't do anything!
                return;
            }

            MainActivity mainActivity = (MainActivity) weakActivity.get();

            switch (swipeType) {
                case StartAssigned:
                    mainActivity.showStart(competitor, categories);
                    break;
                case StartUnassigned:
                    mainActivity.selectCompetitor(nfcId, competitors, categories);
                    break;
                case Finish:
                    mainActivity.doFinish(competitor);
                    break;
            }
        }
    }

    /**
     * Creates an alert dialog for selection of person or creation of new person
     * @param nfcId NFC ID of swiped card
     * @param competitors List of all competitors not on course
     * @param categories List of all categories in event
     */
    private void selectCompetitor(final long nfcId, final List<Competitor> competitors, final List<WjrCategory> categories) {
        final MainActivity mainActivity = this;
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(mainActivity);
        View mView = layoutInflaterAndroid.inflate(R.layout.alert_select_person, null);
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mainActivity)
            .setView(mView)
            .setNegativeButton(mainActivity.getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                mainActivity.queuedSwipe = false;
                }
            })
            .setCancelable(false);
        final AlertDialog alertDialog = alertDialogBuilder.create();

        Button addNewCompetitor = mView.findViewById(R.id.add_new_person);
        addNewCompetitor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LayoutInflater layoutInflaterAndroid = LayoutInflater.from(mainActivity);
                View mView = layoutInflaterAndroid.inflate(R.layout.add_new_competitor_dialog, null);
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mainActivity)
                        .setView(mView)
                        .setCancelable(false);

                final EditText firstName = mView.findViewById(R.id.first_name_input);
                final EditText lastName = mView.findViewById(R.id.last_name_input);
                final Spinner spinner = mView.findViewById(R.id.new_person_category_spinner);
                ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(mainActivity, android.R.layout.simple_list_item_1, categories);
                spinner.setAdapter(catAdapter);
                alertDialogBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String firstNameString = firstName.getText().toString();
                        String lastNameString = lastName.getText().toString();
                        if (firstNameString.isEmpty() || lastNameString.isEmpty()) {
                            // No name given, warn
                            Toast.makeText(mainActivity, R.string.no_name, Toast.LENGTH_LONG).show();
                            dialogInterface.cancel();
                        } else {
                            Competitor competitor = new Competitor(eventId, firstName.getText().toString(), lastName.getText().toString());
                            WjrCategory category = (WjrCategory) spinner.getSelectedItem();
                            competitor.wjrCategoryId = category.wjrCategoryId;
                            competitor.nfcTagId = nfcId;
                            mainActivity.showStart(competitor, null);

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
        final ArrayAdapter adapter = new ArrayAdapter<>(mainActivity,
                android.R.layout.simple_list_item_1,
                competitors);
        final ListView listView = mView.findViewById(R.id.competitor_list);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Bring up start confirmation screen
                Competitor competitor = (Competitor) adapterView.getItemAtPosition(i);
                competitor.nfcTagId = nfcId;
                mainActivity.showStart(competitor, categories);
                alertDialog.dismiss();
            }
        });
        listView.setAdapter(adapter);

        alertDialog.show();
    }

    /**
     * Called to confirm someone's start and optionally change their category
     * @param competitor Competitor containing NFC ID and optionally WJR ID
     * @param categories List of all categories for the event
     */
    private void showStart(final Competitor competitor, List<WjrCategory> categories) {
        final MainActivity mainActivity = this;
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(mainActivity);
        View mView = layoutInflaterAndroid.inflate(R.layout.alert_confirm_start, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mainActivity)
                .setView(mView)
                .setCancelable(false);

        // Setup category spinner, hiding it if no category info supplied
        final Spinner catSpinner = mView.findViewById(R.id.cat_spinner);
        if (categories != null) {
            ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(mainActivity, android.R.layout.simple_list_item_1, categories);
            catSpinner.setAdapter(catAdapter);
            catSpinner.setSelection(categoryIndexFromList(competitor.wjrCategoryId, categories));
        } else {
            catSpinner.setVisibility(View.GONE);
        }

        alertDialogBuilder.setTitle(R.string.confirm_start)
                .setMessage(getString(R.string.confirm_start_msg,
                        competitor.firstName + " " + competitor.lastName))
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        WjrCategory category = (WjrCategory) catSpinner.getSelectedItem();
                        competitor.wjrCategoryId = category.wjrCategoryId;
                        competitor.startTime = System.currentTimeMillis() / 1000;
                        competitor.status = Competitor.statusToInt("DNF");
                        new UpdateCompetitorTask(mainActivity, database).execute(competitor);
                        dialogInterface.dismiss();
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

    /**
     * Called when a competitor has finished
     * Plays a sound and saves the competitors time to DB
     * @param competitor Competitor object containing all values except finish time
     */
    private void doFinish(final Competitor competitor) {
        competitor.endTime = System.currentTimeMillis() / 1000;
        competitor.status = Competitor.statusToInt("OK");
        competitor.nfcTagId = -1;
        new UpdateCompetitorTask(this, database).execute(competitor);
        queuedSwipe = false;

        final MediaPlayer player = MediaPlayer.create(this, R.raw.beep);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                player.release();
            }
        });
        player.start();
    }

    /**
     * Find index of a category id
     * @param wjrCategoryId Desired category id
     * @param categories List of categories for event
     * @return Index (eg for setSelection) of category id in categories
     */
    private int categoryIndexFromList(int wjrCategoryId, List<WjrCategory> categories) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).wjrCategoryId == wjrCategoryId)
                return i;
        }
        return -1;
    }

    /**
     * Updates a competitor in the database, adding it if needed
     */
    private static class UpdateCompetitorTask extends AsyncTask<Competitor, Void, Void> {
        private final WeakReference<Activity> weakActivity;
        private WjrDatabase database;

        UpdateCompetitorTask(Activity activity, WjrDatabase database) {
            weakActivity = new WeakReference<>(activity);
            this.database = database;
        }

        @Override
        protected Void doInBackground(Competitor... competitors) {
            int ret = database.daoAccess().updateCompetitor(competitors[0]);

            // If we didn't change any, then we're trying to insert a new competitor, not update one
            if (ret == 0) {
                // Try and find a WJR ID, if necessary
                if (competitors[0].wjrId <= 0) {
                    WjrPerson person = database.daoAccess().getPersonByName(competitors[0].firstName, competitors[0].lastName);
                    if (person != null)
                        competitors[0].wjrId = person.wjrId;
                }
                Log.d("UpdateCompetitor", "Adding new competitor");
                database.daoAccess().insertCompetitors(competitors[0]);
            }
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

            final MainActivity mainActivity = (MainActivity) weakActivity.get();
            if (mainActivity.currentFrame == FrameType.FinishFrag) {
                FinishFragment fragment = (FinishFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                if (fragment != null)
                    fragment.refreshResultList();
            } else if (mainActivity.currentFrame == FrameType.StartFrag) {
                StartFragment fragment = (StartFragment) mainActivity.getSupportFragmentManager().findFragmentById(R.id.frame_fragmentholder);
                if (fragment != null)
                    fragment.setupStartList();
            }
        }
    }
}
