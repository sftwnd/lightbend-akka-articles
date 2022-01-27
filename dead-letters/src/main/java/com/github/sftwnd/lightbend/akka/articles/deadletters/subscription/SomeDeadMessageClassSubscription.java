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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


@Slf4j
public class SomeDeadMessageClassSubscription {

    private static final String STOP_MESSAGE = "STOP!!!";
    private static final Duration APPLICATION_STOP_TIMEOUT = Duration.ofSeconds(10);

    public static void main(String[] args) {
        logger.info("Start");
        ActorSystem<Object> actorSystem = ActorSystem.create(Behaviors.setup(Application::new), "application");
        AtomicInteger id = new AtomicInteger();
        actorSystem.scheduler().scheduleAtFixedRate(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                () -> {
                    actorSystem.tell("Message #"+id.incrementAndGet());
                    actorSystem.tell(id.incrementAndGet());
                    actorSystem.tell(1.0F * id.incrementAndGet());
                    actorSystem.tell(1.0D * id.incrementAndGet());
                },
                actorSystem.executionContext()
        );
        actorSystem.scheduler().scheduleOnce(Duration.ofSeconds(7), () -> actorSystem.tell(STOP_MESSAGE), actorSystem.executionContext());
    }

    @Slf4j
    public static class Application extends AbstractBehavior<Object> {

        private final ActorRef<Object> processor;
        private Instant limit = Instant.MAX;

        public Application(ActorContext<Object> context) {
            super(context);
            processor = getContext().spawn(Behaviors.setup(Processor::new), "processor");
            ActorRef<String> stringDeadLetter = getContext().spawn(new DeadLetterProcessor<>(String.class).construct(), "string-dead-letter");
            ActorRef<Double> doubleDeadLetter = getContext().spawn(new DeadLetterProcessor<>(Double.class).construct(), "double-dead-letter");
            Collection<DeadLetterSubscription<?>> deadLetterSubscriptions = new ArrayList<>();
            deadLetterSubscriptions.add(new DeadLetterSubscription<>(String.class, stringDeadLetter));
            deadLetterSubscriptions.add(new DeadLetterSubscription<>(Double.class, doubleDeadLetter));
            ActorRef<DeadLetter> deadLetter = getContext().spawn(new DeadLetterSubscriber(deadLetterSubscriptions).construct(), "dead-letters");
            getContext().getSystem().eventStream().tell(new EventStream.Subscribe<>(DeadLetter.class, deadLetter));
            getContext().watch(processor);
        }

        @Override
        public Receive<Object> createReceive() {
            return newReceiveBuilder()
                    .onSignal(Terminated.class, this::onTerminated)
                    .onMessage(Object.class, this::onMessage)
                    .build();
        }

        private Behavior<Object> onMessage(Object message) {
            processor.tell(message);
            if (Instant.now().isAfter(limit)) {
                logger.info("Application has been stopped");
                return Behaviors.stopped();
            }
            return this;
        }

        private Behavior<Object> onTerminated(Terminated signal) {
            this.limit = Instant.now().plus(APPLICATION_STOP_TIMEOUT);
            return this;
        }

    }

    @Slf4j
    static class Processor extends AbstractBehavior<Object> {
        public Processor(ActorContext<Object> context) { super(context); }
        @Override
        public Receive<Object> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Object.class, this::onMessage)
                    .build();
        }
        private Behavior<Object> onMessage(Object message) {
            logger.info("Message [{}] received: {}",message.getClass().getSimpleName(), message);
            if (STOP_MESSAGE.equals(message)) {
                logger.warn("Processor is dead...");
                return Behaviors.stopped();
            }
            return this;
        }
    }

    @Slf4j
    @AllArgsConstructor
    static class DeadLetterProcessor<M> {
        private Class<M> clazz;
        Behavior<M> construct() {
            return Behaviors.receive(clazz)
                    .onMessage(clazz, this::onDeadLetter)
                    .build();
        }
        private Behavior<M> onDeadLetter(M deadMessage) {
            logger.info("Dead letter [{}: {}]", clazz.getSimpleName(), deadMessage);
            return Behaviors.same();
        }
    }

    @AllArgsConstructor
    public static class DeadLetterSubscription<M> {
        @Getter Class<M> clazz;
        @Getter ActorRef<M> subscriber;
        public void process(Object message) {
            subscriber.tell(clazz.cast(message));
        }
    }

    @Slf4j
    @AllArgsConstructor
    static class DeadLetterSubscriber {

        private Map<Class<?>, Consumer<Object>> classActorRefMap;

        public DeadLetterSubscriber(Collection<DeadLetterSubscription<?>> subscriptions) {
            classActorRefMap = new HashMap<>();
            subscriptions.forEach(subscription -> classActorRefMap.put(subscription.getClazz(), subscription::process));
        }

        Behavior<DeadLetter> construct() {
            return Behaviors.receive(DeadLetter.class)
                    .onMessage(DeadLetter.class, this::onDeadLetter)
                    .build();
        }

        private Behavior<DeadLetter> onDeadLetter(DeadLetter deadLetter) {
            Optional.ofNullable(classActorRefMap.get(deadLetter.message().getClass()))
                    .ifPresent(consumer -> consumer.accept(deadLetter.message()));
            return Behaviors.same();
        }

    }

}
