package com.flipboard.psyncsample;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {

    private static final String NEWLINE = "\n";
    private static final String TAB = "\t";

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        // Normally you would do this in your Application class
        P.init(getActivity().getApplication());

        TextView display = (TextView) view.findViewById(R.id.display);

        String text = new StringBuilder()
                .append("Server category:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.categoryServer.key).append(NEWLINE)
                .append(NEWLINE)
                .append("Number of columns:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.numberOfColumns.key).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.numberOfColumns.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Number of rows:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.numberOfRows.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.numberOfRows.defaultResId).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.numberOfRows.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Primary color:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.primaryColor.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.primaryColor.defaultResId).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.primaryColor.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Request agent:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.requestAgent.key).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.requestAgent.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Request types:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.requestTypes.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.requestTypes.defaultResId).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.requestTypes.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Server url:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.serverUrl.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.serverUrl.defaultResId).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.serverUrl.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Show images:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.showImages.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.showImages.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .append("Use inputs:")
                .append(NEWLINE)
                .append(TAB).append("key: ").append(P.useInputs.key).append(NEWLINE)
                .append(TAB).append("defaultResId: ").append(P.useInputs.defaultResId).append(NEWLINE)
                .append(TAB).append("defaultValue: ").append(P.useInputs.defaultValue()).append(NEWLINE)
                .append(NEWLINE)
                .toString();

        display.setVisibility(View.VISIBLE);
        display.setText(text);
        return view;
    }
}
