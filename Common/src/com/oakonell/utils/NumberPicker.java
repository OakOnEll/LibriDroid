package com.oakonell.utils;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Simple integer input with +/- buttons
 */
public class NumberPicker extends LinearLayout {
    private OnChangedListener mListener;

    private EditText text;
    private Button increment;
    private Button decrement;

    private int number;

    public interface OnChangedListener {
        void onChanged(NumberPicker picker, int value);
    }

    public NumberPicker(Context context) {
        this(context, null);
    }

    public NumberPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        // setOrientation(HORIZONTAL);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View inflate = inflater.inflate(R.layout.number_picker, this, true);
        increment = (Button) inflate.findViewById(R.id.increment);
        increment.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                number++;
                notifyChange();
                updateView();
            }
        });
        // increment.setNumberPicker(this);

        decrement = (Button) inflate.findViewById(R.id.decrement);
        decrement.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                number--;
                notifyChange();
                updateView();
            }
        });

        text = (EditText) inflate.findViewById(R.id.text);
        // mText.setOnFocusChangeListener(this);
        // mText.setOnEditorActionListener(this);
        // mText.setFilters(new InputFilter[] { inputFilter });
        text.setInputType(InputType.TYPE_CLASS_NUMBER);
        text.setText("" + number);

        text.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int val = Integer.parseInt(s.toString());
                    number = val;
                } catch (NumberFormatException e) {
                    text.setError("Must be an integer");
                }

            }
        });

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    public void setOnChangeListener(OnChangedListener listener) {
        mListener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        increment.setEnabled(enabled);
        decrement.setEnabled(enabled);
        text.setEnabled(enabled);
    }

    protected void notifyChange() {
        if (mListener != null) {
            mListener.onChanged(this, number);
        }
    }

    protected void updateView() {
        text.setText(number + "");
        text.setSelection(text.getText().length());
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
        updateView();
    }
}
