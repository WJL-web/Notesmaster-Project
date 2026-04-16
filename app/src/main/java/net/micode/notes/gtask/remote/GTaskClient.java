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
/*
 * 文件名: GTaskClient.java
 * 功能: 小米便签中Google Task同步的核心客户端类，负责与Google Task服务进行网络通信
 * 作者: The MiCode Open Source Community
 * 创建时间: 2010-2011
 * 修改记录: 包含Google Task API的完整实现，支持任务列表和任务的CRUD操作
 *
 * 重要说明: 本类使用已废弃的Apache HttpClient库，这是2011年左右Android开发的常见做法
 *          现代Android开发应使用OkHttp或HttpURLConnection替代
 *
 * 版权声明:
 * 遵循Apache License, Version 2.0开源协议
 * 许可证详情: http://www.apache.org/licenses/LICENSE-2.0
 */

// 包声明: Google Task远程通信客户端
package net.micode.notes.gtask.remote;

// Android框架导入
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

// 项目内部导入
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

// Apache HTTP客户端库(已废弃，在旧版Android中使用)
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

// JSON处理库
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Java I/O和网络相关
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 类名: GTaskClient
 * 描述: Google Task API客户端实现，单例模式，负责所有与Google Task服务的网络通信
 *
 * 核心功能:
 * 1. 用户认证: 通过Android账户系统获取Google账户的AuthToken
 * 2. 会话管理: 管理登录状态、Cookie和客户端版本
 * 3. 数据同步: 支持任务列表和任务的创建、读取、更新、删除操作
 * 4. 批量更新: 支持将多个更新操作批量提交，减少网络请求
 * 5. 错误处理: 统一处理网络异常和API错误
 *
 * 技术架构:
 * 1. 使用单例模式确保全局只有一个GTask客户端实例
 * 2. 基于Apache HttpClient实现HTTP通信
 * 3. 使用JSON作为数据交换格式
 * 4. 通过Android AccountManager获取Google账户凭证
 *
 * API端点说明(基于文档中的链接):
 * - 基础URL: "" (从链接2-6推测应为Google Tasks API)
 * - GET端点: "ig" (获取任务数据)
 * - POST端点: "r/ig" (提交操作)
 *
 * 注意事项:
 * 1. 本类使用旧版Android网络API，现代应用应迁移到新API
 * 2. Cookie管理较为简单，可能不支持复杂的会话场景
 * 3. 错误处理较为基础，生产环境需要更完善的错误恢复机制
 * 4. 需要INTERNET权限和Google账户权限
 */
public class GTaskClient {
    /** 日志标签: 用于调试和错误追踪 */
    private static final String TAG = GTaskClient.class.getSimpleName();

    /** Google Task基础URL: 从代码看应为空字符串，实际URL在运行时动态构建 */
    private static final String GTASK_URL = "";

    /** GET请求的URL后缀: 用于获取任务数据 */
    private static final String GTASK_GET_URL = "ig";

    /** POST请求的URL后缀: 用于提交操作 */
    private static final String GTASK_POST_URL = "r/ig";

    /** 单例实例: 确保全局只有一个GTaskClient实例 */
    private static GTaskClient mInstance = null;

    /** HTTP客户端: 用于执行所有HTTP请求 */
    private DefaultHttpClient mHttpClient;

    /** 动态生成的GET URL: 可能包含自定义域名 */
    private String mGetUrl;

    /** 动态生成的POST URL: 与GET URL对应 */
    private String mPostUrl;

    /** 客户端版本号: 从服务器响应中获取，用于API版本控制 */
    private long mClientVersion;

    /** 登录状态: 表示当前是否已成功登录Google Task */
    private boolean mLoggedin;

    /** 最后登录时间: 用于会话过期判断 */
    private long mLastLoginTime;

    /** 操作ID计数器: 确保每个操作有唯一ID，用于服务器跟踪 */
    private int mActionId;

    /** 当前同步账户: 存储用于同步的Google账户信息 */
    private Account mAccount;

    /** 批量更新数组: 累积多个更新操作，一次性提交以提高效率 */
    private JSONArray mUpdateArray;

    /**
     * 私有构造方法: 初始化GTaskClient实例
     * 单例模式的一部分，确保只能通过getInstance()获取实例
     */
    private GTaskClient() {
        mHttpClient = null;             // HTTP客户端延迟初始化
        mGetUrl = GTASK_GET_URL;        // 初始化为默认GET URL
        mPostUrl = GTASK_POST_URL;      // 初始化为默认POST URL
        mClientVersion = -1;            // 客户端版本未初始化
        mLoggedin = false;              // 初始状态为未登录
        mLastLoginTime = 0;             // 最后登录时间为0
        mActionId = 1;                  // 操作ID从1开始
        mAccount = null;                // 账户未设置
        mUpdateArray = null;            // 批量更新数组为空
    }

