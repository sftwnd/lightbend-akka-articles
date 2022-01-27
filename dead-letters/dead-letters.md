# Akka Dead Letters

## Subscribe to DeadLetters

To subscribe to a Deadletter, send an EventStream.Subscribe message to the AstorSystem.eventStream() actor ref specifying the actor capable of receiving deadletter messages

Create actor for DeadLetter processing
```java
ActorRef<DeadLetter> deadLetter = getContext().spawn(new DeadLetterBehavior().construct(), "dead-letter");
```
Subscribe created actor to DeadLetters 
```java
getContext().getSystem().eventStream().tell(new EventStream.Subscribe<>(DeadLetter.class, deadLetter));
```
DeadLetter Behavior code:
```java
@Slf4j
static class DeadLetterBehavior {

    Behavior<DeadLetter> construct() {
        return Behaviors.receive(DeadLetter.class)
                .onMessage(DeadLetter.class, this::onDeadLetter)
                .build();
    }

    private Behavior<DeadLetter> onDeadLetter(DeadLetter deadLetter) {
        logger.warn("Dead letter to '{}' has been received for message: {}", deadLetter.recipient().path().toStringWithoutAddress(), deadLetter.message());
        return Behaviors.same();
    }

}
```

Example for DeadLetter subscription: [Subscription.java](./src/main/java/com/github/sftwnd/lightbend/akka/articles/deadletters/subscription/Subscription.java)
Subsciption for some type of messages: [SomeDeadMessageClassSubscription.java](./src/main/java/com/github/sftwnd/lightbend/akka/articles/deadletters/subscription/SomeDeadMessageClassSubscription.java)
