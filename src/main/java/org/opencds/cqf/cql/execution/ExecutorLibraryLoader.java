package org.opencds.cqf.cql.execution;

import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.ObjectFactory;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.cql_annotations.r1.Annotation;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Christopher on 1/13/2017.
 */
public class ExecutorLibraryLoader implements LibraryLoader {

    private Map<String, Library> libraries = new HashMap<>();
    private LibraryManager libraryManager;
    private ModelManager modelManager;

    public ExecutorLibraryLoader(LibraryManager libraryManager, ModelManager modelManager) {
        this.libraryManager = libraryManager;
        this.modelManager = modelManager;
    }

    @Override
    public Library load(VersionedIdentifier versionedIdentifier) {
		Library library = libraries.get(versionedIdentifier.getId());

		// TODO - Figure this out
		if (library == null) {
			org.hl7.elm.r1.VersionedIdentifier elmIdentifier = new org.hl7.elm.r1.VersionedIdentifier()
					.withId(versionedIdentifier.getId()).withVersion(versionedIdentifier.getVersion());

			List<CqlTranslatorException> errors = new ArrayList<>();
			org.cqframework.cql.cql2elm.model.TranslatedLibrary librarySource = libraryManager
					.resolveLibrary(elmIdentifier, errors);
			if (errors.size() > 0) {
				throw new CqlTranslatorIncludeException(
						String.format("Errors occurred translating library %s, version %s.",
								versionedIdentifier.getId(), versionedIdentifier.getVersion()),
						versionedIdentifier.getId(), versionedIdentifier.getVersion());
			}
			String elmXml = toXML(librarySource.getLibrary());
			try {
				library = CqlLibraryReader.read(new ByteArrayInputStream(elmXml.getBytes(StandardCharsets.UTF_8)));
			} catch (IOException | JAXBException e) {
				throw new CqlTranslatorIncludeException(
						String.format("Errors occurred translating library %s, version %s.",
								versionedIdentifier.getId(), versionedIdentifier.getVersion()),
						versionedIdentifier.getId(), versionedIdentifier.getVersion(), e);
			}
		}

        return library;
    }

	private String toXML(org.hl7.elm.r1.Library library) {
		String elmXml = "";
		try {
			StringWriter writer = new StringWriter();
			JAXBContext jaxbContext = JAXBContext.newInstance(org.hl7.elm.r1.Library.class, Annotation.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.marshal(new org.hl7.elm.r1.ObjectFactory().createLibrary(library), writer);
			elmXml =  writer.getBuffer().toString();
		} 
		catch (JAXBException e) {
			throw new CqlTranslatorIncludeException(String.format("Errors occurred marshalling library %s, version %s to ELM xml.",
                    library.getIdentifier().getId(), library.getIdentifier().getVersion()), library.getIdentifier().getId(), library.getIdentifier().getVersion(), e);
		}
		return elmXml;
	}
}
