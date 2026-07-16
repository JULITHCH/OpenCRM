package de.julith.opencrm.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

    private final PipelineRepository pipelineRepository;
    private final PipelineStageRepository pipelineStageRepository;

    public PipelineController(PipelineRepository pipelineRepository,
                              PipelineStageRepository pipelineStageRepository) {
        this.pipelineRepository = pipelineRepository;
        this.pipelineStageRepository = pipelineStageRepository;
    }

    public record StageResponse(UUID id, String name, int sortOrder, BigDecimal probability,
                                boolean isWon, boolean isLost) {
        static StageResponse from(PipelineStage stage) {
            return new StageResponse(stage.getId(), stage.getName(), stage.getSortOrder(), stage.getProbability(),
                    stage.isWon(), stage.isLost());
        }
    }

    public record PipelineResponse(UUID id, String name, boolean isDefault, List<StageResponse> stages) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PipelineResponse> list() {
        return pipelineRepository.findAll().stream()
                .map(pipeline -> new PipelineResponse(pipeline.getId(), pipeline.getName(), pipeline.isDefault(),
                        pipelineStageRepository.findByPipelineIdOrderBySortOrder(pipeline.getId()).stream()
                                .map(StageResponse::from).toList()))
                .toList();
    }
}
