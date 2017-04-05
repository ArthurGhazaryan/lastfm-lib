//Copyright 2016 Arthur Ghazaryan

//        Licensed under the Apache License, Version 2.0 (the "License");
//        you may not use this file except in compliance with the License.
//        You may obtain a copy of the License at
//
//        http://www.apache.org/licenses/LICENSE-2.0
//
//        Unless required by applicable law or agreed to in writing, software
//        distributed under the License is distributed on an "AS IS" BASIS,
//        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//        See the License for the specific language governing permissions and
//        limitations under the License.


package com.ag.lfm.api.httpClient;

import android.os.AsyncTask;

import com.ag.lfm.LfmError;
import com.ag.lfm.LfmParameters;
import com.ag.lfm.LfmRequest;
import com.ag.lfm.ScrobbleParameters;
import com.ag.lfm.util.LfmUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Sending request using AsyncTask.
 */
@SuppressWarnings("unused")
public class JSONOperation extends AsyncTask<LfmRequest.LfmRequestListener, Void, Void> {


    /**
     * API request parameters.
     */
    private LfmParameters params;

    /**
     * Root URL.
     */
    private static final String ROOT_URL = "https://ws.audioscrobbler.com/2.0/";

    /**
     * API method.
     */
    private String method;

    /**
     * API request URL.
     */
    private String requestURL;

    /**
     * Error from API request.
     */
    private LfmError error = new LfmError();

    /**
     * JSON response from API request.
     */
    private JSONObject response;

    /**
     * Scrobble parameters.
     */
    private ScrobbleParameters scrobbleParameters;

    private LfmRequest.LfmRequestListener listener;

    private boolean scrobbleMethod;


    private boolean restRequest;


    private HttpURLConnection connection = null;
    private BufferedReader reader = null;

    public JSONOperation(String method, LfmParameters params, boolean restRequest) {
        this.params = params;
        this.method = method;
        this.restRequest = restRequest;
    }

    public JSONOperation(ScrobbleParameters scrobbleParameters) {
        this.scrobbleParameters = scrobbleParameters;
        scrobbleMethod = true;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (!restRequest && !scrobbleMethod)
            requestURL = LfmUtil.generateRequestURL(method, params);

    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (response != null)
            listener.onComplete(response);
        else if (error != null)
            listener.onError(error);
    }

    @Override
    protected Void doInBackground(LfmRequest.LfmRequestListener... params) {
        this.listener = params[0];
        try {
            if (restRequest) {
                send("restRequest");
            } else if (scrobbleMethod) {
                send("scrobble");
            } else
                send("getJsonResponse");
        } catch (NoSuchMethodException | InvocationTargetException | JSONException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void send(String methodName) throws JSONException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method invokeMethod = this.getClass().getDeclaredMethod(methodName);
            invokeMethod.setAccessible(true);
            invokeMethod.invoke(this);
        } catch (Exception e) {
            if (e.getClass().getName().equals(IOException.class.getName()))
                errorHandling(e.getMessage());
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        if (connection != null)
            connection.disconnect();
        try {
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            error.httpClientError = true;
            error.errorMessage = e.getMessage();
        }
    }

    private void errorHandling(String msg) {
        error.httpClientError = true;
        error.errorMessage = msg;
        response = null;
    }

    private void parseBadRequestResponse() throws JSONException, IOException {
        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        StringBuffer buffer = new StringBuffer();

        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }

        if (!buffer.toString().isEmpty()) {
            JSONObject errorObject = new JSONObject(buffer.toString());
            error = new LfmError(errorObject);
        } else {
            error.errorCode = connection.getResponseCode();
            error.errorMessage = connection.getResponseMessage();
            response = null;
        }
    }

    /**
     * Method for JSON request.
     */
    private void getJsonResponse() throws JSONException, IOException {
        StringBuffer buffer;
        URL url = new URL(requestURL);
        connection = (HttpURLConnection) url.openConnection();

        if (connection.getResponseCode() == 200) {
            InputStream in = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(in));
            buffer = new StringBuffer();

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            response = new JSONObject(buffer.toString());
            if (!response.optString("error").equals("")) {
                error = new LfmError(response);
                response = null;
            } else {
                error = null;
            }
        } else {
            parseBadRequestResponse();
        }
    }


    private void scrobble() throws JSONException, IOException {
        StringBuffer buffer;
        URL url = new URL(ROOT_URL);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(scrobbleParameters.parseParameters());
        wr.flush();
        wr.close();
        if (connection.getResponseCode() == 200) {
            InputStream in = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(in));
            buffer = new StringBuffer();

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            response = new JSONObject(buffer.toString());
            if (!response.optString("error").equals("")) {
                error = new LfmError(response);
                response = null;
            } else if (response.optJSONObject("ignoredMessage") != null) {
                error.errorCode = Integer.valueOf(response.optJSONObject("ignoredMessage").optString("code"));
                error.errorMessage = response.optJSONObject("ignoredMessage").optString("#text");
            } else {
                error = null;
            }

        } else {
            parseBadRequestResponse();
        }

    }

    private void restRequest() throws JSONException, IOException {
        StringBuffer buffer;

        URL url = new URL(ROOT_URL);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(LfmUtil.parseRestRequestParams(method, params));
        wr.flush();
        wr.close();
        if (connection.getResponseCode() == 200) {
            InputStream in = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(in));
            buffer = new StringBuffer();

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            response = new JSONObject(buffer.toString());
            if (!response.optString("error").equals("")) {
                error = new LfmError(response);
                response = null;
            } else {
                error = null;
            }
        } else {
            parseBadRequestResponse();
        }
    }
}


