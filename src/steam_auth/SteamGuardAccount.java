package steam_auth;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonProcessingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static steam_auth.SDAForm.*;
import static steam_auth.TimeAligner.objectMapper;

public class SteamGuardAccount extends Thread implements Serializable {
    private static final long serialVersionUID = 6622936708841124391L;
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    //[JsonProperty("shared_secret")]
    public String SharedSecret;

    //[JsonProperty("serial_number")]
    public String SerialNumber;

    //[JsonProperty("revocation_code")]
    public String RevocationCode;

    //[JsonProperty("uri")]
    public String URI;

    //[JsonProperty("server_time")]
    public long ServerTime;

    //[JsonProperty("account_name")]
    public String AccountName;

    //[JsonProperty("token_gid")]
    public String TokenGID;

    //[JsonProperty("identity_secret")]
    public String IdentitySecret;

    //[JsonProperty("secret_1")]
    public String Secret1;

    //[JsonProperty("status")]
    public int Status;

    //[JsonProperty("device_id")]
    public String DeviceID;

    /// <summary>
    /// Set to true if the authenticator has actually been applied to the account.
    /// </summary>
    //[JsonProperty("fully_enrolled")]
    public boolean FullyEnrolled;

    private SessionData Session;

    private static byte[] steamGuardCodeTranslations = new byte[] { 50, 51, 52, 53, 54, 55, 56, 57, 66, 67, 68, 70, 71, 72, 74, 75, 77, 78, 80, 81, 82, 84, 86, 87, 88, 89 };

    public SessionData getSession() {
         return Session;
    }

    public void setSession(SessionData session) {
        Session = session;
    }

    public boolean DeactivateAuthenticator(int scheme)
    {
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("steamid", String.valueOf(Session.SteamID)));
        postData.add(new BasicNameValuePair("steamguard_scheme", String.valueOf(scheme)));
        postData.add(new BasicNameValuePair("revocation_code", this.RevocationCode));
        postData.add(new BasicNameValuePair("access_token", this.Session.OAuthToken));

