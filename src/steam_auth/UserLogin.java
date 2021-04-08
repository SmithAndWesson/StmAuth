package steam_auth;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

public class UserLogin {
    public String Username;
    public String Password;
    public long SteamID;

    public boolean RequiresCaptcha;
    public String CaptchaGID = null;
    public String CaptchaText = null;

    public boolean RequiresEmail;
    public String EmailDomain = null;
    public String EmailCode = null;

    public boolean Requires2FA;
    public String TwoFactorCode = "";

    public boolean LoggedIn = false;

    public SessionData Session = null;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserLogin(String username, String password)
    {
        this.Username = username;
        this.Password = password;
    }

    public LoginResult DoLogin() {
        try {
            List<NameValuePair> postData = new ArrayList<NameValuePair>();

            postData.add(new BasicNameValuePair("username", this.Username));
            String response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/login/getrsakey", "POST", postData, "");
            if (response == null || response.contains("An error occurred while processing your request.")) return LoginResult.GeneralFailure;

            JsonNode rsaResponse = objectMapper.readTree(response);

            if (!rsaResponse.get("success").asBoolean())
            {
                return LoginResult.BadRSA;
            }

            String publicModulusString = rsaResponse.get("publickey_mod").asText();
            String publicExponentString = rsaResponse.get("publickey_exp").asText();

            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, createPublicKey(publicModulusString, publicExponentString));

            byte[] plainBytes = this.Password.getBytes();
            byte[] cipherData = cipher.doFinal(plainBytes);
            String encryptedStringBase64 = Base64.encodeBase64String(cipherData);

            postData.clear();
            postData.add(new BasicNameValuePair("username", this.Username));
            postData.add(new BasicNameValuePair("password", encryptedStringBase64));
            postData.add(new BasicNameValuePair("twofactorcode", this.TwoFactorCode));
            postData.add(new BasicNameValuePair("captchagid", this.RequiresCaptcha ? this.CaptchaGID : "-1"));
            postData.add(new BasicNameValuePair("captcha_text", this.RequiresCaptcha ? this.CaptchaText : ""));
            postData.add(new BasicNameValuePair("emailsteamid", (this.Requires2FA || this.RequiresEmail) ? String.valueOf(this.SteamID) : ""));
            postData.add(new BasicNameValuePair("emailauth", this.RequiresEmail ? this.EmailCode : ""));
            postData.add(new BasicNameValuePair("rsatimestamp", rsaResponse.get("timestamp").asText()));
            postData.add(new BasicNameValuePair("remember_login", "false"));
            postData.add(new BasicNameValuePair("oauth_client_id", "DE45CD61"));
            postData.add(new BasicNameValuePair("oauth_scope", "read_profile write_profile read_client write_client"));
            postData.add(new BasicNameValuePair("loginfriendlyname", "#login_emailauth_friendlyname_mobile"));
            postData.add(new BasicNameValuePair("donotcache", "1507799810"));

            response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/login/dologin", "POST", postData, "");

            if (response == null) return LoginResult.GeneralFailure;

            JsonNode loginResponse = objectMapper.readTree(response);

            if (loginResponse.get("message") != null && loginResponse.get("message").asText().contains("password that you have entered is incorrect")) {
                return LoginResult.BadCredentials;
            }

            if (loginResponse.has("captchaNeeded")) {
                if (loginResponse.get("captchaNeeded").asBoolean()) {
                    this.RequiresCaptcha = true;
                    this.CaptchaGID = loginResponse.get("captchaGID").asText();
                    return LoginResult.NeedCaptcha;
                }
            }

            if (loginResponse.has("emailauth_needed")) {
                if (loginResponse.get("emailauth_needed").asBoolean()) {
                    this.RequiresEmail = true;
                    this.SteamID = loginResponse.get("emailsteamid").asLong();
                    return LoginResult.NeedEmail;
                }
            }

            if (loginResponse.has("requires_twofactor")) {
                if (loginResponse.get("requires_twofactor").asBoolean() && !loginResponse.get("success").asBoolean()) {
                    this.Requires2FA = true;
                    return LoginResult.Need2FA;
                }
            }

            if (loginResponse.has("message")) {
                if (loginResponse.get("message").asText() != null && loginResponse.get("message").asText().contains("too many login failures")) {
                    return LoginResult.TooManyFailedLogins;
                }
            }

            if (loginResponse.has("oauth")) {
                if (loginResponse.get("oauth").asText() == null) {
                    return LoginResult.GeneralFailure;
                }
            }

            if (loginResponse.has("login_complete")) {
                if (!loginResponse.get("login_complete").asBoolean()) {
                    return LoginResult.BadCredentials;
                }
            }

            if (loginResponse.isNull()) {
                return LoginResult.BadCredentials;
            }
            JsonNode oAuthData = objectMapper.readTree(loginResponse.get("oauth").toString().replaceAll("\\\\", "").substring(1, loginResponse.get("oauth").toString().replaceAll("\\\\", "").length() - 1));

            Connection.Response res = Jsoup.connect("http://steamcommunity.com").method(Method.POST).execute();
            String sessionId = res.cookie("sessionid");

            SessionData session = new SessionData();
            session.OAuthToken = oAuthData.get("oauth_token").asText();
            session.SteamID = oAuthData.get("steamid").asLong();
            session.SteamLogin = session.SteamID + "%7C%7C" + oAuthData.get("wgtoken").asText();
            session.SteamLoginSecure = session.SteamID + "%7C%7C" + oAuthData.get("wgtoken_secure").asText();
            session.WebCookie = oAuthData.get("webcookie").asText();
            session.SessionID = sessionId;
            session.AccountPassword = this.Password;
            this.Session = session;
            this.LoggedIn = true;
            this.Session.AddCookies();
            return LoginResult.LoginOkay;

        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException e) {
            return LoginResult.BadCredentials;
        }
    }

    public enum LoginResult {
        LoginOkay,
        GeneralFailure,
        BadRSA,
        BadCredentials,
        NeedCaptcha,
        Need2FA,
        NeedEmail,
        TooManyFailedLogins,
    }

    private static PublicKey createPublicKey(String mod, String exp) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String publicModulus = mod;
        String publicExponent = exp;
        BigInteger publicExponenInt = new BigInteger(publicExponent, 16);
        BigInteger keyInt = new BigInteger(publicModulus, 16);
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(keyInt, publicExponenInt);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(publicKeySpec);
    }
}
