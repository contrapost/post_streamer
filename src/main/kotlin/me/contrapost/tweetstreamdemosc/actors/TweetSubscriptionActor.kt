package me.contrapost.tweetstreamdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.event.Logging
import me.contrapost.tweetstreamdemosc.util.getLastTweet
import me.contrapost.tweetstreamdemosc.util.getNewTweets
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

class TweetSubscriptionActor(private val userName: String) : AbstractActor() {

    var lastTweetId: String? = null

    private val pollScheduler = context
        .system
        .scheduler().schedule(
            Duration.create(5_000, TimeUnit.MILLISECONDS),
            Duration.create(25_000, TimeUnit.MILLISECONDS),
            self,
            PollCmd,
            context.system.dispatcher,
            self
        )


    companion object {

        val BEARER_TOKEN: String = System.getenv("TWITTER_BEARER_TOKEN")

        fun props(userName: String): Props =
            Props.create(TweetSubscriptionActor::class.java) { TweetSubscriptionActor(userName) }
    }

    private val logger = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive = receiveBuilder()
        .match(PollCmd::class.java, ::pollTweets)
        .match(GetData::class.java, ::getData)
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
        if (tweets.isNotEmpty()) {
            lastTweetId = tweets.first().id
            logger.info("Received new tweets: $tweets")
        } else {
            logger.info("No new tweets")
        }
    }

    private fun getData(cmd: GetData) {
        sender.tell(lastTweetId ?: "done", self)
    }

    private fun shutdown(cmd: Shutdown) {
        pollScheduler.cancel()
        context.stop(self)
    }
}

private object PollCmd

object GetData

object Shutdown
