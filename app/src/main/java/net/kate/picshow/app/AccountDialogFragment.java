package net.kate.picshow.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.widget.ArrayAdapter;

/**
 * Created by Stille on 29.10.2014.
 */
public class AccountDialogFragment extends DialogFragment {
    private static String CREATE_ACCOUNT = "Another account";
    private static String TAG = "AccountDialog";
    //private PicShowActivity activity;
    private ArrayAdapter<String> adapter;
    Account[] accounts;


    public AccountDialogFragment() {
        super();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "AccountDialogFragment is being created");

        AccountManager accountManager = AccountManager.get(getActivity().getApplicationContext());
        accounts = accountManager.getAccountsByType(PicShowActivity.ACCOUNT_TYPE);
        int accountsAmount = accounts.length;
        String[] alternatives = new String[accountsAmount + 1];
        int index = 0;
        for (Account account : accounts) {
            alternatives[index++] = account.toString();
        }
        alternatives[index] = CREATE_ACCOUNT;
        adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.select_dialog_singlechoice, alternatives);
        String adapterInfo = String.format("Size of adapter's data = %s, {", Integer.toString(adapter.getCount()));
        for (int i = 0; i < adapter.getCount(); ++i) {
            adapterInfo += adapter.getItem(i) + ", ";
        }
        adapterInfo += "}";
        Log.d(TAG, adapterInfo);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(TAG, "account dialog is being created");
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.account_title)
                .setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        if (adapter.getItem(i).equals(CREATE_ACCOUNT)) {
                            ((PicShowActivity) getActivity()).addAccount();
                        } else {
                            ((PicShowActivity) getActivity()).changeAccount(accounts[i]);
                        }
                    }
                })
                .setNegativeButton(R.string.account_negative_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .create();
    }
}

