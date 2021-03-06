/*
 * Copyright © YOLANDA. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yolanda.nohttp;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.yolanda.nohttp.tools.NetUtil;

import android.content.Context;
import android.webkit.URLUtil;

/**
 * RESTFUL request actuator</br>
 * Created in Jul 28, 2015 7:33:22 PM
 * 
 * @author YOLANDA
 */
public final class HttpRestConnection extends BasicConnection implements BasicConnectionRest {

	/**
	 * context
	 */
	private final Context mContext;
	/**
	 * User-Agent of request
	 */
	private final String userAgent;
	/**
	 * Singleton pattern: Keep the object
	 */
	private static HttpRestConnection _INSTANCE;

	/**
	 * To create a singleton pattern entrance
	 * 
	 * @return Return my implementation
	 */
	public static BasicConnectionRest getInstance(Context context) {
		synchronized (HttpRestConnection.class) {
			if (_INSTANCE == null)
				_INSTANCE = new HttpRestConnection(context.getApplicationContext());
		}
		return _INSTANCE;
	}

	/**
	 * lock public
	 */
	private HttpRestConnection(Context context) {
		this.mContext = context;
		userAgent = UserAgent.getUserAgent(context);
	}

	/**
	 * The request string
	 * 
	 * @param analyzeRequest request parameters
	 */
	@Override
	public <T> Response<T> request(Request<T> request) {
		Logger.d("--------------Reuqest start--------------");
		AnalyzeRequest analyzeRequest = request.getAnalyzeRequest();

		String url = null;
		boolean isSucceed = false;
		int responseCode = -1;
		Headers headers = null;
		byte[] byteArray = null;
		Object tag = null;
		T result = null;

		if (analyzeRequest != null) {
			url = analyzeRequest.url();
			tag = analyzeRequest.getTag();
			if (!URLUtil.isValidUrl(analyzeRequest.url()))
				byteArray = "URL Error".getBytes();
			else if (!NetUtil.isNetworkAvailable(mContext)) {
				byteArray = "Network error".getBytes();
			} else {
				HttpURLConnection httpConnection = null;
				try {
					httpConnection = getHttpConnection(analyzeRequest);
					httpConnection.connect();
					sendRequestParam(httpConnection, analyzeRequest);
					
					Logger.i("-------Response Start-------");
					responseCode = httpConnection.getResponseCode();
					Logger.d("ResponseCode: " + responseCode);

					Map<String, List<String>> responseHeaders = httpConnection.getHeaderFields();
					headers = Headers.parseMultimap(responseHeaders);
					if (Logger.isDebug)
						for (String headName : responseHeaders.keySet()) {
							List<String> headValues = responseHeaders.get(headName);
							for (String headValue : headValues) {
								Logger.d(headName + ": " + headValue);
							}
						}

					CookieManager cookieManager = NoHttp.getDefaultCookieManager();
					// 这里解析的是set-cookie2和set-cookie
					cookieManager.put(new URI(analyzeRequest.url()), responseHeaders);

					isSucceed = true;

					if (hasResponseBody(analyzeRequest.getRequestMethod(), responseCode)) {
						String contentEncoding = httpConnection.getContentEncoding();
						InputStream inputStream = null;
						try {
							inputStream = httpConnection.getInputStream();
						} catch (IOException e) {
							isSucceed = false;
							inputStream = httpConnection.getErrorStream();
						}
						if (HeaderParser.isGzipContent(contentEncoding))
							inputStream = new GZIPInputStream(inputStream);
						byteArray = readResponseBody(inputStream);
					}
				} catch (Exception e) {
					isSucceed = false;
					String exceptionInfo = getExcetionMessage(e);
					byteArray = exceptionInfo.getBytes();
					Logger.e(e);
				} finally {
					if (httpConnection != null)
						httpConnection.disconnect();
					Logger.i("-------Response End-------");
				}
			}
			if (isSucceed && byteArray != null)
				result = request.parseResponse(url, headers.get(Headers.HEAD_KEY_CONTENT_TYPE), byteArray);
		}
		Logger.d("--------------Reqeust Finish--------------");
		return new RestResponser<T>(url, isSucceed, responseCode, headers, byteArray, tag, result);
	}

	@Override
	protected String getUserAgent() {
		return userAgent;
	}

}
