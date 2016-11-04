package com.beachape.filemanagement

import akka.actor.{ ActorLogging, Actor, Props }
import akka.routing.{ SmallestMailboxPool, DefaultResizer }
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes._
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{ Path, WatchEvent }
import java.nio.file.WatchEvent._

import scala.concurrent.duration._

/**
 * Companion object for creating Monitor actor instances
 */
object MonitorActor {

  type CallbackRegistryMap = Map[WatchEvent.Kind[Path], CallbackRegistry]
  /**
   * Factory method for the params required to instantiate a MonitorActor
   *
   * @param concurrency Integer, the number of concurrent threads for handling callbacks
   * @param dedupeTime Duration, how long to wait between modify event dedupe cycles.
   * @return Props for instantiating a MonitorActor
   */
  def apply(concurrency: Int = 5, dedupeTime: FiniteDuration = 1.5.seconds) = {
    require(concurrency >= 1, s"Callback concurrency requested is $concurrency but it should at least be 1")
    Props(classOf[MonitorActor], concurrency, dedupeTime)
  }

  /**
   * Initial CallbackRegistry map. Empty by definition
   */
  val initialEventTypeCallbackRegistryMap: CallbackRegistryMap = Map(
    ENTRY_CREATE -> CallbackRegistry(),
    ENTRY_MODIFY -> CallbackRegistry(),
    ENTRY_DELETE -> CallbackRegistry()
  )

  private case object FlushTime

}

/**
 * Actor for registering callbacks and delegating callback execution
 *
 * Should be instantiated with Props provided via companion object factory
 * method
 */
