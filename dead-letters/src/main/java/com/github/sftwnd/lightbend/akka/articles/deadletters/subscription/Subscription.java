package com.github.sftwnd.lightbend.akka.articles.deadletters.subscription;

import akka.actor.DeadLetter;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.eventstream.EventStream;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class Subscription {

    private static final String STOP_MESSAGE = "STOP!!!";
    private static final Duration APPLICATION_STOP_TIMEOUT = Duration.ofSeconds(10);

    public static void main(String[] args) {
        logger.info("Start");
        ActorSystem<String> actorSystem = ActorSystem.create(Behaviors.setup(Application::new), "application");
        AtomicInteger id = new AtomicInteger();
        actorSystem.scheduler().scheduleAtFixedRate(Duration.ofSeconds(1), Duration.ofSeconds(1), () -> actorSystem.tell("Message #"+id.incrementAndGet()),
                actorSystem.executionContext()
        );
        actorSystem.scheduler().scheduleOnce(Duration.ofSeconds(7), () -> actorSystem.tell(STOP_MESSAGE), actorSystem.executionContext());
    }

    @Slf4j
    public static class Application extends AbstractBehavior<String> {

        private final ActorRef<String> processor;
        private Instant limit = Instant.MAX;

        public Application(ActorContext<String> context) {
            super(context);
            processor = getContext().spawn(Behaviors.setup(Processor::new), "processor");
            ActorRef<DeadLetter> deadLetter = getContext().spawn(new DeadLetterBehavior().construct(), "dead-letter");
            getContext().getSystem().eventStream().tell(new EventStream.Subscribe<>(DeadLetter.class, deadLetter));
            getContext().watch(processor);
        }

        @Override
        public Receive<String> createReceive() {
            return newReceiveBuilder()
                    .onMessage(String.class, this::onMessage)
                    .onSignal(Terminated.class, this::onTerminated)
                    .build();
        }

        private Behavior<String> onMessage(String message) {
            processor.tell(message);
            if (Instant.now().isAfter(limit)) {
                logger.info("Application has been stopped");
                return Behaviors.stopped();
            }
            return this;
        }

        private Behavior<String> onTerminated(Terminated signal) {
            this.limit = Instant.now().plus(APPLICATION_STOP_TIMEOUT);
            return this;
        }

    }

    @Slf4j
    static class Processor extends AbstractBehavior<String> {

        public Processor(ActorContext<String> context) { super(context); }

        @Override
        public Receive<String> createReceive() {
            return newReceiveBuilder()
                    .onMessage(String.class, this::onStringMessage)
                    .build();
        }

        private Behavior<String> onStringMessage(String message) {
            logger.info("Message received: {}", message);
            if (STOP_MESSAGE.equals(message)) {
                logger.warn("Processor is dead...");
                return Behaviors.stopped();
            }
            return this;
        }

    }

    @Slf4j
    static class DeadLetterBehavior {

        Behavior<DeadLetter> construct() {
            return Behaviors.receive(DeadLetter.class)
                    .onMessage(DeadLetter.class, this::onDeadLetter)
                    .build();
        }

        private Behavior<DeadLetter> onDeadLetter(DeadLetter deadLetter) {
            logger.warn("Dead letter to {} has been received for message: {}", deadLetter.recipient().path().toStringWithoutAddress(), deadLetter.message());
            return Behaviors.same();
        }

    }
}
