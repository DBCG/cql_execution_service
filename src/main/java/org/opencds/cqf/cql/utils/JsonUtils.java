package org.opencds.cqf.cql.utils;

import com.google.gson.*;

public class JsonUtils {

    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static JsonObject toJsonObject(String jsonString) {
        return gson.fromJson(jsonString, JsonObject.class);
    }

    public static JsonArray toJsonArray(String jsonString) {
        JsonElement element = gson.fromJson(jsonString, JsonElement.class);
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        else {
            JsonArray arr = new JsonArray();
            arr.add(element);
            return arr;
        }
    }
}
