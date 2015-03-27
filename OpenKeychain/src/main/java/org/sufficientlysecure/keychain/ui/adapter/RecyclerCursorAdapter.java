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

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;

public abstract class RecyclerCursorAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected Cursor mCursor;
    private OnCursorSwappedListener mOnCursorSwappedListener;

    public RecyclerCursorAdapter(Cursor cursor) {
        mCursor = cursor;

        notifyDataSetChanged();
    }

    /**
     * Swap in a new Cursor, returning the old Cursor. The returned old Cursor is not closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there was not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }

        Cursor oldCursor = mCursor;
        mCursor = newCursor;

        mOnCursorSwappedListener.onCursorSwapped(oldCursor, newCursor);

        notifyDataSetChanged();

        return oldCursor;
    }

    public void setOnCursorSwappedListener(OnCursorSwappedListener mOnCursorSwappedListener) {
        this.mOnCursorSwappedListener = mOnCursorSwappedListener;
    }

    public interface OnCursorSwappedListener {

        public void onCursorSwapped(Cursor oldCursor, Cursor newCursor);

    }

}
