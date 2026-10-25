package dev.termsofsurrender.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfiguration {
    @Bean
    AnalysisEngine analysisEngine(
            AppProperties properties, ObjectMapper mapper, EvidenceValidator validator) {
        return properties.model().enabled()
                ? new ModelAnalysisEngine(properties.model(), mapper, validator)
                : new PreviewAnalysisEngine(validator);
    }

    @Bean
    ContractQa contractQa(
            AppProperties properties, ObjectMapper mapper, EvidenceValidator validator) {
        return new ContractQa(properties.model(), mapper, validator);
    }
}
