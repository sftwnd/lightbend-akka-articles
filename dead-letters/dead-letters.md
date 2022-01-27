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

