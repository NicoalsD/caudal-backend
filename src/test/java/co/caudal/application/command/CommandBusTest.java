package co.caudal.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandBusTest {

  private record Greet(String name) implements Command<String> {}

  private record Ping() implements Command<Done> {}

  private record Unhandled() implements Command<Done> {}

  private static final class GreetHandler implements CommandHandler<Greet, String> {

    int calls;

    @Override
    public String handle(Greet command) {
      calls++;
      return "hello " + command.name();
    }

    @Override
    public Class<Greet> commandType() {
      return Greet.class;
    }
  }

  private static final class PingHandler implements CommandHandler<Ping, Done> {

    @Override
    public Done handle(Ping command) {
      return Done.DONE;
    }

    @Override
    public Class<Ping> commandType() {
      return Ping.class;
    }
  }

  @Test
  void dispatchesToTheHandlerOfTheCommandAndReturnsItsAnswer() {
    GreetHandler greet = new GreetHandler();
    CommandBus bus = new SimpleCommandBus(List.of(greet, new PingHandler()));

    assertThat(bus.dispatch(new Greet("ana"))).isEqualTo("hello ana");
    assertThat(bus.dispatch(new Ping())).isEqualTo(Done.DONE);
    assertThat(greet.calls).isEqualTo(1);
  }

  @Test
  void aCommandWithoutHandlerIsRejected() {
    CommandBus bus = new SimpleCommandBus(List.of(new GreetHandler()));

    assertThatThrownBy(() -> bus.dispatch(new Unhandled()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unhandled");
  }

  @Test
  void twoHandlersForTheSameCommandFailAtStartup() {
    List<CommandHandler<?, ?>> handlers = List.of(new GreetHandler(), new GreetHandler());

    assertThatThrownBy(() -> new SimpleCommandBus(handlers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Greet");
  }

  @Test
  void aNullCommandIsRejected() {
    CommandBus bus = new SimpleCommandBus(List.of());

    assertThatThrownBy(() -> bus.dispatch(null)).isInstanceOf(NullPointerException.class);
  }
}
