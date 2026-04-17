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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 * 功能：用于展示文件夹列表（例如：移动便签到文件夹界面）
 * 数据源：直接绑定数据库 Cursor（文件夹数据）
 */
public class FoldersListAdapter extends CursorAdapter {
    // 查询数据库需要的字段：文件夹ID、文件夹名称
    public static final String [] PROJECTION = {
            NoteColumns.ID,        // 文件夹ID
            NoteColumns.SNIPPET    // 文件夹名称（摘要）
    };

    // 字段索引（对应上面数组）
    public static final int ID_COLUMN   = 0;  // ID 索引
    public static final int NAME_COLUMN = 1;  // 名称索引

    /**
     * 构造方法
     * @param context 上下文
     * @param c 数据库游标（包含所有文件夹数据）
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建新的列表项 View
     * 作用：只在创建新条目时调用，复用已有 View 时不会走这里
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // 返回自定义的文件夹列表项布局
        return new FolderListItem(context);
    }

    /**
     * 绑定数据到 View
     * 作用：将数据库中的文件夹名称设置到 TextView 上
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 特殊处理：根文件夹显示“移至上层文件夹”，其他文件夹显示真实名称
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                    ? context.getString(R.string.menu_move_parent_folder)
                    : cursor.getString(NAME_COLUMN);

            // 给列表项设置名称
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 根据位置获取文件夹名称
     * 供外部调用，用于获取选中的文件夹名称
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        // 同样处理根文件夹名称
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                ? context.getString(R.string.menu_move_parent_folder)
                : cursor.getString(NAME_COLUMN);
    }

    /**
     * 自定义文件夹列表项
     * 内部类：封装单个文件夹条目的布局和功能
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName; // 显示文件夹名称

        public FolderListItem(Context context) {
            super(context);
            // 加载列表项布局
            inflate(context, R.layout.folder_list_item, this);
            // 初始化名称 TextView
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定数据：设置文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }
}