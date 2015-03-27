/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tonicartos.superslim.LayoutManager;
import com.tonicartos.superslim.LinearSLM;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.ui.util.Highlighter;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;

import java.util.ArrayList;

public class KeyListAdapter extends RecyclerCursorAdapter {

    static final int INDEX_MASTER_KEY_ID = 1;
    static final int INDEX_USER_ID = 2;
    static final int INDEX_IS_REVOKED = 3;
    static final int INDEX_IS_EXPIRED = 4;
    static final int INDEX_VERIFIED = 5;
    static final int INDEX_HAS_ANY_SECRET = 6;
    static final int INDEX_HAS_DUPLICATE_USER_ID = 7;

    private Context mContext;
    private ArrayList<Item> mItemList = new ArrayList<>();
    private OnClickListener mOnClickListener;
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
    }

    public void selectAll() {
        for (Item item : mItemList) {
            item.mSelected = item.mSelectable;
        }

        notifyDataSetChanged();
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

    public void setOnClickListener(OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
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
                    mOnClickListener.onImportClick();
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
                    mOnClickListener.onKeyClick(KeyHolder.this, mPosition);
                }

            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    mOnClickListener.onKeyLongClick(KeyHolder.this, mPosition);
                    return true;
                }

            });

            mSlingerImageButton.setColorFilter(mContext.getResources().getColor(R.color.tertiary_text_light),
                    PorterDuff.Mode.SRC_IN);
            mSlingerImageButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    mOnClickListener.onKeySlingerClick(KeyHolder.this, mPosition);
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

        public long getMasterKeyId() {
            return mMasterKeyId;
        }

    }

    public interface OnClickListener {

        public void onImportClick();

        public void onKeySlingerClick(KeyHolder holder, int position);

        public void onKeyClick(KeyHolder holder, int position);

        public void onKeyLongClick(KeyHolder holder, int position);

    }

}
