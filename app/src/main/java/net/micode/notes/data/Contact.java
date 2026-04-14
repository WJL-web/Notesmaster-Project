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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;


/**
 * 联系人工具类
 * 功能：根据手机号码 查询 系统通讯录中对应的联系人姓名
 * 特点：使用 HashMap 做本地缓存，避免重复查询数据库，优化性能
 */

public class Contact {
    // 静态缓存：key=手机号, value=联系人姓名，全局唯一，所有页面共用
    private static HashMap<String, String> sContactCache;

    // 日志TAG，用于调试打印
    private static final String TAG = "Contact";

    /**
     * 查询联系人的 SQL 查询条件模板
     * 作用：精确匹配 手机号 对应的联系人
     * 说明：这是 Android 系统通讯录标准的查询语句
     *
     * 拆解含义：
     * 1. PHONE_NUMBERS_EQUAL(Phone.NUMBER, ?)  →  匹配传入的手机号
     * 2. Data.MIMETYPE = Phone.CONTENT_ITEM_TYPE  →  只查询“电话号码”类型数据
     * 3. 子查询 FROM phone_lookup WHERE min_match = '+'  →  系统优化的手机号匹配表
     */

    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据手机号获取联系人姓名（对外暴露的核心方法）
     * @param context 上下文（用于访问系统内容提供者）
     * @param phoneNumber 要查询的手机号码
     * @return 联系人姓名，找不到返回 null
     */

    public static String getContact(Context context, String phoneNumber) {
        // 1. 初始化缓存：如果缓存还没创建，就新建一个 HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }
        // 2. 缓存命中：如果这个手机号已经查过，直接返回缓存的姓名，不查数据库
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }
        // 3. 生成最终查询语句：把 min_match 的占位符 + 替换成系统标准的手机号匹配格式
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 4. 查询系统通讯录（通过 Android 内容提供者 ContentResolver）
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 5. 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
