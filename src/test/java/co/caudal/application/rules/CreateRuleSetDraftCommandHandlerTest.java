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
import co.caudal.domain.rules.RuleSet;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateRuleSetDraftCommandHandlerTest {

  private static final UUID ACTIVE = UUID.randomUUID();
  private static final UUID DRAFT = UUID.randomUUID();
  private static final Caller MEMBER =
      new Caller(UUID.randomUUID(), UUID.randomUUID(), "BOARD_MEMBER", null);

  private final RuleSetPort ruleSets = mock(RuleSetPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final CreateRuleSetDraftCommandHandler handler =
      new CreateRuleSetDraftCommandHandler(ruleSets, unitOfWork);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
  }

  @Test
  void copiesTheActiveVersionIntoTheNextNumber() {
    when(ruleSets.findActive())
        .thenReturn(Optional.of(RuleSetFixtures.view(ACTIVE, 3, RuleSetFixtures.seedBands())));
    when(ruleSets.findDetail(ACTIVE))
        .thenReturn(
            Optional.of(RuleSetFixtures.detail(ACTIVE, 3, "ACTIVE", RuleSetFixtures.seedBands())));
    when(ruleSets.maxVersion()).thenReturn(5);
    when(ruleSets.insertDraft(
            eq(MEMBER.aqueductId()), any(), eq(MEMBER.userId()), eq("Motivo de prueba.")))
        .thenReturn(DRAFT);
    RuleSetDetail stored = RuleSetFixtures.detail(DRAFT, 6, "DRAFT", RuleSetFixtures.seedBands());
    when(ruleSets.findDetail(DRAFT)).thenReturn(Optional.of(stored));

    CreateRuleSetDraftCommand command = new CreateRuleSetDraftCommand(MEMBER, "Motivo de prueba.");
    RuleSetDetail result = handler.handle(command);

    ArgumentCaptor<RuleSet> draft = ArgumentCaptor.forClass(RuleSet.class);
    verify(ruleSets).insertDraft(any(), draft.capture(), any(), any());
    assertThat(draft.getValue().version()).isEqualTo(6);
    assertThat(draft.getValue().basedOnRuleSetId()).isEqualTo(ACTIVE);
    assertThat(draft.getValue().bands()).hasSize(3);
    assertThat(result).isSameAs(stored);
    assertThat(command.auditEntityType()).isEqualTo("rule_set");
    assertThat(command.auditEntityId(result)).isEqualTo(DRAFT.toString());
    assertThat(handler.commandType()).isEqualTo(CreateRuleSetDraftCommand.class);
  }

  @Test
  void refusesASecondDraftAndAnAqueductWithoutActiveRules() {
    when(ruleSets.draftExists()).thenReturn(true);
    assertThatThrownBy(() -> handler.handle(new CreateRuleSetDraftCommand(MEMBER, null)))
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.CONFLICT);

    when(ruleSets.draftExists()).thenReturn(false);
    when(ruleSets.findActive()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(new CreateRuleSetDraftCommand(MEMBER, null)))
        .extracting(failure -> ((DomainException) failure).code())
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(ruleSets, never()).insertDraft(any(), any(), any(), any());
  }
}
