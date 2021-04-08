package steam_auth;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimeAligner {
    private static boolean _aligned = true;
    private static int _timeDifference = 0;
    public static ObjectMapper objectMapper = new ObjectMapper();

    public static long GetSteamTime()
    {
        if (!TimeAligner._aligned)
        {
            TimeAligner.AlignTime();
        }
        Date d = new Date();
        return (d.getTime() / 1000) + _timeDifference;
    }

    public static void AlignTime()
    {
        Date d = new Date();
        long currentTime = d.getTime() / 1000;
        {
            try
            {
                List<NameValuePair> postData = new ArrayList<NameValuePair>();
                postData.add(new BasicNameValuePair("steamid", "0"));
                String response = SteamWeb.MobileLoginRequest(APIEndpoints.TWO_FACTOR_TIME_QUERY,"POST", postData, "");
                JsonNode query = objectMapper.readTree(response);
                TimeAligner._timeDifference = (int)(query.get("response").get("server_time").asInt() - currentTime);
                TimeAligner._aligned = true;
            } catch (JsonProcessingException e) {
                return;
            } catch (IOException e) {
                return;
            }
        }
    }
}
