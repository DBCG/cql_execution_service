package org.opencds.cqf.cql.execution;

import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by Christopher on 2/19/2017.
 */
public class ExecutorLibrarySourceProvider implements LibrarySourceProvider {

    @Override
    public InputStream getLibrarySource(VersionedIdentifier versionedIdentifier) {
        Path path = Paths.get("src/main/resources").toAbsolutePath();
        File source = new File(path.resolve(versionedIdentifier.getId() + "-" + versionedIdentifier.getVersion() + ".cql").toString());

        try {
            return new FileInputStream(source);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Cannot find library source " + versionedIdentifier.getId());
        }
    }
}