class MonitorActor(concurrency: Int = 5, dedupeTime: FiniteDuration = 1.5.seconds)
    extends Actor
    with ActorLogging
    with RecursiveFileActions {

  import com.beachape.filemanagement.MonitorActor._

  // Smallest mailbox router for callback actors
  private[this] val callbackActors = context.actorOf(
    SmallestMailboxPool(
      concurrency,
      resizer = Some(DefaultResizer(lowerBound = concurrency, upperBound = concurrency + 1))
    ).props(CallbackActor()),
    "callbackActors"
  )

  private[this] val monitorActor = self
  private[this] val watchServiceTask = new WatchServiceTask(monitorActor)
  private[this] val watchThread = new Thread(watchServiceTask, "WatchService")

  private[this] val flushTrackerCycle = {
    import context.dispatcher
    context.system.scheduler.schedule(
      initialDelay = dedupeTime,
      interval = dedupeTime,
      receiver = monitorActor,
      FlushTime
    )
  }

  override def preStart() = {
    watchThread.setDaemon(true)
    watchThread.start()
  }

  override def postStop() = {
    watchThread.interrupt()
    flushTrackerCycle.cancel()
  }

  def receive = withState(initialEventTypeCallbackRegistryMap, initialEventTypeCallbackRegistryMap, EventTracker())

  /**
   * Returns a new Receive that uses a given CallbackRegistryMap
   *
   * @param callbackRegistryMap For holding path to callback maps that result in actual user-visible effects
   * @param internalCallbackRegistryMap For holding path to callback maps that result in internally visible effects (e.g.
   *                                    implementation persistent callbacks for files that are newly created after callbacks
   *                                    have been added for a directory)
   */
  private[this] def withState(
    callbackRegistryMap: CallbackRegistryMap,
    internalCallbackRegistryMap: CallbackRegistryMap,
    eventTracker: EventTracker): Receive = {
    case ev @ EventAtPath(event, _) if WatchServiceTask.SupportedEvents.contains(event) => {
      /*
        * We dedup ENTRY_MODIFY events by tracking them. If they already exist in the tracker,
        * the existing event is popped, and we run callbacks for them, updating this actor
        * to the latest tracker state.
        *
        * The tracker is flushed periodically based on the dedupeTime constructor parameter in case
        * we have orphaned events (due to different systems and JVM implementations).
      */
      val (eventsToProcess, updatedTracker) = if (event == ENTRY_MODIFY) {
        eventTracker.popExistingOrAdd(ev)
      } else (Seq(ev), eventTracker)
      performCallbacks(
        eventsToProcess = eventsToProcess,
        callbackRegistryMap = callbackRegistryMap,
        internalCallbackRegistryMap = internalCallbackRegistryMap
      )
      context.become(
        withState(
          callbackRegistryMap = callbackRegistryMap,
          internalCallbackRegistryMap = internalCallbackRegistryMap,
          eventTracker = updatedTracker
        )
      )
    }

    case m: RegisterCallbackMessage => {
      addCallback(
        callbackRegistryMap = callbackRegistryMap,
        internalCallbackRegistryMap = internalCallbackRegistryMap,
        eventTracker = eventTracker,
        registerMessage = m
      )
    }

    case UnRegisterCallback(event, path, recursive) => {
      // Ensure that only absolute paths are used
      val absolutePath = path.toAbsolutePath
      context.become(
        withState(
          callbackRegistryMap = newCallbackRegistryMap(
            callbackRegistryMap,
            event,
            _.withoutCallbacksFor(absolutePath, recursive)
          ),
          internalCallbackRegistryMap = newCallbackRegistryMap(
            internalCallbackRegistryMap,
            event,
            _.withoutCallbacksFor(absolutePath, recursive)
          ),
          eventTracker = eventTracker
        )
      )
    }

    case FlushTime => {
      val (eventsToProcess, newTracker) = eventTracker.popOlderThan(dedupeTime)
      performCallbacks(
        eventsToProcess = eventsToProcess,
        callbackRegistryMap = callbackRegistryMap,
        internalCallbackRegistryMap = internalCallbackRegistryMap
      )
      context.become(
        withState(
          callbackRegistryMap = callbackRegistryMap,
          internalCallbackRegistryMap = internalCallbackRegistryMap,
          eventTracker = newTracker
        )
      )
    }

    case unexpected => log.error(s"MonitorActor received an unexpected message :( !\n\n $unexpected")
  }

  /**
   * Return a new CallbackRegistryMap for a given Event type
   */
  private[this] def newCallbackRegistryMap(
    cbRegistryMap: CallbackRegistryMap,
    eventType: WatchEvent.Kind[Path],
    modify: CallbackRegistry => CallbackRegistry): CallbackRegistryMap = {
    if (!cbRegistryMap.isDefinedAt(eventType))
      cbRegistryMap
    else {
      cbRegistryMap.updated(eventType, modify(cbRegistryMap(eventType)))
    }

  }

  /**
   * Retrieves the callbacks registered for a path for an Event type
   *
   * @param eventType WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Option[Callbacks] for the path at the event type (Option[List[Path => Unit]])
   */
  private[filemanagement] def callbacksFor(
    cbRegistryMap: CallbackRegistryMap,
    eventType: WatchEvent.Kind[Path],
    path: Path): Option[Callbacks] = {
    cbRegistryMap
      .get(eventType)
      .flatMap(_.callbacksFor(path))
  }

  /**
   * Adds a path to be monitored by the WatchServiceTask. If specified, all
   * subdirectories will be recursively added to the WatchServiceTask.
   */
  private[this] def addPathToWatchServiceTask(callbackMap: CallbackRegistryMap, internalCallbackMap: CallbackRegistryMap, modifier: Option[Modifier], path: Path, recursive: Boolean = false): Unit = {
    log.debug(s"Adding $path to WatchServiceTask")
    val eventTypes = for {
      cbMap <- Seq(callbackMap, internalCallbackMap)
      (eventType, registry) <- cbMap
      if registry.callbacksFor(path).isDefined
    } yield eventType
    if (eventTypes.nonEmpty) {
      val distinctEventTypes = eventTypes.distinct
      watchServiceTask.watch(path, modifier, distinctEventTypes: _*)
      if (recursive) forEachDir(path) { subDir =>
        log.debug(s"Adding $subDir to WatchServiceTask")
        watchServiceTask.watch(subDir, modifier, distinctEventTypes: _*)
      }
    }
  }

  /**
   * Finds the callbacks for a given EventType and path and sends them all to
   * the CallbackActor pool to get processed
   *
   * @param event WatchEvent.Kind[Path] Java7 Event type
   * @param path Path (Java type) to be registered
   * @return Unit
   */
  private[filemanagement] def processCallbacksFor(
    cbRegistryMap: CallbackRegistryMap,
    internalCbRegistryMap: CallbackRegistryMap,
    event: WatchEvent.Kind[Path],
    path: Path): Unit = {

    def processCallbacks(lookupPath: Path): Unit = {
      val userCallbacks = callbacksFor(cbRegistryMap, event, lookupPath).toSeq
      val internalCallbacks = callbacksFor(internalCbRegistryMap, event, lookupPath).toSeq
      (userCallbacks ++ internalCallbacks).flatten.foreach { callback =>
        callbackActors ! PerformCallback(path, callback)
      }
    }
    processCallbacks(path)
    /*
      If event is ENTRY_DELETE or ENTRY_CREATE or the path is a file, check for callbacks that
      need to be fired for the directory the file is in
    */
    if (event == ENTRY_DELETE || event == ENTRY_CREATE || path.toFile.isFile) {
      processCallbacks(path.getParent)
    }
  }

  private[this] def addCallback(
    callbackRegistryMap: CallbackRegistryMap,
    internalCallbackRegistryMap: CallbackRegistryMap,
    eventTracker: EventTracker,
    registerMessage: RegisterCallbackMessage): Unit = {
    // Ensure that only absolute paths are used
    val absolutePath = registerMessage.path.toAbsolutePath
    // Generate a map with the specific path requested
    val withNewCallbackMap = newCallbackRegistryMap(
      callbackRegistryMap,
      registerMessage.event,
      _.withCallbackFor(
        path = absolutePath,
        callback = registerMessage.callback,
        recursive = registerMessage.recursive,
        bossy = registerMessage.bossy
      )
    )
    val withNewInternalMap = withPersistentCallbacks(internalCallbackRegistryMap, absolutePath, registerMessage)
    addPathToWatchServiceTask(
      callbackMap = withNewCallbackMap,
      internalCallbackMap = withNewInternalMap,
      modifier = registerMessage.modifier,
      path = absolutePath,
      recursive = registerMessage.recursive
    )
    context.become(
      withState(
        callbackRegistryMap = withNewCallbackMap,
        internalCallbackRegistryMap = withNewInternalMap,
        eventTracker = eventTracker
      )
    )
  }

  private[this] def withPersistentCallbacks(
    withNewPathRegistryMap: CallbackRegistryMap,
    absolutePath: Path,
    registerMessage: RegisterCallbackMessage) = {
    if (registerMessage.persistent && absolutePath.toFile.isDirectory) {
      val persistentCallback = persistentRegisterCallback(registerMessage)
      val persistentCreate = newCallbackRegistryMap(
        withNewPathRegistryMap,
        ENTRY_CREATE,
        _.withCallbackFor(
          path = absolutePath,
          callback = persistentCallback,
          recursive = registerMessage.recursive,
          bossy = true
        )
      )
      val persistentUnregisterCallback = persistentUnRegister(registerMessage)
      newCallbackRegistryMap(
        persistentCreate,
        ENTRY_DELETE,
        _.withCallbackFor(
          path = absolutePath,
          callback = persistentUnregisterCallback,
          recursive = registerMessage.recursive,
          bossy = true
        )
      )
    } else withNewPathRegistryMap
  }

  private[this] def performCallbacks(
    eventsToProcess: Seq[EventAtPath],
    callbackRegistryMap: CallbackRegistryMap,
    internalCallbackRegistryMap: CallbackRegistryMap): Unit = {
    eventsToProcess.foreach {
      case EventAtPath(event, path) =>
        log.debug(s"Event $event at path: $path")
        val absolutePath = path.toAbsolutePath
        processCallbacksFor(
          cbRegistryMap = callbackRegistryMap,
          internalCbRegistryMap = internalCallbackRegistryMap,
          event = event.asInstanceOf[WatchEvent.Kind[Path]],
          path = absolutePath
        )
    }
  }

  private[this] def persistentRegisterCallback(m: RegisterCallbackMessage): Callback = { p =>
    val registerMessage = m match {
      case m: RegisterCallback => m.copy(path = p)
      case m: RegisterBossyCallback => m.copy(path = p)
      case m: RegisterSubscriber => m.copy(path = p)
      case m: RegisterBossySubscriber => m.copy(path = p)
    }
    monitorActor ! registerMessage
  }

  private[this] def persistentUnRegister(m: RegisterCallbackMessage): Callback = { p =>
    monitorActor ! UnRegisterCallback(
      event = m.event,
      recursive = m.recursive,
      path = m.path
    )
  }

}