package it.unibz.inf.ontop.materialization;

import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.answering.resultset.MaterializedGraphResultSet;
import it.unibz.inf.ontop.exception.OBDASpecificationException;
import it.unibz.inf.ontop.injection.OntopSystemConfiguration;
import it.unibz.inf.ontop.materialization.impl.DefaultOntopRDFMaterializer;
import org.apache.commons.rdf.api.IRI;

import javax.annotation.Nonnull;

public interface OntopRDFMaterializer {

    /**
     * Materializes the saturated RDF graph
     */
    MaterializedGraphResultSet materialize(@Nonnull OntopSystemConfiguration configuration,
                                           @Nonnull MaterializationParams params)
            throws OBDASpecificationException;

    /**
     * Materializes a sub-set of the saturated RDF graph corresponding the selected vocabulary
     */
    MaterializedGraphResultSet materialize(@Nonnull OntopSystemConfiguration configuration,
                                           @Nonnull ImmutableSet<IRI> selectedVocabulary,
                                           @Nonnull MaterializationParams params)
            throws OBDASpecificationException;

    /**
     * Default implementation
     */
    static OntopRDFMaterializer defaultMaterializer() {
        return new DefaultOntopRDFMaterializer();
    }

}
