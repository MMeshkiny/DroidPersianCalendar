package com.byagowi.persiancalendar.view.dialog;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

/**
 * Created by ebraminio on 2/21/16.
 */
public class AthanNumericDialog extends EditTextPreferenceDialogFragmentCompat {
    public static AthanNumericDialog newInstance(String key) {
        final AthanNumericDialog
                fragment = new AthanNumericDialog();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText editText = (EditText) view.findViewById(android.R.id.edit);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        // on platforms supporting direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            editText.setTextDirection(View.TEXT_DIRECTION_LTR);
            editText.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }
}
