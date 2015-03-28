/*
 * Copyright (C) 2014-2015 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tonicartos.superslim.LayoutManager;
import com.tonicartos.superslim.LinearSLM;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.pgp.WrappedSignature;
import org.sufficientlysecure.keychain.provider.KeychainContract;
import org.sufficientlysecure.keychain.provider.KeychainDatabase;
import org.sufficientlysecure.keychain.ui.adapter.RecyclerCursorAdapter;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.util.Log;

import java.util.ArrayList;


public class ViewKeyAdvCertsFragment extends LoaderFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    public static final String ARG_DATA_URI = "data_uri";

    private RecyclerView mRecyclerView;
    private CertListAdapter mCertsAdapter;

    private Uri mDataUriCerts;

    // These are the rows that we will retrieve.
    static final String[] CERTS_PROJECTION = new String[]{
            KeychainContract.Certs._ID,
            KeychainContract.Certs.MASTER_KEY_ID,
            KeychainContract.Certs.VERIFIED,
            KeychainContract.Certs.TYPE,
            KeychainContract.Certs.RANK,
            KeychainContract.Certs.KEY_ID_CERTIFIER,
            KeychainContract.Certs.USER_ID,
            KeychainContract.Certs.SIGNER_UID
    };

    // sort by our user id,
    static final String CERTS_SORT_ORDER =
            KeychainDatabase.Tables.CERTS + "." + KeychainContract.Certs.RANK + " ASC, "
                    + KeychainContract.Certs.VERIFIED + " DESC, "
                    + KeychainDatabase.Tables.CERTS + "." + KeychainContract.Certs.TYPE + " DESC, "
                    + KeychainContract.Certs.SIGNER_UID + " ASC";

    /**
     * Creates new instance of this fragment
     */
    public static ViewKeyAdvCertsFragment newInstance(Uri dataUri) {
        ViewKeyAdvCertsFragment frag = new ViewKeyAdvCertsFragment();

        Bundle args = new Bundle();
        args.putParcelable(ARG_DATA_URI, dataUri);

        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup superContainer, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, superContainer, savedInstanceState);
        View view = inflater.inflate(R.layout.view_key_adv_certs_fragment, getContainer());

        mRecyclerView = (RecyclerView) view.findViewById(R.id.certs_recycler);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LayoutManager(getActivity()));

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Uri dataUri = getArguments().getParcelable(ARG_DATA_URI);
        if (dataUri == null) {
            Log.e(Constants.TAG, "Data missing. Should be Uri of key!");
            getActivity().finish();
            return;
        }

        loadData(dataUri);
    }

    private void loadData(Uri dataUri) {
        mDataUriCerts = KeychainContract.Certs.buildCertsUri(dataUri);

        // Create an empty adapter we will use to display the loaded data.
        mCertsAdapter = new CertListAdapter(getActivity());
        mRecyclerView.setAdapter(mCertsAdapter);

        // Prepare the loaders. Either re-connect with an existing ones,
        // or start new ones.
        getLoaderManager().initLoader(0, null, this);
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        setContentShown(false);


        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(getActivity(), mDataUriCerts,
                CERTS_PROJECTION, null, null, CERTS_SORT_ORDER);

    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Avoid NullPointerExceptions, if we get an empty result set.
        if (data.getCount() == 0) {
            return;
        }

        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mCertsAdapter.swapCursor(data);
        mRecyclerView.setAdapter(mCertsAdapter);

        TextView emptyTextView = (TextView) getActivity().findViewById(R.id.empty);
        if (mCertsAdapter.getItemCount() == 0) {
            mRecyclerView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            emptyTextView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }

        // TODO: maybe show not before both are loaded!
        setContentShown(true);
    }

    /**
     * This is called when the last Cursor provided to onLoadFinished() above is about to be closed.
     * We need to make sure we are no longer using it.
     */
    public void onLoaderReset(Loader<Cursor> loader) {
        mCertsAdapter.swapCursor(null);
    }

    private class CertListAdapter extends RecyclerCursorAdapter {

        private Context mContext;
        private ArrayList<Item> mItemList = new ArrayList<>();
        private int mIndexMasterKeyId, mIndexUserId, mIndexRank;
        private int mIndexSignerKeyId, mIndexSignerUserId;
        private int mIndexVerified, mIndexType;

        public CertListAdapter(Context context) {
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

                    // Get column indexes for performance reasons.
                    // For a performance comparison see http://stackoverflow.com/a/17999582
                    mIndexMasterKeyId = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.MASTER_KEY_ID);
                    mIndexUserId = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.USER_ID);
                    mIndexRank = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.RANK);
                    mIndexType = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.TYPE);
                    mIndexVerified = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.VERIFIED);
                    mIndexSignerKeyId = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.KEY_ID_CERTIFIER);
                    mIndexSignerUserId = newCursor.getColumnIndexOrThrow(KeychainContract.Certs.SIGNER_UID);

                    int prevHeaderIndex;
                    for (int i = 0; i < count; i++) {
                        prevHeaderIndex = mItemList.size();
                        mItemList.add(new Item(Item.TYPE_HEADER, prevHeaderIndex));
                        mItemList.add(new Item(Item.TYPE_CERT, prevHeaderIndex, i));
                    }
                }

            });
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RecyclerView.ViewHolder viewHolder = null;
            switch (viewType) {
                case Item.TYPE_HEADER:
                    viewHolder = new HeaderViewHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.view_key_adv_certs_header, parent, false));
                    break;

                case Item.TYPE_CERT:
                    viewHolder = new CertViewHolder(LayoutInflater.from(mContext)
                            .inflate(R.layout.view_key_adv_certs_item, parent, false));
                    break;
            }

            return viewHolder;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = mItemList.get(position);
            switch (item.mType) {
                case Item.TYPE_HEADER:
                    ((HeaderViewHolder) holder).bind(position);
                    break;

                case Item.TYPE_CERT:
                    ((CertViewHolder) holder).bind(position);
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

        private class Item {

            public static final int TYPE_HEADER = 0;
            public static final int TYPE_CERT = 1;

            private int mType, mHeaderIndex, mCursorPosition;

            private Item(int type, int headerIndex, int cursorPosition) {
                mType = type;
                mHeaderIndex = headerIndex;
                mCursorPosition = cursorPosition;
            }

            private Item(int type, int headerIndex) {
                this(type, headerIndex, -1);
            }

        }

        private class HeaderViewHolder extends RecyclerView.ViewHolder {

            private TextView mTextView, mCountTextView;

            public HeaderViewHolder(View itemView) {
                super(itemView);

                mTextView = (TextView) itemView.findViewById(R.id.certs_text);
                mCountTextView = (TextView) itemView.findViewById(R.id.certs_num);
            }

            private void bind(int position) {
                Item nextItem = mItemList.get(position + 1);
                mCursor.moveToPosition(nextItem.mCursorPosition);

                mTextView.setText(mCursor.getString(mIndexUserId));
                mCountTextView.setVisibility(View.GONE);
            }

        }

        private class CertViewHolder extends RecyclerView.ViewHolder {

            private TextView mSignerNameTextView, mSignerKeyIdTextView, mSignStatusTextView;
            private long mMasterKeyId, mRank, mSignerKeyId;

            public CertViewHolder(View itemView) {
                super(itemView);

                mSignerNameTextView = (TextView) itemView.findViewById(R.id.signerName);
                mSignerKeyIdTextView = (TextView) itemView.findViewById(R.id.signerKeyId);
                mSignStatusTextView = (TextView) itemView.findViewById(R.id.signStatus);

                itemView.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Intent viewIntent = new Intent(getActivity(), ViewCertActivity.class);
                        viewIntent.setData(KeychainContract.Certs.buildCertsSpecificUri(
                                mMasterKeyId, mRank, mSignerKeyId));
                        startActivity(viewIntent);
                    }

                });
            }

            private void bind(int position) {
                Item item = mItemList.get(position);
                mCursor.moveToPosition(item.mCursorPosition);

                String signerKeyId = KeyFormattingUtils.beautifyKeyIdWithPrefix(mContext, mCursor.getLong(mIndexSignerKeyId));
                KeyRing.UserId userId = KeyRing.splitUserId(mCursor.getString(mIndexSignerUserId));
                if (userId.name != null) {
                    mSignerNameTextView.setText(userId.name);
                } else {
                    mSignerNameTextView.setText(R.string.user_id_no_name);
                }
                mSignerKeyIdTextView.setText(signerKeyId);

                switch (mCursor.getInt(mIndexType)) {
                    case WrappedSignature.DEFAULT_CERTIFICATION: // 0x10
                        mSignStatusTextView.setText(R.string.cert_default);
                        break;
                    case WrappedSignature.NO_CERTIFICATION: // 0x11
                        mSignStatusTextView.setText(R.string.cert_none);
                        break;
                    case WrappedSignature.CASUAL_CERTIFICATION: // 0x12
                        mSignStatusTextView.setText(R.string.cert_casual);
                        break;
                    case WrappedSignature.POSITIVE_CERTIFICATION: // 0x13
                        mSignStatusTextView.setText(R.string.cert_positive);
                        break;
                    case WrappedSignature.CERTIFICATION_REVOCATION: // 0x30
                        mSignStatusTextView.setText(R.string.cert_revoke);
                        break;
                }

                mMasterKeyId = mCursor.getLong(mIndexMasterKeyId);
                mRank = mCursor.getLong(mIndexRank);
                mSignerKeyId = mCursor.getLong(mIndexSignerKeyId);
            }

        }

    }

}
