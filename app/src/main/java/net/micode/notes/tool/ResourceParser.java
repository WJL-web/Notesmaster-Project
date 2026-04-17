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

package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类
 * 功能：统一管理便签应用的所有背景图片、文字样式、颜色主题等资源获取
 * 分类管理：编辑页背景、列表项背景、桌面小部件背景、文字大小样式
 */
public class ResourceParser {

    // ==================== 便签背景颜色类型常量 ====================
    public static final int YELLOW           = 0; // 黄色
    public static final int BLUE             = 1; // 蓝色
    public static final int WHITE            = 2; // 白色
    public static final int GREEN            = 3; // 绿色
    public static final int RED              = 4; // 红色

    // 默认背景颜色（黄色）
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ==================== 文字大小常量 ====================
    public static final int TEXT_SMALL       = 0; // 小
    public static final int TEXT_MEDIUM      = 1; // 中
    public static final int TEXT_LARGE       = 2; // 大
    public static final int TEXT_SUPER       = 3; // 超大

    // 默认字体大小（中等）
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    // ==================== 内部类：编辑页面背景资源 ====================
    public static class NoteBgResources {
        // 便签编辑页整体背景图片数组
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,
                R.drawable.edit_blue,
                R.drawable.edit_white,
                R.drawable.edit_green,
                R.drawable.edit_red
        };

        // 便签编辑页标题栏背景图片数组
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,
                R.drawable.edit_title_blue,
                R.drawable.edit_title_white,
                R.drawable.edit_title_green,
                R.drawable.edit_title_red
        };

        /**
         * 根据颜色ID获取便签编辑页背景
         * @param id 颜色常量（YELLOW/BLUE等）
         * @return 对应背景资源ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色ID获取便签编辑页标题背景
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色ID
     * 支持设置：随机颜色 / 固定黄色
     * @param context 上下文
     * @return 颜色ID
     */
    public static int getDefaultBgId(Context context) {
        // 判断用户是否开启了“随机背景色”设置
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 开启：随机返回一种颜色
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            // 未开启：返回默认黄色
            return BG_DEFAULT_COLOR;
        }
    }

    // ==================== 内部类：列表项（文件夹/便签）背景资源 ====================
    public static class NoteItemBgResources {
        // 列表第一个条目背景
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        // 列表中间条目背景
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        // 列表最后一个条目背景
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        // 列表只有单个条目时的背景
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        // 获取列表第一个条目背景
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        // 获取列表最后一个条目背景
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        // 获取单个条目背景
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        // 获取列表中间条目背景
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        // 获取文件夹专用背景
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    // ==================== 内部类：桌面小部件背景资源 ====================
    public static class WidgetBgResources {
        // 2x2 桌面小部件背景
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        // 获取2x2小部件背景
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // 4x4 桌面小部件背景
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        // 获取4x4小部件背景
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    // ==================== 内部类：文字大小样式资源 ====================
    public static class TextAppearanceResources {
        // 文字样式资源（对应小/中/大/超大）
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,
                R.style.TextAppearanceMedium,
                R.style.TextAppearanceLarge,
                R.style.TextAppearanceSuper
        };

        /**
         * 根据字号ID获取文字样式
         * 做了容错处理：防止ID越界，返回默认大小
         */
        public static int getTexAppearanceResource(int id) {
            // 容错：如果传入的ID超过数组长度，返回默认中等字号
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文字大小总数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}