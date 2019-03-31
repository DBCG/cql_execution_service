package org.opencds.cqf.cql.execution;

import com.google.gson.*;
import org.cqframework.cql.tools.formatter.CqlFormatterVisitor;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Path("format")
public class Formatter {

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String formatCql(String unformattedCql) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json;
        try
        {
            json = gson.fromJson(unformattedCql, JsonObject.class);
        }
        catch (Exception e)
        {
            JsonObject errorResponse = new JsonObject();
            errorResponse.add("error", new JsonPrimitive(e.getMessage()));
            return gson.toJson(errorResponse);
        }

        String code = json.has("code") && json.get("code").isJsonPrimitive() && json.get("code").getAsJsonPrimitive().isString()
                ? json.get("code").getAsString() : null;

        if (code == null) return null;

        CqlFormatterVisitor.FormatResult formatResult = CqlFormatterVisitor.getFormattedOutput(new ByteArrayInputStream(code.getBytes()));
        StringBuilder output = new StringBuilder();
        if (formatResult.getErrors() != null && !formatResult.getErrors().isEmpty())
        {
            for (Exception e : formatResult.getErrors()) {
                output.append(e.getMessage()).append("\n");
            }
        }

        output.append(formatResult.getOutput());

        JsonArray results = new JsonArray();
        JsonObject element = new JsonObject();
        element.add("formatted-cql", new JsonPrimitive(output.toString()));
        results.add(element);

        return gson.toJson(results);
    }
}
