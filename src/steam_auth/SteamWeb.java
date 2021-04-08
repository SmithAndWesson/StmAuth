package steam_auth;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.codehaus.jackson.node.ObjectNode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class SteamWeb {
    public static CookiesData cookiesData = new CookiesData();

    SteamWeb(CookiesData cookiesData) {
        this.cookiesData = cookiesData;
    }

    public static String MobileLoginRequest(String url, String method, List<NameValuePair> data, String inc) {
        HttpRequest request = createNewRequest(url, method, data, inc);
        HttpResponse response = null;

        try {
            HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 30000);
            HttpConnectionParams.setSoTimeout(httpParams, 30000);
            HttpClient httpClient = new DefaultHttpClient(httpParams);
            httpClient.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
            HttpClientContext httpClientContext = new HttpClientContext();
            response = httpClient.execute((HttpUriRequest) request, httpClientContext);
        } catch (IOException e) {
            response = null;
        }

        if (response == null)
            return "";

        assert(response != null);

        java.util.Scanner serverAnswer = null;
        try {
            serverAnswer = new java.util.Scanner(response.getEntity().getContent(), "UTF-8").useDelimiter("\\A");
        } catch (IOException e) {
            serverAnswer = null;
        }

        if (serverAnswer == null)
            return "";
        else {
            String answer = serverAnswer.hasNext() ? serverAnswer.next() : "";
            return answer;
        }
    }

    public static HttpRequest createNewRequest(String url, String method, List<NameValuePair> params, String inc) {
        HttpRequest toReturn = null;

        if(method.equals("GET")) {
            toReturn = new HttpGet(url);
            if (inc.equals("inc")) {
                for (Map.Entry header : cookiesData.STEAM_HEADERS.entrySet()) {
                    toReturn.setHeader(header.getKey().toString(), header.getValue().toString());
                }
                toReturn.setHeader("Cookie", "sessionid=" + cookiesData.STEAM_COOKIES.get("sessionid") + "; webTradeEligibility=%7B%22allowed%22%3A1%2C%22allowed_at_time%22%3A0%2C%22steamguard_required_days%22%3A15%2C%22sales_this_year%22%3A1%2C%22max_sales_per_year%22%3A200%2C%22forms_requested%22%3A0%2C%22new_device_cooldown_days%22%3A7%2C%22time_checked%22%3A0%7D; steamLogin=" + cookiesData.STEAM_COOKIES.get("steamLogin") + "; steamLoginSecure=" + cookiesData.STEAM_COOKIES.get("steamLoginSecure"));
//                toReturn.setHeader("Cookie", "sessionid=ed57d6824d3343e77b3224a4; steamCountry=UA%7C7689eeb6bf34b1e4b884b4104e2eace8; steamMachineAuth76561198375116632=11657E41699F9B180909067FB6115012434F160E; steamMachineAuth76561197992897470=D0028E483ED067114E66F130F17EA75DAF8B8F3A; rgDiscussionPrefs=%7B%22cTopicRepliesPerPage%22%3A30%7D; _ga=GA1.2.591161597.1495220173; strResponsiveViewPrefs=desktop; recentlyVisitedAppHubs=236870%2C570%2C730; timezoneOffset=10800,0; strInventoryLastContext=570_2; webTradeEligibility=%7B%22allowed%22%3A1%2C%22allowed_at_time%22%3A0%2C%22steamguard_required_days%22%3A15%2C%22sales_this_year%22%3A1%2C%22max_sales_per_year%22%3A200%2C%22forms_requested%22%3A0%2C%22new_device_cooldown_days%22%3A7%2C%22time_checked%22%3A1522403550%7D; _gid=GA1.2.706576524.1523524688; steamLogin=76561197992897470%7C%7C9F9D5ACC91323D62169BF822459742C3F2BA643E; steamLoginSecure=76561197992897470%7C%7C348CE5575D82F4CDD80D6A63D2D6E35314A2BCB3; tsTradeOffersLastRead=1523529852");
            } else {
                for (Map.Entry header : cookiesData.STEAM_HEADERS.entrySet()) {
                    toReturn.setHeader(header.getKey().toString(), header.getValue().toString());
                }
                String cookieStr = "";
                for (Map.Entry cookie : cookiesData.STEAM_COOKIES.entrySet()) {
                    cookieStr += cookie.getKey() + "=" + cookie.getValue() + ";";
                }
                toReturn.setHeader("Cookie", cookieStr.substring(0, cookieStr.length() - 1));
            }
        } else if (method.equals("POST")) {
            toReturn = new HttpPost(url);
            if (inc.equals("accept")) {
                for (Map.Entry header : cookiesData.STEAM_HEADERS_ACCEPT.entrySet()) {
                    toReturn.setHeader(header.getKey().toString(), header.getValue().toString());
                }
                toReturn.setHeader("Cookie", "sessionid=" + cookiesData.STEAM_COOKIES.get("sessionid") + "; steamLogin=" + cookiesData.STEAM_COOKIES.get("steamLogin") + "; steamLoginSecure=" + cookiesData.STEAM_COOKIES.get("steamLoginSecure"));
            } else {
                for (Map.Entry header : cookiesData.STEAM_HEADERS.entrySet()) {
                    toReturn.setHeader(header.getKey().toString(), header.getValue().toString());
                }
                String cookieStr = "";
                for (Map.Entry cookie : cookiesData.STEAM_COOKIES.entrySet()) {
                    cookieStr += cookie.getKey() + "=" + cookie.getValue() + ";";
                }
                toReturn.setHeader("Cookie", cookieStr.substring(0, cookieStr.length() - 1));
            }
        }

        if(params != null && method.equals("POST")) {
            try {
                ((HttpPost) toReturn).setEntity(new UrlEncodedFormEntity(params));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return toReturn;
    }

    public String getResponse(String baseURI, String method, Object params, String inc) {
        String answer = "";

        try {
            URL url = new URL(baseURI);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (inc.equals("inc")) {
                for (Map.Entry header : cookiesData.STEAM_HEADERS.entrySet()) {
                    conn.setRequestProperty(header.getKey().toString(), header.getValue().toString());
                }
                conn.setRequestProperty("Cookie", "sessionid=" + cookiesData.STEAM_COOKIES.get("sessionid") + "; webTradeEligibility=%7B%22allowed%22%3A1%2C%22allowed_at_time%22%3A0%2C%22steamguard_required_days%22%3A15%2C%22sales_this_year%22%3A1%2C%22max_sales_per_year%22%3A200%2C%22forms_requested%22%3A0%2C%22new_device_cooldown_days%22%3A7%2C%22time_checked%22%3A0%7D; steamLogin=" + cookiesData.STEAM_COOKIES.get("steamLogin") + "; steamLoginSecure=" + cookiesData.STEAM_COOKIES.get("steamLoginSecure"));
            } else if (inc.equals("accept")) {
                for (Map.Entry header : cookiesData.STEAM_HEADERS_ACCEPT.entrySet()) {
                    conn.setRequestProperty(header.getKey().toString(), header.getValue().toString());
                }
                conn.setRequestProperty("Cookie", "sessionid=" + cookiesData.STEAM_COOKIES.get("sessionid") + "; steamLogin=" + cookiesData.STEAM_COOKIES.get("steamLogin") + "; steamLoginSecure=" + cookiesData.STEAM_COOKIES.get("steamLoginSecure"));
            } else {
                for (Map.Entry header : cookiesData.STEAM_HEADERS.entrySet()) {
                    conn.setRequestProperty(header.getKey().toString(), header.getValue().toString());
                }
                String cookieStr = "";
                for (Map.Entry cookie : cookiesData.STEAM_COOKIES.entrySet()) {
                    cookieStr += cookie.getKey() + "=" + cookie.getValue() + ";";
                }
                conn.setRequestProperty("cookie", cookieStr);
            }

            if(method.equals("GET")) {
                conn.setRequestMethod("GET");
            } else if(method.equals("POST")) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
                if (params instanceof List)
                    writer.write(getQuery((List<NameValuePair>)params));
                if (params instanceof String)
                    writer.write(URLEncoder.encode((String)params, "UTF-8"));
                if (params instanceof ObjectNode)
                    writer.write(params.toString());
                writer.flush();
                writer.close();
            }

//            conn.setConnectTimeout(7000);
            int HttpResult = conn.getResponseCode();
            if (HttpResult == HttpURLConnection.HTTP_OK) {
                InputStream stream = conn.getInputStream();
                ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
                byte buffer[] = new byte[1024];
                int bytesRead = 0;
                while ((bytesRead = stream.read(buffer)) > 0) {
                    responseBody.write(buffer, 0, bytesRead);
                }
                answer = new String(responseBody.toByteArray(), "UTF-8");
                responseBody.close();
                stream.close();
            } else {
                System.out.println(conn.getResponseCode() + "\t" + conn.getResponseMessage());
            }
        } catch (IOException e) {

        }

        return answer;
    }

    private String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params)
        {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }
}