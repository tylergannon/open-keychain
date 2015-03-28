/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.tonicartos.superslim.LayoutManager;
import com.tonicartos.superslim.LinearSLM;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.keyimport.ParcelableKeyRing;
import org.sufficientlysecure.keychain.operations.results.ConsolidateResult;
import org.sufficientlysecure.keychain.operations.results.DeleteResult;
import org.sufficientlysecure.keychain.operations.results.ImportKeyResult;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.KeychainDatabase;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.CloudImportService;
import org.sufficientlysecure.keychain.service.KeychainIntentService;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.service.ServiceProgressHandler;
import org.sufficientlysecure.keychain.ui.adapter.RecyclerCursorAdapter;
import org.sufficientlysecure.keychain.ui.dialog.DeleteKeyDialogFragment;
import org.sufficientlysecure.keychain.ui.dialog.ProgressDialogFragment;
import org.sufficientlysecure.keychain.ui.util.Highlighter;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.util.ExportHelper;
import org.sufficientlysecure.keychain.util.FabContainer;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.Preferences;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Public key list with sticky list headers. It does _not_ extend ListFragment because it uses
 * StickyListHeaders library which does not extend upon ListView.
 */
public class KeyListFragment extends LoaderFragment
        implements SearchView.OnQueryTextListener, LoaderManager.LoaderCallbacks<Cursor>,
        FabContainer, ActionMode.Callback {

    static final int REQUEST_REPEAT_PASSPHRASE = 1;
    static final int REQUEST_ACTION = 2;

    ExportHelper mExportHelper;

    private RecyclerView mRecyclerView;
    private KeyListAdapter mAdapter;

    // saves the mode object for multiselect, needed for reset at some point
    private ActionMode mActionMode = null;

    private String mQuery;

    private FloatingActionsMenu mFab;

    // This ids for multiple key export.
    private ArrayList<Long> mIdsForRepeatAskPassphrase;
    // This index for remembering the number of master key.
    private int mIndex;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportHelper = new ExportHelper(getActivity());
    }

    /**
     * Load custom layout with StickyListView from library
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup superContainer, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, superContainer, savedInstanceState);
        View view = inflater.inflate(R.layout.key_list_fragment, getContainer());

        mRecyclerView = (RecyclerView) view.findViewById(R.id.key_list_recycler);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LayoutManager(getActivity()));

        mFab = (FloatingActionsMenu) view.findViewById(R.id.fab_main);

        FloatingActionButton fabQrCode = (FloatingActionButton) view.findViewById(R.id.fab_add_qr_code);
        FloatingActionButton fabCloud = (FloatingActionButton) view.findViewById(R.id.fab_add_cloud);
        FloatingActionButton fabFile = (FloatingActionButton) view.findViewById(R.id.fab_add_file);

        fabQrCode.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFab.collapse();
                scanQrCode();
            }
        });
        fabCloud.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFab.collapse();
                searchCloud();
            }
        });
        fabFile.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFab.collapse();
                importFile();
            }
        });


        return root;
    }

    /**
     * Define Adapter and Loader on create of Activity
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // show app name instead of "keys" from nav drawer
        getActivity().setTitle(R.string.app_name);

        // We have a menu item to show in action bar.
        setHasOptionsMenu(true);

        // Start out with a progress indicator.
        setContentShown(false);

        // Create an empty adapter we will use to display the loaded data.
        mAdapter = new KeyListAdapter(getActivity());
        mRecyclerView.setAdapter(mAdapter);

        // Prepare the loader. Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    // These are the rows that we will retrieve.
    static final String[] PROJECTION = new String[]{
            KeyRings._ID,
            KeyRings.MASTER_KEY_ID,
            KeyRings.USER_ID,
            KeyRings.IS_REVOKED,
            KeyRings.IS_EXPIRED,
            KeyRings.VERIFIED,
            KeyRings.HAS_ANY_SECRET,
            KeyRings.HAS_DUPLICATE_USER_ID,
    };

    static final int INDEX_MASTER_KEY_ID = 1;
    static final int INDEX_USER_ID = 2;
    static final int INDEX_IS_REVOKED = 3;
    static final int INDEX_IS_EXPIRED = 4;
    static final int INDEX_VERIFIED = 5;
    static final int INDEX_HAS_ANY_SECRET = 6;
    static final int INDEX_HAS_DUPLICATE_USER_ID = 7;

    static final String ORDER =
            KeyRings.HAS_ANY_SECRET + " DESC, UPPER(" + KeyRings.USER_ID + ") ASC";


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created. This
        // sample only has one Loader, so we don't care about the ID.
        Uri baseUri = KeyRings.buildUnifiedKeyRingsUri();
        String where = null;
        String whereArgs[] = null;
        if (mQuery != null) {
            String[] words = mQuery.trim().split("\\s+");
            whereArgs = new String[words.length];
            for (int i = 0; i < words.length; ++i) {
                if (where == null) {
                    where = "";
                } else {
                    where += " AND ";
                }
                where += KeyRings.USER_ID + " LIKE ?";
                whereArgs[i] = "%" + words[i] + "%";
            }
        }

        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), baseUri, PROJECTION, where, whereArgs, ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.setSearchQuery(mQuery);
        mAdapter.swapCursor(data);

        mRecyclerView.setAdapter(mAdapter);

        // this view is made visible if no data is available
        LinearLayout emptyLayout = (LinearLayout) getActivity().findViewById(R.id.key_list_empty);
        if (mAdapter.getItemCount() == 0) {
            mRecyclerView.setVisibility(View.GONE);
            emptyLayout.setVisibility(View.VISIBLE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }

        // end action mode, if any
        if (mActionMode != null) {
            mActionMode.finish();
        }

        // The list should now be shown.
        if (isResumed()) {
            setContentShown(true);
        } else {
            setContentShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    protected void encrypt(ActionMode mode, long[] masterKeyIds) {
        Intent intent = new Intent(getActivity(), EncryptFilesActivity.class);
        intent.setAction(EncryptFilesActivity.ACTION_ENCRYPT_DATA);
        intent.putExtra(EncryptFilesActivity.EXTRA_ENCRYPTION_KEY_IDS, masterKeyIds);
        // used instead of startActivity set actionbar based on callingPackage
        startActivityForResult(intent, REQUEST_ACTION);

        mode.finish();
    }

    /**
     * Show dialog to delete key
     *
     * @param hasSecret must contain whether the list of masterKeyIds contains a secret key or not
     */
    public void showDeleteKeyDialog(final ActionMode mode, long[] masterKeyIds, boolean hasSecret) {
        // Can only work on singular secret keys
        if (hasSecret && masterKeyIds.length > 1) {
            Notify.create(getActivity(), R.string.secret_cannot_multiple,
                    Notify.Style.ERROR).show();
            return;
        }

        // Message is received after key is deleted
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.arg1 == DeleteKeyDialogFragment.MESSAGE_OKAY) {
                    Bundle data = message.getData();
                    if (data != null) {
                        DeleteResult result = data.getParcelable(DeleteResult.EXTRA_RESULT);
                        if (result != null) {
                            result.createNotify(getActivity()).show();
                        }
                    }
                    mode.finish();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        DeleteKeyDialogFragment deleteKeyDialog = DeleteKeyDialogFragment.newInstance(messenger,
                masterKeyIds);

        deleteKeyDialog.show(getActivity().getSupportFragmentManager(), "deleteKeyDialog");
    }


    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.key_list, menu);

        if (Constants.DEBUG) {
            menu.findItem(R.id.menu_key_list_debug_cons).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_read).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_write).setVisible(true);
            menu.findItem(R.id.menu_key_list_debug_first_time).setVisible(true);
        }

        // Get the searchview
        MenuItem searchItem = menu.findItem(R.id.menu_key_list_search);

        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        // Execute this when searching
        searchView.setOnQueryTextListener(this);

        // Erase search result without focus
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {

                // disable swipe-to-refresh
                // mSwipeRefreshLayout.setIsLocked(true);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mQuery = null;
                getLoaderManager().restartLoader(0, null, KeyListFragment.this);

                // enable swipe-to-refresh
                // mSwipeRefreshLayout.setIsLocked(false);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.menu_key_list_create:
                createKey();
                return true;

            case R.id.menu_key_list_export:
                mExportHelper.showExportKeysDialog(null, Constants.Path.APP_DIR_FILE, true);
                return true;

            case R.id.menu_key_list_update_all_keys:
                updateAllKeys();
                return true;

            case R.id.menu_key_list_debug_cons:
                consolidate();
                return true;

            case R.id.menu_key_list_debug_read:
                try {
                    KeychainDatabase.debugBackup(getActivity(), true);
                    Notify.create(getActivity(), "Restored debug_backup.db", Notify.Style.OK).show();
                    getActivity().getContentResolver().notifyChange(KeychainContract.KeyRings.CONTENT_URI, null);
                } catch (IOException e) {
                    Log.e(Constants.TAG, "IO Error", e);
                    Notify.create(getActivity(), "IO Error " + e.getMessage(), Notify.Style.ERROR).show();
                }
                return true;

            case R.id.menu_key_list_debug_write:
                try {
                    KeychainDatabase.debugBackup(getActivity(), false);
                    Notify.create(getActivity(), "Backup to debug_backup.db completed", Notify.Style.OK).show();
                } catch (IOException e) {
                    Log.e(Constants.TAG, "IO Error", e);
                    Notify.create(getActivity(), "IO Error: " + e.getMessage(), Notify.Style.ERROR).show();
                }
                return true;

            case R.id.menu_key_list_debug_first_time:
                Preferences prefs = Preferences.getPreferences(getActivity());
                prefs.setFirstTime(true);
                Intent intent = new Intent(getActivity(), CreateKeyActivity.class);
                intent.putExtra(CreateKeyActivity.EXTRA_FIRST_TIME, true);
                startActivity(intent);
                getActivity().finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        Log.d(Constants.TAG, "onQueryTextChange s:" + s);
        // Called when the action bar search text has changed.  Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        // If the nav drawer is opened, onQueryTextChange("") is executed.
        // This hack prevents restarting the loader.
        // TODO: better way to fix this?
        String tmp = (mQuery == null) ? "" : mQuery;
        if (!s.equals(tmp)) {
            mQuery = s;
            getLoaderManager().restartLoader(0, null, this);
        }
        return true;
    }

    private void searchCloud() {
        Intent importIntent = new Intent(getActivity(), ImportKeysActivity.class);
        importIntent.putExtra(ImportKeysActivity.EXTRA_QUERY, (String) null); // hack to show only cloud tab
        startActivity(importIntent);
    }

    private void scanQrCode() {
        Intent scanQrCode = new Intent(getActivity(), ImportKeysProxyActivity.class);
        scanQrCode.setAction(ImportKeysProxyActivity.ACTION_SCAN_IMPORT);
        startActivityForResult(scanQrCode, REQUEST_ACTION);
    }

    private void importFile() {
        Intent intentImportExisting = new Intent(getActivity(), ImportKeysActivity.class);
        intentImportExisting.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_FILE_AND_RETURN);
        startActivityForResult(intentImportExisting, REQUEST_ACTION);
    }

    private void createKey() {
        Intent intent = new Intent(getActivity(), CreateKeyActivity.class);
        startActivityForResult(intent, REQUEST_ACTION);
    }

    private void updateAllKeys() {
        Context context = getActivity();

        ProviderHelper providerHelper = new ProviderHelper(context);

        Cursor cursor = providerHelper.getContentResolver().query(
                KeyRings.buildUnifiedKeyRingsUri(), new String[]{
                        KeyRings.FINGERPRINT
                }, null, null, null
        );

        ArrayList<ParcelableKeyRing> keyList = new ArrayList<>();

        while (cursor.moveToNext()) {
            byte[] blob = cursor.getBlob(0);//fingerprint column is 0
            String fingerprint = KeyFormattingUtils.convertFingerprintToHex(blob);
            ParcelableKeyRing keyEntry = new ParcelableKeyRing(fingerprint, null, null);
            keyList.add(keyEntry);
        }

        ServiceProgressHandler serviceHandler = new ServiceProgressHandler(
                getActivity(),
                getString(R.string.progress_updating),
                ProgressDialog.STYLE_HORIZONTAL,
                true,
                ProgressDialogFragment.ServiceType.CLOUD_IMPORT) {
            public void handleMessage(Message message) {
                // handle messages by standard KeychainIntentServiceHandler first
                super.handleMessage(message);

                if (message.arg1 == MessageStatus.OKAY.ordinal()) {
                    // get returned data bundle
                    Bundle returnData = message.getData();
                    if (returnData == null) {
                        return;
                    }
                    final ImportKeyResult result =
                            returnData.getParcelable(OperationResult.EXTRA_RESULT);
                    if (result == null) {
                        Log.e(Constants.TAG, "result == null");
                        return;
                    }

                    result.createNotify(getActivity()).show();
                }
            }
        };

        // Send all information needed to service to query keys in other thread
        Intent intent = new Intent(getActivity(), CloudImportService.class);

        // fill values for this action
        Bundle data = new Bundle();

        // search config
        {
            Preferences prefs = Preferences.getPreferences(getActivity());
            Preferences.CloudSearchPrefs cloudPrefs =
                    new Preferences.CloudSearchPrefs(true, true, prefs.getPreferredKeyserver());
            data.putString(CloudImportService.IMPORT_KEY_SERVER, cloudPrefs.keyserver);
        }

        data.putParcelableArrayList(CloudImportService.IMPORT_KEY_LIST, keyList);

        intent.putExtra(CloudImportService.EXTRA_DATA, data);

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(serviceHandler);
        intent.putExtra(CloudImportService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        serviceHandler.showProgressDialog(getActivity());

        // start service with intent
        getActivity().startService(intent);
    }

    private void consolidate() {
        // Message is received after importing is done in KeychainIntentService
        ServiceProgressHandler saveHandler = new ServiceProgressHandler(
                getActivity(),
                getString(R.string.progress_importing),
                ProgressDialog.STYLE_HORIZONTAL,
                ProgressDialogFragment.ServiceType.KEYCHAIN_INTENT) {
            public void handleMessage(Message message) {
                // handle messages by standard KeychainIntentServiceHandler first
                super.handleMessage(message);

                if (message.arg1 == MessageStatus.OKAY.ordinal()) {
                    // get returned data bundle
                    Bundle returnData = message.getData();
                    if (returnData == null) {
                        return;
                    }
                    final ConsolidateResult result =
                            returnData.getParcelable(OperationResult.EXTRA_RESULT);
                    if (result == null) {
                        return;
                    }

                    result.createNotify(getActivity()).show();
                }
            }
        };

        // Send all information needed to service to import key in other thread
        Intent intent = new Intent(getActivity(), KeychainIntentService.class);

        intent.setAction(KeychainIntentService.ACTION_CONSOLIDATE);

        // fill values for this action
        Bundle data = new Bundle();

        intent.putExtra(KeychainIntentService.EXTRA_DATA, data);

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(KeychainIntentService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(getActivity());

        // start service with intent
        getActivity().startService(intent);
    }

    private void showMultiExportDialog(long[] masterKeyIds) {
        mIdsForRepeatAskPassphrase = new ArrayList<Long>();
        for(long id: masterKeyIds) {
            try {
                if (PassphraseCacheService.getCachedPassphrase(
                        getActivity(), id, id) == null) {
                    mIdsForRepeatAskPassphrase.add(Long.valueOf(id));
                }
            } catch (PassphraseCacheService.KeyNotFoundException e) {
                // This happens when the master key is stripped
                // and ignore this key.
                continue;
            }
        }
        mIndex = 0;
        if (mIdsForRepeatAskPassphrase.size() != 0) {
            startPassphraseActivity();
            return;
        }
        long[] idsForMultiExport = new long[mIdsForRepeatAskPassphrase.size()];
        for(int i=0; i<mIdsForRepeatAskPassphrase.size(); ++i) {
            idsForMultiExport[i] = mIdsForRepeatAskPassphrase.get(i).longValue();
        }
        mExportHelper.showExportKeysDialog(idsForMultiExport,
                Constants.Path.APP_DIR_FILE,
                mAdapter.isAnySecretSelected());
    }

    private void startPassphraseActivity() {
        Intent intent = new Intent(getActivity(), PassphraseDialogActivity.class);
        long masterKeyId = mIdsForRepeatAskPassphrase.get(mIndex++).longValue();
        intent.putExtra(PassphraseDialogActivity.EXTRA_SUBKEY_ID, masterKeyId);
        startActivityForResult(intent, REQUEST_REPEAT_PASSPHRASE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_REPEAT_PASSPHRASE) {
            if(resultCode != Activity.RESULT_OK) {
                return;
            }
            if (mIndex < mIdsForRepeatAskPassphrase.size()) {
                startPassphraseActivity();
                return;
            }
            long[] idsForMultiExport = new long[mIdsForRepeatAskPassphrase.size()];
            for(int i=0; i<mIdsForRepeatAskPassphrase.size(); ++i) {
                idsForMultiExport[i] = mIdsForRepeatAskPassphrase.get(i).longValue();
            }
            mExportHelper.showExportKeysDialog(idsForMultiExport,
                    Constants.Path.APP_DIR_FILE,
                    mAdapter.isAnySecretSelected());
        }

        if (requestCode == REQUEST_ACTION) {
            // if a result has been returned, display a notify
            if (data != null && data.hasExtra(OperationResult.EXTRA_RESULT)) {
                OperationResult result = data.getParcelableExtra(OperationResult.EXTRA_RESULT);
                result.createNotify(getActivity()).show();
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void fabMoveUp(int height) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mFab, "translationY", 0, -height);
        // we're a little behind, so skip 1/10 of the time
        anim.setDuration(270);
        anim.start();
    }

    @Override
    public void fabRestorePosition() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mFab, "translationY", 0);
        // we're a little ahead, so wait a few ms
        anim.setStartDelay(70);
        anim.setDuration(300);
        anim.start();
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.key_list_multi, menu);
        mActionMode = actionMode;
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        // get IDs for checked positions as long array
        long[] ids;

        switch (menuItem.getItemId()) {
            case R.id.menu_key_list_multi_encrypt: {
                ids = mAdapter.getCurrentSelectedMasterKeyIds();
                encrypt(actionMode, ids);
                break;
            }
            case R.id.menu_key_list_multi_delete: {
                ids = mAdapter.getCurrentSelectedMasterKeyIds();
                showDeleteKeyDialog(actionMode, ids, mAdapter.isAnySecretSelected());
                break;
            }
            case R.id.menu_key_list_multi_export: {
                ids = mAdapter.getCurrentSelectedMasterKeyIds();
                showMultiExportDialog(ids);
                break;
            }
            case R.id.menu_key_list_multi_select_all: {
                mAdapter.selectAll();
                break;
            }
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mActionMode = null;
        mAdapter.clearSelection();
    }

    public class KeyListAdapter extends RecyclerCursorAdapter {

        private Context mContext;
        private ArrayList<Item> mItemList = new ArrayList<>();
        private String mQuery;

        public KeyListAdapter(Context context) {
            super(null);

            mContext = context;

            setOnCursorSwappedListener(new OnCursorSwappedListener() {

                @Override
                public void onCursorSwapped(Cursor oldCursor, Cursor newCursor) {
                    mItemList = new ArrayList<>();

                    if (newCursor == null) {
                        return;
                    }

                    int count = newCursor.getCount();
                    if (count == 0) {
                        return;
                    }

                    mItemList.add(new Item(Item.TYPE_MY_KEYS_HEADER, 0));

                    int i;
                    for (i = 0; i < count; i++) {
                        if (newCursor.moveToPosition(i) && newCursor.getInt(INDEX_HAS_ANY_SECRET) != 0) {
                            mItemList.add(new Item(Item.TYPE_KEY, 0, i));
                        } else {
                            break;
                        }
                    }

                    if (mItemList.size() == 1) {
                        if (mQuery == null || mQuery.isEmpty()) {
                            mItemList.add(new Item(Item.TYPE_IMPORT, 0));
                        } else {
                            mItemList.clear();
                        }
                    }

                    char prevHeaderChar = '\0';
                    int prevHeaderIndex = 0;
                    for (; i < count; i++) {
                        if (newCursor.moveToPosition(i)) {
                            char headerChar = Character.toUpperCase(newCursor.getString(INDEX_USER_ID).charAt(0));

                            if (headerChar != prevHeaderChar) {
                                prevHeaderChar = headerChar;
                                prevHeaderIndex = mItemList.size();

                                mItemList.add(new Item(Item.TYPE_CHAR_HEADER, prevHeaderIndex));
                            }

                            mItemList.add(new Item(Item.TYPE_KEY, prevHeaderIndex, i));
                        }
                    }

                    mItemList.add(new Item(Item.TYPE_FOOTER, mItemList.size() - 1));
                }

            });
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder viewHolder = null;
            switch (viewType) {
                case Item.TYPE_MY_KEYS_HEADER:

                case Item.TYPE_CHAR_HEADER:
                    viewHolder = new HeaderHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.key_list_header, parent, false));
                    break;

                case Item.TYPE_IMPORT:
                    viewHolder = new ImportKeyHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.key_list_import, parent, false));
                    break;

                case Item.TYPE_KEY:
                    viewHolder = new KeyHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.key_list_key, parent, false));
                    break;

                case Item.TYPE_FOOTER:
                    viewHolder = new RecyclerView.ViewHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.key_list_footer, parent, false)) {
                    };
                    break;
            }

            return viewHolder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = mItemList.get(position);
            switch (item.mType) {
                case Item.TYPE_MY_KEYS_HEADER:

                case Item.TYPE_CHAR_HEADER:
                    ((HeaderHolder) holder).bind(position);
                    break;

                case Item.TYPE_KEY:
                    ((KeyHolder) holder).bind(position);
                    break;
            }

            View itemView = holder.itemView;
            LayoutManager.LayoutParams layoutParams = new LayoutManager.LayoutParams(itemView.getLayoutParams());
            layoutParams.setSlm(LinearSLM.ID);
            layoutParams.setFirstPosition(item.mHeaderIndex);
            itemView.setLayoutParams(layoutParams);
        }

        @Override
        public int getItemViewType(int position) {
            return mItemList.get(position).mType;
        }

        @Override
        public int getItemCount() {
            return mItemList.size();
        }

        public void toggleSelection(int position) {
            Item item = mItemList.get(position);
            item.mSelected = item.mSelectable && !item.mSelected;

            notifyDataSetChanged();

            int count = mAdapter.getSelectionCount();
            if (count == 0) {
                mActionMode.finish();
            } else {
                mActionMode.setTitle(getResources().getQuantityString(R.plurals.key_list_selected_keys, count, count));
            }
        }

        public void selectAll() {
            for (Item item : mItemList) {
                item.mSelected = item.mSelectable;
            }

            notifyDataSetChanged();

            int count = mAdapter.getSelectionCount();
            mActionMode.setTitle(getResources().getQuantityString(R.plurals.key_list_selected_keys, count, count));
        }

        public void clearSelection() {
            for (Item item : mItemList) {
                item.mSelected = false;
            }

            notifyDataSetChanged();
        }

        public int getSelectionCount() {
            int count = 0;
            for (Item item : mItemList) {
                if (item.mSelected) {
                    count++;
                }
            }

            return count;
        }

        public boolean isAnySecretSelected() {
            for (Item item : mItemList) {
                if (item.mSelected
                        && mCursor.moveToPosition(item.mCursorPosition)
                        && mCursor.getInt(INDEX_HAS_ANY_SECRET) != 0) {
                    return true;
                }
            }

            return false;
        }

        public long[] getCurrentSelectedMasterKeyIds() {
            long[] ids = new long[getSelectionCount()];
            int i = 0;
            for (Item item : mItemList) {
                if (item.mSelected
                        && mCursor.moveToPosition(item.mCursorPosition)) {
                    ids[i++] = mCursor.getLong(INDEX_MASTER_KEY_ID);
                }
            }

            return ids;
        }

        public void setSearchQuery(String query) {
            mQuery = query;
        }

        private class Item {

            public static final int TYPE_MY_KEYS_HEADER = 0;
            public static final int TYPE_CHAR_HEADER = 1;
            public static final int TYPE_IMPORT = 2;
            public static final int TYPE_KEY = 3;
            public static final int TYPE_FOOTER = 4;

            private int mType, mHeaderIndex, mCursorPosition;
            private boolean mSelectable, mSelected;

            private Item(int type, int headerIndex, int cursorPosition) {
                mType = type;
                mHeaderIndex = headerIndex;
                mCursorPosition = cursorPosition;
                mSelectable = cursorPosition != -1;
            }

            private Item(int type, int headerIndex) {
                this(type, headerIndex, -1);
            }

        }

        public class HeaderHolder extends RecyclerView.ViewHolder {

            private TextView mTextView, mCountTextView;

            private HeaderHolder(View itemView) {
                super(itemView);

                mTextView = (TextView) itemView.findViewById(R.id.key_list_header_text);
                mCountTextView = (TextView) itemView.findViewById(R.id.key_list_header_count);
            }

            private void bind(int position) {
                Item item = mItemList.get(position);
                switch (item.mType) {
                    case Item.TYPE_MY_KEYS_HEADER: {
                        mTextView.setText(mContext.getString(R.string.my_keys));

                        int count = mCursor.getCount();
                        mCountTextView.setText(mContext.getResources().getQuantityString(R.plurals.n_keys, count, count));
                        mCountTextView.setVisibility(View.VISIBLE);
                        break;
                    }

                    case Item.TYPE_CHAR_HEADER: {
                        Item nextItem = mItemList.get(position + 1);
                        mCursor.moveToPosition(nextItem.mCursorPosition);

                        String userId = mCursor.getString(INDEX_USER_ID),
                                text = mContext.getString(R.string.user_id_no_name);
                        if (userId != null && !userId.isEmpty()) {
                            text = String.valueOf(Character.toUpperCase(userId.charAt(0)));
                        }
                        mTextView.setText(text);

                        if (position == 0) {
                            int count = mCursor.getCount();
                            mCountTextView.setText(mContext.getResources().getQuantityString(R.plurals.n_keys, count, count));
                            mCountTextView.setVisibility(View.VISIBLE);
                        } else {
                            mCountTextView.setVisibility(View.GONE);
                        }
                        break;
                    }
                }
            }

        }

        public class ImportKeyHolder extends RecyclerView.ViewHolder {

            public ImportKeyHolder(View itemView) {
                super(itemView);

                itemView.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        importFile();
                    }

                });
            }

        }

        public class KeyHolder extends RecyclerView.ViewHolder {

            private View mDividerView;
            private TextView mNameTextView, mEmailTextView;
            private LinearLayout mSlingerLayout;
            private ImageButton mSlingerImageButton;
            private ImageView mStatusImageView;

            private int mPosition;
            private long mMasterKeyId;

            private KeyHolder(View itemView) {
                super(itemView);

                mDividerView = itemView.findViewById(R.id.key_list_key_divider);
                mNameTextView = (TextView) itemView.findViewById(R.id.key_list_key_name);
                mEmailTextView = (TextView) itemView.findViewById(R.id.key_list_key_email);
                mSlingerLayout = (LinearLayout) itemView.findViewById(R.id.key_list_key_slinger_view);
                mSlingerImageButton = (ImageButton) itemView.findViewById(R.id.key_list_key_slinger_button);
                mStatusImageView = (ImageView) itemView.findViewById(R.id.key_list_key_status_icon);

                itemView.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (mActionMode == null) {
                            Intent viewIntent = new Intent(getActivity(), ViewKeyActivity.class);
                            viewIntent.setData(KeychainContract.KeyRings.buildGenericKeyRingUri(mMasterKeyId));
                            startActivity(viewIntent);
                        } else {
                            mAdapter.toggleSelection(mPosition);
                        }
                    }

                });

                itemView.setOnLongClickListener(new View.OnLongClickListener() {

                    @Override
                    public boolean onLongClick(View v) {
                        if (mActionMode == null) {
                            ((ActionBarActivity) getActivity()).startSupportActionMode(KeyListFragment.this);
                        }

                        mAdapter.toggleSelection(mPosition);
                        return true;
                    }

                });

                mSlingerImageButton.setColorFilter(mContext.getResources().getColor(R.color.tertiary_text_light),
                        PorterDuff.Mode.SRC_IN);
                mSlingerImageButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Intent safeSlingerIntent = new Intent(getActivity(), SafeSlingerActivity.class);
                        safeSlingerIntent.putExtra(SafeSlingerActivity.EXTRA_MASTER_KEY_ID, mMasterKeyId);
                        startActivityForResult(safeSlingerIntent, 0);
                    }

                });
            }

            private void bind(int position) {
                mPosition = position;

                Item item = mItemList.get(position);
                mCursor.moveToPosition(item.mCursorPosition);

                if (item.mSelected) {
                    // selected position color
                    itemView.setBackgroundColor(itemView.getResources().getColor(R.color.emphasis));
                } else {
                    // default color
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }

                Item prevItem = mItemList.get(position - 1);
                if (prevItem.mType == Item.TYPE_MY_KEYS_HEADER
                        || prevItem.mType == Item.TYPE_CHAR_HEADER) {
                    mDividerView.setVisibility(View.GONE);
                } else {
                    mDividerView.setVisibility(View.VISIBLE);
                }

                Highlighter highlighter = new Highlighter(mContext, mQuery);

                // set name and stuff, common to both key types
                String userId = mCursor.getString(INDEX_USER_ID);
                KeyRing.UserId userIdSplit = KeyRing.splitUserId(userId);
                if (userIdSplit.name != null) {
                    mNameTextView.setText(highlighter.highlight(userIdSplit.name));
                } else {
                    mNameTextView.setText(R.string.user_id_no_name);
                }
                if (userIdSplit.email != null) {
                    mEmailTextView.setText(highlighter.highlight(userIdSplit.email));
                    mEmailTextView.setVisibility(View.VISIBLE);
                } else {
                    mEmailTextView.setVisibility(View.GONE);
                }

                // set edit button and status, specific by key type
                long masterKeyId = mCursor.getLong(INDEX_MASTER_KEY_ID);
                boolean isSecret = mCursor.getInt(INDEX_HAS_ANY_SECRET) != 0;
                boolean isRevoked = mCursor.getInt(INDEX_IS_REVOKED) > 0;
                boolean isExpired = mCursor.getInt(INDEX_IS_EXPIRED) != 0;
                boolean isVerified = mCursor.getInt(INDEX_VERIFIED) > 0;
                boolean hasDuplicate = mCursor.getInt(INDEX_HAS_DUPLICATE_USER_ID) == 1;

                mMasterKeyId = masterKeyId;

                // Note: order is important!
                if (isRevoked) {
                    KeyFormattingUtils.setStatusImage(mContext, mStatusImageView, null, KeyFormattingUtils.State.REVOKED, R.color.bg_gray);
                    mStatusImageView.setVisibility(View.VISIBLE);
                    mSlingerLayout.setVisibility(View.GONE);
                    mNameTextView.setTextColor(mContext.getResources().getColor(R.color.bg_gray));
                    mEmailTextView.setTextColor(mContext.getResources().getColor(R.color.bg_gray));
                } else if (isExpired) {
                    KeyFormattingUtils.setStatusImage(mContext, mStatusImageView, null, KeyFormattingUtils.State.EXPIRED, R.color.bg_gray);
                    mStatusImageView.setVisibility(View.VISIBLE);
                    mSlingerLayout.setVisibility(View.GONE);
                    mNameTextView.setTextColor(mContext.getResources().getColor(R.color.bg_gray));
                    mEmailTextView.setTextColor(mContext.getResources().getColor(R.color.bg_gray));
                } else if (isSecret) {
                    mStatusImageView.setVisibility(View.GONE);
                    mSlingerLayout.setVisibility(View.VISIBLE);
                    mNameTextView.setTextColor(mContext.getResources().getColor(R.color.black));
                    mEmailTextView.setTextColor(mContext.getResources().getColor(R.color.black));
                } else {
                    // this is a public key - show if it's verified
                    if (isVerified) {
                        KeyFormattingUtils.setStatusImage(mContext, mStatusImageView, KeyFormattingUtils.State.VERIFIED);
                        mStatusImageView.setVisibility(View.VISIBLE);
                    } else {
                        KeyFormattingUtils.setStatusImage(mContext, mStatusImageView, KeyFormattingUtils.State.UNVERIFIED);
                        mStatusImageView.setVisibility(View.VISIBLE);
                    }
                    mSlingerLayout.setVisibility(View.GONE);
                    mNameTextView.setTextColor(mContext.getResources().getColor(R.color.black));
                    mEmailTextView.setTextColor(mContext.getResources().getColor(R.color.black));
                }
            }

        }

    }

}
