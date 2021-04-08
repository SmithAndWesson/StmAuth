package steam_auth;

import java.io.Serializable;

public class SessionData implements Serializable {
    private static final long serialVersionUID = 4996898438451898651L;

    public String SessionID;
    public String SteamLogin;
    public String SteamLoginSecure;
    public String WebCookie;
    public String OAuthToken;
    public String AccountPassword;
    public long SteamID;
    public CookiesData cookies = new CookiesData();

    public void AddCookies()
    {
        cookies.STEAM_COOKIES.put("steamid", String.valueOf(SteamID));
        cookies.STEAM_COOKIES.put("sessionid", this.SessionID);
        cookies.STEAM_COOKIES.put("steamLogin", SteamLogin);
        cookies.STEAM_COOKIES.put("steamLoginSecure", SteamLoginSecure);
        cookies.STEAM_COOKIES.put("dob", "");
    }
}
