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

// ResourceParser - Utility class for parsing and providing resource IDs (colors, backgrounds, text sizes)
/* 资源解析器 - 提供笔记背景颜色、字体大小、小部件背景等资源ID的工具类 */
public class ResourceParser {

    // Background color constants
    /* 背景颜色常量 - 黄色 */
    public static final int YELLOW           = 0;
    /* 背景颜色常量 - 蓝色 */
    public static final int BLUE             = 1;
    /* 背景颜色常量 - 白色 */
    public static final int WHITE            = 2;
    /* 背景颜色常量 - 绿色 */
    public static final int GREEN            = 3;
    /* 背景颜色常量 - 红色 */
    public static final int RED              = 4;

    // Default background color
    /* 默认背景颜色（黄色） */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // Text size constants
    /* 字体大小常量 - 小 */
    public static final int TEXT_SMALL       = 0;
    /* 字体大小常量 - 中 */
    public static final int TEXT_MEDIUM      = 1;
    /* 字体大小常量 - 大 */
    public static final int TEXT_LARGE       = 2;
    /* 字体大小常量 - 超大 */
    public static final int TEXT_SUPER       = 3;

    // Default font size
    /* 默认字体大小（中） */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    // NoteBgResources - inner class for note background resources (edit mode)
    /* 笔记背景资源内部类 - 提供编辑模式下笔记的背景图片资源 */
    public static class NoteBgResources {
        // Background resources for note editing (yellow, blue, white, green, red)
        /* 编辑笔记时的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,
            R.drawable.edit_blue,
            R.drawable.edit_white,
            R.drawable.edit_green,
            R.drawable.edit_red
        };

        // Title bar background resources for note editing
        /* 编辑笔记时的标题栏背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        };

        // Get note background resource by color id
        /* 根据颜色ID获取笔记背景资源 */
        /* @param id 颜色ID（YELLOW/BLUE等） */
        /* @return 对应的背景图片资源ID */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        // Get note title background resource by color id
        /* 根据颜色ID获取笔记标题栏背景资源 */
        /* @param id 颜色ID */
        /* @return 对应的标题栏背景图片资源ID */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    // Get default background color id (random if user enabled random color)
    /* 获取默认背景颜色ID（如果用户开启了随机颜色，则返回随机颜色） */
    /* @param context 上下文对象 */
    /* @return 背景颜色ID */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // Random color mode: return random index
            /* 随机颜色模式：返回随机索引 */
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    // NoteItemBgResources - inner class for list item backgrounds (first, normal, last, single)
    /* 笔记列表项背景资源内部类 - 提供列表中笔记项的背景图片（首项、中间项、末项、单项） */
    public static class NoteItemBgResources {
        // Background resources for the first item in a list (yellow, blue, white, green, red)
        /* 列表第一项的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        };

        // Background resources for normal (middle) items in a list
        /* 列表中间项的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        };

        // Background resources for the last item in a list
        /* 列表最后一项的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        };

        // Background resources for a single item (only one item in list)
        /* 列表单项（仅一项）的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        };

        // Get background resource for first item
        /* 获取第一项的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        // Get background resource for last item
        /* 获取最后一项的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        // Get background resource for single item
        /* 获取单项的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        // Get background resource for normal (middle) item
        /* 获取中间项的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        // Get folder background resource
        /* 获取文件夹列表项的背景资源 */
        /* @return 文件夹背景图片资源ID */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    // WidgetBgResources - inner class for widget background resources (2x and 4x sizes)
    /* 桌面小部件背景资源内部类 - 提供2x4和4x4小部件的背景图片资源 */
    public static class WidgetBgResources {
        // Background resources for 2x widget (yellow, blue, white, green, red)
        /* 2x4小部件的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        };

        // Get 2x widget background resource by color id
        /* 根据颜色ID获取2x4小部件的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // Background resources for 4x widget (yellow, blue, white, green, red)
        /* 4x4小部件的背景图片资源数组（黄、蓝、白、绿、红） */
        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        };

        // Get 4x widget background resource by color id
        /* 根据颜色ID获取4x4小部件的背景资源 */
        /* @param id 颜色ID */
        /* @return 背景图片资源ID */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    // TextAppearanceResources - inner class for text appearance (font size) resources
    /* 文本外观资源内部类 - 提供字体大小样式资源 */
    public static class TextAppearanceResources {
        // Text appearance resources array (small, medium, large, super)
        /* 文本样式资源数组（小、中、大、超大） */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        };

        // Get text appearance resource by id (with fallback for invalid id)
        /* 根据ID获取文本样式资源（如果ID无效则返回默认字体大小） */
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        /* 注意：修复共享偏好中存储资源ID的bug，ID可能超出数组长度，此时返回默认字体大小 */
        public static int getTexAppearanceResource(int id) {
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        // Get the number of text appearance resources
        /* 获取文本样式资源的数量 */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}