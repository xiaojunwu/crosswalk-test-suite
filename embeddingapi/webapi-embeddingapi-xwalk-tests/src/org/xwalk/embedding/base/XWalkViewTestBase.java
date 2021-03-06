// Copyright (c) 2014 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.embedding.base;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.chromium.content.browser.test.util.CallbackHelper;
import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;
import org.chromium.net.test.util.TestWebServer;
import org.xwalk.core.JavascriptInterface;
import org.xwalk.core.XWalkJavascriptResult;
import org.xwalk.core.XWalkNavigationHistory;
import org.xwalk.core.XWalkNavigationItem;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.embedding.MainActivity;

import com.test.server.ActivityInstrumentationTestCase2;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Pair;

public class XWalkViewTestBase extends ActivityInstrumentationTestCase2<MainActivity> {

    public XWalkViewTestBase(Class<MainActivity> activityClass) {
        super(activityClass);
    }

    protected final String mExpectedStr = "xwalk";

    protected final static int WAIT_TIMEOUT_SECONDS = 15;
    protected final static long WAIT_TIMEOUT_MS = 2000;
    private final static int CHECK_INTERVAL = 100;
    
    protected XWalkView mXWalkView;
    protected MainActivity mainActivity;
    protected TestWebServer mWebServer;
    protected final TestHelperBridge mTestHelperBridge = new TestHelperBridge();

    private String mUrls[]=new String[3];

    protected static final int NUM_NAVIGATIONS = 3;

    private static final String PATHS[] = {
        "file:///android_asset/p1bar.html",
        "file:///android_asset/p2bar.html",
        "file:///android_asset/p3bar.html",
    };

    protected final String ALERT_TEXT = "Hello World!";
    protected final String PROMPT_TEXT = "How do you like your eggs in the morning?";
    protected final String PROMPT_DEFAULT = "Scrambled";
    protected final String PROMPT_RESULT = "I like mine with a kiss";
    final String CONFIRM_TEXT = "Would you like a cookie?";
    protected final AtomicBoolean callbackCalled = new AtomicBoolean(false);
    final CallbackHelper jsBeforeUnloadHelper = new CallbackHelper();
    boolean flagForConfirmCancelled = false;

