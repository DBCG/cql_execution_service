package org.opencds.cqf.cql.util.service;

import java.util.ArrayList;
import java.util.List;

import org.cqframework.cql.elm.execution.Code;
import org.cqframework.cql.elm.execution.CodeSystemDef;
import org.cqframework.cql.elm.execution.CodeSystemRef;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ConceptMap;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.Parameters.ParametersParameterComponent;
import org.opencds.cqf.cql.util.LibraryUtil;
import org.hl7.fhir.dstu3.model.StringType;

import ca.uhn.fhir.context.FhirContext;

public class FhirCodeMapperServiceStu3 extends BaseCodeMapperService {

	public FhirCodeMapperServiceStu3() {
		this.fhirContext = FhirContext.forDstu3();
        fhirContext.getRestfulClientFactory().setSocketTimeout(1200 * 10000);
        
	}
	
	@Override
	public List<Code> translateCode(Code code,String sourceSystem, String targetSystem,Library library) throws CodeMapperIncorrectEquivalenceException, CodeMapperNotFoundException {
		List<Code> returnList = new ArrayList<Code>();
		Parameters inParams = new Parameters();
		inParams.addParameter().setName("system").setValue(new StringType(sourceSystem));
		inParams.addParameter().setName("code").setValue(new StringType(code.getCode()));
		inParams.addParameter().setName("targetsystem").setValue(new StringType(targetSystem));
		Parameters outParams = fhirClient.operation()
				.onType(ConceptMap.class)
				.named("$translate")
				.withParameters(inParams)
				.useHttpGet()
				.execute();
		if(!outParams.isEmpty()) {
			for(ParametersParameterComponent outParam:outParams.getParameter()) {
				if(outParam.hasName() && outParam.getName().equals("match")) {
					for(ParametersParameterComponent matchpart:outParam.getPart()){
						if(matchpart.hasName() && matchpart.getName().equals("equivalence")) {
							String equivalenceValue = ((CodeType)matchpart.getValue()).primitiveValue();
							if(!equivalenceValue.equals("equivalent")) {
								throw new CodeMapperIncorrectEquivalenceException("Translated code expected an equivalent match but found a match of equivalence " + equivalenceValue + "instead");
							}
						}
						if(matchpart.hasName() && matchpart.getName().equals("concept")) {
							Coding coding = (Coding)matchpart.getValue();
							Code translatedCode = new Code().withCode(coding.getCode());
							if(coding.getSystem() != null) {
								CodeSystemDef targetSystemDef = LibraryUtil.getCodeSystemDefFromURI(library, coding.getSystem());
								if(targetSystemDef == null) {
									targetSystemDef = LibraryUtil.addCodeSystemToLibrary(library, LibraryUtil.generateReferenceName(), coding.getSystem());
								}
								CodeSystemRef targetSystemRef = new CodeSystemRef().withName(targetSystemDef.getName());
								translatedCode.withSystem(targetSystemRef);
							}
							if(coding.getDisplay() != null) {
								translatedCode.withDisplay(coding.getDisplay());
							}
							returnList.add(translatedCode);
						}
					}
				}
			}
		}
		if(returnList.isEmpty()) {
			throw new CodeMapperNotFoundException("No translation found for code " + code.toString() + " in target codesystem " + targetSystem);
		}
		return returnList;
	}
	
}