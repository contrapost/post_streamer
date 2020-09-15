package me.contrapost.poststreamdemosc.pollers.twitter

import com.fasterxml.jackson.module.kotlin.readValue
import me.contrapost.poststreamdemosc.models.Post
import me.contrapost.poststreamdemosc.models.twitter.TweetDto
import me.contrapost.poststreamdemosc.pollers.PostPoller
import me.contrapost.poststreamdemosc.util.getResponse
import me.contrapost.poststreamdemosc.util.mapper
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class TwitterPostPoller : PostPoller {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        val BEARER_TOKEN: String = System.getenv("TWITTER_BEARER_TOKEN")
        const val USER_TIMELINE_URL = "https://api.twitter.com/1.1/statuses/user_timeline.json"
    }

    override fun getLastPost(userScreenName: String): Post? {
        var lastTweet: Post? = null
        val errorCause = "Error occurred while fetching last tweet for $userScreenName from $USER_TIMELINE_URL"

        runCatching {
            "${USER_TIMELINE_URL}?screen_name=$userScreenName&count=1".getResponse(BEARER_TOKEN)
        }.onSuccess { response ->
            val entityAsString = EntityUtils.toString(response.entity, "UTF-8")

            when {
                HttpStatus.valueOf(response.statusLine.statusCode).is2xxSuccessful -> {

                    val tweets: List<TweetDto> = mapper.readValue(entityAsString)
                    lastTweet = tweets.getOrNull(0)
                }
                else -> fetchingException(errorCause, entityAsString)
            }
        }.onFailure { fetchingException(errorCause, it.message) }

        return lastTweet
    }

    override fun getNewPosts(userScreenName: String, lastTweetId: String?): List<Post> {
        val lastTweetPostfix = when (lastTweetId) {
            null -> ""
            else -> "&since_id=$lastTweetId"
        }

        val newTweets = mutableListOf<Post>()
        val errorCause = "Error occurred while fetching new tweets for $userScreenName from $USER_TIMELINE_URL"

        runCatching {
            "${USER_TIMELINE_URL}?screen_name=$userScreenName$lastTweetPostfix".getResponse(BEARER_TOKEN)
        }.onSuccess { response ->
            val entityAsString = EntityUtils.toString(response.entity, "UTF-8")
            when {
                HttpStatus.valueOf(response.statusLine.statusCode).is2xxSuccessful -> {

                    val tweets: List<TweetDto> = mapper.readValue(entityAsString)
                    newTweets.addAll(tweets)
                }
                // can retry TODO: limit number of retries
                HttpStatus.valueOf(response.statusLine.statusCode).is5xxServerError -> {}
                else -> fetchingException(errorCause, entityAsString)
            }
        }.onFailure { fetchingException(errorCause, it.message) }

        return newTweets
    }

    private fun fetchingException(errorCause: String, additionalLogMessage: String?): Nothing {
        logger.error("$errorCause: $additionalLogMessage")
        throw IllegalArgumentException(errorCause)
    }
}
