package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

/**
 * Created by jon on 16/03/18.
 * ArrayAdapter extension for the results fragment
 */

public class FinishArrayAdapter extends ArrayAdapter<Competitor> {
    private final List<Competitor> competitorList;
    private final Activity context;
    private final WjrDatabase database;


    FinishArrayAdapter(Activity context, List<Competitor> competitorList) {
        super(context, R.layout.list_result, competitorList);
        this.competitorList = competitorList;
        this.context = context;

        // Initialize database
        database = Room.databaseBuilder(context.getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addMigrations(WjrDatabase.MIGRATION_1_2)
                .build();
    }

    static class ViewHolder {
        TextView nameHolder;
        TextView statusHolder;
        Spinner spinner;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Competitor competitor = competitorList.get(position);
        View view;
        if (convertView == null) {
            LayoutInflater inflater = context.getLayoutInflater();
            view = inflater.inflate(R.layout.list_result, null);
            final FinishArrayAdapter.ViewHolder viewHolder = new FinishArrayAdapter.ViewHolder();
            viewHolder.nameHolder = view.findViewById(R.id.name_field);
            viewHolder.statusHolder = view.findViewById(R.id.status_field);
            viewHolder.spinner = view.findViewById(R.id.status_spinner);
            ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, Competitor.statuses);
            viewHolder.spinner.setAdapter(statusAdapter);
            viewHolder.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    Competitor competitor = (Competitor) viewHolder.spinner.getTag();
                    if (competitor.status != i) {
                        competitor.status = i;
                        new UpdateCompetitorTask(database).execute((Competitor) viewHolder.spinner.getTag());
                        Log.d("Success:", "Updating competitor's status");
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    // Do nothing
                }
            });

            view.setTag(viewHolder);
        } else {
            view = convertView;
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        holder.spinner.setTag(competitorList.get(position));
        holder.nameHolder.setText(competitor.toString());

        // Select proper status
        if (competitor.status == 0)
            holder.spinner.setEnabled(false);
        else
            holder.spinner.setEnabled(true);
        holder.spinner.setSelection(competitor.status);

        if (competitor.startTime == 0) {
            // Not started
            holder.statusHolder.setText("");
        } else if (competitor.endTime == 0 && competitor.startTime > 0) {
            // Competitor has started, but has yet to finish
            holder.statusHolder.setText(R.string.status_on_course);
        } else {
            // Competitor has finished, show time
            holder.statusHolder.setText(DateUtils.formatElapsedTime(competitor.endTime - competitor.startTime));
        }

        return view;
    }

    private static class UpdateCompetitorTask extends AsyncTask<Competitor, Void, Boolean> {
        private WjrDatabase database;

        UpdateCompetitorTask(WjrDatabase database) {
            this.database = database;
        }

        @Override
        protected  Boolean doInBackground(Competitor... competitor) {
            database.daoAccess().updateCompetitor(competitor);
            return true;
        }
    }
}
