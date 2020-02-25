package org.opencds.cqf.cql.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class HttpUtils {

    public static JsonObject performGetRequestWithSingleResult(String requestUrl) {
        JsonArray array = performGetRequest(requestUrl);
        if (array.size() > 0) {
            return array.get(0).getAsJsonObject();
        }

        return new JsonObject();
    }

    public static JsonArray performGetRequest(String requestUrl) {
        HttpGet request = new HttpGet(requestUrl);
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(request))
        {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return JsonUtils.toJsonArray(EntityUtils.toString(entity));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error performing GET request: " + requestUrl);
        }

        return new JsonArray();
    }
}
