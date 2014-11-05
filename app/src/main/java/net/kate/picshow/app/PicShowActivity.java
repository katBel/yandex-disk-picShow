package net.kate.picshow.app;

/**
 * Created by Stille on 26.10.2014.
 */

import android.accounts.*;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import com.yandex.disk.client.ListItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PicShowActivity extends FragmentActivity {
    private static final String TAG = "Activity";

    public static final String FRAGMENT_TAG = "mainList";
    public static final String SHOW_TAG = "showFragment";

    private static final int GET_ACCOUNT_CREDS_INTENT = 100;

    public static final String CLIENT_ID = "8b4bce688ed341c4b24213eb78e91ccf";
    public static final String CLIENT_SECRET = "1d800af1160c4687861a7c951342e896";

    public static final String ACCOUNT_TYPE = "com.yandex";
    public static final String AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id="+CLIENT_ID;
    private static final String ACTION_ADD_ACCOUNT = "com.yandex.intent.ADD_ACCOUNT";
    private static final String KEY_CLIENT_SECRET = "clientSecret";

    public static String USERNAME = "picShow.username";
    public static String TOKEN = "picShow.token";

    private final static int DEFAULT_INTERVAL = 2;

    private int interval;
    private List<ListItem> toShow;

    public void addItemToShow(ListItem item) {
        toShow.add(item);
    }

    public void removeItemFromShow(ListItem item) {
        toShow.remove(item);
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "PicShowActivity onCreate");
        super.onCreate(savedInstanceState);
        interval = DEFAULT_INTERVAL;
        toShow = new ArrayList<ListItem>();

        if (getIntent() != null && getIntent().getData() != null) {
            onLogin();
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String token = preferences.getString(TOKEN, null);
        Log.d(TAG, "Token = " + token);
        if (token == null) {
            if (AccountManager.get(getApplicationContext()).getAccountsByType(ACCOUNT_TYPE).length > 0) {
                new AccountDialogFragment().show(getSupportFragmentManager(), "account");
            } else {
                addAccount();
            }
            return;
        }

        if (savedInstanceState == null) {
            startFragment();
        }
    }

    private void startFragment() {
        Log.d(TAG, "startFragment");
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new ListPicFragment(), FRAGMENT_TAG)
                .commit();
    }


    private void onLogin () {
        Uri data = getIntent().getData();
        setIntent(null);
        Log.d(TAG, "onLogin: data from OAuth-server = " + data.toString());
        Pattern pattern = Pattern.compile("access_token=(.*?)(&|$)");
        Matcher matcher = pattern.matcher(data.toString());
        if (matcher.find()) {
            final String token = matcher.group(1);
            if (!TextUtils.isEmpty(token)) {
                Log.d(TAG, "onLogin: token: "+token);
                saveToken(token);
            } else {
                Log.w(TAG, "onRegistrationSuccess: empty token");
            }
        } else {
            Log.w(TAG, "onRegistrationSuccess: token not found in return url");
        }
    }

    private void saveToken(String token) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(USERNAME, "");
        editor.putString(TOKEN, token);
        editor.commit();
    }

    public void reloadContent() {
        ListPicFragment fragment = (ListPicFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        fragment.restartLoader();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_ACCOUNT_CREDS_INTENT) {
            if (resultCode == RESULT_OK) {
                Bundle bundle = data.getExtras();
                String name = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                String type = bundle.getString(AccountManager.KEY_ACCOUNT_TYPE);
                Log.d(TAG, "GET_ACCOUNT_CREDS_INTENT: name="+name+" type="+type);
                Account account = new Account(name, type);
                getAuthToken(account);
            }
        }
    }

    private void getAuthToken(Account account) {
        Log.d(TAG, "getAuthToken");
        AccountManager systemAccountManager = AccountManager.get(getApplicationContext());
        Bundle options = new Bundle();
        options.putString(KEY_CLIENT_SECRET, CLIENT_SECRET);
        systemAccountManager.getAuthToken(account, CLIENT_ID, options, this, new GetAuthTokenCallback(), null);
    }

    private void invalidateAuthToken(String authToken) {
        AccountManager systemAccountManager = AccountManager.get(getApplicationContext());
        systemAccountManager.invalidateAuthToken(ACCOUNT_TYPE, authToken);
    }

    public void updateToken() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String token = preferences.getString(TOKEN, null);
        invalidateAuthToken(token);
    }

    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
        public void run(AccountManagerFuture<Bundle> result) {
            Log.d(TAG, "GetAuthTokenCallback");
            try {
                Bundle bundle = result.getResult();
                Log.d(TAG, "bundle: "+bundle);

                String message = (String) bundle.get(AccountManager.KEY_ERROR_MESSAGE);
                if (message != null) {
                    Toast.makeText(PicShowActivity.this, message, Toast.LENGTH_LONG).show();
                }

                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                Log.d(TAG, "intent: "+intent);
                if (intent != null) {
                    // User input required
                    startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                } else {
                    String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    Log.d(TAG, "GetAuthTokenCallback: token="+token);
                    saveToken(token);
                    startFragment();
                }
            } catch (OperationCanceledException ex) {
                Log.d(TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(PicShowActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            } catch (AuthenticatorException ex) {
                Log.d(TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(PicShowActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            } catch (IOException ex) {
                Log.d(TAG, "GetAuthTokenCallback", ex);
                Toast.makeText(PicShowActivity.this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void addAccount() {
        Log.d(TAG, "addAccount");
        AccountManager accountManager = AccountManager.get(getApplicationContext());
        for (AuthenticatorDescription authDesc : accountManager.getAuthenticatorTypes()) {
            if (ACCOUNT_TYPE.equals(authDesc.type)) {
                Log.d(TAG, "Starting " + ACTION_ADD_ACCOUNT);
                Intent intent = new Intent(ACTION_ADD_ACCOUNT);
                startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                return;
            }
        }
        new AuthDialogFragment().show(getSupportFragmentManager(), "auth");
    }

    public void changeAccount(Account account) {
        Log.d(TAG, "changeAccount");
        if (account == null) {
            addAccount();
        } else {
            //here should be check of token's validity
            getAuthToken(account);
        }
    }

    public static class AuthDialogFragment extends DialogFragment {

        public AuthDialogFragment () {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Log.d(TAG, "AuthDialog is opened");
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.auth_title)
                    .setMessage(R.string.auth_message)
                    .setPositiveButton(R.string.auth_positive_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Log.d(TAG, "We are going to start browser");
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL)));
                        }
                    })
                    .setNegativeButton(R.string.auth_negative_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick (DialogInterface dialog, int which) {
                            dialog.dismiss();
                            getActivity().finish();
                        }
                    })
                    .create();
        }
    }

    public static class IntervalDialogFragment extends DialogFragment {
        public IntervalDialogFragment() {
            super();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Log.d(TAG, "IntervalDialog is opened");
            final EditText intervalValue = new EditText(getActivity());
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.interval_title)
                    .setMessage(R.string.interval_message)
                    .setView(intervalValue)
                    .setPositiveButton(R.string.interval_positive_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String setTime = intervalValue.getText().toString();
                            Log.d(TAG, "setTime = " + setTime);
                            dialogInterface.dismiss();
                            try {
                                int time = Integer.parseInt(setTime);
                                Log.d(TAG, "Interval is going to be set to " + time);
                                ((PicShowActivity) getActivity()).setFlippingInterval(time);
                            } catch (NumberFormatException ex) {
                                Toast.makeText(getActivity(), R.string.interval_invalid, Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton(R.string.interval_negative_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .create();
        }
    }

    public void setFlippingInterval(int time) {
        Log.d(TAG, "Set flipping interval = " + Integer.toString(time));
        interval = time;
    }

    public void startShow() {
        if (toShow.size() == 0) {
            Toast.makeText(this, "No images are selected", Toast.LENGTH_SHORT).show();
            return;
        }
        ShowFragment showFragment = new ShowFragment();
        showFragment.setListForShow(new ArrayList<ListItem>(toShow));
        showFragment.setInterval(interval);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, showFragment, SHOW_TAG)
                .addToBackStack(null)
                .commit();
        Log.d(TAG, "Start show!");
    }
}