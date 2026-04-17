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

package net.micode.notes.gtask.remote;

// Android账户相关类
import android.accounts.Account;                    // 账户信息
import android.accounts.AccountManager;            // 账户管理器
import android.accounts.AccountManagerFuture;      // 异步操作结果
import android.app.Activity;                       // 活动界面
import android.os.Bundle;                          // 数据容器
import android.text.TextUtils;                     // 字符串工具
import android.util.Log;                           // 日志工具

// 项目内部类
import net.micode.notes.gtask.data.Node;           // 节点基类
import net.micode.notes.gtask.data.Task;           // 任务类
import net.micode.notes.gtask.data.TaskList;       // 任务列表类
import net.micode.notes.gtask.exception.ActionFailureException;   // 操作失败异常
import net.micode.notes.gtask.exception.NetworkFailureException;  // 网络失败异常
import net.micode.notes.tool.GTaskStringUtils;     // 字符串常量工具
import net.micode.notes.ui.NotesPreferenceActivity; // 设置界面

// Apache HTTP客户端相关类（已过时但仍在使用）
import org.apache.http.HttpEntity;                 // HTTP实体
import org.apache.http.HttpResponse;               // HTTP响应
import org.apache.http.client.ClientProtocolException;  // 协议异常
import org.apache.http.client.entity.UrlEncodedFormEntity;  // URL编码表单实体
import org.apache.http.client.methods.HttpGet;     // GET请求
import org.apache.http.client.methods.HttpPost;    // POST请求
import org.apache.http.cookie.Cookie;              // Cookie
import org.apache.http.impl.client.BasicCookieStore;  // Cookie存储
import org.apache.http.impl.client.DefaultHttpClient;  // HTTP客户端
import org.apache.http.message.BasicNameValuePair; // 键值对参数
import org.apache.http.params.BasicHttpParams;     // HTTP参数
import org.apache.http.params.HttpConnectionParams; // 连接参数
import org.apache.http.params.HttpParams;          // HTTP参数接口
import org.apache.http.params.HttpProtocolParams;  // 协议参数

// JSON解析类
import org.json.JSONArray;                         // JSON数组
import org.json.JSONException;                     // JSON异常
import org.json.JSONObject;                        // JSON对象

// Java标准库
import java.io.BufferedReader;                     // 缓冲读取器
import java.io.IOException;                        // IO异常
import java.io.InputStream;                        // 输入流
import java.io.InputStreamReader;                  // 输入流读取器
import java.util.LinkedList;                       // 链表
import java.util.List;                             // 列表接口
import java.util.zip.GZIPInputStream;              // GZIP解压流
import java.util.zip.Inflater;                     // 解压器
import java.util.zip.InflaterInputStream;          // 解压输入流

/**
 * GTaskClient 类 - Google Tasks 客户端
 *
 * 作用：与 Google Tasks 服务器进行通信的单例客户端类
 * 负责用户认证、发送同步请求、接收响应等操作
 *
 * 功能：
 * 1. Google账户登录认证（OAuth）
 * 2. 创建/更新/删除任务和任务列表
 * 3. 批量操作提交
 * 4. 处理GZIP/deflate压缩响应
 *
 * 注意：使用了Apache HttpClient（已过时），在新项目中推荐使用OkHttp
 */
public class GTaskClient {

    /** 日志标签 */
    private static final String TAG = GTaskClient.class.getSimpleName();

    /** Google Tasks 基础URL */
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    /** GET请求URL（获取数据） */
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    /** POST请求URL（提交数据） */
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    /** 单例实例 */
    private static GTaskClient mInstance = null;

    /** HTTP客户端（执行请求） */
    private DefaultHttpClient mHttpClient;

    /** GET请求URL（可能是自定义域名） */
    private String mGetUrl;

    /** POST请求URL（可能是自定义域名） */
    private String mPostUrl;

    /** 客户端版本号（从服务器获取） */
    private long mClientVersion;

    /** 是否已登录 */
    private boolean mLoggedin;

    /** 上次登录时间（用于判断是否过期） */
    private long mLastLoginTime;

    /** 操作ID（每次请求递增） */
    private int mActionId;

    /** 当前登录的Google账户 */
    private Account mAccount;

    /** 待提交的更新操作数组 */
    private JSONArray mUpdateArray;

    /**
     * 私有构造函数（单例模式）
     * 初始化各个成员变量为默认值
     */
    private GTaskClient() {
        mHttpClient = null;              // HTTP客户端初始为空
        mGetUrl = GTASK_GET_URL;         // 默认GET URL
        mPostUrl = GTASK_POST_URL;       // 默认POST URL
        mClientVersion = -1;             // 版本号初始无效
        mLoggedin = false;               // 未登录状态
        mLastLoginTime = 0;              // 上次登录时间为0
        mActionId = 1;                   // 操作ID从1开始
        mAccount = null;                 // 账户为空
        mUpdateArray = null;             // 更新数组为空
    }

    /**
     * 获取单例实例（线程安全）
     *
     * @return GTaskClient单例对象
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录Google Tasks
     *
     * 主要流程：
     * 1. 检查登录是否过期（5分钟）
     * 2. 检查账户是否切换
     * 3. 获取Google账户认证Token
     * 4. 尝试登录（支持自定义域名）
     *
     * @param activity 用于账户认证的Activity
     * @return true 登录成功，false 登录失败
     */
    public boolean login(Activity activity) {
        // 我们假设Cookie会在5分钟后过期，需要重新登录
        final long interval = 1000 * 60 * 5;  // 5分钟间隔
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;  // 登录过期
        }

