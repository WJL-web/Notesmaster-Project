/*
 * 版权声明 (c) 2010-2011, 米代码开源社区 (www.micode.net)
 *
 * 根据 Apache License 2.0 版本（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，根据许可证分发的软件
 * 是按“原样”基础分发的，不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言下的权限和限制。
 */

package net.micode.notes.widget;

// Android相关导入
import android.app.PendingIntent;              // 待定意图（延迟触发的Intent）
import android.appwidget.AppWidgetManager;     // 应用小部件管理器
import android.appwidget.AppWidgetProvider;    // 应用小部件提供者基类
import android.content.ContentValues;          // 内容值（用于数据库更新）
import android.content.Context;                // 上下文对象
import android.content.Intent;                 // 意图（用于页面跳转）
import android.database.Cursor;                // 数据库游标
import android.util.Log;                       // 日志工具
import android.widget.RemoteViews;             // 远程视图（用于更新小部件UI）

// 项目内部类导入
import net.micode.notes.R;                     // 资源文件
import net.micode.notes.data.Notes;            // 便签数据契约类
import net.micode.notes.data.Notes.NoteColumns; // 便签表列名
import net.micode.notes.tool.ResourceParser;   // 资源解析器（获取默认背景等）
import net.micode.notes.ui.NoteEditActivity;   // 便签编辑界面
import net.micode.notes.ui.NotesListActivity;  // 便签列表界面

/**
 * NoteWidgetProvider 抽象类 - 便签桌面小部件提供者
 *
 * 作用：提供便签应用的桌面小部件功能
 * 继承自 AppWidgetProvider，是 Android 桌面小部件的标准实现方式
 *
 * 功能：
 * 1. 在桌面上显示便签内容预览
 * 2. 支持点击小部件跳转到便签编辑界面
 * 3. 支持隐私模式（访客模式下不显示实际内容）
 * 4. 支持小部件删除时清理数据库中的关联信息
 * 5. 支持2x2和4x4两种尺寸（由子类实现具体尺寸）
 *
 * 子类：
 * - NoteWidgetProvider_2x：2x2 尺寸的小部件
 * - NoteWidgetProvider_4x：4x4 尺寸的小部件
 *
 * 抽象方法：
 * - getBgResourceId()：根据背景ID获取对应的资源ID
 * - getLayoutId()：获取小部件的布局资源ID
 * - getWidgetType()：获取小部件类型（2x2 或 4x4）
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    // ==================== 常量定义 ====================

    /**
     * 数据库查询投影
     * 查询小部件关联的便签信息时需要获取的列
     */
    public static final String [] PROJECTION = new String [] {
            NoteColumns.ID,           // 便签ID
            NoteColumns.BG_COLOR_ID,  // 背景颜色ID
            NoteColumns.SNIPPET       // 便签摘要（预览内容）
    };

    /** 便签ID在投影中的列索引 */
    public static final int COLUMN_ID           = 0;

    /** 背景颜色ID在投影中的列索引 */
    public static final int COLUMN_BG_COLOR_ID  = 1;

    /** 便签摘要（内容）在投影中的列索引 */
    public static final int COLUMN_SNIPPET      = 2;

    /** 日志标签 */
    private static final String TAG = "NoteWidgetProvider";

    // ==================== 生命周期方法 ====================

    /**
     * 当小部件被删除时调用
     *
     * 功能：清理数据库中的小部件关联信息
     * 将 note 表中对应 widget_id 字段设置为无效值
     *
     * @param context 上下文对象
     * @param appWidgetIds 被删除的小部件ID数组
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        // 设置 widget_id 为无效值，表示不再关联任何小部件
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        for (int i = 0; i < appWidgetIds.length; i++) {
            // 更新数据库：将 widget_id 匹配的记录的小部件ID设为无效
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i]) });
        }
    }

    // ==================== 数据查询方法 ====================

    /**
     * 获取小部件关联的便签信息
     *
     * 查询条件：
     * 1. widget_id 匹配指定值
     * 2. 便签不在回收站中（parent_id ≠ 回收站ID）
     *
     * @param context 上下文对象
     * @param widgetId 小部件ID
     * @return 包含便签信息的游标，最多一条记录
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    // ==================== 更新方法 ====================

    /**
     * 更新小部件（重载方法）
     *
     * @param context 上下文对象
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 要更新的小部件ID数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 更新小部件（完整方法）
     *
     * 功能：
     * 1. 为每个小部件查询关联的便签信息
     * 2. 根据隐私模式决定显示内容（正常模式显示便签摘要，隐私模式显示提示文字）
     * 3. 设置小部件的背景颜色
     * 4. 设置点击跳转的 Intent（点击小部件打开便签编辑界面）
     *
     * @param context 上下文对象
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 要更新的小部件ID数组
     * @param privacyMode 是否隐私模式（true-隐藏实际内容，false-正常显示）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 检查小部件ID是否有效
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 初始化默认值
                int bgId = ResourceParser.getDefaultBgId(context);  // 默认背景ID
                String snippet = "";                                 // 默认空内容

                // 构建跳转 Intent
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);    // 栈顶复用模式
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);    // 传递小部件ID
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());  // 传递小部件类型

                // 查询小部件关联的便签信息
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    // 安全检查：同一个小部件ID不应该关联多个便签
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 从游标读取便签信息
                    snippet = c.getString(COLUMN_SNIPPET);           // 便签内容摘要
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);             // 背景颜色
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));  // 便签ID
                    intent.setAction(Intent.ACTION_VIEW);            // 查看模式
                } else {
                    // 没有关联便签时，显示提示文字
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);  // 新建模式
                }

                if (c != null) {
                    c.close();  // 关闭游标
                }

                // 创建远程视图（用于更新小部件UI）
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                // 设置小部件背景图片
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);  // 传递背景ID

                /**
                 * 生成待定意图（PendingIntent）用于启动小部件的宿主Activity
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式：显示访客提示文字，点击打开便签列表（不显示具体内容）
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式：显示便签摘要，点击打开对应的便签编辑界面
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // 设置点击文本区域的待定意图
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                // 更新小部件
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    // ==================== 抽象方法（子类实现） ====================

    /**
     * 获取背景图片资源ID
     *
     * 根据背景颜色ID返回对应的图片资源
     * 不同尺寸的小部件可能有不同的背景资源
     *
     * @param bgId 背景颜色ID
     * @return 背景图片资源ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取小部件布局资源ID
     *
     * 2x2 和 4x4 小部件使用不同的布局文件
     *
     * @return 布局资源ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取小部件类型
     *
     * @return 小部件类型（Notes.TYPE_WIDGET_2X 或 Notes.TYPE_WIDGET_4X）
     */
    protected abstract int getWidgetType();
}