package it.unibz.inf.ontop.injection.impl;


import it.unibz.inf.ontop.injection.OntopMappingConfiguration;
import it.unibz.inf.ontop.injection.OntopMappingSettings;
import it.unibz.inf.ontop.mapping.extraction.MappingAndDBMetadataExtractor;


public class OntopMappingModule extends OntopAbstractModule {

    private final OntopMappingSettings settings;

    OntopMappingModule(OntopMappingConfiguration configuration) {
        super(configuration.getSettings());
        this.settings = configuration.getSettings();
    }

    @Override
    protected void configure() {
        bind(OntopMappingSettings.class).toInstance(settings);
        bindFromPreferences(MappingAndDBMetadataExtractor.class);
    }
}
