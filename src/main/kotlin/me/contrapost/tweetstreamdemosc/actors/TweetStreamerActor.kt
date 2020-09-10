package me.contrapost.tweetstreamdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.event.Logging
import me.contrapost.tweetstreamdemosc.util.getStream
import me.contrapost.tweetstreamdemosc.util.setupRules
import java.io.BufferedReader

class TweetStreamerActor(private val userName: String) : AbstractActor() {

    companion object {

        val BEARER_TOKEN: String = System.getenv("BEARER_TOKEN")

        fun props(userName: String): Props = Props.create(TweetStreamerActor::class.java) { TweetStreamerActor(userName) }
    }

    private val logger = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder().build()
    }

    override fun preStart() {
        super.preStart()
        logger.info("Starting to stream tweets from $userName")
        logTweets()
    }

    private fun logTweets() {
        setupRules(BEARER_TOKEN, userName)
        when (val stream = getStream(BEARER_TOKEN)) {
            null -> logger.info("Oooops")
            else -> {
                val reader = BufferedReader(stream)
                var line = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) logger.info("Tweet: $line")
                    line = reader.readLine()
                }
            }
        }
    }
}
