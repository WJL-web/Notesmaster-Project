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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * 小米便签 主界面（便签列表页面）
 * 核心功能：
 * 1. 展示便签/文件夹列表
 * 2. 新建、查看、删除、移动便签
 * 3. 文件夹管理（创建、重命名、删除）
 * 4. 列表多选操作（ActionMode）
 * 5. 搜索便签
 * 6. 桌面小部件同步更新
 * 7. 首次启动导入欢迎便签
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {

    // 搜索框（用户自行新增的搜索控件）
    private EditText mSearchEditText;

    // 异步查询 Token 标记
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0; // 查询文件夹/便签列表
    private static final int FOLDER_LIST_QUERY_TOKEN = 1;      // 查询目标文件夹（移动用）

    // 文件夹长按菜单 ID
    private static final int MENU_FOLDER_DELETE = 0;       // 删除文件夹
    private static final int MENU_FOLDER_VIEW = 1;        // 查看文件夹
    private static final int MENU_FOLDER_CHANGE_NAME = 2; // 重命名文件夹

    // 首次启动标记：是否已导入欢迎介绍便签
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    // 列表当前编辑状态：普通便签列表 / 子文件夹 / 通话记录文件夹
    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };

    private ListEditState mState;                         // 当前列表状态
    private BackgroundQueryHandler mBackgroundQueryHandler; // 异步数据库查询
    private NotesListAdapter mNotesListAdapter;           // 列表适配器
    private ListView mNotesListView;                      // 便签列表控件
    private Button mAddNewNote;                           // 新建便签按钮
    private boolean mDispatch;                            // 滑动分发标记
    private int mOriginY;
    private int mDispatchY;
    private TextView mTitleBar;                           // 子文件夹标题栏
    private long mCurrentFolderId;                        // 当前所在文件夹 ID
    private ContentResolver mContentResolver;             // 内容解析器
    private ModeCallback mModeCallBack;                   // 多选操作模式回调
    private static final String TAG = "NotesListActivity";
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;
    private NoteItemData mFocusNoteDataItem;              // 当前长按选中的条目数据

    // 查询条件：普通文件夹（parentId=?）
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";
    // 查询条件：根文件夹（包含所有普通便签 + 有数据的通话记录文件夹）
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    // Activity 跳转请求码
    private final static int REQUEST_CODE_OPEN_NODE = 102;  // 打开已有便签
    private final static int REQUEST_CODE_NEW_NODE = 103;   // 新建便签

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();          // 初始化控件与适配器
        setAppInfoFromRawRes();   // 首次启动：导入欢迎便签
    }

    /**
     * 编辑页面返回后，刷新列表
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null); // 刷新列表
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 首次启动：从 raw 资源读取介绍文本，创建为欢迎便签
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char[] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 创建欢迎便签并保存
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery(); // 页面可见时，加载列表数据
    }

    /**
     * 初始化所有控件、适配器、事件监听
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER; // 默认在根文件夹

        mNotesListView = (ListView) findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false); // 添加列表底部占位
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);

        // 新建便签按钮
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());

        // 滑动与标题栏
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();

        // ====================== 搜索框逻辑（用户新增） ======================
        mSearchEditText = (EditText) findViewById(R.id.et_search);
        if (mSearchEditText != null) {
            mSearchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String selection = null;
                    String[] selectionArgs = null;
                    String searchString = s.toString().trim();

                    if (!TextUtils.isEmpty(searchString)) {
                        // 根据摘要模糊搜索
                        selection = NoteColumns.SNIPPET + " LIKE ?";
                        selectionArgs = new String[] { "%" + searchString + "%" };
                    }

                    // 异步执行搜索查询
                    mBackgroundQueryHandler.startQuery(
                            FOLDER_NOTE_LIST_QUERY_TOKEN,
                            null,
                            Notes.CONTENT_NOTE_URI,
                            NoteItemData.PROJECTION,
                            selection,
                            selectionArgs,
                            NoteColumns.MODIFIED_DATE + " DESC");
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    /**
     * 多选操作模式（ActionMode）回调
     * 处理：全选/取消全选、删除、移动
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;
        private ActionMode mActionMode;
        private MenuItem mMoveMenu;

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);

            // 判断是否显示“移动”按钮
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }

            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true); // 开启多选
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);

            // 自定义多选顶部菜单
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }
            });
            return true;
        }

        // 更新菜单：全选/取消全选、数量显示
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        // 退出多选模式
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        public void finishActionMode() {
            mActionMode.finish();
        }

        // 列表项选中状态变化
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        // 菜单点击：删除 / 移动
        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show();
                return true;
            }

            int id = item.getItemId();
            if (id == R.id.delete) {
                // 删除确认弹窗
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes, mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        batchDelete(); // 批量删除
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (id == R.id.move) {
                startQueryDestinationFolders(); // 查询可移动的目标文件夹
            }
            return true;
        }
    }

    /**
     * 新建按钮触摸滑动优化
     * 实现按钮区域顺滑滑动列表效果
     */
    private class NewNoteOnTouchListener implements OnTouchListener {
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                Display display = getWindowManager().getDefaultDisplay();
                int screenHeight = display.getHeight();
                int newNoteViewHeight = mAddNewNote.getHeight();
                int start = screenHeight - newNoteViewHeight;
                int eventY = start + (int) event.getY();
                if (mState == ListEditState.SUB_FOLDER) {
                    eventY -= mTitleBar.getHeight();
                    start -= mTitleBar.getHeight();
                }
                if (event.getY() < (event.getX() * (-0.12) + 94)) {
                    View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                            - mNotesListView.getFooterViewsCount());
                    if (view != null && view.getBottom() > start
                            && (view.getTop() < (start + 94))) {
                        mOriginY = (int) event.getY();
                        mDispatchY = eventY;
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = true;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mDispatch) {
                    mDispatchY += (int) event.getY() - mOriginY;
                    event.setLocation(event.getX(), mDispatchY);
                    return mNotesListView.dispatchTouchEvent(event);
                }
            } else {
                if (mDispatch) {
                    event.setLocation(event.getX(), mDispatchY);
                    mDispatch = false;
                    return mNotesListView.dispatchTouchEvent(event);
                }
            }
            return false;
        }
    };

    /**
     * 异步查询当前文件夹下的便签/文件夹列表
     */
    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                        String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 异步查询处理类：查询完成后更新列表
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (token == FOLDER_NOTE_LIST_QUERY_TOKEN) {
                mNotesListAdapter.changeCursor(cursor); // 刷新便签列表
            } else if (token == FOLDER_LIST_QUERY_TOKEN) {
                if (cursor != null && cursor.getCount() > 0) {
                    showFolderListMenu(cursor); // 显示移动目标文件夹选择框
                } else {
                    Log.e(TAG, "Query folder failed");
                }
            }
        }
    }

    /**
     * 显示文件夹选择列表（移动便签时用）
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 批量移动选中便签
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode(); // 退出多选模式
            }
        });
        builder.show();
    }

    /**
     * 新建便签：跳转到编辑页面
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 批量删除选中便签（异步任务）
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 非同步模式：直接删除
                    if (!DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter.getSelectedItemIds())) {
                        Log.e(TAG, "Delete notes error");
                    }
                } else {
                    // 同步模式：移入回收站
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter.getSelectedItemIds(),
                            Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                // 删除后同步更新小部件
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /**
     * 删除文件夹（包含内部便签）
     */
    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER)
            return;
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver, folderId);
        if (!isSyncMode()) {
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        // 更新小部件
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /**
     * 打开便签（进入编辑页）
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 打开文件夹（进入子文件夹列表）
     */
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 通话记录文件夹：隐藏新建按钮
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            // 普通子文件夹
            mState = ListEditState.SUB_FOLDER;
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    /**
     * 按钮点击：新建便签
     */
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    // 显示/隐藏软键盘
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * 创建/重命名文件夹对话框
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            // 重命名：填入原有名称
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                return;
            }
        } else {
            // 新建文件夹
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name), Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    // 重命名文件夹
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] { String.valueOf(mFocusNoteDataItem.getId()) });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    // 新建文件夹
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        // 输入为空时禁用确定按钮
        if (TextUtils.isEmpty(etName.getText()))
            positive.setEnabled(false);
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                positive.setEnabled(!TextUtils.isEmpty(etName.getText()));
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 返回键处理：子文件夹 → 退回根目录；根目录 → 退出
     */
    @Override
    public void onBackPressed() {
        if (mState == ListEditState.SUB_FOLDER) {
            mCurrentFolderId = Notes.ID_ROOT_FOLDER;
            mState = ListEditState.NOTE_LIST;
            startAsyncNotesListQuery();
            mTitleBar.setVisibility(View.GONE);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            mCurrentFolderId = Notes.ID_ROOT_FOLDER;
            mState = ListEditState.NOTE_LIST;
            mAddNewNote.setVisibility(View.VISIBLE);
            mTitleBar.setVisibility(View.GONE);
            startAsyncNotesListQuery();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 更新桌面小部件数据
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            return;
        }
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { appWidgetId });
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    // 文件夹长按菜单创建
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null)
            mNotesListView.setOnCreateContextMenuListener(null);
        super.onContextMenuClosed(menu);
    }

    /**
     * 文件夹长按菜单点击：查看/删除/重命名
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null)
            return false;
        int id = item.getItemId();
        if (id == MENU_FOLDER_VIEW) {
            openFolder(mFocusNoteDataItem);
        } else if (id == MENU_FOLDER_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_folder));
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    deleteFolder(mFocusNoteDataItem.getId());
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (id == MENU_FOLDER_CHANGE_NAME) {
            showCreateOrModifyFolderDialog(false);
        }
        return true;
    }

    /**
     * 创建选项菜单（右上角菜单）
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            menu.findItem(R.id.menu_sync).setTitle(GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        }
        return true;
    }

    /**
     * 右上角选项菜单点击
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_note) {
            createNewNote();
            return true;
        } else if (id == R.id.menu_sync) {
            return true;
        } else if (id == R.id.menu_setting) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 是否开启同步模式（默认关闭）
    private boolean isSyncMode() {
        return false;
    }

    /**
     * 查询可移动的目标文件夹（排除当前文件夹+回收站）
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.ID + "<>? AND " + NoteColumns.ID + "<>?";
        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, FoldersListAdapter.PROJECTION, selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                }, NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 列表长按：便签进入多选，文件夹弹出菜单
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }

    /**
     * 列表点击：便签打开编辑 / 文件夹打开列表
     */
    private class OnListItemClickListener implements OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    // 多选模式：切换选中状态
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, !mNotesListAdapter.isSelectedItem(position));
                } else {
                    // 普通模式：打开便签/文件夹
                    if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item);
                    } else if (item.getType() == Notes.TYPE_FOLDER) {
                        openFolder(item);
                    }
                }
            }
        }
    }
}