        try
        {
            String response = new SteamWeb(this.Session.cookies).MobileLoginRequest(APIEndpoints.STEAMAPI_BASE + "/ITwoFactorService/RemoveAuthenticator/v0001", "POST", postData, "");
            JsonNode removeResponse = objectMapper.readTree(response);
            if (removeResponse != null || removeResponse.has("response"))
                if (!removeResponse.get("response").get("success").asBoolean()) return false;
            return true;
        } catch (JsonProcessingException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public String GenerateSteamGuardCode()
    {
        return GenerateSteamGuardCodeForTime(TimeAligner.GetSteamTime());
    }

    public String GenerateSteamGuardCodeForTime(long time) {
        if (SharedSecret == null || SharedSecret.length() == 0) {
            return "";
        }

        byte[] timeArray = new byte[8];

        time /= 30L;

        for (int i = 8; i > 0; i--) {
            timeArray[i - 1] = (byte)time;
            time >>= 8;
        }

        SecretKeySpec signingKey = new SecretKeySpec(Base64.getDecoder().decode(SharedSecret.getBytes()), "HmacSHA1");
        Mac mac = null;
        try {
            mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        byte[] hashedData = mac.doFinal(timeArray);
        byte[] codeArray = new byte[5];

        byte b = (byte)(hashedData[19] & 0xF);
        int codePoint = (hashedData[b] & 0x7F) << 24 | (hashedData[b + 1] & 0xFF) << 16 | (hashedData[b + 2] & 0xFF) << 8 | (hashedData[b + 3] & 0xFF);

        for (int i = 0; i < 5; ++i) {
            codeArray[i] = steamGuardCodeTranslations[codePoint % steamGuardCodeTranslations.length];
            codePoint /= steamGuardCodeTranslations.length;
        }

        return new String(codeArray);
    }

    public List<Confirmation> FetchConfirmations() throws WGTokenInvalidException {
        List<Confirmation> confirmations = new ArrayList<Confirmation>();
        String url = GenerateConfirmationURL("conf");
        List<NameValuePair> params = GenerateConfirmationQueryParamsAsNVC("conf");

        this.Session.AddCookies();

        String response = new SteamWeb(this.Session.cookies).getResponse(url, "POST", params, "");
//        String response = new SteamWeb(this.Session.cookies).getResponse(url, "GET", null, "");

        if (response == null || response.equals("")) {
            throw new WGTokenInvalidException();
        }

        Document doc = Jsoup.parse(response);
        if (response.indexOf("Invalid authenticator") > 0)
            System.out.println("Invalid authenticator");

        Elements elements = doc.getElementsByAttribute("data-confid");
        for (Element element : elements) {
            Confirmation conf = new Confirmation();
            conf.ID = element.attr("data-confid");
            conf.Key = element.attr("data-key");
            conf.Description = element.getElementsByAttributeValue("class", "mobileconf_list_entry_description").get(0).child(0).text();
            conf.ConfType = (conf.Description == null || conf.Description.isEmpty()) ? Confirmation.ConfirmationType.Unknown : (conf.Description.startsWith("Confirm ")) ? Confirmation.ConfirmationType.GenericConfirmation : (conf.Description.startsWith("Trade with ")) ? Confirmation.ConfirmationType.Trade : (conf.Description.startsWith("Sell -")) ? Confirmation.ConfirmationType.MarketSellTransaction : Confirmation.ConfirmationType.Unknown;
            confirmations.add(conf);
        }

        for (Confirmation conf : confirmations) {
            System.out.println(conf.Description);
        }

        return confirmations;
    }

    public List<Confirmation> getIncomingTradeOffers_API()
    {
        List<Confirmation> activeTradeOffers = new ArrayList<Confirmation>();
        String responseOut = new SteamWeb(this.Session.cookies).MobileLoginRequest("http://steamcommunity.com/profiles/" + getSession().SteamID + "/tradeoffers/sent", "GET", null, "inc");
//        String response = new SteamWeb(this.Session.cookies).getResponse("https://api.steampowered.com/IEconService/GetTradeOffers/v1/?key="+ getApiKey() +"&get_sent_offers=true&get_received_offers=true&active_only=true", "GET", null, "inc");
        String response = new SteamWeb(this.Session.cookies).MobileLoginRequest("https://api.steampowered.com/IEconService/GetTradeOffers/v1/?key="+ getApiKey() +"&get_sent_offers=true&get_received_offers=true&active_only=true", "GET", null, "");
        if (response.equals("")) return activeTradeOffers;
        System.out.println(this.AccountName + response);
        try {
            JsonNode removeResponse = objectMapper.readTree(response.toString());

            for (Iterator<Map.Entry<String, JsonNode>> arrayNodes = removeResponse.get("response").getFields(); arrayNodes.hasNext(); ) {
                Map.Entry<String, JsonNode> arrayNode = arrayNodes.next();
                String key = arrayNode.getKey();
                JsonNode tradeOffers = arrayNode.getValue();
                for (JsonNode tradeOffer : tradeOffers) {
                    boolean isConfAdd = true;
                    if(key.equals("trade_offers_received")) {
                        if(tradeOffer.has("items_to_give")) {
                            for (JsonNode item : tradeOffer.get("items_to_give")) {
                                if (item.get("appid").asInt() == 730) {
                                    isConfAdd = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (isConfAdd && tradeOffer.has("tradeofferid")) {
                        Confirmation conf = new Confirmation();
                        conf.ID = tradeOffer.get("tradeofferid").asText();
                        conf.partnerId = tradeOffer.get("accountid_other").asText();
                        conf.ConfState = tradeOffer.get("trade_offer_state").asInt();
                        conf.Type = key;
                        activeTradeOffers.add(conf);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return activeTradeOffers;
    }

    public List<Confirmation> getTradeOffers()
    {
        List<Confirmation> activeTradeOffers = new ArrayList<Confirmation>();
        String responseInc = new SteamWeb(this.Session.cookies).MobileLoginRequest("http://steamcommunity.com/profiles/" + getSession().SteamID + "/tradeoffers", "GET", null, "inc");
        String responseOut = new SteamWeb(this.Session.cookies).MobileLoginRequest("http://steamcommunity.com/profiles/" + getSession().SteamID + "/tradeoffers/sent", "GET", null, "inc");
        Document doc = Jsoup.parse(responseInc);
        Elements tradeOfferElements = doc.getElementsByClass("tradeoffer");
        for(Element tradeOfferElement : tradeOfferElements) {
            boolean active = tradeOfferElement.getElementsByClass("tradeoffer_items_ctn").get(0).hasClass("active");
            if (active) {
                Elements items_our = tradeOfferElement.getElementsByClass("tradeoffer_item_list").get(1).getElementsByAttribute("data-economy-item");
                boolean isConfAdd = true;

                for(Element itemOur : items_our){
                    String item = itemOur.attr("data-economy-item");
                    if(item.indexOf("/730/") > 0) {
                        isConfAdd = false;
                        break;
                    }
                }

                if(isConfAdd) {
                    Confirmation conf = new Confirmation();
                    conf.ID = tradeOfferElement.id().substring(13);
                    conf.partnerId = tradeOfferElements.get(0).childNodes().get(3).childNodes().get(1).attributes().get("data-miniprofile");
                    conf.Type = "trade_offers_received";
                    conf.ConfState = 2;
                    activeTradeOffers.add(conf);
                }
            }
        }

        doc = Jsoup.parse(responseOut);
        tradeOfferElements = doc.getElementsByClass("tradeoffer");
        for(Element tradeOfferElement : tradeOfferElements) {
            boolean active = tradeOfferElement.getElementsByClass("tradeoffer_items_ctn").get(0).hasClass("inactive");
            if (active) {
                Confirmation conf = new Confirmation();
                conf.ID = tradeOfferElement.id().substring(13);
                conf.partnerId = tradeOfferElements.get(0).childNodes().get(3).childNodes().get(1).attributes().get("data-miniprofile");
                conf.Type = "trade_offers_sent";
                conf.ConfState = 9;
                activeTradeOffers.add(conf);
            }
        }

        return activeTradeOffers;
    }

    public List<Confirmation> getIncomingTradeOffers()
    {
        List<Confirmation> activeTradeOffers = new ArrayList<Confirmation>();
        String response = new SteamWeb(this.Session.cookies).MobileLoginRequest("http://steamcommunity.com/profiles/" + getSession().SteamID + "/tradeoffers", "GET", null, "inc");
        Document doc = Jsoup.parse(response);
        Elements tradeOfferElements = doc.getElementsByClass("tradeoffer");
        for(Element tradeOfferElement : tradeOfferElements) {
            boolean active = tradeOfferElement.getElementsByClass("tradeoffer_items_ctn").get(0).hasClass("active");
            if (active) {
                Confirmation conf = new Confirmation();
                conf.ID = tradeOfferElement.id().substring(13);
                conf.partnerId = tradeOfferElements.get(0).childNodes().get(3).childNodes().get(1).attributes().get("data-miniprofile");
                activeTradeOffers.add(conf);
            }
        }

        return activeTradeOffers;
    }

    public String AcceptTradeOffer(String tradeOfferId, String partnerId) {
        String baseURI = "https://steamcommunity.com/tradeoffer/" + tradeOfferId + "/accept";

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("sessionid", getSession().SessionID));
        params.add(new BasicNameValuePair("serverid", "1"));
        params.add(new BasicNameValuePair("tradeofferid", tradeOfferId));
        params.add(new BasicNameValuePair("partner", partnerId));
        params.add(new BasicNameValuePair("captcha", ""));

        String answer = new SteamWeb(this.Session.cookies).getResponse(baseURI, "POST", params, "accept");
        try {
            JsonNode node = objectMapper.readTree(answer);
            return node.toString();
        } catch (JsonParseException e) {
            return answer;
        } catch (IOException e1) {
            return answer;
        }
    }

    public boolean RefreshSession() {
        String url = APIEndpoints.MOBILEAUTH_GETWGTOKEN;
        List<NameValuePair> postData = new ArrayList<NameValuePair>();
        postData.add(new BasicNameValuePair("access_token", this.Session.OAuthToken));

        String response = new SteamWeb(this.Session.cookies).MobileLoginRequest(url, "POST", postData, "");

        if (response == null || response.equals("")) return false;

        try {
            JsonNode refreshResponse = objectMapper.readTree(response);
            if (refreshResponse == null || refreshResponse.has("response") == false)
                return false;

            String token = this.Session.SteamID + "%7C%7C" + refreshResponse.get("response").get("token").asText();
            String tokenSecure = this.Session.SteamID + "%7C%7C" + refreshResponse.get("response").get("token_secure").asText();

            this.Session.SteamLogin = token;
            this.Session.SteamLoginSecure = tokenSecure;
            return true;
        } catch (JsonProcessingException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public long GetConfirmationTradeOfferID(Confirmation conf) {
        JsonNode confDetails = _getConfirmationDetails(conf);
        if (confDetails == null || !confDetails.get("success").asBoolean()) return -1;

//        Regex tradeOfferIDRegex = new Regex("<div class=\"tradeoffer\" id=\"tradeofferid_(\\d+)\" >");
//        if (!tradeOfferIDRegex.IsMatch(confDetails.HTML)) return -1;
//        return long.Parse(tradeOfferIDRegex.Match(confDetails.HTML).Groups[1].Value);
        return 0;
    }

    private JsonNode _getConfirmationDetails(Confirmation conf) {
        String url = APIEndpoints.COMMUNITY_BASE + "/mobileconf/details/" + conf.ID + "?";
        String queryString = GenerateConfirmationQueryParams("details");
        url += queryString;

        this.Session.AddCookies();

        String response = new SteamWeb(this.Session.cookies).MobileLoginRequest(url, "GET", null, "");
        if (response == null || response.isEmpty()) return null;

        JsonNode confResponse = null;
        try {
            confResponse = objectMapper.readTree(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (confResponse == null || response.isEmpty()) return null;

        return confResponse;
    }

    public String GenerateConfirmationURL(String tag) {
// ПЕРЕВЕЛ НА POST ЗАПРОС для GET нужно раскоментировать (какой-то из параметров неправильно энкодится)
//        String endpoint = APIEndpoints.COMMUNITY_BASE + "/mobileconf/conf?";
//        String queryString = GenerateConfirmationQueryParams(tag);
//        return endpoint + queryString;
        String endpoint = APIEndpoints.COMMUNITY_BASE + "/mobileconf/conf";
        return endpoint;
    }

    public String GenerateConfirmationQueryParams(String tag) {
        if (DeviceID == null || DeviceID.isEmpty()) {
            try {
                throw new Exception("Device ID is not present");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<NameValuePair> queryParams = GenerateConfirmationQueryParamsAsNVC(tag);

        return "p=" + queryParams.get(0).getValue() + "&a=" + queryParams.get(1).getValue() + "&k=" + queryParams.get(2).getValue() + "&t=" + queryParams.get(3).getValue() + "&m=" + queryParams.get(4).getValue() + "&tag=" + queryParams.get(5).getValue();
    }

    public List<NameValuePair> GenerateConfirmationQueryParamsAsNVC(String tag) {
        if (DeviceID == null || DeviceID.isEmpty()) {
            try {
                throw new Exception("Device ID is not present");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        long time = TimeAligner.GetSteamTime();

        List<NameValuePair>  ret = new ArrayList<NameValuePair>();
        ret.add(new BasicNameValuePair("p", this.DeviceID));
        ret.add(new BasicNameValuePair("a", String.valueOf(this.Session.SteamID)));
        ret.add(new BasicNameValuePair("k", _generateConfirmationHashForTime(time, tag)));
        ret.add(new BasicNameValuePair("t", String.valueOf(time)));
        ret.add(new BasicNameValuePair("m", "android"));
        ret.add(new BasicNameValuePair("tag", tag));

        return ret;
    }

    private String _generateConfirmationHashForTime(long time, String tag) {
        int n2 = 8;
        if (tag != null) {
            if (tag.length() > 32) {
                n2 = 8 + 32;
            } else {
                n2 = 8 + tag.length();
            }
        }
        byte[] array = new byte[n2];
        int n3 = 8;
        while (true) {
            int n4 = n3 - 1;
            if (n3 <= 0) {
                break;
            }
            array[n4] = (byte) time;
            time >>= 8;
            n3 = n4;
        }
        if (tag != null) {
            System.arraycopy(tag.getBytes(), 0, array, 8, n2 - 8);
        }

        try {
            SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(this.IdentitySecret.getBytes()), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(keySpec);
            byte[] encodedData = mac.doFinal(array);
            String hash = new String(Base64.getEncoder().encode(encodedData), "UTF-8");

            return hash;
        } catch (Exception e) {
            return null;
        }

//        try {
//            // Get an hmac_sha1 key from the raw key bytes
//            byte[] keyBytes = this.IdentitySecret.getBytes();
//            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");
//
//            // Get an hmac_sha1 Mac instance and initialize with the signing key
//            Mac mac = Mac.getInstance("HmacSHA1");
//            mac.init(signingKey);
//
//            // Compute the hmac on input data bytes
//            byte[] rawHmac = mac.doFinal(array);
//
//            //  Covert array of Hex bytes to a String
//            String hash = new String(Base64.getEncoder().encode(rawHmac), "UTF-8");
//
//            return hash;
//        } catch (Exception e) {
//            return null;
//        }

    }

    public boolean AcceptConfirmation(Confirmation conf)
    {
        return _sendConfirmationAjax(conf, "allow");
    }

    public boolean DenyConfirmation(Confirmation conf)
    {
        return _sendConfirmationAjax(conf, "cancel");
    }

    public boolean AcceptMultipleConfirmations(List<Confirmation> confs)
    {
        return _sendMultiConfirmationAjax(confs, "allow");
    }

    public boolean DenyMultipleConfirmations(List<Confirmation> confs)
    {
        return _sendMultiConfirmationAjax(confs, "cancel");
    }

    private  boolean _sendConfirmationAjax(Confirmation conf, String op) {
        String url = APIEndpoints.COMMUNITY_BASE + "/mobileconf/ajaxop";

        List<NameValuePair> query = GenerateConfirmationQueryParamsAsNVC(op);
        query.add(new BasicNameValuePair("op", op));
        query.add(new BasicNameValuePair("cid", conf.ID));
        query.add(new BasicNameValuePair("ck", conf.Key));

        this.Session.AddCookies();

        String response = new SteamWeb(this.Session.cookies).MobileLoginRequest(url, "POST", query, "");
        if (response == null) return false;
        System.out.println(response);

        JsonNode confResponse = null;
        try {
            confResponse = objectMapper.readTree(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return confResponse.get("success").asBoolean();
    }

    private boolean _sendMultiConfirmationAjax(List<Confirmation> confs, String op) {
        String url = APIEndpoints.COMMUNITY_BASE + "/mobileconf/multiajaxop";

        List<NameValuePair> query = GenerateConfirmationQueryParamsAsNVC(op);
        query.add(new BasicNameValuePair("op", op));
        for (Confirmation conf : confs) {
            query.add(new BasicNameValuePair("cid[]", conf.ID));
            query.add(new BasicNameValuePair("ck[]", conf.Key));
        }

        this.Session.AddCookies();

        String response = new SteamWeb(this.Session.cookies).getResponse(url, "POST", query, "");
        if (response.equals("")) return false;
        System.out.println(response);

        JsonNode confResponse = null;
        try {
            confResponse = objectMapper.readTree(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return confResponse.get("success").asBoolean();
    }

    public class WGTokenInvalidException extends Throwable {
    }

    @Override
    public void run() {
        Map<Confirmation, Boolean> main_confirmations = new HashMap<Confirmation, Boolean>();

        while (!isInterrupted()) {
            while(autoAcceptConfirmations) {
                try {
                    List<Confirmation> tradeOffers = getIncomingTradeOffers_API();
//                    List<Confirmation> tradeOffers = getTradeOffers();

                    for (Confirmation tradeOffer : tradeOffers) {
//                        if (!confirmations.containsKey((Confirmation)tradeOffer)) {
                            if (tradeOffer.Type.equals("trade_offers_sent")) {
                                if (tradeOffer.ConfState == 9) {
                                    main_confirmations.put(tradeOffer, false);
                                }
                            } else if (tradeOffer.Type.equals("trade_offers_received")) {
                                if (tradeOffer.ConfState == 2) {
                                    System.out.println(getSession().SteamID);
                                    String answer = AcceptTradeOffer(tradeOffer.ID, tradeOffer.partnerId);
                                    if (!answer.equals(""))
                                        System.out.println(answer);
                                    main_confirmations.put(tradeOffer, true);
                                } else if (tradeOffer.ConfState == 9) {
                                    main_confirmations.put(tradeOffer, false);
                                }
                            }
//                        }
                    }

                    Thread.sleep(15000);

                    if (main_confirmations.size() > 0) {
                        acceptConfirmations();
                        main_confirmations.clear();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    boolean acceptConfirmations() {
        try {
            List<Confirmation> confirmations = FetchConfirmations();
            if (confirmations.size() > 0) {
                return AcceptMultipleConfirmations(confirmations);
            }
        } catch (WGTokenInvalidException e) {
            e.printStackTrace();
            menuRefreshSession_Click(this);
        }
        return false;
    }
}
