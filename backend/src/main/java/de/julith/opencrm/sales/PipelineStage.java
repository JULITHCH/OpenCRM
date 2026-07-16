package de.julith.opencrm.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pipeline_stages")
public class PipelineStage {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Prozentwert 0.00-100.00 (E-18). */
    @Column(nullable = false)
    private BigDecimal probability;

    @Column(name = "is_won", nullable = false)
    private boolean isWon;

    @Column(name = "is_lost", nullable = false)
    private boolean isLost;

    protected PipelineStage() {
    }

    public PipelineStage(UUID pipelineId, String name, int sortOrder, BigDecimal probability,
                         boolean isWon, boolean isLost) {
        this.pipelineId = pipelineId;
        this.name = name;
        this.sortOrder = sortOrder;
        this.probability = probability;
        this.isWon = isWon;
        this.isLost = isLost;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPipelineId() {
        return pipelineId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public BigDecimal getProbability() {
        return probability;
    }

    public boolean isWon() {
        return isWon;
    }

    public boolean isLost() {
        return isLost;
    }
}
