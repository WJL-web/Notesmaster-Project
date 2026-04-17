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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 便签列表项视图
 *
 * 这是一个自定义的 LinearLayout，用于在便签列表（NotesListActivity）中显示单个便签或文件夹的条目。
 *
 * 主要功能：
 * - 显示便签/文件夹的图标、标题、修改时间、提醒图标等
 * - 支持选择模式（ChoiceMode），显示复选框用于批量操作
 * - 根据便签类型（普通便签、文件夹、通话记录文件夹）采用不同的显示样式
 * - 根据便签在列表中的位置（首个、最后一个、中间、单独）使用不同的背景圆角样式
 *
 * 视图布局：R.layout.note_item
 *
 * @author MiCode Open Source Community
 * @see NoteItemData 便签列表项的数据模型
 * @see NotesListActivity 使用此视图的列表Activity
 */
public class NotesListItem extends LinearLayout {

    /** 提醒图标（闹钟图标） */
    private ImageView mAlert;

    /** 标题文本（便签摘要或文件夹名称） */
    private TextView mTitle;

    /** 修改时间文本 */
    private TextView mTime;

    /** 通话联系人姓名（仅用于通话记录文件夹下的便签） */
    private TextView mCallName;

    /** 列表项数据模型 */
    private NoteItemData mItemData;

    /** 复选框（用于选择模式下的批量操作） */
    private CheckBox mCheckBox;

    /**
     * 构造方法
     *
     * 加载 note_item 布局文件，并初始化各个子视图。
     *
     * @param context 上下文对象
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载布局文件
        inflate(context, R.layout.note_item, this);
        // 初始化视图组件
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到列表项视图
     *
     * 根据数据类型（普通便签/文件夹/通话记录）和状态（是否有提醒、位置等）
     * 设置视图的显示内容、样式和背景。
     *
     * @param context    上下文对象
     * @param data       便签列表项数据
     * @param choiceMode 是否处于选择模式
     * @param checked    复选框是否被选中（仅 choiceMode=true 时有效）
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        // ========== 1. 处理复选框（选择模式） ==========
        // 只有在选择模式且数据类型为普通便签时，才显示复选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;

        // ========== 2. 根据数据类型设置显示内容 ==========

        // 情况1：系统通话记录文件夹（特殊文件夹）
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);                    // 隐藏通话姓名
            mAlert.setVisibility(View.VISIBLE);                    // 显示图标
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            // 显示格式："通话记录 (X条)"
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);       // 通话记录图标
        }
        // 情况2：通话记录文件夹下的便签（通话记录条目）
        else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());                 // 显示联系人姓名
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));  // 显示通话内容摘要

            // 显示提醒图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        }
        // 情况3：普通便签或普通文件夹
        else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 文件夹类型
            if (data.getType() == Notes.TYPE_FOLDER) {
                // 显示格式："文件夹名 (X条)"
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                        data.getNotesCount()));
                mAlert.setVisibility(View.GONE);  // 文件夹不显示提醒图标
            }
            // 普通便签类型
            else {
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                // 根据是否有提醒显示闹钟图标
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // ========== 3. 设置修改时间 ==========
        // 使用相对时间显示（如"3分钟前"、"昨天"等）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // ========== 4. 设置背景样式 ==========
        setBackground(data);
    }

    /**
     * 设置列表项的背景
     *
     * 根据便签类型和在列表中的位置，设置不同的背景圆角样式。
     *
     * 背景样式规则：
     * - 文件夹：使用统一的文件夹背景
     * - 便签：
     *   - 单个便签（没有相邻项）：使用单条目圆角样式（四个角都圆）
     *   - 第一个条目：上方圆角，下方直角
     *   - 最后一个条目：下方圆角，上方直角
     *   - 中间条目：四个角都是直角
     *   - 跟随在文件夹后的第一个便签：上方圆角
     *
     * @param data 便签列表项数据（包含位置信息）
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();  // 获取背景颜色ID

        // 普通便签类型
        if (data.getType() == Notes.TYPE_NOTE) {
            // 根据位置选择不同的背景资源
            if (data.isSingle() || data.isOneFollowingFolder()) {
                // 单独一个便签，或紧跟在文件夹后的便签 → 四个角都圆
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                // 列表中的最后一个便签 → 只有下方圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                // 第一个便签，或多个便签中跟随文件夹的第一个 → 只有上方圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                // 中间的便签 → 没有圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        }
        // 文件夹类型
        else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项的数据
     *
     * @return NoteItemData 对象
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}