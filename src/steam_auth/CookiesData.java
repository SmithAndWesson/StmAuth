package steam_auth;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CookiesData implements Serializable
{
    private static final long serialVersionUID = 4996898438451898647L;

    public Map<String, String> STEAM_COOKIES = new HashMap<String, String>();
    public Map<String, String> STEAM_HEADERS = new HashMap<String, String>();
    public Map<String, String> STEAM_HEADERS_ACCEPT = new HashMap<String, String>();

    public CookiesData(){
        STEAM_COOKIES.put("mobileClientVersion", "0 (2.1.3)");
        STEAM_COOKIES.put("mobileClient", "android");
        STEAM_COOKIES.put("Steam_Language", "english");

        STEAM_HEADERS.put("Accept", "text/javascript, text/html, application/xml, text/xml, */*");
//        STEAM_HEADERS.put("Accept-Encoding", "gzip, deflate");
//        STEAM_HEADERS.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8, */*");
//        STEAM_HEADERS.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
//        STEAM_HEADERS.put("Referer", "https://steamcommunity.com/login?oauth_client_id=DE45CD61&oauth_scope=read_profile%20write_profile%20read_client%20write_client");
        STEAM_HEADERS.put("Referer", "https://steamcommunity.com/");
        STEAM_HEADERS.put("X-Requested-With", "com.valvesoftware.android.steam.community");
        STEAM_HEADERS.put("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-us; Google Nexus 4 - 4.1.1 - API 16 - 768x1280 Build/JRO03S) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30");

        // for AcceptTradeOffers
        STEAM_HEADERS_ACCEPT.put("Referer", "https://steamcommunity.com/tradeoffer/1/");
        STEAM_HEADERS_ACCEPT.put("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-us; Google Nexus 4 - 4.1.1 - API 16 - 768x1280 Build/JRO03S) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30");
    }

    public void AddCookies(SessionData sessionData)
    {
        STEAM_COOKIES.put("steamid", String.valueOf(sessionData.SteamID));
        STEAM_COOKIES.put("sessionid", sessionData.SessionID);
        STEAM_COOKIES.put("steamLogin", sessionData.SteamLogin);
        STEAM_COOKIES.put("steamLoginSecure", sessionData.SteamLoginSecure);
        STEAM_COOKIES.put("dob", "");
    }

 }