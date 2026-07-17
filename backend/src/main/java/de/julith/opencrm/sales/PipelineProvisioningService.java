package de.julith.opencrm.sales;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt die Default-Pipeline eines frischen Mandanten an (docs/07, Abschnitt Pipelines).
 * Wird von der Tenant-Provisionierung unter dem Kontext des neuen Tenants aufgerufen.
 */
@Service
public class PipelineProvisioningService {

    private final PipelineRepository pipelineRepository;
    private final PipelineStageRepository pipelineStageRepository;

    public PipelineProvisioningService(PipelineRepository pipelineRepository,
                                       PipelineStageRepository pipelineStageRepository) {
        this.pipelineRepository = pipelineRepository;
        this.pipelineStageRepository = pipelineStageRepository;
    }

    @Transactional
    public Pipeline seedDefaultPipeline(UUID tenantId) {
        Pipeline pipeline = pipelineRepository.save(new Pipeline(tenantId, "Standard-Vertrieb", true));
        record Stage(String name, int order, String probability, boolean won, boolean lost) {
        }
        var stages = java.util.List.of(
                new Stage("Qualifizierung", 10, "10.00", false, false),
                new Stage("Bedarfsanalyse", 20, "25.00", false, false),
                new Stage("Angebot", 30, "50.00", false, false),
                new Stage("Verhandlung", 40, "75.00", false, false),
                new Stage("Gewonnen", 50, "100.00", true, false),
                new Stage("Verloren", 60, "0.00", false, true));
        for (Stage stage : stages) {
            pipelineStageRepository.save(new PipelineStage(pipeline.getId(), stage.name(), stage.order(),
                    new BigDecimal(stage.probability()), stage.won(), stage.lost()));
        }
        return pipeline;
    }
}
