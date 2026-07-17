package de.julith.opencrm.lead;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRuleRepository extends JpaRepository<AssignmentRule, UUID> {

    List<AssignmentRule> findByActiveTrueOrderByPriority();

    List<AssignmentRule> findAllByOrderByPriority();
}
