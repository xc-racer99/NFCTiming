package ca.orienteeringbc.nfctiming;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by jon on 16/03/18.
 * ArrayAdapter extension for the results fragment
 */

public class FinishArrayAdapter extends ArrayAdapter<Competitor> {
    private final List<Competitor> competitorList;
    private final Activity context;

    FinishArrayAdapter(Activity context, List<Competitor> competitorList) {
        super(context, R.layout.list_result, competitorList);
        this.competitorList = competitorList;
        this.context = context;
    }

    static class ViewHolder {
        TextView nameHolder;
        TextView statusHolder;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view = null;
        if (convertView == null) {
            LayoutInflater inflater = context.getLayoutInflater();
            view = inflater.inflate(R.layout.list_result, null);
            final FinishArrayAdapter.ViewHolder viewHolder = new FinishArrayAdapter.ViewHolder();
            viewHolder.nameHolder = view.findViewById(R.id.name_field);
            viewHolder.statusHolder = view.findViewById(R.id.status_field);

            view.setTag(viewHolder);
        } else {
            view = convertView;
        }

        ViewHolder holder = (ViewHolder) view.getTag();
        Competitor competitor = competitorList.get(position);
        holder.nameHolder.setText(competitor.firstName + " " + competitor.lastName);

        if (competitor.startTime == 0) {
            // Competitor hasn't started
            holder.statusHolder.setText(R.string.status_dns);
        } else if (competitor.endTime == 0) {
            // Competitor has started, but has yet to finish
            holder.statusHolder.setText(R.string.status_dnf);
        } else {
            // Competitor has finished, show time
            holder.statusHolder.setText(String.valueOf(competitor.endTime - competitor.startTime));
        }

        return view;
    }
}
