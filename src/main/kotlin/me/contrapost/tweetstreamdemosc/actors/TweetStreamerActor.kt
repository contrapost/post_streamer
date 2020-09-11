package me.contrapost.tweetstreamdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.event.Logging
import me.contrapost.tweetstreamdemosc.util.getLastTweet
import me.contrapost.tweetstreamdemosc.util.getNewTweets
import me.contrapost.tweetstreamdemosc.util.getStream
import me.contrapost.tweetstreamdemosc.util.setupRules
import scala.concurrent.duration.Duration
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class TweetStreamerActor(private val userName: String) : AbstractActor() {

    var lastTweetId: String? = null

    private fun dispatchPollCommand() = context
        .system
        .scheduler()
        .schedule(
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
            Props.create(TweetStreamerActor::class.java) { TweetStreamerActor(userName) }
    }

    private val logger = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive = receiveBuilder()
        .match(PollCmd::class.java, ::pollTweets)
        .match(GetData::class.java, ::getData)
        .build()

    override fun preStart() {
        context
        super.preStart()
        logger.info("Starting to stream tweets from $userName")
        lastTweetId = getLastTweet(userName, BEARER_TOKEN)?.id
        logger.info("Last tweet: $lastTweetId")
        dispatchPollCommand()
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

}

private object PollCmd

object GetData
