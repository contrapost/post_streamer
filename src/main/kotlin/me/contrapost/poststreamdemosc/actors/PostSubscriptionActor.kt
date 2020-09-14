package me.contrapost.poststreamdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Cancellable
import akka.actor.Props
import akka.event.Logging
import me.contrapost.poststreamdemosc.models.twitter.TweetDto
import me.contrapost.poststreamdemosc.pollers.PostPoller
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class PostSubscriptionActor(private val userName: String, private val pollerService: PostPoller) : AbstractActor() {

    private var lastTweetId: String? = null
    private lateinit var pollScheduler: Cancellable
    private val logger = Logging.getLogger(context.system, this)

    companion object {
        val DEFAULT_INTERVAL = PollInterval(30, TimeUnit.SECONDS)
        private val MIN_POLLING_INTERVAL = PollInterval(10, TimeUnit.SECONDS)

        fun props(userName: String, poller: PostPoller): Props =
            Props.create(PostSubscriptionActor::class.java) { PostSubscriptionActor(userName, poller) }

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
            }
            else -> logger.info("No new tweets")
        }
    }

    private fun addNewTweets(tweets: TweetDto) {

    }

    private fun registerSubscription(registerSubscriptionCmd: RegisterSubscription) {
        val pollInterval = registerSubscriptionCmd.pollInterval ?: DEFAULT_INTERVAL

        runCatching {
            pollerService.getLastPost(userName)?.id
        }.onSuccess {
            lastTweetId = it
            logger.info("Starting to stream tweets from $userName, last tweet: $lastTweetId")
            pollScheduler = context
                .system
                .scheduler().schedule(
                    Duration.create(pollInterval.value, pollInterval.unit),
                    Duration.create(pollInterval.value, pollInterval.unit),
                    self,
                    PollCmd,
                    context.system.dispatcher,
                    self
                )
            sender.tell(
                SubscriptionRegistrationResult(
                    true,
                    pollingInterval = pollInterval.toString(),
                    subscriptionData = "Info"
                ), self
            )
        }.onFailure {
            sender.tell(
                SubscriptionRegistrationResult(
                    false,
                    pollingInterval = pollInterval.toString(),
                    errors = listOf(it.message ?: "Error while fetching post"),
                    subscriptionData = "Info"
                ), self
            )
            context.stop(self)
        }
    }

    private fun shutdown(cmd: Shutdown) {
        pollScheduler.cancel()
        context.stop(self)
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
    val subscriptionData: String? = null
)
