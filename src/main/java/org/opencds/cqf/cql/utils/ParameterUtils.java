package org.opencds.cqf.cql.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ParameterUtils {

    public static boolean validateStringProperty(JsonObject objToValidate, String property)
    {
        return objToValidate.has(property) && objToValidate.get(property).isJsonPrimitive()
                && objToValidate.get(property).getAsJsonPrimitive().isString();
    }

    public static Quantity resolveQuantityParameter(JsonObject quantityJson)
    {
        BigDecimal value;
        String unit = "1";
        if (quantityJson.has("value") && quantityJson.get("value").isJsonPrimitive()
                && quantityJson.get("value").getAsJsonPrimitive().isNumber())
        {
            value = quantityJson.get("value").getAsJsonPrimitive().getAsBigDecimal();
        }
        else return null;

        if (validateStringProperty(quantityJson, "unit"))
        {
            unit = quantityJson.get("unit").getAsJsonPrimitive().getAsString();
        }

        return new Quantity().withValue(value).withUnit(unit);
    }

    public static Code resolveCodeParameter(JsonObject codeJson)
    {
        String system = null;
        String code;
        String display = null;
        String version = null;

        if (validateStringProperty(codeJson, "code"))
        {
            code = codeJson.get("code").getAsJsonPrimitive().getAsString();
        }
        else return null;

        if (validateStringProperty(codeJson, "system"))
        {
            system = codeJson.get("system").getAsJsonPrimitive().getAsString();
        }

        if (validateStringProperty(codeJson, "display"))
        {
            display = codeJson.get("display").getAsJsonPrimitive().getAsString();
        }

        if (validateStringProperty(codeJson, "version"))
        {
            version = codeJson.get("version").getAsJsonPrimitive().getAsString();
        }

        return new Code().withCode(code).withSystem(system).withDisplay(display).withVersion(version);
    }

    public static Concept resolveConceptParameter(JsonObject conceptJson)
    {
        List<Code> codes = new ArrayList<>();
        String display = null;

        if (conceptJson.has("codes") && conceptJson.get("codes").isJsonArray())
        {
            for (JsonElement code : conceptJson.get("codes").getAsJsonArray())
            {
                if (code.isJsonObject())
                {
                    codes.add(resolveCodeParameter(code.getAsJsonObject()));
                }
            }
        }

        if (validateStringProperty(conceptJson, "display"))
        {
            display = conceptJson.get("display").getAsString();
        }

        return new Concept().withCodes(codes).withDisplay(display);
    }

    public static Interval resolveIntervalParameter(JsonObject intervalJson, String subType)
    {
        JsonElement start;
        JsonElement end;

        if (intervalJson.has("start") && intervalJson.has("end"))
        {
            start = intervalJson.get("start");
            end = intervalJson.get("end");
        }
        else return null;

        switch (subType.toLowerCase())
        {
            case "integer": return start.isJsonPrimitive() && start.getAsJsonPrimitive().isNumber() && end.isJsonPrimitive() && end.getAsJsonPrimitive().isNumber()
                    ? new Interval(start.getAsInt(), true, end.getAsInt(), true) : null;
            case "decimal": return start.isJsonPrimitive() && start.getAsJsonPrimitive().isNumber() && end.isJsonPrimitive() && end.getAsJsonPrimitive().isNumber()
                    ? new Interval(start.getAsBigDecimal(), true, end.getAsBigDecimal(), true) : null;
            case "quantity": return start.isJsonObject() && end.isJsonObject()
                    ? new Interval(resolveQuantityParameter(start.getAsJsonObject()), true, resolveQuantityParameter(end.getAsJsonObject()), true) : null;
            case "datetime": return validateStringProperty(intervalJson, "start") && start.getAsString().startsWith("@") && validateStringProperty(intervalJson, "end") && end.getAsString().startsWith("@")
                    ? new Interval(new DateTime(start.getAsString().replace("@", ""), null), true, new DateTime(end.getAsString().replace("@", ""), null), true) : null;
            case "time": return validateStringProperty(intervalJson, "start") && start.getAsString().startsWith("T") && validateStringProperty(intervalJson, "end") && end.getAsString().startsWith("T")
                    ? new Interval(new Time(start.getAsString(), null), true, new Time(end.getAsString(), null), true) : null;
        }

        return null;
    }

    public static Object resolveParameterType(String type, JsonElement value)
    {
        String subType = type.contains("<") ? type.substring(type.indexOf("<") + 1, type.indexOf(">")) : null;

        switch (type.replaceAll("<.*>", "").toLowerCase())
        {
            case "boolean": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsJsonPrimitive().getAsBoolean() : null;
            case "string": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsJsonPrimitive().getAsString() : null;
            case "integer": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsJsonPrimitive().getAsInt() : null;
            case "decimal": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsJsonPrimitive().getAsBigDecimal() : null;
            case "datetime": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsJsonPrimitive().getAsString().startsWith("@")
                    ? new DateTime(value.getAsJsonPrimitive().getAsString().replace("@", ""), null)
                    : null;
            case "time": return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsJsonPrimitive().getAsString().startsWith("T")
                    ? new Time(value.getAsJsonPrimitive().getAsString(), null)
                    : null;
            case "quantity": return value.isJsonObject() ? resolveQuantityParameter(value.getAsJsonObject()) : null;
            case "code": return value.isJsonObject() ? resolveCodeParameter(value.getAsJsonObject()) : null;
            case "concept": return value.isJsonObject() ? resolveConceptParameter(value.getAsJsonObject()) : null;
            case "interval": return value.isJsonObject() ? resolveIntervalParameter(value.getAsJsonObject(), subType) : null;
        }

        return null;
    }

    public static void resolveParameters(JsonArray parameters, Context context)
    {
        if (parameters != null)
        {
            for (JsonElement paramElem : parameters)
            {
                if (paramElem.isJsonObject())
                {
                    JsonObject paramObj = paramElem.getAsJsonObject();
                    JsonElement name = paramObj.get("name");
                    JsonElement type = paramObj.get("type");
                    JsonElement value = paramObj.get("value");

                    if (name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                            && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString())
                    {
                        context.setParameter(null, name.getAsString(), resolveParameterType(type.getAsString(), value));
                    }
                }
            }
        }
    }
    
}
