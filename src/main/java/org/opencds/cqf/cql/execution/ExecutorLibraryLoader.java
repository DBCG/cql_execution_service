package org.opencds.cqf.cql.execution;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
//        if (library == null) {
//            org.hl7.elm.r1.VersionedIdentifier elmIdentifier =
//                    new org.hl7.elm.r1.VersionedIdentifier()
//                            .withId(versionedIdentifier.getId())
//                            .withVersion(versionedIdentifier.getVersion());
//
//            List<CqlTranslatorException> errors = new ArrayList<>();
//            org.cqframework.cql.cql2elm.model.TranslatedLibrary librarySource = libraryManager.resolveLibrary(elmIdentifier, errors);
//
//            if (errors.size() > 0) {
//                throw new CqlTranslatorIncludeException(String.format("Errors occurred translating library %s, version %s.",
//                        versionedIdentifier.getId(), versionedIdentifier.getVersion()), versionedIdentifier.getId(), versionedIdentifier.getVersion());
//            }
//
//            try {
//                InputStream xmlStream = new ByteArrayInputStream(CqlTranslator.fromStream(is == null ? new ByteArrayInputStream(new byte[]{}) : is, modelManager, libraryManager).convertToXml(librarySource).getBytes(StandardCharsets.UTF_8)));
//                library = CqlLibraryReader.read(xmlStream);
//            }
//            catch (IOException | JAXBException e) {
//                throw new CqlTranslatorIncludeException(String.format("Errors occurred translating library %s, version %s.",
//                        versionedIdentifier.getId(), versionedIdentifier.getVersion()), versionedIdentifier.getId(), versionedIdentifier.getVersion(), e);
//            }
//
//            libraries.put(versionedIdentifier.getId(), library);
//        }

        return library;
    }
}
