package de.julith.opencrm.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assignment_rules")
public class AssignmentRule {

    public enum TargetType { USER, TEAM }

    public enum Strategy { DIRECT, ROUND_ROBIN }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active = true;

    /** Kriterien (docs/06 Abschnitt 3.3): sources, productInterests, regions — AND zwischen Schluesseln, OR in Listen. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> criteria = new java.util.HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Strategy strategy = Strategy.DIRECT;

    protected AssignmentRule() {
    }

    public AssignmentRule(UUID tenantId, String name, int priority, Map<String, Object> criteria,
                          TargetType targetType, UUID targetId, Strategy strategy) {
        this.tenantId = tenantId;
        this.name = name;
        this.priority = priority;
        this.criteria = criteria != null ? criteria : new java.util.HashMap<>();
        this.targetType = targetType;
        this.targetId = targetId;
        this.strategy = strategy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public Map<String, Object> getCriteria() {
        return criteria;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * First-Match-Prüfung: jedes gesetzte Kriterium muss passen (AND), Listenwerte sind Alternativen (OR).
     */
    @SuppressWarnings("unchecked")
    public boolean matches(Lead lead) {
        return matchesList((java.util.List<Object>) criteria.get("sources"), lead.getSource().name())
                && matchesList((java.util.List<Object>) criteria.get("productInterests"),
                        stringCustom(lead, "product_interest"))
                && matchesList((java.util.List<Object>) criteria.get("regions"), stringCustom(lead, "region"));
    }

    private static boolean matchesList(java.util.List<Object> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        return actual != null && expected.stream().map(Object::toString).anyMatch(actual::equalsIgnoreCase);
    }

    private static String stringCustom(Lead lead, String key) {
        Object value = lead.getCustom().get(key);
        return value == null ? null : value.toString();
    }
}