    protected void setResourceClient(final XWalkResourceClient client) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getXWalkView().setResourceClient(client);
            }
        });
    }

    public XWalkViewTestBase() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mainActivity = (MainActivity) getActivity();
        mWebServer = new TestWebServer(false);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView = mainActivity.getXWalkView();
                mXWalkView.setUIClient(new TestXWalkUIClient());
            }
        });
    }

    protected void loadUrlAsync(final String url) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView.load(url, null);
            }
        });
    }

    protected void loadUrlAsync(final String url,final String content) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView.load(url, content);
            }
        });
    }

    protected void loadDataAsync(final String url, final String data, final String mimeType,
            final boolean isBase64Encoded) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView.load(url, data);
            }
        });
    }

    protected String getTitleOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getTitle();
            }
        });
    }

    protected <R> R runTestOnUiThreadAndGetResult(Callable<R> callable)
            throws Exception {
        FutureTask<R> task = new FutureTask<R>(callable);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(task);
        return task.get();
    }

    protected String getFileContent(String fileName) {
        try {
            Context context = getInstrumentation().getContext();
            InputStream inputStream = context.getAssets().open(fileName);
            int size = inputStream.available();
            byte buffer[] = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            String fileContent = new String(buffer);
            return fileContent;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected XWalkView getXWalkView() {
        return mXWalkView;
    }


    protected boolean canGoBackOnUiThread() throws Throwable {
        return runTestOnUiThreadAndGetResult(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return mXWalkView.getNavigationHistory().canGoBack();
            }
        });
    }

    protected boolean hasEnteredFullScreenOnUiThread() throws Throwable {
        return runTestOnUiThreadAndGetResult(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return mXWalkView.hasEnteredFullscreen();
            }
        });
    }

    protected void leaveFullscreenOnUiThread() throws Throwable {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView.leaveFullscreen();
            }
        });
    }

    protected boolean canGoForwardOnUiThread() throws Throwable {
        return runTestOnUiThreadAndGetResult(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return mXWalkView.getNavigationHistory().canGoForward();
            }
        });
    }

    protected XWalkNavigationItem getCurrentItemOnUiThread() throws Throwable {
        return runTestOnUiThreadAndGetResult(new Callable<XWalkNavigationItem>() {
            @Override
            public XWalkNavigationItem call() {
                return mXWalkView.getNavigationHistory().getCurrentItem();
            }
        });
    }

    protected String executeJavaScriptAndWaitForResult(final String code) throws Exception {
        final OnEvaluateJavaScriptResultHelper helper = mTestHelperBridge.getOnEvaluateJavaScriptResultHelper();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                helper.evaluateJavascript(mXWalkView, code);
            }
        });
        helper.waitUntilHasValue();
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.", helper.hasValue());
        return helper.getJsonResultAndClear();
        }

    protected String getUrlOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getUrl();
            }
        });
    }

    protected String getCurrentItemUrlOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getNavigationHistory().getCurrentItem().getUrl();
            }
        });
    }

    protected String getNavigationUrlOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                
                return mXWalkView.getNavigationHistory().getCurrentItem().getUrl();
            }
        });
    }

    protected String getNavigationOriginalUrlOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                
                return mXWalkView.getNavigationHistory().getCurrentItem().getOriginalUrl();
            }
        });
    }

    protected String getNavigationTitleOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getNavigationHistory().getCurrentItem().getTitle();
            }
        });
    }

    protected String getSizeOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return String.valueOf(mXWalkView.getNavigationHistory().size());
            }
        });
    }

    protected String hasItemAtOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return String.valueOf(mXWalkView.getNavigationHistory().hasItemAt(1));
            }
        });
    }

    protected String getOriginalUrlOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getOriginalUrl();
            }
        });
    }

    protected void clearCacheOnUiThread(final boolean includeDiskFiles) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mXWalkView.clearCache(includeDiskFiles);
            }
        });
    }

    protected String getAPIVersionOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getAPIVersion();
            }
        });
    }

    protected String getXWalkVersionOnUiThread() throws Exception {
        return runTestOnUiThreadAndGetResult(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return mXWalkView.getXWalkVersion();
            }
        });
    }

    private String getAssetsFileContent(AssetManager assetManager, String fileName)
            throws IOException {
        String result = "";
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open(fileName);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            result = new String(buffer);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return result;
    }

    public class PerformExecute implements Runnable
    {
        protected StringBuffer urlBuf;
        public PerformExecute(StringBuffer url) {
            urlBuf = url;
        }
        @Override
        public void run() {
            
            
        }
    }

    protected void goBackSync(final int n) throws Throwable {
        runTestWaitPageFinished(new Runnable(){
            @Override
            public void run() {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        mXWalkView.getNavigationHistory().navigate(
                            XWalkNavigationHistory.Direction.BACKWARD, n);
                    }
                });
            }
        });
    }


    protected void goForwardSync(final int n) throws Throwable {
        runTestWaitPageFinished(new Runnable(){
            @Override
            public void run() {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        mXWalkView.getNavigationHistory().navigate(
                            XWalkNavigationHistory.Direction.FORWARD, n);
                    }
                });
            }
        });
    }

    protected void setServerResponseAndLoad(int upto) throws Throwable {
        for (int i = 0; i < upto; ++i) {
            mUrls[i] = PATHS[i];
            loadUrlSync(mUrls[i].toString());
        }
    }

    protected void saveAndRestoreStateOnUiThread() throws Throwable {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                mXWalkView.saveState(bundle);
                mXWalkView.restoreState(bundle);
            }
        });
    }

    protected void checkHistoryItemList() throws Throwable {
        XWalkNavigationHistory history = getNavigationHistoryOnUiThread(mXWalkView);
        assertEquals(6, history.size());
        assertEquals(NUM_NAVIGATIONS - 1, history.getCurrentIndex());

        for (int i = 0; i < NUM_NAVIGATIONS; ++i) {
            assertEquals(mUrls[i], history.getItemAt(i).getUrl());
        }
    }

    private XWalkNavigationHistory getNavigationHistoryOnUiThread(
            final XWalkView content) throws Throwable{
        return runTestOnUiThreadAndGetResult(new Callable<XWalkNavigationHistory>() {
            @Override
            public XWalkNavigationHistory call() throws Exception {
                return content.getNavigationHistory();
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        if (mWebServer != null) {
            mWebServer.shutdown();
        }
        if(mainActivity != null)
        {
            mainActivity.finish();
        }
        super.tearDown();
    }

    public class TestJavascriptInterface {
        @JavascriptInterface
        public String getTextWithoutAnnotation() {
            return mExpectedStr;
        }

        @JavascriptInterface
        public String getText() {
            return mExpectedStr;
        }
    }

    protected void addJavascriptInterface() {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getXWalkView().addJavascriptInterface(new TestJavascriptInterface(),
                        "testInterface");
            }
        });
    }

    protected void raisesExceptionAndSetTitle(String script) throws Throwable {
        executeJavaScriptAndWaitForResult("try { var title = " +
                                          script + ";" +
                                          "  document.title = title;" +
                                          "} catch (exception) {" +
                                          "  document.title = \"xwalk\";" +
                                          "}");
    }

    class TestXWalkUIClientBase extends XWalkUIClient {
        TestHelperBridge mInnerContentsClient;
        public TestXWalkUIClientBase(TestHelperBridge client) {
            super(getXWalkView());
            mInnerContentsClient = client;
        }

        @Override
        public void onPageLoadStarted(XWalkView view, String url) {
            mInnerContentsClient.onPageStarted(url);
        }

        @Override
        public void onPageLoadStopped(XWalkView view, String url, LoadStatus status) {
            mInnerContentsClient.onPageFinished(url);
        }

        @Override
        public void onReceivedTitle(XWalkView view, String title) {
            mInnerContentsClient.onTitleChanged(title);
        }

        @Override
        public void onJavascriptCloseWindow(XWalkView view) {
            mInnerContentsClient.onJavascriptCloseWindow();
        }

        @Override
        public void onScaleChanged(XWalkView view, float oldScale,
                float newScale) {
            mInnerContentsClient.onScaleChanged(newScale);
        }

        @Override
        public void onRequestFocus(XWalkView view) {
            mInnerContentsClient.onRequestFocus();
        }

        @Override
        public boolean onJavascriptModalDialog(XWalkView view, JavascriptMessageType type,
                String url, String message, String defaultValue, XWalkJavascriptResult result) {
            switch(type) {
                case JAVASCRIPT_ALERT:
                    callbackCalled.set(true);
                    result.confirm();
                    assertEquals(ALERT_TEXT, message);
                    return false;
                case JAVASCRIPT_CONFIRM:
                    assertEquals(CONFIRM_TEXT, message);
                    if (flagForConfirmCancelled == true) {
                        result.cancel();
                    } else {
                        result.confirm();
                    }
                    callbackCalled.set(true);
                    return false;
                case JAVASCRIPT_PROMPT:
                    assertEquals(PROMPT_TEXT, message);
                    assertEquals(PROMPT_DEFAULT, defaultValue);
                    result.confirmWithResult(PROMPT_RESULT);
                    callbackCalled.set(true);
                    return false;
                case JAVASCRIPT_BEFOREUNLOAD:
                    result.cancel();
                    jsBeforeUnloadHelper.notifyCalled();
                    return false;
                default:
                    break;
                }
            assert(false);
            return false;
        }
    }

    public class TestXWalkUIClient extends TestXWalkUIClientBase {
        public TestXWalkUIClient() {
            super(mTestHelperBridge);
        }
    }

    protected void loadUrlSync(final String url) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        loadUrlAsync(url);
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void loadUrlSync(final String url, final String content) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        loadUrlAsync(url, content);
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void loadJavaScriptSync(final String url, final String code) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        loadUrlAsync(url);
        loadUrlAsync(code);
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void loadFromManifestSync(final String path, final String name) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        loadFromManifestAsync(path, name);
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }
    
    protected void loadFromManifestAsync(final String path, final String name) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                String manifestContent = "";
                try {
                    manifestContent = getAssetsFileContent(mainActivity.getAssets(), name);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mXWalkView.loadAppFromManifest(path, manifestContent);
            }
        });
    }

    protected void loadAssetFile(String fileName) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        String fileContent = getFileContent(fileName);
        loadDataAsync(fileName, fileContent, "text/html", false);

        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void runTestWaitPageFinished(Runnable runnable) throws Exception{
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        runnable.run();
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void reloadSync(final int mode) throws Exception {
        runTestWaitPageFinished(new Runnable(){
            @Override
            public void run() {
                getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        mXWalkView.reload(mode);
                    }
                });
            }
        });
    }

    protected void loadDataSync(final String url, final String data, final String mimeType,
            final boolean isBase64Encoded) throws Exception {
        CallbackHelper pageFinishedHelper = mTestHelperBridge.getOnPageFinishedHelper();
        int currentCallCount = pageFinishedHelper.getCallCount();
        loadDataAsync(url, data, mimeType, isBase64Encoded);
        pageFinishedHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    public void loadAssetFileAndWaitForTitle(String fileName) throws Exception {
        CallbackHelper getTitleHelper = mTestHelperBridge.getOnTitleUpdatedHelper();
        int currentCallCount = getTitleHelper.getCallCount();
        String fileContent = getFileContent(fileName);

        loadDataSync(fileName, fileContent, "text/html", false);

        getTitleHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    public boolean checkMethodInClass(Class<?> clazz, String methodName){
        Method[] methods = clazz.getMethods();
        for(Method method : methods)
        {
            if(method.getName().equals(methodName)){
                return true;
            }
        }
        Method[] methods2 = clazz.getDeclaredMethods();
        for(Method method : methods2)
        {
            if(method.getName().equals(methodName)){
                return true;
            }
        }
        return false;
    }

    public void clickOnElementId_changeTitle(final String id) throws Exception {
        Assert.assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                try {
                    String idIsNotNull = executeJavaScriptAndWaitForResult(
                        "document.getElementById('" + id + "') != null");
                    return idIsNotNull.equals("true");
                } catch (Throwable t) {
                    t.printStackTrace();
                    Assert.fail("Failed to check if DOM is loaded: " + t.toString());
                    return false;
                }
            }
        }, WAIT_TIMEOUT_MS, CHECK_INTERVAL));

        CallbackHelper getTitleHelper = mTestHelperBridge.getOnTitleUpdatedHelper();
        int currentCallCount = getTitleHelper.getCallCount();
        executeJavaScriptAndWaitForResult(
                "var evObj = document.createEvent('Events'); " +
                "evObj.initEvent('click', true, false); " +
                "document.getElementById('" + id + "').dispatchEvent(evObj);" +
                "console.log('element with id [" + id + "] clicked');");
        getTitleHelper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    public void clickOnElementId(final String id, String frameName) throws Exception {
        String str;
        if (frameName != null) {
            str = "top.window." + frameName + ".document.getElementById('" + id + "')";
        } else {
            str = "document.getElementById('" + id + "')";
        }
        final String script1 = str + " != null";
        final String script2 = str + ".dispatchEvent(evObj);";
        Assert.assertTrue(CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                try {
                    String idIsNotNull = executeJavaScriptAndWaitForResult(script1);
                    return idIsNotNull.equals("true");
                } catch (Throwable t) {
                    t.printStackTrace();
                    Assert.fail("Failed to check if DOM is loaded: " + t.toString());
                    return false;
                }
            }
        }, WAIT_TIMEOUT_MS, CHECK_INTERVAL));

        try {
            executeJavaScriptAndWaitForResult(
                "var evObj = document.createEvent('Events'); " +
                "evObj.initEvent('click', true, false); " +
                script2 +
                "console.log('element with id [" + id + "] clicked');");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    protected String addPageToTestServer(TestWebServer webServer, String httpPath, String html) {
        List<Pair<String, String>> headers = new ArrayList<Pair<String, String>>();
        headers.add(Pair.create("Content-Type", "text/html"));
        headers.add(Pair.create("Cache-Control", "no-store"));
        return webServer.setResponse(httpPath, html, headers);
    }
}
