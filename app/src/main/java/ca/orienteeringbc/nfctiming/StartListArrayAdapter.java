package ca.orienteeringbc.nfctiming;

import java.util.List;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
/**
 * Created by jon on 15/03/18.
 * Based roughly on http://www.vogella.com/tutorials/AndroidListView/article.html#listsactivity_performance
 */

public class StartListArrayAdapter extends ArrayAdapter<Competitor> {

    private final List<Competitor> list;
    private final List<WjrCategory> categories;
    private final Activity context;
    private final WjrDatabase database;

    StartListArrayAdapter(Activity context, List<Competitor> list, List<WjrCategory> categories) {
        super(context, R.layout.list_start, list);
        this.context = context;
        this.list = list;
        this.categories = categories;

        // Initialize database
        database = Room.databaseBuilder(context.getApplicationContext(), WjrDatabase.class, MainActivity.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .addMigrations(WjrDatabase.MIGRATION_1_2)
                .build();
    }

    static class ViewHolder {
        TextView textView;
        Spinner spinner;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view;
        if (convertView == null) {
            LayoutInflater inflater = context.getLayoutInflater();
            view = inflater.inflate(R.layout.list_start, null);
            final ViewHolder viewHolder = new ViewHolder();
            viewHolder.textView = view.findViewById(R.id.label);
            viewHolder.spinner = view.findViewById(R.id.cat_spinner);
            ArrayAdapter<WjrCategory> catAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, categories);
            viewHolder.spinner.setAdapter(catAdapter);
            viewHolder.spinner.setOnItemSelectedListener(
                    new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                            Competitor competitor = (Competitor) viewHolder.spinner.getTag();
                            int newId = categories.get(i).wjrCategoryId;
                            if (competitor.wjrCategoryId != newId) {
                                Log.d("Update", "Updating competitor's class");
                                competitor.wjrCategoryId = newId;
                                new UpdateCompetitorTask(database).execute((Competitor) viewHolder.spinner.getTag());
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> adapterView) {
                            // Don't do anything...
                        }
                    }
            );
            view.setTag(viewHolder);
            viewHolder.spinner.setTag(list.get(position));
        } else {
            view = convertView;
            ViewHolder holder = (ViewHolder) view.getTag();
            holder.spinner.setTag(list.get(position));
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        Competitor competitor = list.get(position);
        holder.textView.setText(competitor.toString());
        holder.spinner.setSelection(indexFromId(competitor.wjrCategoryId));
        return view;
    }

    private int indexFromId(int id) {
        for (int ret = 0; ret < categories.size(); ret++ ) {
            if (categories.get(ret).wjrCategoryId == id)
                return ret;
        }
        return 0;
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
