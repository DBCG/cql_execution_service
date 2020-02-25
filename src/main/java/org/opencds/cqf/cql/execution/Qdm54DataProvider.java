package org.opencds.cqf.cql.execution;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.runtime.*;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.cql.terminology.ValueSetInfo;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import org.opencds.cqf.cql.utils.HttpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Qdm54DataProvider implements DataProvider
{
    private TerminologyProvider terminologyProvider;
    public TerminologyProvider getTerminologyProvider() {
        return terminologyProvider;
    }
    private String baseEndpoint;

    public Qdm54DataProvider(String qdmEndpoint, String terminologyEndpoint) {
        this.terminologyProvider = new FhirTerminologyProvider().setEndpoint(terminologyEndpoint, false);
        this.baseEndpoint = qdmEndpoint;
        this.setPackageName("com.google.gson");
    }

    @Override
    public Iterable<Object> retrieve(String context, Object contextValue, String dataType, String templateId,
                                     String codePath, Iterable<Code> codes, String valueSet, String datePath,
                                     String dateLowPath, String dateHighPath, Interval dateRange)
    {
        List<Object> retVal = new ArrayList<>();
        List<JsonObject> candidates = new ArrayList<>();
        StringBuilder requestUrl = new StringBuilder().append(baseEndpoint);
        boolean includeCandidate = true;

        if (valueSet != null && valueSet.startsWith("urn:oid:"))
        {
            valueSet = valueSet.replace("urn:oid:", "");
        }

        if (codePath == null && (codes != null || valueSet != null))
        {
            throw new IllegalArgumentException("A code path must be provided when filtering on codes or a valueset.");
        }

        if (dataType == null)
        {
            throw new IllegalArgumentException("A data type (i.e. Procedure, Valueset, etc...) must be specified for clinical data retrieval");
        }

        if (context != null && context.equals("Patient") && contextValue != null && !contextValue.equals("null"))
        {
            if (dataType.equals("Patient"))
            {
                requestUrl.append("/Patient/").append(contextValue.toString().replace("Patient/", ""));
                candidates.add(HttpUtils.performGetRequestWithSingleResult(requestUrl.toString()));
            }
            else
            {
                requestUrl.append("/").append(dataType);
                for (JsonElement element : HttpUtils.performGetRequest(requestUrl.toString())) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("patientId")) {
                        if (obj.get("patientId").getAsJsonObject().has("value")
                                && obj.getAsJsonObject("patientId").get("value").getAsJsonPrimitive().getAsString().equals(contextValue.toString().replace("Patient/", "")))
                        {
                            candidates.add(obj);
                        }
                    }
//                    candidates.add(element.getAsJsonObject());
                }
            }
        }
        else {
            if (dataType.equals("Patient")) {
                requestUrl.append("/Patient");
                for (JsonElement element : HttpUtils.performGetRequest(requestUrl.toString())) {
                    candidates.add(element.getAsJsonObject());
                }
            }
            else
            {
                requestUrl.append("/").append(dataType);
                for (JsonElement element : HttpUtils.performGetRequest(requestUrl.toString())) {
                    candidates.add(element.getAsJsonObject());
                }
            }
        }

        if (codePath != null && !codePath.equals(""))
        {
            if (valueSet != null && !valueSet.equals(""))
            {
                if (terminologyProvider != null)
                {
                    ValueSetInfo valueSetInfo = new ValueSetInfo().withId(valueSet);
                    codes = terminologyProvider.expand(valueSetInfo);
                }
            }

            if (codes != null)
            {
                for (Code code : codes)
                {
                    for (JsonObject candidate : candidates)
                    {
                        if (candidate.has("code")
                                && candidate.get("code").getAsJsonObject().has("code")
                                && candidate.get("code").getAsJsonObject().get("code").getAsJsonPrimitive().getAsString().equals(code.getCode())
                                && candidate.get("code").getAsJsonObject().has("system")
                                && candidate.get("code").getAsJsonObject().get("system").getAsJsonPrimitive().getAsString().equals(code.getSystem()))
                        {
                            retVal.add(candidate);
                        }
                    }
                }
            }
        }

        if (dateRange != null)
        {
            // TODO
        }

        if (retVal.isEmpty() && !candidates.isEmpty())
        {
            retVal.addAll(candidates);
        }

        return ensureIterable(retVal);
    }

    private String packageName = "org.opencds.cqf.qdm.fivepoint4.model";
    @Override
    public String getPackageName()
    {
        return packageName;
    }

    @Override
    public void setPackageName(String packageName)
    {
        this.packageName = packageName;
    }

    @Override
    public Object resolvePath(Object target, String path)
    {
        if (target instanceof JsonObject) {
            JsonObject qdmResource = (JsonObject) target;

            return mapQdmToCqlType(qdmResource, path);
        }

        return null;
    }

    @Override
    public Class resolveType(String s)
    {
        return null;
    }

    @Override
    public Class resolveType(Object o)
    {
        return null;
    }

    @Override
    public Object createInstance(String s)
    {
        return null;
    }

    @Override
    public void setValue(Object o, String s, Object o1)
    {

    }

    @Override
    public Boolean objectEqual(Object o, Object o1) {
        return o.equals(o1);
    }

    @Override
    public Boolean objectEquivalent(Object o, Object o1) {
        return o.equals(o1);
    }

    private Iterable<Object> ensureIterable(Object candidate)
    {
        if (candidate instanceof Iterable)
        {
            return (Iterable<Object>) candidate;
        }

        return Collections.singletonList(candidate);
    }

    private Object mapQdmToCqlType(JsonObject qdmResource, String path) {
        if (qdmResource.has(path)) {
            if (path.toLowerCase().endsWith("datetime")) {
                return new DateTime(qdmResource.get(path).getAsJsonPrimitive().getAsString(), TemporalHelper.getDefaultZoneOffset());
            }
            else if (qdmResource.get(path).isJsonObject()) {
                JsonObject structure = qdmResource.getAsJsonObject(path);
                if (structure.has("code")) {
                    return new Code()
                            .withCode(structure.getAsJsonPrimitive("code").getAsString())
                            .withSystem(structure.has("system") ? structure.getAsJsonPrimitive("system").getAsString() : null)
                            .withDisplay(structure.has("display") ? structure.getAsJsonPrimitive("display").getAsString() : null)
                            .withVersion(structure.has("version") ? structure.getAsJsonPrimitive("version").getAsString() : null);
                }
                else if (structure.has("value")) {
                    if (path.equals("id")) {
                        return structure.getAsJsonPrimitive("value").getAsString();
                    }
                    return new Quantity()
                            .withValue(structure.get("value").getAsBigDecimal())
                            .withUnit(structure.has("unit") ? structure.getAsJsonPrimitive("unit").getAsString() : null);
                }
                else if (structure.has("start") || structure.has("end")) {
                    Object start = structure.get("start");
                    Object end = structure.get("end");
                    if (start instanceof JsonPrimitive && end instanceof JsonPrimitive) {
                        return new Interval(
                                new DateTime(((JsonPrimitive) start).getAsJsonPrimitive().getAsString(), TemporalHelper.getDefaultZoneOffset()), true,
                                new DateTime(((JsonPrimitive) end).getAsJsonPrimitive().getAsString(), TemporalHelper.getDefaultZoneOffset()), true
                        );
                    }
                    else {
                        return new Interval(start, true, end, true);
                    }
                }
                else {
                    return qdmResource.getAsJsonPrimitive(path).getAsString();
                }
            }
        }
        else if (path.startsWith("result")) {
            return mapQdmToCqlType(qdmResource, "resultQuantity");
        }

        return null;
    }
}
