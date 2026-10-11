package co.caudal.application.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.domain.rules.RuleSet;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateRuleSetDraftCommandHandlerTest {

  private static final UUID ID = UUID.randomUUID();
  private static final Caller MEMBER =
      new Caller(UUID.randomUUID(), UUID.randomUUID(), "BOARD_MEMBER", null);

  private final RuleSetPort ruleSets = mock(RuleSetPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final UpdateRuleSetDraftCommandHandler handler =
      new UpdateRuleSetDraftCommandHandler(ruleSets, unitOfWork);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
  }

  private static RuleSetChanges changes(BigDecimal reserve, String reason, List<BandView> bands) {
    return new RuleSetChanges(
        reason, reserve, null, null, null, null, null, null, null, null, null, null, null, null,
        bands, null, null, null);
  }

  @Test
  void changesOnlyWhatComesAndKeepsTheRest() {
    when(ruleSets.findDetail(ID))
        .thenReturn(
            Optional.of(RuleSetFixtures.detail(ID, 4, "DRAFT", RuleSetFixtures.seedBands())));

    UpdateRuleSetDraftCommand command =
        new UpdateRuleSetDraftCommand(MEMBER, ID, changes(new BigDecimal("1.20"), null, null));
    handler.handle(command);

    ArgumentCaptor<RuleSet.Parameters> parameters =
        ArgumentCaptor.forClass(RuleSet.Parameters.class);
    verify(ruleSets).updateDraft(eq(ID), parameters.capture(), eq(null));
    assertThat(parameters.getValue().reserveLevel()).isEqualByComparingTo("1.20");
    assertThat(parameters.getValue().maxShiftHours()).isEqualByComparingTo("4.00");
    verify(ruleSets, never()).replaceChildren(any(), any());
    assertThat(command.auditEntityId(null)).isEqualTo(ID.toString());
    assertThat(command.auditEntityType()).isEqualTo("rule_set");
  }

  @Test
  void aNewListReplacesTheChildren() {
    when(ruleSets.findDetail(ID))
        .thenReturn(
            Optional.of(RuleSetFixtures.detail(ID, 4, "DRAFT", RuleSetFixtures.seedBands())));
    List<BandView> one =
        List.of(
            new BandView("HIGH", BigDecimal.ZERO, new BigDecimal("5.00"), BigDecimal.TEN, false));

    handler.handle(
        new UpdateRuleSetDraftCommand(MEMBER, ID, changes(null, "Motivo nuevo largo.", one)));

    ArgumentCaptor<RuleSet> rules = ArgumentCaptor.forClass(RuleSet.class);
    verify(ruleSets).updateDraft(eq(ID), any(), eq("Motivo nuevo largo."));
    verify(ruleSets).replaceChildren(eq(ID), rules.capture());
    assertThat(rules.getValue().bands()).hasSize(1);
    assertThat(rules.getValue().sectors()).hasSize(1);
    assertThat(handler.commandType()).isEqualTo(UpdateRuleSetDraftCommand.class);
  }

  @Test
  void onlyADraftChanges() {
    when(ruleSets.findDetail(ID))
        .thenReturn(
            Optional.of(RuleSetFixtures.detail(ID, 3, "ACTIVE", RuleSetFixtures.seedBands())));
    assertThatThrownBy(
            () ->
                handler.handle(
                    new UpdateRuleSetDraftCommand(MEMBER, ID, changes(null, null, null))))
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.RULE_SET_IMMUTABLE);

    when(ruleSets.findDetail(ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                handler.handle(
                    new UpdateRuleSetDraftCommand(MEMBER, ID, changes(null, null, null))))
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.NOT_FOUND);
  }
}
