package com.android.smap.api.requests;

import android.os.AsyncTask;
import android.util.Log;

import com.android.smap.GatewayApp;
import com.android.smap.api.ApiConstants;
import com.android.smap.controllers.HttpErrorListener;
import com.android.smap.controllers.HttpListener;
import com.android.smap.controllers.NetworkError;
import com.android.smap.utils.HttpURLDigestAuth;

import org.apache.http.util.ByteArrayBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by kai on 7/05/2015.
 */
public abstract class RawHttpUrlRequest<T> implements ApiConstants {

    private String url;
    private String username;
    private String password;
    private String method;
    private int responseCode;
    private int port;
    private boolean digestAuth;
    private HttpListener<T> httpListener;
    private HttpErrorListener httpErrorListener;

    protected RawHttpUrlRequest(String url, String method, boolean digestAuth, HttpListener<T> httpListener, HttpErrorListener httpErrorListener) {
        port = 80;
        this.url = url;
        this.method = method;
        this.digestAuth = digestAuth;
        this.httpListener = httpListener;
        this.httpErrorListener = httpErrorListener;

        if (digestAuth) {
            username = GatewayApp.getPreferenceWrapper().getUserName();
            password = GatewayApp.getPreferenceWrapper().getPassword();
            Log.i("username & password", username + " " + password);
//            username = "admin";
//            password = "admin";
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isDigestAuth() {
        return digestAuth;
    }

    public void setDigestAuth(boolean digestAuth) {
        this.digestAuth = digestAuth;
    }

    public HttpListener<T> getHttpListener() {
        return httpListener;
    }

    public HttpErrorListener getHttpErrorListener() {
        return httpErrorListener;
    }

    public NetworkError parseNetworkError(String errorStr) {
        NetworkError error = null;

        return error;
    }

    public String addPortToUrlStr() {
        String resultUrl = null;
        try {
            URL url = new URL(this.url);
            resultUrl = url.getProtocol() + "://" + url.getHost();
            String path = url.toString().substring(resultUrl.length());
            resultUrl = resultUrl + ":" + String.valueOf(port) + path;

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return resultUrl;
    }

    public void executeRequest() {
        new NetworkConnectionTask().execute(url);
    }

    public abstract T onResultResponse(String result);

    public HttpURLConnection doGet(HttpURLConnection conn) {
        HttpURLConnection finalConn;

        if (isDigestAuth()) {
            finalConn = HttpURLDigestAuth.tryDigestAuthentication(conn, username, password);
        } else
            finalConn = conn;

        finalConn.setReadTimeout(10000 /* milliseconds */);
        finalConn.setConnectTimeout(15000 /* milliseconds */);
        finalConn.setDoInput(true);
        return finalConn;
    }

    public HttpURLConnection doPost(HttpURLConnection conn) {
        HttpURLConnection finalConn;

        if (isDigestAuth()) {
            finalConn = HttpURLDigestAuth.tryDigestAuthentication(conn, username, password);
        } else
            finalConn = conn;

        finalConn.setReadTimeout(10000 /* milliseconds */);
        finalConn.setConnectTimeout(15000 /* milliseconds */);
        finalConn.setDoInput(true);
        return finalConn;
    }

    private class NetworkConnectionTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                e.printStackTrace();
                return httpErrorListener.errorMessage;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Log.i("resultAsString:", result);

            if (result.equals(httpErrorListener.errorMessage)) {
                NetworkError error = new NetworkError();
                error.setNetworkDescription(result);
                httpErrorListener.onErrorResponse(error);

            } else if (responseCode >= 400) {
                httpErrorListener.onErrorResponse(parseNetworkError(result));

            } else {
                httpListener.onResponse(onResultResponse(result));
            }
        }
    }

    private String downloadUrl(String myUrl) throws IOException {
        InputStream in = null;

        try {
            // Add port to Url scheme
            addPortToUrlStr();
            URL url = new URL(myUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            Log.i("url", " " + conn.getURL().getPort());

            HttpURLConnection finalConn;

            if (method.equals(DO_GET))
                finalConn = doGet(conn);
            else
                finalConn = doPost(conn);

            // Starts the query
            finalConn.connect();
            responseCode = finalConn.getResponseCode();
            Log.d("", "The response is: " + responseCode + " " + finalConn.getURL());

            if (responseCode >= 400)
                in = finalConn.getErrorStream();
            else
                in = finalConn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(in);

            return contentAsString;

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    public String readIt(InputStream in) throws IOException {

        BufferedInputStream bis = new BufferedInputStream(in);
        ByteArrayBuffer baf = new ByteArrayBuffer(50);
        int read = 0;
        int bufSize = 512;
        byte[] buffer = new byte[bufSize];
        while (true) {
            read = bis.read(buffer);
            if (read == -1) {
                break;
            }
            baf.append(buffer, 0, read);
        }
        return new String(baf.toByteArray());
    }

}