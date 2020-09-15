package me.contrapost.poststreamdemosc.actors

import akka.actor.*
import akka.event.Logging
import akka.pattern.Patterns.ask
import me.contrapost.poststreamdemosc.models.Post
import me.contrapost.poststreamdemosc.models.twitter.TweetDto
import me.contrapost.poststreamdemosc.pollers.PostPoller
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import java.lang.Exception
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PostSubscriptionActor(
    private val userName: String,
    private val subscriberId: String,
    private val pollerService: PostPoller
) : AbstractActor() {

    private var lastTweetId: String? = null
    private lateinit var pollScheduler: Cancellable
    private val publisherActorName = "publisher-actor-${self.path().name()}"
    private var queueId: String? = null
    private lateinit var publisherActor: ActorRef
    private val logger = Logging.getLogger(context.system, this)

    companion object {
        val DEFAULT_INTERVAL = PollInterval(30, TimeUnit.SECONDS)
        private val MIN_POLLING_INTERVAL = PollInterval(10, TimeUnit.SECONDS)

        fun props(userName: String, subscriberId: String, poller: PostPoller): Props =
            Props.create(PostSubscriptionActor::class.java) { PostSubscriptionActor(userName, subscriberId, poller) }

        fun validateSetup(registerSubscriptionMsg: RegisterSubscription): SubscriptionSetupValidationResult {
            val errors = mutableListOf<String>()
            if (registerSubscriptionMsg.pollInterval != null && registerSubscriptionMsg.pollInterval < MIN_POLLING_INTERVAL)
                errors.add("Polling interval must be greater or equal to $MIN_POLLING_INTERVAL")
            return SubscriptionSetupValidationResult(errors.isEmpty(), errors)
        }
    }

    override fun preStart() {
        super.preStart()
    }

    override fun postStop() {
        logger.info("Stopped to stream tweets from $userName")
        super.postStop()
    }

    override fun createReceive(): Receive = receiveBuilder()
        .match(PollCmd::class.java, ::pollTweets)
        .match(RegisterSubscription::class.java, ::registerSubscription)
        .match(Shutdown::class.java, ::shutdown)
        .build()

    private fun pollTweets(pollMessage: PollCmd) {
        val tweets = pollerService.getNewPosts(userName, lastTweetId)
        when {
            tweets.isNotEmpty() -> {
                lastTweetId = tweets.first().id
                logger.info("Received new tweets: $tweets")
                addNewTweets(tweets)
            }
            else -> logger.info("No new tweets")
        }
    }

    private fun addNewTweets(tweets: List<Post>) {
        publisherActor.tell(AddToQueue(tweets), self)
    }

    private fun registerSubscription(registerSubscriptionCmd: RegisterSubscription) {
        val pollInterval = registerSubscriptionCmd.pollInterval ?: DEFAULT_INTERVAL

        runCatching {
            pollerService.getLastPost(userName)?.id
        }.onSuccess {
            lastTweetId = it
            logger.info("Starting to stream tweets from $userName, last tweet: $lastTweetId")
            when (createQueue()) {
                true -> {
                    pollScheduler = initializeScheduler(pollInterval)
                    sender.tell(
                        SubscriptionRegistrationResult(
                            true,
                            pollingInterval = pollInterval.toString(),
                            subscriptionId = queueId
                        ), self
                    )
                }
                else -> {
                    sender.tell(
                        SubscriptionRegistrationResult(
                            false,
                            pollingInterval = pollInterval.toString(),
                            errors = listOf("Error while creating a queue for post publishing")
                        ), self
                    )
                }
            }
        }.onFailure {
            sender.tell(
                SubscriptionRegistrationResult(
                    false,
                    pollingInterval = pollInterval.toString(),
                    errors = listOf(it.message ?: "Error while fetching post")
                ), self
            )
            context.stop(self)
        }
    }

    private fun createQueue(): Boolean {
        publisherActor = context.actorOf(PostPublisherDemoActor.props(userName, subscriberId), publisherActorName)
        return try {
            val future = CompletableFuture<QueueCreationConfirmation>()
            ask(publisherActor, CreateQueue, 5_000).onComplete(complete(future), context.dispatcher)
            queueId = future.get().queueId
            true
        } catch (exception: Exception) {
            logger.error("Failed to create queue for post publishing: $exception")
            stopPublisherActor()
            false
        }
    }

    private fun initializeScheduler(pollInterval: PollInterval) = context
        .system
        .scheduler().schedule(
            Duration.create(pollInterval.value, pollInterval.unit),
            Duration.create(pollInterval.value, pollInterval.unit),
            self,
            PollCmd,
            context.system.dispatcher,
            self
        )

    private fun shutdown(cmd: Shutdown) {
        pollScheduler.cancel()
        stopPublisherActor()
        context.stop(self)
    }

    private fun stopPublisherActor() = publisherActor.tell(PoisonPill.getInstance(), ActorRef.noSender())

    private fun complete(future: CompletableFuture<QueueCreationConfirmation>) = { result: Any ->
        try {
            when (result) {
                is Success<*> -> future.complete(result.get() as QueueCreationConfirmation)
                is Failure<*> -> future.completeExceptionally(result.exception())
                else -> future.completeExceptionally(IllegalArgumentException("Unknown response type: $result"))
            }
        } catch (throwable: Throwable) {
            future.completeExceptionally(throwable)
        }
    }
}

data class SubscriptionSetupValidationResult(val valid: Boolean, val errors: List<String>)

private object PollCmd

data class RegisterSubscription(val pollInterval: PollInterval?)

data class PollInterval(
    val value: Long,
    val unit: TimeUnit
) : Comparable<PollInterval> {
    override fun compareTo(other: PollInterval): Int =
        Duration.create(value, unit).compareTo(Duration.create(other.value, other.unit))

    override fun toString(): String = "$value${unit.name.first()}"
}

object Shutdown

data class SubscriptionRegistrationResult(
    val created: Boolean,
    val errors: List<String>? = null,
    val pollingInterval: String? = null,
    val subscriptionId: String? = null
)
