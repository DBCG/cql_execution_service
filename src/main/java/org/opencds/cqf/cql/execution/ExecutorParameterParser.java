package org.opencds.cqf.cql.execution;

import org.cqframework.cql.elm.execution.ParameterDef;

public class ExecutorParameterParser {
	public Object parseParameter(String parameterText, ParameterDef paramDef) {
		
		switch(paramDef.getParameterType().getLocalPart()) {
		case "Boolean":
		case "Integer":
		case "Decimal":
		case "String":
			break;
		case "DateTime":
			break;
		case "Time":
			break;
		case "Code":
			break;
		case "Concept":
			break;
		case "Quantity":
			break;
		case "ValueSet":
			break;
		case "Ratio":
			break;
		case "Tuple":
			break;
		case "List":
			break;
		case "Interval":
			break;
		}
		return new Object();
	}
}
