package org.opencds.cqf.cql.utils;

import com.google.gson.JsonObject;
import org.cqframework.cql.elm.execution.*;
import org.opencds.cqf.cql.elm.execution.CodeEvaluator;
import org.opencds.cqf.cql.elm.execution.CodeSystemRefEvaluator;
import org.opencds.cqf.cql.elm.execution.ConceptEvaluator;
import org.opencds.cqf.cql.utils.service.BaseCodeMapperService;

import java.util.ArrayList;
import java.util.List;

public class CodeMappingUtils {

    public static void mapConcept(ConceptEvaluator conceptEval, Library library, BaseCodeMapperService codeMapperService, JsonObject codeMapperSystemsMap) {
        for (org.cqframework.cql.elm.execution.Code codeConcept : conceptEval.getCode()) {
            String systemRefName = codeConcept.getSystem().getName();
            String sourceSystemUri = LibraryUtils.getCodeSystemDefFromName(library, systemRefName).getId();
            if (codeMapperSystemsMap.get(sourceSystemUri) != null) {
                String targetSystemUri = codeMapperSystemsMap.get(sourceSystemUri).getAsString();
                try {
                    List<Code> translatedCodes =
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

    public static void resolveConceptExpression(Expression expression, Library library, BaseCodeMapperService codeMapperService, JsonObject codeMapperSystemsMap) {
        // TODO: need to assess other expression types to ensure all instances of ConceptEvaluator are accounted for
        if (expression instanceof BinaryExpression) {
            resolveConceptExpression(((BinaryExpression) expression).getOperand().get(0), library, codeMapperService, codeMapperSystemsMap);
            resolveConceptExpression(((BinaryExpression) expression).getOperand().get(1), library, codeMapperService, codeMapperSystemsMap);
        }
        if (expression instanceof ConceptEvaluator) {
            mapConcept((ConceptEvaluator) expression, library, codeMapperService, codeMapperSystemsMap);
        }
    }

    public static void resolveCodeMapping(Library library, BaseCodeMapperService codeMapperService, JsonObject codeMapperSystemsMap) {
        for (ExpressionDef def : library.getStatements().getDef()) {
            resolveConceptExpression(def.getExpression(), library, codeMapperService, codeMapperSystemsMap);
        }
    }
    
}
