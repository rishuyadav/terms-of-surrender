package dev.termsofsurrender.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.termsofsurrender.AppProperties;
import dev.termsofsurrender.privacy.AiDemoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EngineConfiguration {
    @Bean
    @Primary
    AnalysisEngine analysisEngine(
            AppProperties properties,
            ObjectMapper mapper,
            EvidenceValidator validator,
            AiDemoProperties demo) {
        return properties.model().enabled()
                ? new ModelAnalysisEngine(
                        properties.model(),
                        mapper,
                        validator,
                        demo.enabled() ? demo.maxCompletionTokens() : 8000)
                : new PreviewAnalysisEngine(validator);
    }

    @Bean
    PreviewAnalysisEngine previewAnalysisEngine(EvidenceValidator validator) {
        return new PreviewAnalysisEngine(validator);
    }

    @Bean
    ContractQa contractQa(
            AppProperties properties, ObjectMapper mapper, EvidenceValidator validator) {
        return new ContractQa(properties.model(), mapper, validator);
    }
}
