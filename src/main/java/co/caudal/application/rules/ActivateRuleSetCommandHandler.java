package co.caudal.application.rules;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.domain.rules.RuleSet;
import co.caudal.domain.rules.RuleSetValidator;
import co.caudal.domain.rules.RuleSetValidator.Gauge;
import co.caudal.domain.rules.RuleSetValidator.Violation;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Activates a draft in one transaction (docs/Reglas-de-negocio.md, section 1.2): it validates the
 * version against the tanks and valves of the aqueduct, marks the active one {@code SUPERSEDED}
 * with {@code valid_to} now, and puts the draft in {@code ACTIVE} from now with who and why.
 *
 * @pattern P13 Command
 */
public class ActivateRuleSetCommandHandler
    implements CommandHandler<ActivateRuleSetCommand, RuleSetDetail> {

  private final RuleSetPort ruleSets;
  private final NetworkPort network;
  private final UnitOfWorkPort unitOfWork;
  private final Clock clock;

  /**
   * Creates the handler.
   *
   * @param ruleSets versions of the rules
   * @param network tanks, sectors and valves
   * @param unitOfWork transaction boundary
   * @param clock source of time
   */
  public ActivateRuleSetCommandHandler(
      RuleSetPort ruleSets, NetworkPort network, UnitOfWorkPort unitOfWork, Clock clock) {
    this.ruleSets = ruleSets;
    this.network = network;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
  }

  @Override
  public RuleSetDetail handle(ActivateRuleSetCommand command) {
    ChangeReason reason = ChangeReason.require(command.changeReason());
    return unitOfWork.executeAs(command.caller().scope(), () -> activate(command, reason));
  }

  private RuleSetDetail activate(ActivateRuleSetCommand command, ChangeReason reason) {
    RuleSetDetail detail =
        ruleSets
            .findDetail(command.ruleSetId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    RuleSet draft = RuleSetConversion.toDomain(detail);
    if (!draft.isDraft()) {
      throw new DomainException(
          ErrorCode.INVALID_STATE_TRANSITION, Map.of("from", draft.status(), "to", "ACTIVE"));
    }
    List<Violation> violations = RuleSetValidator.validate(draft, gauges(), valvesBySector());
    if (!violations.isEmpty()) {
      throw new DomainException(
          ErrorCode.RULE_SET_INVALID, Map.of("violations", describe(violations)));
    }
    Instant now = clock.instant();
    ruleSets.findActive().ifPresent(active -> ruleSets.supersede(active.id(), now));
    ruleSets.activate(command.ruleSetId(), now, command.caller().userId(), reason.value());
    return ruleSets.findDetail(command.ruleSetId()).orElseThrow();
  }

  private List<Gauge> gauges() {
    return network.tanks().stream()
        .filter(TankView::active)
        .map(tank -> new Gauge(tank.id(), tank.gaugeMin(), tank.gaugeMax()))
        .toList();
  }

  private Map<UUID, Set<UUID>> valvesBySector() {
    Map<UUID, Set<UUID>> valves = new LinkedHashMap<>();
    for (SectorView sector : network.sectors()) {
      valves.put(
          sector.id(),
          network.valves(sector.id()).stream()
              .filter(ValveView::active)
              .map(ValveView::id)
              .collect(Collectors.toSet()));
    }
    return valves;
  }

  private static List<Map<String, Object>> describe(List<Violation> violations) {
    return violations.stream()
        .map(
            violation -> {
              Map<String, Object> item = new LinkedHashMap<>();
              item.put("code", violation.code());
              item.putAll(violation.details());
              return item;
            })
        .toList();
  }

  @Override
  public Class<ActivateRuleSetCommand> commandType() {
    return ActivateRuleSetCommand.class;
  }
}
