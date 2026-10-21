package com.gathin.tvhome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The news cards on Home (news_row): one headline each, its source above and its age under it.
 * The row is horizontal and never scrolls on its own; the remote moves focus across it and the
 * RecyclerView brings the focused card into view.
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.Holder> {
    interface Listener { void onOpen(NewsFeed.Headline headline); }

    private final List<NewsFeed.Headline> items = new ArrayList<>();
    private final Listener listener;

    NewsAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<NewsFeed.Headline> headlines) {
        items.clear();
        items.addAll(headlines);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NewsFeed.Headline item = items.get(position);
        holder.source.setText(item.source);
        holder.title.setText(item.title);
        if (item.published > 0) {
            holder.age.setText(android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.published, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
            holder.age.setVisibility(View.VISIBLE);
        } else {
            holder.age.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> listener.onOpen(item));
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView source, title, age;

        Holder(@NonNull View itemView) {
            super(itemView);
            source = itemView.findViewById(R.id.newsSource);
            title = itemView.findViewById(R.id.newsTitle);
            age = itemView.findViewById(R.id.newsAge);
        }
    }
}
