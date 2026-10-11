package co.caudal.application.schedule;

import co.caudal.application.command.CommandHandler;
import co.caudal.application.network.SectorView;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.ProposalPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.RuleSetView;
import co.caudal.domain.rules.ChangeReason;
import co.caudal.domain.rules.RuleSet.OperatingWindow;
import co.caudal.domain.schedule.EditedShift;
import co.caudal.domain.schedule.ProposalState;
import co.caudal.domain.schedule.ProposedTurn;
import co.caudal.domain.schedule.ShiftChangeRules;
import co.caudal.domain.schedule.TurnReason;
import co.caudal.shared.error.FieldValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies the changes of the Board to a proposal pending review: it checks the reason and the
 * limits of the day, keeps the original proposal once ({@code ProposalMemento}), replaces the
 * shifts and approves it with changes. A changed shift keeps its reasons and gets {@code
 * BOARD_CHANGE}; a new one gets only {@code BOARD_CHANGE}.
 *
 * @pattern P13 Command
 */
public class ModifyProposalCommandHandler
    implements CommandHandler<ModifyProposalCommand, ProposalDetail> {

  private static final String MODIFIED = "MODIFIED";
  private static final String ADDED = "ADDED";
  private static final String BOARD_CHANGE = "BOARD_CHANGE";
  private static final String NOT_ALLOWED = "FIELD_VALUE_NOT_ALLOWED";

  private final ProposalDecisions decisions;
  private final RuleSetPort ruleSets;
  private final NetworkPort network;
  private final ProposalPort proposals;
  private final UnitOfWorkPort unitOfWork;

  /**
   * Creates the handler.
   *
   * @param decisions shared steps of a decision
   * @param ruleSets rules of the proposal
   * @param network sectors
   * @param proposals proposals
   * @param unitOfWork transaction boundary
   */
  public ModifyProposalCommandHandler(
      ProposalDecisions decisions,
      RuleSetPort ruleSets,
      NetworkPort network,
      ProposalPort proposals,
      UnitOfWorkPort unitOfWork) {
    this.decisions = decisions;
    this.ruleSets = ruleSets;
    this.network = network;
    this.proposals = proposals;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public ProposalDetail handle(ModifyProposalCommand command) {
    return unitOfWork.executeAs(
        command.caller().scope(),
        () -> {
          ProposalDetail current =
              decisions.current(command.caller(), command.proposalId(), command.lockVersion());
          ProposalState next = ProposalState.of(current.summary().status()).modify();
          ChangeReason reason = ChangeReason.require(command.reason());
          checkReferences(current, command.items());
          RuleSetView rules = ruleSets.findById(current.summary().ruleSetId()).orElseThrow();
          ShiftChangeRules.check(
              command.items().stream()
                  .map(i -> new EditedShift(i.sectorId(), i.startAt(), i.endAt()))
                  .toList(),
              new ShiftChangeRules.DayLimits(
                  current.summary().serviceDate(),
                  decisions.zone(command.caller()),
                  rules.operatingWindows().stream()
                      .map(w -> new OperatingWindow(w.dayOfWeek(), w.startTime(), w.endTime()))
                      .toList(),
                  current.summary().availableHours(),
                  rules.minShiftHours(),
                  rules.maxShiftHours()));
          decisions.snapshotOnce(command.caller(), current);
          proposals.replaceItems(
              command.caller().aqueductId(),
              command.proposalId(),
              turns(current, command.items(), reason));
          return decisions.decide(
              command.caller(),
              command.proposalId(),
              command.lockVersion(),
              next.code(),
              reason.value());
        });
  }

  /** Every id must be a shift of this proposal, once, and every sector an active one. */
  private void checkReferences(ProposalDetail current, List<ModifiedItem> items) {
    Set<UUID> known = current.items().stream().map(ItemView::id).collect(Collectors.toSet());
    Set<UUID> sectors =
        network.sectors().stream()
            .filter(SectorView::active)
            .map(SectorView::id)
            .collect(Collectors.toSet());
    Set<UUID> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      ModifiedItem item = items.get(i);
      if (item.id() != null && (!known.contains(item.id()) || !seen.add(item.id()))) {
        throw new FieldValidationException("items[" + i + "].id", NOT_ALLOWED);
      }
      if (!sectors.contains(item.sectorId())) {
        throw new FieldValidationException("items[" + i + "].sector_id", NOT_ALLOWED);
      }
    }
  }

  private static List<ProposedTurn> turns(
      ProposalDetail current, List<ModifiedItem> items, ChangeReason reason) {
    Map<UUID, ItemView> before =
        current.items().stream().collect(Collectors.toMap(ItemView::id, Function.identity()));
    TurnReason change = new TurnReason(BOARD_CHANGE, Map.of("motivo", reason.value()));
    List<ModifiedItem> ordered =
        items.stream()
            .sorted(
                Comparator.comparingInt(ModifiedItem::sequence)
                    .thenComparing(ModifiedItem::startAt))
            .toList();
    List<ProposedTurn> turns = new ArrayList<>();
    for (ModifiedItem item : ordered) {
      ItemView old = item.id() == null ? null : before.get(item.id());
      String origin;
      List<TurnReason> reasons = new ArrayList<>();
      if (old == null) {
        origin = ADDED;
        reasons.add(change);
      } else {
        old.reasons().stream()
            .filter(r -> !BOARD_CHANGE.equals(r.code()))
            .map(r -> new TurnReason(r.code(), r.params()))
            .forEach(reasons::add);
        boolean same =
            old.sectorId().equals(item.sectorId())
                && old.startAt().equals(item.startAt())
                && old.endAt().equals(item.endAt());
        origin = same ? old.origin() : MODIFIED;
        if (!same) {
          reasons.add(change);
        }
      }
      turns.add(
          new ProposedTurn(
              item.sectorId(), turns.size() + 1, item.startAt(), item.endAt(), origin, reasons));
    }
    return turns;
  }

  @Override
  public Class<ModifyProposalCommand> commandType() {
    return ModifyProposalCommand.class;
  }
}