        // 账户切换后需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                .getSyncAccountName(activity))) {
            mLoggedin = false;  // 账户已更改
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;  // 已登录，直接返回
        }

        // 记录本次登录时间
        mLastLoginTime = System.currentTimeMillis();

        // 获取Google账户认证Token
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // 处理自定义域名（非Gmail/Google邮箱）
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            // 构建自定义域名的URL
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            // 尝试使用自定义域名登录
            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // 使用Google官方URL尝试登录
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    /**
     * 登录Google账户获取认证Token
     *
     * @param activity 用于认证的Activity
     * @param invalidateToken 是否使现有Token失效
     * @return 认证Token，失败返回null
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

        // 查找设置中指定的同步账户
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // 获取认证Token（使用"goanna_mobile"权限）
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (invalidateToken) {
                // 使Token失效并重新获取
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    /**
     * 尝试登录Google Tasks
     * 如果Token过期，会重新获取
     *
     * @param activity 用于重新认证的Activity
     * @param authToken 认证Token
     * @return true 登录成功，false 登录失败
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // Token可能已过期，使Token失效后重试
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
     * 执行Google Tasks登录
     *
     * 通过GET请求访问Google Tasks，获取Cookie和客户端版本号
     *
     * @param authToken 认证Token
     * @return true 登录成功，false 登录失败
     */
    private boolean loginGtask(String authToken) {
        // 配置连接参数
        int timeoutConnection = 10000;  // 连接超时10秒
        int timeoutSocket = 15000;      // Socket超时15秒
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        // 创建HTTP客户端并配置Cookie存储
        mHttpClient = new DefaultHttpClient(httpParameters);
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // 登录Google Tasks
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 检查Cookie中是否包含认证Cookie
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

            // 解析响应获取客户端版本号
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";        // JavaScript响应开始标记
            String jsEnd = ")}</script>";      // JavaScript响应结束标记
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");   // 获取客户端版本号
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // 捕获所有异常
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    /**
     * 获取下一个操作ID（自增）
     *
     * @return 操作ID
     */
    private int getActionId() {
        return mActionId++;
    }

    /**
     * 创建HTTP POST请求对象
     *
     * @return 配置好的HttpPost对象
     */
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    /**
     * 获取HTTP响应内容（支持压缩格式）
     *
     * 支持的解压格式：
     * - gzip：GZIP压缩
     * - deflate：Deflate压缩
     * - 无压缩：直接读取
     *
     * @param entity HTTP实体
     * @return 响应内容字符串
     * @throws IOException IO异常
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        // 获取内容编码格式
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        // 根据编码格式选择解压方式
        InputStream input = entity.getContent();
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());      // GZIP解压
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);  // Deflate解压
        }

        // 读取解压后的内容
        try {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        } finally {
            input.close();  // 确保关闭输入流
        }
    }

    /**
     * 发送POST请求
     *
     * @param js 请求的JSON数据
     * @return 响应的JSON对象
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            // 构建请求参数
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

    /**
     * 创建任务
     *
     * @param task 要创建的任务对象
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();  // 先提交已有的更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list：构建创建操作的JSON
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version：客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送POST请求
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            // 设置服务器返回的任务ID
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 创建任务列表（文件夹）
     *
     * @param tasklist 要创建的任务列表对象
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 提交所有待处理的更新操作
     *
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // action_list：待提交的更新操作列表
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // client_version：客户端版本号
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                postRequest(jsPost);
                mUpdateArray = null;  // 清空已提交的更新
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 添加待更新的节点
     *
     * 批量更新机制：累积多个更新操作，超过10个时自动提交
     *
     * @param node 要更新的节点
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 更新项过多可能导致错误，设置最大为10项
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();  // 超过10条，先提交
            }

            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 移动任务（支持同列表内移动和跨列表移动）
     *
     * @param task 要移动的任务
     * @param preParent 原父列表
     * @param curParent 新父列表
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();  // 先提交现有更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list：构建移动操作的JSON
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());

            // 同一列表内移动且不是第一个时，设置前驱兄弟节点ID
            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());

            // 跨列表移动时，设置目标列表ID
            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 删除节点（任务或任务列表）
     *
     * @param node 要删除的节点
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 设置节点为已删除状态并生成更新操作
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 获取所有任务列表
     *
     * @return 任务列表的JSON数组
     * @throws NetworkFailureException 网络请求失败时抛出
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

            // 解析响应获取任务列表
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
     * 获取指定任务列表中的所有任务
     *
     * @param listGid 任务列表的全局ID
     * @return 任务数组的JSON对象
     * @throws NetworkFailureException 网络请求失败时抛出
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list：构建获取所有任务的请求
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);  // 不获取已删除的任务
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    /**
     * 获取当前同步账户
     *
     * @return 当前使用的Google账户
     */
    public Account getSyncAccount() {
        return mAccount;
    }

    /**
     * 重置更新数组
     * 清空所有待提交的更新操作
     */
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}