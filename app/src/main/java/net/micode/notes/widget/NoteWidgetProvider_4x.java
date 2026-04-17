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

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

// NoteWidgetProvider_4x - Widget provider for 4x size widget
/* 4x4尺寸桌面小部件提供者 - 继承自NoteWidgetProvider，实现4x4尺寸的小部件 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {
    
    // Called when widget is updated
    /* 当小部件更新时调用 */
    /* @param context 上下文对象 */
    /* @param appWidgetManager 小部件管理器 */
    /* @param appWidgetIds 需要更新的小部件ID数组 */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    // Get the layout resource id for 4x widget
    /* 获取4x尺寸小部件的布局资源ID */
    /* @return 布局资源ID */
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    // Get background resource by color id for 4x widget
    /* 根据颜色ID获取4x小部件的背景图片资源 */
    /* @param bgId 背景颜色ID */
    /* @return 背景图片资源ID */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    // Get widget type constant
    /* 获取小部件类型常量 */
    /* @return 小部件类型（4x尺寸） */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}