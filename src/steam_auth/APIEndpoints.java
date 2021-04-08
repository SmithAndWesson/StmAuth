package steam_auth;

public class APIEndpoints {
    public static String STEAMAPI_BASE = "https://api.steampowered.com";
    public static String COMMUNITY_BASE = "https://steamcommunity.com";
    public static String MOBILEAUTH_BASE = STEAMAPI_BASE + "/IMobileAuthService/%s/v0001";
    public static String MOBILEAUTH_GETWGTOKEN = MOBILEAUTH_BASE.replace("%s", "GetWGToken");
    public static String TWO_FACTOR_BASE = STEAMAPI_BASE + "/ITwoFactorService/%s/v0001";
    public static String TWO_FACTOR_TIME_QUERY = TWO_FACTOR_BASE.replace("%s", "QueryTime");
}
