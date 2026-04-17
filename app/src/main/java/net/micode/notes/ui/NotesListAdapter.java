/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * 小米便签 主页面列表适配器
 * 功能：
 * 1. 绑定数据库游标，展示便签列表
 * 2. 支持多选模式（批量删除/分享）
 * 3. 管理选中状态、统计选中数量
 * 4. 支持桌面小部件相关数据获取
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";

    private Context mContext;
    // 存储列表项的选中状态：key = position, value = 是否选中
    private HashMap<Integer, Boolean> mSelectedIndex;
    // 列表中【普通便签】的总数量（排除文件夹）
    private int mNotesCount;
    // 是否开启【多选模式】
    private boolean mChoiceMode;

    /**
     * 桌面小部件属性实体
     * 用于批量获取小部件 ID 和类型
     */
    public static class AppWidgetAttribute {
        public int widgetId;      // 小部件ID
        public int widgetType;    // 小部件类型（2x/4x）
    };

    /**
     * 构造方法：初始化选中状态集合
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建列表项 Item 视图（每个 item 都是 NotesListItem）
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    /**
     * 绑定数据到 Item 视图
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            // 封装当前行数据
            NoteItemData itemData = new NoteItemData(context, cursor);
            // 绑定到列表项
            ((NotesListItem) view).bind(context, itemData, mChoiceMode, isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置某个位置的选中状态
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    /**
     * 是否处于多选模式
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置多选模式
     * 切换时清空所有选中状态
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选 / 取消全选
     * 只对普通便签生效，不选文件夹
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有【选中便签的ID】
     * 返回 Set 方便批量操作
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position)) {
                long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取所有选中项对应的【桌面小部件信息】
     * 用于批量更新小部件
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position)) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取【已选中的便签数量】
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (values == null) return 0;

        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (iter.next()) count++;
        }
        return count;
    }

    /**
     * 判断是否【全部选中】
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /**
     * 判断某个位置是否被选中
     */
    public boolean isSelectedItem(final int position) {
        return Boolean.TRUE.equals(mSelectedIndex.get(position));
    }

    /**
     * 数据变化时重新统计便签总数
     */
    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    /**
     * 切换游标时重新统计便签总数
     */
    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 统计列表中【普通便签】的总数（排除文件夹）
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}