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

/**
 * 小米便签 4×4 大小桌面小部件
 * 继承自 NoteWidgetProvider（所有小部件的通用父类）
 * 作用：
 * 1. 指定 4x 小部件的布局文件
 * 2. 指定 4x 小部件对应的背景图片
 * 3. 指定小部件类型为 TYPE_WIDGET_4X
 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {

    /**
     * 系统调用：小部件需要更新时
     * 直接调用父类的更新逻辑，所有小部件刷新逻辑统一实现
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回 4x 小部件对应的布局文件：widget_4x.xml
     */
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    /**
     * 根据背景ID，返回 4x 小部件对应的背景图片资源
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    /**
     * 返回当前小部件类型：4x 小部件
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}