package me.contrapost.tweetstreamdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Cancellable
import akka.actor.Props
import akka.event.Logging
import me.contrapost.tweetstreamdemosc.models.twitter.TweetDto
import me.contrapost.tweetstreamdemosc.util.getLastTweet
import me.contrapost.tweetstreamdemosc.util.getNewTweets
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * Should be refactored to be stateful
 */
class TweetSubscriptionActor(private val userName: String) : AbstractActor() {

    var lastTweetId: String? = null

    private lateinit var pollScheduler: Cancellable

    companion object {
        val DEFAULT_INTERVAL = PollInterval(30, TimeUnit.SECONDS)
        private val MIN_POLLING_INTERVAL = PollInterval(10, TimeUnit.SECONDS)

        val BEARER_TOKEN: String = System.getenv("TWITTER_BEARER_TOKEN")

        const val SUBSCRIPTION_ACTOR_NAME_PREFIX = "tweet-subscription"

        fun props(userName: String): Props =
            Props.create(TweetSubscriptionActor::class.java) { TweetSubscriptionActor(userName) }

        fun validateSetup(registerSubscriptionMsg: RegisterSubscription): SubscriptionSetupValidationResult {
            val errors = mutableListOf<String>()
            if (registerSubscriptionMsg.pollInterval != null && registerSubscriptionMsg.pollInterval < MIN_POLLING_INTERVAL)
                errors.add("Polling interval must be greater or equal to $MIN_POLLING_INTERVAL")
            return SubscriptionSetupValidationResult(errors.isEmpty(), errors)
        }
    }

    private val logger = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive = receiveBuilder()
        .match(PollCmd::class.java, ::pollTweets)
        .match(RegisterSubscription::class.java, ::registerSubscription)
        .match(Shutdown::class.java, ::shutdown)
        .build()

    override fun preStart() {
        super.preStart()
        logger.info("Starting to stream tweets from $userName")
        lastTweetId = getLastTweet(userName, BEARER_TOKEN)?.id
        logger.info("Last tweet: $lastTweetId")
    }

    override fun postStop() {
        logger.info("Stopped to stream tweets from $userName")
        super.postStop()
    }

    private fun pollTweets(pollMessage: PollCmd) {
        val tweets = getNewTweets(userName, BEARER_TOKEN, lastTweetId)
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
