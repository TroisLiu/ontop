package it.unibz.inf.ontop.injection;


import it.unibz.inf.ontop.injection.impl.OntopMappingConfigurationImpl;
import it.unibz.inf.ontop.model.DBMetadata;
import it.unibz.inf.ontop.owlrefplatform.core.mappingprocessing.TMappingExclusionConfig;

import javax.annotation.Nonnull;
import java.util.Optional;

public interface OntopMappingConfiguration extends OntopOBDASpecificationConfiguration, OntopOptimizationConfiguration {

    Optional<TMappingExclusionConfig> getTmappingExclusions();

    @Override
    OntopMappingSettings getSettings();



    static Builder<? extends Builder> defaultBuilder() {
        return new OntopMappingConfigurationImpl.BuilderImpl<>();
    }


    interface OntopMappingBuilderFragment<B extends Builder<B>> {

        B tMappingExclusionConfig(@Nonnull TMappingExclusionConfig config);

        B enableFullMetadataExtraction(boolean obtainFullMetadata);

        B enableOntologyAnnotationQuerying(boolean queryingAnnotationsInOntology);

        B dbMetadata(@Nonnull DBMetadata dbMetadata);

    }

    interface Builder<B extends Builder<B>> extends OntopMappingBuilderFragment<B>, OntopOBDAConfiguration.Builder<B>,
            OntopOptimizationConfiguration.Builder<B> {

        @Override
        OntopMappingConfiguration build();
    }

}
