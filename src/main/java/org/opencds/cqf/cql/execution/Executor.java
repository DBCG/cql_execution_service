package org.opencds.cqf.cql.execution;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.google.gson.*;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.elm.execution.*;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.json.simple.parser.ParseException;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirBundleCursorStu3;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.elm.execution.CodeEvaluator;
import org.opencds.cqf.cql.elm.execution.CodeSystemRefEvaluator;
import org.opencds.cqf.cql.elm.execution.ConceptEvaluator;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.Concept;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.runtime.Quantity;
import org.opencds.cqf.cql.runtime.Time;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;
import org.opencds.cqf.cql.util.LibraryUtil;
import org.opencds.cqf.cql.util.service.BaseCodeMapperService;
import org.opencds.cqf.cql.util.service.FhirCodeMapperServiceStu3;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

@Path("evaluate")
public class Executor {

    private Map<String, List<Integer>> locations = new HashMap<>();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // for future use
    private ModelManager modelManager;
    private BaseCodeMapperService codeMapperService;
    private ModelManager getModelManager() {
        if (modelManager == null) {
            modelManager = new ModelManager();
        }
        return modelManager;
    }

    private LibraryManager libraryManager;
    private LibraryManager getLibraryManager() {
        if (libraryManager == null) {
            libraryManager = new LibraryManager(getModelManager());
            libraryManager.getLibrarySourceLoader().clearProviders();
            libraryManager.getLibrarySourceLoader().registerProvider(getLibrarySourceProvider());
        }
        return libraryManager;
    }

    private ExecutorLibrarySourceProvider librarySourceProvider;
    private ExecutorLibrarySourceProvider getLibrarySourceProvider() {
        if (librarySourceProvider == null) {
            librarySourceProvider = new ExecutorLibrarySourceProvider();
        }
        return librarySourceProvider;
    }

    private LibraryLoader libraryLoader;
    private LibraryLoader getLibraryLoader() {
        if (libraryLoader == null) {
            libraryLoader = new ExecutorLibraryLoader(getLibraryManager(), getModelManager());
        }
        return libraryLoader;
    }

    private void registerProviders(Context context, String termSvcUrl, String termUser,
                                   String termPass, String dataPvdrURL, String dataUser,
                                   String dataPass, String codeMapServiceUri)
    {
        // TODO: plugin authorization for data provider when available

        String defaultEndpoint = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3";

        BaseFhirDataProvider provider = new FhirDataProviderStu3()
                .setEndpoint(dataPvdrURL == null ? defaultEndpoint : dataPvdrURL);

//        if(dataUser != null && !dataUser.isEmpty() && dataPass != null && !dataPass.isEmpty()) {
//        	provider = provider.withBasicAuth(dataUser,dataPass);
//        }

        FhirContext fhirContext = provider.getFhirContext();
        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        provider.setFhirContext(fhirContext);
        provider.getFhirClient().setEncoding(EncodingEnum.JSON);

        FhirTerminologyProvider terminologyProvider = new FhirTerminologyProvider()
                .withBasicAuth(termUser, termPass)
                .setEndpoint(termSvcUrl == null ? defaultEndpoint : termSvcUrl, false);
        
        codeMapperService = codeMapServiceUri == null ? null : new FhirCodeMapperServiceStu3().setEndpoint(codeMapServiceUri);

        provider.setTerminologyProvider(terminologyProvider);
//        provider.setSearchUsingPOST(true);
//        provider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", provider);
        context.registerTerminologyProvider(terminologyProvider);
        context.registerLibraryLoader(getLibraryLoader());
    }

    private void performRetrieve(Iterable result, JsonObject results) {
        FhirContext fhirContext = FhirContext.forDstu3(); // for JSON parsing
        Iterator it = result.iterator();
        List<Object> findings = new ArrayList<>();

        while (it.hasNext()) {
            // returning full JSON retrieve response
            findings.add(fhirContext
                    .newJsonParser()
                    .setPrettyPrint(true)
                    .encodeResourceToString((org.hl7.fhir.instance.model.api.IBaseResource)it.next()));
        }

        results.add("result", new JsonPrimitive(findings.toString()));
    }

    private String resolveType(Object result) {
        String type = result == null ? "Null" : result.getClass().getSimpleName();
        switch (type) {
            case "BigDecimal": return "Decimal";
            case "ArrayList": return "List";
            case "FhirBundleCursor": return "Retrieve";
        }
        return type;
    }

    private CqlTranslator getTranslator(String cql, LibraryManager libraryManager, ModelManager modelManager) {
        return getTranslator(new ByteArrayInputStream(cql.getBytes(StandardCharsets.UTF_8)), libraryManager, modelManager);
    }

    private CqlTranslator getTranslator(InputStream cqlStream, LibraryManager libraryManager, ModelManager modelManager) {
        ArrayList<CqlTranslator.Options> options = new ArrayList<>();
        options.add(CqlTranslator.Options.EnableDateRangeOptimization);
//        options.add(CqlTranslator.Options.EnableAnnotations);
//        options.add(CqlTranslator.Options.EnableDetailedErrors);
        CqlTranslator translator;
        try {
            translator = CqlTranslator.fromStream(cqlStream, modelManager, libraryManager,
                    options.toArray(new CqlTranslator.Options[options.size()]));
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format("Errors occurred translating library: %s", e.getMessage()));
        }

//        String xml = translator.toXml();

