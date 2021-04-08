package steam_auth;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class AuthenticatorLinker {
    /// Set to register a new phone number when linking. If a phone number is not set on the account, this must be set. If a phone number is set on the account, this must be null.
    public String PhoneNumber = null;

    /// Randomly-generated device ID. Should only be generated once per linker.
    public String DeviceID;

    /// After the initial link step, if successful, this will be the SteamGuard data for the account. PLEASE save this somewhere after generating it; it's vital data.
    public SteamGuardAccount LinkedAccount;

    private SessionData _session;
    private CookiesData _cookies;
    private boolean confirmationEmailSent = false;


    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthenticatorLinker(SessionData session) throws NoSuchAlgorithmException {
        this._session = session;
        this.DeviceID = GenerateDeviceID();
        this._cookies = new CookiesData();
        _session.AddCookies();
        SteamWeb.cookiesData.AddCookies(_session);
    }

    public LinkResult AddAuthenticator() throws IOException, URISyntaxException {
        boolean hasPhone = _hasPhoneAttached();
        if (hasPhone && PhoneNumber != null)
            return LinkResult.MustRemovePhoneNumber;
        if (!hasPhone && PhoneNumber == null)
            return LinkResult.MustProvidePhoneNumber;

        if (!hasPhone) {
            if (confirmationEmailSent) {
                if (!_checkEmailConfirmation()) {
                    return LinkResult.GeneralFailure;
                }
            } else if (!_addPhoneNumber()) {
                return LinkResult.GeneralFailure;
            } else {
                confirmationEmailSent = true;
                return LinkResult.MustConfirmEmail;
            }
        }

        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("access_token", _session.OAuthToken));
        postData.add(new BasicNameValuePair("steamid", String.valueOf(_session.SteamID)));
        postData.add(new BasicNameValuePair("authenticator_type", "1"));
        postData.add(new BasicNameValuePair("device_identifier", this.DeviceID));
        postData.add(new BasicNameValuePair("sms_phone_id", "1"));

        String response = SteamWeb.MobileLoginRequest(APIEndpoints.STEAMAPI_BASE + "/ITwoFactorService/AddAuthenticator/v0001", "POST", postData, "");
        if (response.equals("")) return LinkResult.GeneralFailure;

        JsonNode addAuthenticatorResponse = objectMapper.readTree(response);
        if (addAuthenticatorResponse == null || addAuthenticatorResponse.get("response") == null) {
            return LinkResult.GeneralFailure;
        }

        if (addAuthenticatorResponse.get("response").get("status").asInt() == 29) {
            return LinkResult.AuthenticatorPresent;
        }

        if (addAuthenticatorResponse.get("response").get("status").asInt() == 84) {
            return LinkResult.RateLimitExceeded;
        }

        if (addAuthenticatorResponse.get("response").get("status").asInt() != 1) {
            return LinkResult.GeneralFailure;
        }

        this.LinkedAccount = new SteamGuardAccount();
        LinkedAccount.SharedSecret = addAuthenticatorResponse.get("response").get("shared_secret").asText();
        LinkedAccount.SerialNumber = addAuthenticatorResponse.get("response").get("serial_number").asText();
        LinkedAccount.RevocationCode = addAuthenticatorResponse.get("response").get("revocation_code").asText();
        LinkedAccount.URI = addAuthenticatorResponse.get("response").get("uri").asText();
        LinkedAccount.ServerTime = addAuthenticatorResponse.get("response").get("server_time").asLong();
        LinkedAccount.AccountName = addAuthenticatorResponse.get("response").get("account_name").asText();
        LinkedAccount.AccountName = addAuthenticatorResponse.get("response").get("account_name").asText();
        LinkedAccount.IdentitySecret = addAuthenticatorResponse.get("response").get("identity_secret").asText();
        LinkedAccount.Secret1 = addAuthenticatorResponse.get("response").get("secret_1").asText();
        LinkedAccount.Status = addAuthenticatorResponse.get("response").get("status").asInt();
        LinkedAccount.setSession(this._session);
        LinkedAccount.DeviceID = this.DeviceID;

        return LinkResult.AwaitingFinalization;
    }

    public FinalizeResult FinalizeAddAuthenticator(String smsCode) throws IOException, URISyntaxException {
        //The act of checking the SMS code is necessary for Steam to finalize adding the phone number to the account.
        //Of course, we only want to check it if we're adding a phone number in the first place...

        if (!(this.PhoneNumber == null || this.PhoneNumber.isEmpty()) && !this._checkSMSCode(smsCode))
        {
            return FinalizeResult.BadSMSCode;
        }

        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("steamid", String.valueOf(_session.SteamID)));
        postData.add(new BasicNameValuePair("access_token", _session.OAuthToken));
        postData.add(new BasicNameValuePair("activation_code", smsCode));
        postData.add(new BasicNameValuePair("authenticator_code", ""));
        postData.add(new BasicNameValuePair("authenticator_time", ""));
        int tries = 0;
        while (tries <= 30)
        {
            postData.set(3, new BasicNameValuePair("authenticator_code", LinkedAccount.GenerateSteamGuardCode()));
            postData.set(4, new BasicNameValuePair("authenticator_time", String.valueOf(TimeAligner.GetSteamTime())));

            String response = SteamWeb.MobileLoginRequest(APIEndpoints.STEAMAPI_BASE + "/ITwoFactorService/FinalizeAddAuthenticator/v0001", "POST", postData, "");
            if (response.equals("")) return FinalizeResult.GeneralFailure;

            JsonNode finalizeResponse = objectMapper.readTree(response);

            if (finalizeResponse == null || !finalizeResponse.has("response")) {
                return FinalizeResult.GeneralFailure;
            }

            if (finalizeResponse.get("response").get("status").asInt() == 89) {
                return FinalizeResult.BadSMSCode;
            }

            if (finalizeResponse.get("response").get("status").asInt() == 88) {
                if (tries >= 30) {
                    return FinalizeResult.UnableToGenerateCorrectCodes;
                } //else continue;
            }

            if (!finalizeResponse.get("response").get("success").asBoolean()) {
                return FinalizeResult.GeneralFailure;
            }

            if (finalizeResponse.get("response").get("want_more").asBoolean()) {
                tries++;
                continue;
            }

            this.LinkedAccount.FullyEnrolled = true;
            return FinalizeResult.Success;
        }

        return FinalizeResult.GeneralFailure;
    }

    private boolean _checkSMSCode(String smsCode) throws IOException, URISyntaxException {
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("op", "check_sms_code"));
        postData.add(new BasicNameValuePair("arg", smsCode));
        postData.add(new BasicNameValuePair("sessionid", _session.SessionID));

        String response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/steamguard/phoneajax", "POST", postData, "");
        if (response.equals("")) return false;

        JsonNode addPhoneNumberResponse = objectMapper.readTree(response);

        if (!addPhoneNumberResponse.get("success").asBoolean())
        {
            try {
                Thread.sleep(3500); //It seems that Steam needs a few seconds to finalize the phone number on the account.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return _hasPhoneAttached();
        }

        return true;
    }

    private boolean _addPhoneNumber() throws IOException {
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("op", "add_phone_number"));
        postData.add(new BasicNameValuePair("arg", PhoneNumber));
        postData.add(new BasicNameValuePair("sessionid", _session.SessionID));

        String response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/steamguard/phoneajax", "POST", postData, "");
        if (response.equals("")) return false;

        JsonNode addPhoneNumberResponse = objectMapper.readTree(response);
        return addPhoneNumberResponse.get("success").asBoolean();
    }

    private boolean _checkEmailConfirmation() throws IOException {
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("op", "email_confirmation"));
        postData.add(new BasicNameValuePair("arg", ""));
        postData.add(new BasicNameValuePair("sessionid", _session.SessionID));

        String response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/steamguard/phoneajax", "POST", postData, "");
        if (response.equals("")) return false;

        JsonNode emailConfirmationResponse = objectMapper.readTree(response);
        return emailConfirmationResponse.get("success").asBoolean();
    }

    private boolean _hasPhoneAttached() throws IOException, URISyntaxException {
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("op", "has_phone"));
        postData.add(new BasicNameValuePair("arg", null));
        postData.add(new BasicNameValuePair("sessionid", _session.SessionID));

        String response = SteamWeb.MobileLoginRequest(APIEndpoints.COMMUNITY_BASE + "/steamguard/phoneajax", "POST", postData, "");
        if (response.equals("")) return false;

        JsonNode hasPhoneResponse = objectMapper.readTree(response);
        return hasPhoneResponse.get("has_phone").asBoolean();
    }

    public enum LinkResult {
        MustProvidePhoneNumber, //No phone number on the account
        MustRemovePhoneNumber,  //A phone number is already on the account
        MustConfirmEmail,       //User need to click link from confirmation email
        AwaitingFinalization,   //Must provide an SMS code
        GeneralFailure,         //General failure (really now!)
        RateLimitExceeded,      //Your account called API too many times
        AuthenticatorPresent
    }

    public enum FinalizeResult {
        BadSMSCode,
        UnableToGenerateCorrectCodes,
        Success,
        GeneralFailure
    }

    public static String GenerateDeviceID() throws NoSuchAlgorithmException {
        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");

        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[8];
        secureRandom.nextBytes(randomBytes);

        randomBytes = md.digest(randomBytes);
        String random32 = toHexadecimal(randomBytes);
        random32 = random32.substring(0, 32).toLowerCase();

        return "android:" + SplitOnRatios(random32, new int[]{8, 4, 4, 4, 12}, "-");
    }

    private static String toHexadecimal(byte[] digest) {
        String hash = "";

        for(byte aux : digest) {
            int b = aux & 0xff;
            if (Integer.toHexString(b).length() == 1) hash += "0";
            hash += Integer.toHexString(b);
        }

        return hash;
    }

    private static String SplitOnRatios(String str, int[] ratios, String intermediate) {
        String result = "";

        int pos = 0;
        for (int index = 0; index < ratios.length; index++) {
            result += str.substring(pos, pos + ratios[index]);
            pos = ratios[index];

            if (index < ratios.length - 1)
                result += intermediate;
        }

        return result;
    }
}
