package com.exlibris.restapis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class AlmaRestUtil {

    final private static Logger logger = Logger.getLogger(AlmaRestUtil.class);

    public static HttpResponse sendHttpReq(String url, String method, String body) {
        logger.info("Sending " + method + " request to URL : " + url.replaceAll("apikey=.*", "apikey=notOnLog"));
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Accept", "application/json");
            if (body != null) {
                try {
                    new JSONObject(body);
                    con.setRequestProperty("Content-Type", "application/json");
                } catch (Exception e) {
                    con.setRequestProperty("Content-Type", "application/xml");
                }
                con.setDoOutput(true);
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(con.getOutputStream(), "UTF-8"));
                bw.write(body);
                bw.flush();
                bw.close();
            }

            logger.info("Response Code : " + con.getResponseCode());

            BufferedReader in = null;
            if (con.getErrorStream() != null) {
                // logger.error("reading con.getErrorStream()...");
                in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            }
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
                response.append(System.lineSeparator());
            }
            in.close();
            String out = response.toString().trim();

            // Log output of PUT only on error. Always log output of GET.
            if (con.getResponseCode() >= HttpsURLConnection.HTTP_OK
                    && con.getResponseCode() <= HttpsURLConnection.HTTP_MULT_CHOICE && method != "PUT") {
                logger.info("output: " + out);
            } else {
                try {
                    JSONObject jsonErrror = new JSONObject(out);
                    out = jsonErrror.getJSONObject("errorList").getJSONArray("error").getJSONObject(0)
                            .getString("errorMessage");
                    logger.info("message: " + out);
                } catch (Exception e) {
                    logger.info("message: " + out);
                }
            }

            con.disconnect();

            HttpResponse responseObj = new HttpResponse(out, con.getHeaderFields(), con.getResponseCode());

            return responseObj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}