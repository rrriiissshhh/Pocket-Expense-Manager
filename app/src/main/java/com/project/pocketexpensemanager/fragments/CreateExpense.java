package com.project.pocketexpensemanager.fragments;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.app.Fragment;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.project.pocketexpensemanager.HomeActivity;
import com.project.pocketexpensemanager.R;
import com.project.pocketexpensemanager.utilities.Constants;
import com.project.pocketexpensemanager.database.DatabaseHelper;
import com.project.pocketexpensemanager.database.tables.CategoryTable;
import com.project.pocketexpensemanager.database.tables.ExpenseAmountTable;
import com.project.pocketexpensemanager.database.tables.ExpenseTable;
import com.project.pocketexpensemanager.database.tables.LogTable;
import com.project.pocketexpensemanager.database.tables.ReserveTable;
import com.project.pocketexpensemanager.fragments.communication.Display;

import java.util.Calendar;

import static com.project.pocketexpensemanager.utilities.Constants.SEPARATOR;

public class CreateExpense extends Fragment {
    private Display mDisplay;
    private DatabaseHelper dbHelper;
    private Cursor categoryCursor, mopCursor, expenseCursor;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.create_expense, container, false);
        final View dateText = view.findViewById(R.id.date_text);
        final View descriptionText = view.findViewById(R.id.description_text);
        final View categorySpinner = view.findViewById(R.id.category_spinner);

        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        SQLiteDatabase mDb = dbHelper.getReadableDatabase();

        //DescriptionText
        descriptionText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent keyEvent) {
                if (keyCode == 66 && keyEvent.getAction() == KeyEvent.ACTION_UP && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                return false;
            }
        });

        // Category picker
        int[] adapterRowViews = new int[]{android.R.id.text1};
        categoryCursor = mDb.rawQuery("SELECT * FROM " + CategoryTable.TABLE_NAME + " where " + CategoryTable.COLUMN_ACTIVE + " = ? ;", new String[]{String.valueOf(Constants.ACTIVATED)});
        SimpleCursorAdapter categorySca = new SimpleCursorAdapter(getActivity(), android.R.layout.simple_spinner_item,
                categoryCursor, new String[]{CategoryTable.COLUMN_TYPE}, adapterRowViews, 0);
        categorySca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) categorySpinner).setAdapter(categorySca);

        mDb.close();
        // Date Picker
        ((EditText) dateText).setInputType(InputType.TYPE_NULL);
        dateText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    Calendar currentDate = Calendar.getInstance();
                    int mYear = currentDate.get(Calendar.YEAR);
                    int mMonth = currentDate.get(Calendar.MONTH);
                    int mDay = currentDate.get(Calendar.DAY_OF_MONTH);

                    DatePickerDialog mDatePicker = new DatePickerDialog(getActivity(), new DatePickerDialog.OnDateSetListener() {
                        public void onDateSet(DatePicker datepicker, int selectedyear, int selectedmonth, int selectedday) {
                            String date = String.valueOf(selectedday) + SEPARATOR + String.valueOf(selectedmonth + 1) + SEPARATOR + String.valueOf(selectedyear);
                            ((EditText) view.findViewById(R.id.date_text)).setText(mDisplay.parseDate(date));
                        }
                    }, mYear, mMonth, mDay);
                    mDatePicker.setTitle("Select date");
                    mDatePicker.show();
                    categorySpinner.performClick();
                }
            }
        });

        view.findViewById(R.id.fab_save_expxense).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveExpense(view);
            }
        });

        view.findViewById(R.id.add_payment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addDetailedPayment(view);
            }
        });

        descriptionText.requestFocus();
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

        return view;
    }

    private void saveExpense(View view) {
        String date = ((EditText) view.findViewById(R.id.date_text)).getText().toString();
        String category = ((TextView) ((Spinner) view.findViewById(R.id.category_spinner)).getSelectedView()).getText().toString();
        String description = ((EditText) view.findViewById(R.id.description_text)).getText().toString();
        String paymentOption = ((TextView) view.findViewById(R.id.amount_text)).getText().toString();
        if (date.equals("")) {
            HomeActivity.showMessage(getActivity(), "Date field cannot be empty");
        } else if (category.equals("")) {
            HomeActivity.showMessage(getActivity(), "No categories chosen. Create categories first");
        } else if (description.equals("")) {
            HomeActivity.showMessage(getActivity(), "Description field cannot be empty");
        } else if (paymentOption.equals(getString(R.string.method_of_payment))) {
            HomeActivity.showMessage(getActivity(), "Select a payment option");
        } else {
            SQLiteDatabase mDb = dbHelper.getWritableDatabase();
            mDb.execSQL("insert into " + ExpenseTable.TABLE_NAME + " (" +
                            ExpenseTable.COLUMN_DATE + "," +
                            ExpenseTable.COLUMN_CATEGORY + "," +
                            ExpenseTable.COLUMN_DESCRIPTION +
                            ") " + " values (?, ?, ?);",
                    new String[]{date, category, description});

            expenseCursor = mDb.rawQuery("SELECT _id from " + ExpenseTable.TABLE_NAME + " order by _id DESC limit 1;", null);
            if (expenseCursor.moveToFirst()) {
                String[] mop = paymentOption.split(", ");
                String id = expenseCursor.getString(0);
                float amt = 0f;
                for (String payment : mop) {
                    String reserve = payment.split(SEPARATOR)[0];
                    String amount = payment.split(SEPARATOR)[1];
                    amt += Float.valueOf(amount);
                    mDb.execSQL("insert into " + ExpenseAmountTable.TABLE_NAME + " (" +
                                    ExpenseAmountTable.COLUMN_EXPENSE_ID + "," +
                                    ExpenseAmountTable.COLUMN_MOP + "," +
                                    ExpenseAmountTable.COLUMN_AMOUNT +
                                    ") " + " values (?, ?, ?);",
                            new String[]{id, reserve, amount});
                }

                Calendar calendar = Calendar.getInstance();
                String currentDate = mDisplay.parseDate(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)) + SEPARATOR +
                        String.valueOf(calendar.get(Calendar.MONTH) + 1) + SEPARATOR + String.valueOf(calendar.get(Calendar.YEAR)));
                mDb.execSQL("insert into " + LogTable.TABLE_NAME + " (" +
                                LogTable.COLUMN_TITLE + "," +
                                LogTable.COLUMN_DESCRIPTION_MAIN + "," +
                                LogTable.COLUMN_DESCRIPTION_SUB + "," +
                                LogTable.COLUMN_AMOUNT + "," +
                                LogTable.COLUMN_HIDDEN_ID + "," +
                                LogTable.COLUMN_LOG_DATE + "," +
                                LogTable.COLUMN_EVENT_DATE + "," +
                                LogTable.COLUMN_TYPE + ") " + " values (?, ?, ?, ?, ?, ?, ?, ?);",
                        new String[]{category, description, "Expense Created", String.valueOf(amt), id, currentDate, date,
                                ExpenseTable.TABLE_NAME});
            }

            mDb.close();
            mDisplay.displayFragment(HomeActivity.SEE_LOG);
        }
    }

    public View getViewByPosition(int pos, ListView listView) {
        final int firstListItemPosition = listView.getFirstVisiblePosition();
        final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1;

        if (pos < firstListItemPosition || pos > lastListItemPosition ) {
            return listView.getAdapter().getView(pos, null, listView);
        } else {
            final int childIndex = pos - firstListItemPosition;
            return listView.getChildAt(childIndex);
        }
    }

    private void addDetailedPayment(final View view) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.payment_detail_dialog, null);
        builderSingle.setView(dialogView);
        builderSingle.setTitle("Amount and Method Of Payment:-");

        SQLiteDatabase mDb = dbHelper.getReadableDatabase();
        mopCursor = mDb.rawQuery("SELECT * FROM " + ReserveTable.TABLE_NAME + " where " + ReserveTable.COLUMN_ACTIVE + " = ? ;", new String[]{String.valueOf(Constants.ACTIVATED)});
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(getActivity(), R.layout.payment_detail_list_item,
                mopCursor, new String[]{ReserveTable.COLUMN_TYPE}, new int[]{R.id.mop_caption}, 0);
        adapter.setDropDownViewResource(R.layout.payment_detail_list_item);
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(dialogView.findViewById(R.id.amount_text), InputMethodManager.SHOW_IMPLICIT);
        final ListView paymentList = (ListView) dialogView.findViewById(R.id.payment_list);
        paymentList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View v, int i, long l) {
                v.findViewById(R.id.mop_amount).requestFocus();
            }
        });
        paymentList.setAdapter(adapter);

        builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builderSingle.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String finalString = "";
                boolean isValueZero = true;
                for (int i = 0; i < mopCursor.getCount(); i++) {
                    View child = getViewByPosition(i,paymentList);
                    String reserve = ((TextView) child.findViewById(R.id.mop_caption)).getText().toString();
                    String amount = ((EditText) child.findViewById(R.id.mop_amount)).getText().toString();
                    if (amount.equals("")) {
                        amount = "0";
                    }
                    try{
                        if(Float.valueOf(amount) != 0){
                            isValueZero = false;
                        }
                    } catch (NumberFormatException e){
                        HomeActivity.showMessage(getActivity(),"Enter valid payment");
                        return;
                    }
                    finalString += reserve + SEPARATOR + amount + ", ";
                }
                finalString = finalString.substring(0, finalString.length() - 2);
                if(isValueZero){
                    HomeActivity.showMessage(getActivity(),"Select atleast one reserve for payment");
                } else{
                    ((TextView) view.findViewById(R.id.amount_text)).setText(finalString);
                    dialog.dismiss();
                }
            }
        });
        AlertDialog b = builderSingle.create();
        b.show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mDisplay = (Display) context;
            dbHelper = DatabaseHelper.getInstance(getActivity());
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString());
        }
    }

    @Override
    public void onDetach() {
        if (categoryCursor != null && !categoryCursor.isClosed())
            categoryCursor.close();
        if (expenseCursor != null && !expenseCursor.isClosed())
            expenseCursor.close();
        if (mopCursor != null && !mopCursor.isClosed())
            mopCursor.close();
        super.onDetach();
    }

}