        if (translator.getErrors().size() > 0) {
            throw new IllegalArgumentException(errorsToString(translator.getErrors()));
        }

        return translator;
    }

    private String errorsToString(Iterable<CqlTranslatorException> exceptions) {
        ArrayList<String> errors = new ArrayList<>();
        for (CqlTranslatorException error : exceptions) {
            TrackBack tb = error.getLocator();
            String lines = tb == null ? "[n/a]" : String.format("%s[%d:%d, %d:%d]",
                    (tb.getLibrary() != null ? tb.getLibrary().getId() + (tb.getLibrary().getVersion() != null
                            ? ("-" + tb.getLibrary().getVersion()) : "") : ""),
                    tb.getStartLine(), tb.getStartChar(), tb.getEndLine(), tb.getEndChar());
            errors.add(lines + error.getMessage() + "\n");
        }

        return errors.toString();
    }

    private void setExpressionLocations(org.hl7.elm.r1.Library library) {
        if (library.getStatements() == null) return;
        for (org.hl7.elm.r1.ExpressionDef def : library.getStatements().getDef()) {
            int startLine = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartLine();
            int startChar = def.getTrackbacks().isEmpty() ? 0 : def.getTrackbacks().get(0).getStartChar();
            List<Integer> loc = Arrays.asList(startLine, startChar);
            locations.put(def.getName(), loc);
        }
    }

    private Library readLibrary(InputStream xmlStream) {
        try {
            return CqlLibraryReader.read(xmlStream);
        } catch (IOException | JAXBException e) {
            throw new IllegalArgumentException("Error encountered while reading ELM xml: " + e.getMessage());
        }
    }

    private Library translateLibrary(CqlTranslator translator) {
        return readLibrary(new ByteArrayInputStream(translator.toXml().getBytes(StandardCharsets.UTF_8)));
    }

    private JsonObject getErrorResponse(String message)
    {
        JsonObject errorResponse = new JsonObject();
        errorResponse.add("error", new JsonPrimitive(message));
        return errorResponse;
    }

    private boolean validateStringProperty(JsonObject objToValidate, String property)
    {
        return objToValidate.has(property) && objToValidate.get(property).isJsonPrimitive()
                && objToValidate.get(property).getAsJsonPrimitive().isString();
    }

    private Quantity resolveQuantityParameter(JsonObject quantityJson)
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

    private Code resolveCodeParameter(JsonObject codeJson)
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

    private Concept resolveConceptParameter(JsonObject conceptJson)
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

    private Interval resolveIntervalParameter(JsonObject intervalJson, String subType)
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

    private Object resolveParameterType(String type, JsonElement value)
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

    private void resolveParameters(JsonArray parameters, Context context)
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

    private void mapConcept(ConceptEvaluator conceptEval, Library library, JsonObject codeMapperSystemsMap) {
        for (org.cqframework.cql.elm.execution.Code codeConcept : conceptEval.getCode()) {
            String systemRefName = codeConcept.getSystem().getName();
            String sourceSystemUri = LibraryUtil.getCodeSystemDefFromName(library, systemRefName).getId();
            if (codeMapperSystemsMap.get(sourceSystemUri) != null) {
                String targetSystemUri = codeMapperSystemsMap.get(sourceSystemUri).getAsString();
                try {
                    List<org.cqframework.cql.elm.execution.Code> translatedCodes =
                            codeMapperService.translateCode(codeConcept, sourceSystemUri, targetSystemUri, library);
                    List<CodeEvaluator> translatedCodeEvaluators = new ArrayList<>();
                    for (org.cqframework.cql.elm.execution.Code translatedCode : translatedCodes) {
                        CodeEvaluator translatedCodeEvaluator = new CodeEvaluator();
                        if (translatedCode.getCode() != null) {
                            translatedCodeEvaluator.withCode(translatedCode.getCode());
                        }
                        if (translatedCode.getDisplay() != null) {
                            translatedCodeEvaluator.withDisplay(translatedCode.getDisplay());
                        }
                        if (translatedCode.getSystem() != null) {
                            CodeSystemRefEvaluator systemRefEvaluator = new CodeSystemRefEvaluator();
                            systemRefEvaluator.withName(translatedCode.getSystem().getName());
                            translatedCodeEvaluator.withSystem(systemRefEvaluator);
                        }
                        translatedCodeEvaluators.add(translatedCodeEvaluator);
                    }
                    conceptEval.getCode().remove(codeConcept);
                    conceptEval.getCode().addAll(translatedCodeEvaluators);
                }
                catch (BaseCodeMapperService.CodeMapperIncorrectEquivalenceException
                        | BaseCodeMapperService.CodeMapperNotFoundException e)
                {
                    // ignore
                }
            }
        } // end for each code in concept
    }

    private void resolveConceptExpression(Expression expression, Library library, JsonObject codeMapperSystemsMap) {
        // TODO: need to assess other expression types to ensure all instances of ConceptEvaluator are accounted for
        if (expression instanceof BinaryExpression) {
            resolveConceptExpression(((BinaryExpression) expression).getOperand().get(0), library, codeMapperSystemsMap);
            resolveConceptExpression(((BinaryExpression) expression).getOperand().get(1), library, codeMapperSystemsMap);
        }
        if (expression instanceof ConceptEvaluator) {
            mapConcept((ConceptEvaluator) expression, library, codeMapperSystemsMap);
        }
    }

    private void resolveCodeMapping(Library library, JsonObject codeMapperSystemsMap) {
        for (ExpressionDef def : library.getStatements().getDef()) {
            resolveConceptExpression(def.getExpression(), library, codeMapperSystemsMap);
        }
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public String evaluateCql(String requestData) throws JAXBException, IOException, ParseException {

        JsonArray results = new JsonArray();
        JsonObject json;
        try {
            json = gson.fromJson(requestData, JsonObject.class);
        } catch (Exception e) {
            results.add(getErrorResponse(e.getMessage()));
            return results;
        }

        String code = json.has("code") ? json.get("code").getAsString() : null;
        String terminologyServiceUri = json.has("terminologyServiceUri") ? json.get("terminologyServiceUri").getAsString() : null;
        String terminologyUser = json.has("terminologyUser") ? json.get("terminologyUser").getAsString() : null;
        String terminologyPass = json.has("terminologyPass") ? json.get("terminologyPass").getAsString() : null;
        String dataServiceUri = json.has("dataServiceUri") ? json.get("dataServiceUri").getAsString() : null;
        String dataUser = json.has("dataUser") ? json.get("dataUser").getAsString() : null;
        String dataPass = json.has("dataPass") ? json.get("dataPass").getAsString() : null;
        String patientId = json.has("patientId") ? json.get("patientId").getAsString() : null;
        String codeMapperServiceUri = json.has("codeMapperServiceUri") ? json.get("codeMapperServiceUri").getAsString() : null;
        JsonObject codeMapperSystemsMap =  json.has("codeMapperSystemsMap") ? json.get("codeMapperSystemsMap").getAsJsonObject() : null;
        JsonArray parameters =
                json.get("parameters") == null
                        ? null
                        : json.get("parameters").getAsJsonArray();
        
        CqlTranslator translator;
        try {
            translator = getTranslator(code, getLibraryManager(), getModelManager());
        }
        catch (Exception e)
        {
            results.add(getErrorResponse(e.getMessage()));
            return results;
        }

        setExpressionLocations(translator.getTranslatedLibrary().getLibrary());

        if (locations.isEmpty())
        {
            results.add(getErrorResponse("No expressions found"));
            return results;
        }

        Library library = translateLibrary(translator);

        Context context = new Context(library);
        registerProviders(context, terminologyServiceUri, terminologyUser, terminologyPass, dataServiceUri, dataUser, dataPass, codeMapperServiceUri);
        resolveParameters(parameters, context);

        if (codeMapperService != null && codeMapperSystemsMap != null) {
            resolveCodeMapping(library, codeMapperSystemsMap);
        }

        for (ExpressionDef def : library.getStatements().getDef())
        {
            context.enterContext(def.getContext());

            if (patientId != null && !patientId.isEmpty())
            {
                context.setContextValue(context.getCurrentContext(), patientId);
            }
            else
            {
                context.setContextValue(context.getCurrentContext(), "null");
            }

            JsonObject result = new JsonObject();

            try {
                result.add("name", new JsonPrimitive(def.getName()));

                String location = String.format("[%d:%d]", locations.get(def.getName()).get(0), locations.get(def.getName()).get(1));
                result.add("location", new JsonPrimitive(location));

                Object expressionResult = def instanceof FunctionDef ? "Definition successfully validated" : def.getExpression().evaluate(context);

                if (expressionResult == null)
                {
                    result.add("result", new JsonPrimitive("Null"));
                }
                else if (expressionResult instanceof FhirBundleCursorStu3)
                {
                    performRetrieve((Iterable) expressionResult, result);
                }
                else if (expressionResult instanceof List)
                {
                    if (((List) expressionResult).size() > 0 && ((List) expressionResult).get(0) instanceof IBaseResource)
                    {
                        performRetrieve((Iterable) expressionResult, result);
                    }
                    else
                    {
                        result.add("result", new JsonPrimitive(expressionResult.toString()));
                    }
                }
                else if (expressionResult instanceof IBaseResource)
                {
                    result.add("result", new JsonPrimitive(FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) expressionResult)));
                }
                else
                {
                    result.add("result", new JsonPrimitive(expressionResult.toString()));
                }
                result.add("resultType", new JsonPrimitive(resolveType(expressionResult)));
            }
            catch (RuntimeException re)
            {
                result.add("error", new JsonPrimitive(re.getMessage()));
                re.printStackTrace();
            }

            results.add(result);
        }

        return gson.toJson(results);
    }

}
