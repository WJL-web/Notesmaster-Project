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

package net.micode.notes.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

// NoteWidgetProvider - Abstract base class for note widget providers
/* 笔记小部件提供者抽象基类 - 提供桌面小部件的通用功能，子类需实现布局、背景和类型 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    
    // Query projection for widget note info
    /* 查询小部件笔记信息的投影字段 */
    public static final String [] PROJECTION = new String [] {
        NoteColumns.ID,           // 笔记ID
        NoteColumns.BG_COLOR_ID,  // 背景颜色ID
        NoteColumns.SNIPPET       // 笔记摘要（预览文本）
    };

    // Column index constants for projection
    /* 投影列索引常量 */
    public static final int COLUMN_ID           = 0;   // ID列索引
    public static final int COLUMN_BG_COLOR_ID  = 1;   // 背景颜色ID列索引
    public static final int COLUMN_SNIPPET      = 2;   // 摘要列索引

    private static final String TAG = "NoteWidgetProvider";

    // Called when widget is deleted
    /* 当小部件被删除时调用，清除数据库中关联的widget_id */
    /* @param context 上下文对象 */
    /* @param appWidgetIds 被删除的小部件ID数组 */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    // Get widget note info from database
    /* 从数据库查询指定小部件关联的笔记信息 */
    /* @param context 上下文对象 */
    /* @param widgetId 小部件ID */
    /* @return 游标包含笔记信息，失败返回null */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    // Update widget - wrapper method
    /* 更新小部件 - 包装方法（非隐私模式） */
    /* @param context 上下文对象 */
    /* @param appWidgetManager 小部件管理器 */
    /* @param appWidgetIds 需要更新的小部件ID数组 */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    // Update widget - core implementation
    /* 更新小部件 - 核心实现 */
    /* @param context 上下文对象 */
    /* @param appWidgetManager 小部件管理器 */
    /* @param appWidgetIds 需要更新的小部件ID数组 */
    /* @param privacyMode 隐私模式（true时隐藏内容） */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
            boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Initialize with default values
                /* 使用默认值初始化 */
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                
                // Create intent for note editing
                /* 创建打开笔记编辑界面的Intent */
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // Query note info associated with this widget
                /* 查询与此小部件关联的笔记信息 */
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // Use existing note data
                    /* 使用已有的笔记数据 */
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    // No note associated, show placeholder
                    /* 没有关联笔记，显示占位文本 */
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                // Build RemoteViews for widget display
                /* 构建小部件的RemoteViews视图 */
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
                
                /**
                 * Generate the pending intent to start host for the widget
                 */
                /* 生成启动宿主Activity的PendingIntent */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // Privacy mode: hide content and show message
                    /* 隐私模式：隐藏内容，显示提示信息 */
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // Normal mode: show note snippet
                    /* 普通模式：显示笔记摘要 */
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // Set click handler and update widget
                /* 设置点击事件处理器并更新小部件 */
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    // Abstract methods to be implemented by subclasses
    /* 抽象方法 - 由子类实现，提供特定尺寸小部件的资源 */
    
    // Get background resource id by color id
    /* 根据颜色ID获取背景图片资源ID */
    protected abstract int getBgResourceId(int bgId);

    // Get layout resource id
    /* 获取小部件布局资源ID */
    protected abstract int getLayoutId();

    // Get widget type constant
    /* 获取小部件类型常量 */
    protected abstract int getWidgetType();
}