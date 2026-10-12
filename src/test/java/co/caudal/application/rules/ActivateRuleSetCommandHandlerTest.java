package co.caudal.application.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.caudal.application.auth.Caller;
import co.caudal.application.event.DomainEventPublisher;
import co.caudal.application.network.SectorView;
import co.caudal.application.network.TankView;
import co.caudal.application.network.ValveView;
import co.caudal.application.port.out.NetworkPort;
import co.caudal.application.port.out.RuleSetPort;
import co.caudal.application.port.out.UnitOfWorkPort;
import co.caudal.application.rules.RuleSetView.BandView;
import co.caudal.domain.event.RuleSetActivated;
import co.caudal.shared.error.DomainException;
import co.caudal.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ActivateRuleSetCommandHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-11T15:00:00Z");
  private static final UUID DRAFT = UUID.randomUUID();
  private static final UUID ACTIVE = UUID.randomUUID();
  private static final Caller ADMIN =
      new Caller(UUID.randomUUID(), UUID.randomUUID(), "BOARD_ADMIN", null);
  private static final String REASON = "Se aprueba en la reunión de octubre.";

  private final RuleSetPort ruleSets = mock(RuleSetPort.class);
  private final NetworkPort network = mock(NetworkPort.class);
  private final UnitOfWorkPort unitOfWork = mock(UnitOfWorkPort.class);
  private final DomainEventPublisher events = mock(DomainEventPublisher.class);
  private final ActivateRuleSetCommandHandler handler =
      new ActivateRuleSetCommandHandler(
          ruleSets, network, events, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(unitOfWork.executeAs(any(), any()))
        .thenAnswer(call -> ((Supplier<Object>) call.getArgument(1)).get());
    when(network.tanks())
        .thenReturn(
            List.of(
                new TankView(
                    UUID.randomUUID(),
                    "Tanque",
                    BigDecimal.ZERO,
                    new BigDecimal("5.00"),
                    new BigDecimal("0.05"),
                    20000,
                    true)));
    when(network.sectors())
        .thenReturn(
            List.of(new SectorView(RuleSetFixtures.SECTOR, "ALTO", "Alto", null, 10, true)));
    when(network.valves(RuleSetFixtures.SECTOR))
        .thenReturn(
            List.of(new ValveView(RuleSetFixtures.VALVE, "V-1", "V", null, false, "KEEP", true)));
    when(ruleSets.findActive())
        .thenReturn(Optional.of(RuleSetFixtures.view(ACTIVE, 3, RuleSetFixtures.seedBands())));
  }

  private void givenDraft(List<BandView> bands) {
    when(ruleSets.findDetail(DRAFT))
        .thenReturn(Optional.of(RuleSetFixtures.detail(DRAFT, 4, "DRAFT", bands)));
  }

  private static ErrorCode codeOf(Throwable failure) {
    return ((DomainException) failure).code();
  }

  @Test
  void supersedesTheActiveVersionBeforeActivatingTheDraft() {
    givenDraft(RuleSetFixtures.seedBands());

    handler.handle(new ActivateRuleSetCommand(ADMIN, DRAFT, "  " + REASON + "  "));

    InOrder order = inOrder(ruleSets);
    order.verify(ruleSets).supersede(ACTIVE, NOW);
    order.verify(ruleSets).activate(DRAFT, NOW, ADMIN.userId(), REASON);
    verify(events)
        .publish(
            new RuleSetActivated(
                ADMIN.aqueductId(), DRAFT, 4, ACTIVE, ADMIN.userId(), REASON, NOW));
    assertThat(handler.commandType()).isEqualTo(ActivateRuleSetCommand.class);
  }

  @Test
  void aGapIsReportedAndNothingChanges() {
    givenDraft(
        List.of(
            new BandView("LOW", BigDecimal.ZERO, new BigDecimal("3.00"), BigDecimal.TEN, false),
            new BandView(
                "HIGH", new BigDecimal("3.50"), new BigDecimal("5.00"), BigDecimal.TEN, false)));

    assertThatThrownBy(() -> handler.handle(new ActivateRuleSetCommand(ADMIN, DRAFT, REASON)))
        .isInstanceOf(DomainException.class)
        .satisfies(
            failure -> {
              DomainException domain = (DomainException) failure;
              assertThat(domain.code()).isEqualTo(ErrorCode.RULE_SET_INVALID);
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> violations =
                  (List<Map<String, Object>>) domain.details().get("violations");
              assertThat(violations.getFirst()).containsEntry("code", "BAND_GAP");
            });
    verify(ruleSets, never()).supersede(any(), any());
    verify(ruleSets, never()).activate(any(), any(), any(), any());
    verify(events, never()).publish(any());
  }

  @Test
  void refusesAMissingReasonAVersionThatIsNotADraftAndAnUnknownOne() {
    assertThatThrownBy(() -> handler.handle(new ActivateRuleSetCommand(ADMIN, DRAFT, null)))
        .extracting(ActivateRuleSetCommandHandlerTest::codeOf)
        .isEqualTo(ErrorCode.REASON_REQUIRED);

    when(ruleSets.findDetail(DRAFT))
        .thenReturn(
            Optional.of(RuleSetFixtures.detail(DRAFT, 4, "ACTIVE", RuleSetFixtures.seedBands())));
    assertThatThrownBy(() -> handler.handle(new ActivateRuleSetCommand(ADMIN, DRAFT, REASON)))
        .extracting(ActivateRuleSetCommandHandlerTest::codeOf)
        .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

    when(ruleSets.findDetail(DRAFT)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(new ActivateRuleSetCommand(ADMIN, DRAFT, REASON)))
        .extracting(ActivateRuleSetCommandHandlerTest::codeOf)
        .isEqualTo(ErrorCode.NOT_FOUND);
  }
}