    /**
     * 方法名: getInstance
     * 功能: 获取GTaskClient的单例实例(线程安全)
     * 设计模式: 懒汉式单例模式
     *
     * @return GTaskClient的唯一实例
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 方法名: login
     * 功能: 登录Google Task服务
     * 流程:
     * 1. 检查会话是否过期(5分钟)
     * 2. 检查账户是否切换
     * 3. 获取Google账户的AuthToken
     * 4. 尝试使用自定义域名登录
     * 5. 回退到官方域名登录
     *
     * @param activity 上下文Activity，用于账户认证
     * @return 登录是否成功
     */
    public boolean login(Activity activity) {
        // 检查会话是否过期(假设Cookie 5分钟后过期)
        final long interval = 1000 * 60 * 5; // 5分钟
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false; // 会话过期，需要重新登录
        }

        // 如果账户切换，需要重新登录
        if (mLoggedin && !TextUtils.equals(getSyncAccount().name,
                NotesPreferenceActivity.getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        // 如果已登录，直接返回成功
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis(); // 更新最后登录时间

        // 步骤1: 获取Google账户的AuthToken
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // 步骤2: 尝试使用自定义域名登录(针对企业Google账户)
        if (!(mAccount.name.toLowerCase().endsWith("") ||
                mAccount.name.toLowerCase().endsWith(""))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // 步骤3: 如果自定义域名登录失败，尝试使用官方域名
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true; // 标记为已登录
        return true;
    }

    /**
     * 方法名: loginGoogleAccount
     * 功能: 通过Android账户系统获取Google账户的认证令牌
     *
     * @param activity 上下文Activity
     * @param invalidateToken 是否使旧令牌失效并获取新令牌
     * @return 认证令牌，失败返回null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);

        // 获取所有Google账户
        Account[] accounts = accountManager.getAccountsByType("com.google");
        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 从设置中获取要同步的账户名
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;

        // 查找匹配的账户
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }

        if (account != null) {
            mAccount = account; // 保存账户引用
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // 获取认证令牌
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(
                account, "goanna_mobile", null, activity, null, null);

        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);

            // 如果需要使旧令牌失效
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                // 递归调用获取新令牌
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }
        return authToken;
    }

    /**
     * 方法名: tryToLoginGtask
     * 功能: 尝试登录Google Task服务
     * 机制: 先尝试使用当前令牌登录，失败则刷新令牌后重试
     *
     * @param activity 上下文Activity
     * @param authToken 认证令牌
     * @return 登录是否成功
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // 令牌可能过期，刷新令牌后重试
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }
            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 方法名: loginGtask
     * 功能: 使用认证令牌登录Google Task服务
     * 流程:
     * 1. 初始化HTTP客户端和Cookie存储
     * 2. 发送GET请求携带认证令牌
     * 3. 验证认证Cookie(GTL)
     * 4. 从响应中提取客户端版本号
     *
     * @param authToken 认证令牌
     * @return 登录是否成功
     */
    private boolean loginGtask(String authToken) {
        // 设置HTTP连接超时参数
        int timeoutConnection = 10000; // 连接超时10秒
        int timeoutSocket = 15000;     // Socket超时15秒

        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        // 初始化HTTP客户端
        mHttpClient = new DefaultHttpClient(httpParameters);
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // 登录Google Task
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 检查是否收到认证Cookie
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }

            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // 从响应中提取客户端版本号
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;

            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }

            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v"); // 保存客户端版本号

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // 捕获所有异常，简化错误处理
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }
        return true;
    }

    /**
     * 方法名: getActionId
     * 功能: 生成并返回唯一的操作ID
     * 作用: 确保每个发送到服务器的操作都有唯一标识
     *
     * @return 新的操作ID
     */
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 方法名: createHttpPost
     * 功能: 创建配置好的HTTP POST请求
     * 注意: 设置必要的请求头，包括内容类型和AT头
     *
     * @return 配置好的HttpPost对象
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1"); // AT头，用途未知，可能是API版本标识
        return httpPost;
    }

    /**
     * 方法名: getResponseContent
     * 功能: 从HTTP响应实体中读取内容，支持GZIP和Deflate压缩
     *
     * @param entity HTTP响应实体
     * @return 响应内容字符串
     * @throws IOException 当读取响应内容失败时抛出
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        InputStream input = entity.getContent();
        // 根据压缩编码选择合适的解压流
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }

        try {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            // 逐行读取响应内容
            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        } finally {
            input.close(); // 确保流被关闭
        }
    }

    /**
     * 方法名: postRequest
     * 功能: 发送JSON请求到Google Task服务器
     * 流程:
     * 1. 检查登录状态
     * 2. 创建并配置POST请求
     * 3. 执行请求并获取响应
     * 4. 解析响应为JSON对象
     *
     * @param js 要发送的JSON对象
     * @return 服务器响应的JSON对象
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();

        try {
            // 创建请求参数
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // 执行POST请求
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    // ==================== 业务方法 ====================

    /**
     * 方法名: createTask
     * 功能: 在Google Task中创建新任务
     * 流程:
     * 1. 提交已有的批量更新
     * 2. 构建创建任务的JSON请求
     * 3. 发送请求并处理响应
     * 4. 更新任务对象的GID(全局ID)
     *
     * @param task 要创建的任务对象
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate(); // 先提交已有的更新

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 添加创建任务的操作
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);

            // 更新任务的GID
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 方法名: createTaskList
     * 功能: 在Google Task中创建新任务列表
     * 流程与createTask类似
     *
     * @param tasklist 要创建的任务列表对象
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate(); // 先提交已有的更新

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 添加创建任务列表的操作
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);

            // 更新任务列表的GID
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 方法名: commitUpdate
     * 功能: 提交累积的批量更新操作
     * 作用: 将多个更新操作一次性发送到服务器，减少网络请求
     *
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // 添加批量更新操作
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // 添加客户端版本
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                // 发送请求
                postRequest(jsPost);
                mUpdateArray = null; // 清空更新数组

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 方法名: addUpdateNode
     * 功能: 添加节点更新操作到批量更新数组
     * 优化: 当累积的更新超过10个时自动提交，避免请求过大
     *
     * @param node 要更新的节点对象
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 避免更新项过多导致错误，设置最大为10项
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate(); // 自动提交
            }

            if (mUpdateArray == null) {
                mUpdateArray = new JSONArray();
            }

            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 方法名: moveTask
     * 功能: 移动任务到不同的位置或任务列表
     * 场景:
     * 1. 在同一任务列表内调整顺序
     * 2. 在不同任务列表之间移动
     *
     * @param task 要移动的任务
     * @param preParent 原始父任务列表
     * @param curParent 目标父任务列表
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate(); // 先提交已有的更新

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 设置移动操作类型
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());

            // 如果是在同一任务列表内移动且不是第一个，设置前驱兄弟ID
            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID,
                        task.getPriorSibling());
            }

            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());

            // 如果是在不同任务列表间移动，设置目标列表
            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求
            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 方法名: deleteNode
     * 功能: 删除节点(任务或任务列表)
     * 流程:
     * 1. 提交已有的批量更新
     * 2. 标记节点为已删除
     * 3. 发送删除请求
     *
     * @param node 要删除的节点
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate(); // 先提交已有的更新

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 标记节点为已删除并获取更新操作
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));

            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求
            postRequest(jsPost);
            mUpdateArray = null; // 清空更新数组

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 方法名: getTaskLists
     * 功能: 获取用户的所有任务列表
     * 流程:
     * 1. 检查登录状态
     * 2. 发送GET请求获取任务列表数据
     * 3. 从响应中提取并返回任务列表数组
     *
     * @return 任务列表的JSON数组
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 从响应中提取任务列表数据
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;

            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }

            JSONObject js = new JSONObject(jsString);
            // 返回任务列表数组
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    /**
     * 方法名: getTaskList
     * 功能: 获取指定任务列表中的所有任务
     * 流程:
     * 1. 提交已有的批量更新
     * 2. 发送获取任务列表的请求
     * 3. 返回任务数组
     *
     * @param listGid 任务列表的全局ID
     * @return 任务数组
     * @throws NetworkFailureException 网络通信失败时抛出
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate(); // 先提交已有的更新

        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 设置获取所有任务的操作
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false); // 不获取已删除的任务

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并返回任务数组
            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    /**
     * 方法名: getSyncAccount
     * 功能: 获取当前用于同步的Google账户
     *
     * @return 当前同步账户
     */
    public Account getSyncAccount() {
        return mAccount;
    }

    /**
     * 方法名: resetUpdateArray
     * 功能: 重置批量更新数组
     * 使用场景: 当需要放弃当前累积的更新时调用
     */
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}