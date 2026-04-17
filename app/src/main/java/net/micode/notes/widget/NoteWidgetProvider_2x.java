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
 * 2x2 尺寸的桌面小部件提供者
 *
 * 该类继承自 NoteWidgetProvider，专门用于提供 2x2 尺寸的便签桌面小部件。
 * 小部件在桌面上占据 2 个单元格宽度 × 2 个单元格高度。
 *
 * 主要功能：
 * - 指定小部件的布局文件（widget_2x.xml）
 * - 根据背景颜色ID获取对应的背景资源
 * - 标识小部件类型为 2x2 尺寸
 *
 * 使用场景：
 * - 用户在桌面上添加便签小部件时，选择 2x2 尺寸
 * - 系统调用此类来创建和更新小部件
 *
 * @author MiCode Open Source Community
 * @see NoteWidgetProvider 父类，包含小部件的核心逻辑
 * @see NoteWidgetProvider_4x 4x4 尺寸的小部件提供者
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 小部件更新回调方法
     *
     * 当小部件需要更新时（如时间变化、用户手动刷新等），系统会调用此方法。
     * 委托给父类的 update 方法执行实际的更新逻辑。
     *
     * @param context          应用上下文
     * @param appWidgetManager 小部件管理器，用于更新小部件
     * @param appWidgetIds     需要更新的小部件ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 调用父类的 update 方法，执行通用的更新逻辑
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 获取小部件的布局资源ID
     *
     * 2x2 小部件使用 widget_2x.xml 布局文件。
     * 该布局定义了小部件的视图结构（背景、标题、内容等）。
     *
     * @return 布局资源ID（R.layout.widget_2x）
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据背景颜色ID获取对应的背景图片资源
     *
     * 小部件的背景会根据便签的背景颜色进行适配。
     * 2x2 小部件有专门的一套背景资源（尺寸和样式适配 2x2 网格）。
     *
     * @param bgId 背景颜色ID（来自 ResourceParser 的颜色常量）
     * @return 对应的背景图片资源ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 获取小部件类型
     *
     * 返回常量 Notes.TYPE_WIDGET_2X，用于标识这是一个 2x2 尺寸的小部件。
     * 此类型值会存储在小部件的配置中，用于后续更新时识别小部件尺寸。
     *
     * @return 小部件类型常量（Notes.TYPE_WIDGET_2X）
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}