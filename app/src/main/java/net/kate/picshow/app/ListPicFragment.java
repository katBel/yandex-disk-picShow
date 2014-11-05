package net.kate.picshow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ListItem;

import java.util.List;

/**
 * Created by Stille on 26.10.2014.
 */

public class ListPicFragment extends ListFragment implements LoaderManager.LoaderCallbacks<List<ListItem>>{

    private static String TAG = "ListPicFragment";

    private static final String UNAUTH = "Unauthorized";

    private static final String CURRENT_DIR_KEY = "pic_show.current.dir";
    private static final String ROOT = "/";

    private boolean allSelected;

    private Credentials credentials;
    private String currentDir;

    private ListPicAdapter adapter;


    public void restartLoader() {
        getLoaderManager().initLoader(0, null, this);
    }

    private void setDefaultEmptyText() {
        setEmptyText(getString(R.string.no_images));
    }

    public static class ListPicAdapter extends ArrayAdapter<ListItem> {
        private final LayoutInflater inflater;

        public ListPicAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_multiple_choice);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setData(List<ListItem> data) {
            clear();
            if (data != null) {
                addAll(data);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView == null) {
                view = inflater.inflate(android.R.layout.simple_list_item_activated_2, parent, false);
            } else {
                view = convertView;
            }

            ListItem item = getItem(position);
            ((TextView)view.findViewById(android.R.id.text1)).setText(item.getDisplayName());
            ((TextView)view.findViewById(android.R.id.text2))
                    .setText(item.isCollection() ? "" : "" + item.getContentLength());

            return view;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "LPFrag onActivityCreated");

        setHasOptionsMenu(true);

        adapter = new ListPicAdapter(getActivity());
        setListAdapter(adapter);
        setListShown(false);

        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        getListView().setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (getListView().isItemChecked(position)) {
                    //structure such toShow must be field of activity!!!
                    Log.d(TAG, "The item number " + position + " is checked ");
                    ((PicShowActivity)getActivity()).addItemToShow(adapter.getItem(position));
                } else {
                    Log.d(TAG, "The item number " + position + " is unchecked ");
                    ((PicShowActivity) getActivity()).removeItemFromShow(adapter.getItem(position));
                }
            }
        });
        getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (adapter.getItem(position).isCollection()) {
                    changeDir(adapter.getItem(position).getFullPath());
                    return true;
                }
                return false;
            }
        });

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String username = preferences.getString(PicShowActivity.USERNAME, null);
        String token = preferences.getString(PicShowActivity.TOKEN, null);

        credentials = new Credentials(username, token);

        Bundle args = getArguments();
        if (args != null) {
            currentDir = args.getString(CURRENT_DIR_KEY);
        }
        if (currentDir == null) {
            currentDir = ROOT;
        }
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(!ROOT.equals(currentDir));

        Log.d(TAG, "initLoader");
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.show_action_bar, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
            case R.id.start_show:
                Log.d(TAG, "start from ListFragment");
                ((PicShowActivity) getActivity()).startShow();
                break;
            case R.id.set_interval:
                Log.d(TAG, "settings were clicked");
                new PicShowActivity.IntervalDialogFragment()
                        .show(getActivity().getSupportFragmentManager(), "setInterval");
                break;
            case R.id.set_account:
                Log.d(TAG, "set account");
                new AccountDialogFragment().show(getActivity().getSupportFragmentManager(), "account");
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void onLoaderReset(Loader<List<ListItem>> loader) {
        adapter.setData(null);
    }

    public Loader<List<ListItem>> onCreateLoader(int i, Bundle bundle) {
        Log.d(TAG, "Loader will be created!"
                + " currentDir = " + currentDir + " credentials = " + credentials.toString());
        return new ListPicLoader(getActivity(), credentials, currentDir);
    }

    @Override
    public void onLoadFinished(final Loader<List<ListItem>> loader, List<ListItem> data) {
        Log.d(TAG, "Loading has been finished");
        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
        if (data.isEmpty()) {
            Exception ex = ((ListPicLoader) loader).getException();
            if (ex != null) {
                String message = ((ListPicLoader) loader).getException().getMessage();
                /*if (message.toString().equals(UNAUTH)) {
                    ((PicShowActivity) getActivity()).updateToken(this.getId());
                    //new PicShowActivity.AuthDialogFragment().show(getActivity().getSupportFragmentManager(), "auth");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("picshow:://"));
                    startActivity(intent);
            } else {*/
                setEmptyText(message);
                /*if getMessage() = "Not authorized" then
                *   getActivity().someMethod:
                  * - invalidateAuthToken()
                  * - make an attempt of authorization*/
            } else {
                setDefaultEmptyText();
            }
        } else {
            adapter.setData(data);
        }
    }

    protected void changeDir(String dir) {
        //here I should update structure of files to show
        Bundle args = new Bundle();
        args.putString(CURRENT_DIR_KEY, dir);

        ListPicFragment fragment = new ListPicFragment();
        fragment.setArguments(args);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment, PicShowActivity.FRAGMENT_TAG)
                .addToBackStack(null)
                .commit();
    }


}
