package ca.orienteeringbc.nfctiming;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Bundle;
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
import android.widget.ListView;
import android.widget.Toast;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity implements HomeFragment.OnEventIdChangeListener {

    // Shared prefs keys
    public static final String SELECTED_CLUB_KEY = "SELECTED_WJR_CLUB";
    public static final String SELECTED_EVENT_KEY = "SELECTED_WJR_EVENT";
    public static final String WJR_USERNAME = "WJR_USERNAME";
    public static final String WJR_PASSWORD = "WJR_PASSWORD";

    // Database name
    public static final String DATABASE_NAME = "wjr_database";

    // Counter to determine if we should reload the frame or not
    int currentFrame = 0;

    // NFC related vars
    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    // Database
    private WjrDatabase database;

    // Event Id
    private int eventId;

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
                                if (currentFrame != 0) {
                                    addHomeFragment();
                                    currentFrame = 0;
                                }

                                return true;
                            case R.id.bottombaritem_start:
                                if (currentFrame != 1) {
                                    addStartFragment();
                                    currentFrame = 1;
                                }
                                return true;
                            case R.id.bottombaritem_config:
                                if (currentFrame != 2) {
                                    addFinishFragment();
                                    currentFrame = 2;
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
                // TODO - Limit to text/plain only?
                ndef.addDataType("*/*");
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

        // Fetch the tag from the intent
        NdefMessage[] messages = (NdefMessage[]) intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);

        if (messages != null && messages.length > 0) {
            // Look for a WJR Id
            // TODO
            // new CompetitorFromWjrIdTask().execute(wjrId, -1);
        }

        // Note, this is an attempt to convert the byte array into a long
        // BigInteger (which uses 2's complement), gives a different number
        Tag t = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        byte[] id_array  = t.getId();
        long value = 0;
        for (int i = 0; i < id_array.length; i++) {
            value += ((long) id_array[i] & 0xffL) << (8 * i);
        }

        Toast.makeText(this, " " + value, Toast.LENGTH_SHORT).show();

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

        @Override
        protected List<Competitor> doInBackground(Long... ids) {
            nfcId = ids[0];
            wjrId = ids[1].intValue();
            return database.daoAccess().getUnstartedCompetitorsByEvent(eventId);
        }

        @Override
        protected void onPostExecute(List<Competitor> competitors) {
            LayoutInflater layoutInflaterAndroid = LayoutInflater.from(MainActivity.this);
            View mView = layoutInflaterAndroid.inflate(R.layout.alert_select_person, null);
            final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
            alertDialogBuilder.setView(mView);
            final AlertDialog alertDialog = alertDialogBuilder.create();

            Button addNewCompetitor = mView.findViewById(R.id.add_new_person);
            addNewCompetitor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Todo - create a new competitor task
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
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(R.string.confirm_finish);
        alertDialog.setMessage(getString(R.string.confirm_finish_msg,
                competitor.firstName + " " + competitor.lastName));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getText(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        new UpdateCompetitorTask().execute(competitor);
                        dialogInterface.dismiss();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        alertDialog.show();
    }

    // Called to confirm start time
    private void showStart(final Competitor competitor) {
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(R.string.confirm_start);
        alertDialog.setMessage(getString(R.string.confirm_start_msg,
                competitor.firstName + " " + competitor.lastName));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getText(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int which) {
                        competitor.startTime = System.currentTimeMillis() / 1000;
                        new UpdateCompetitorTask().execute(competitor);
                        dialogInterface.dismiss();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getText(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        alertDialog.show();
    }
}
