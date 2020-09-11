package me.contrapost.tweetstreamdemosc.util

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.contrapost.tweetstreamdemosc.models.twitter.TweetDto
import me.contrapost.tweetstreamdemosc.util.TwitterAPIURL.USER_TIMELINE_URL
import org.apache.http.client.HttpClient
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

val mapper = jacksonObjectMapper()

object TwitterAPIURL {
    const val USER_TIMELINE_URL = "https://api.twitter.com/1.1/statuses/user_timeline.json"
}

fun getLastTweet(userScreenName: String, token: String): TweetDto? {
    val response = "$USER_TIMELINE_URL?screen_name=$userScreenName&count=1".getResponse(token)

    val tweets: List<TweetDto> = mapper.readValue(response)

    return tweets.getOrNull(0)
}

fun getNewTweets(userScreenName: String, token: String, lastTweetId: String?): List<TweetDto> {
    val lastTweetPostfix = when (lastTweetId) {
        null -> ""
        else -> "&since_id=$lastTweetId"
    }
    val response = "$USER_TIMELINE_URL?screen_name=$userScreenName$lastTweetPostfix".getResponse(token)

    return mapper.readValue(response)
}

fun String.getResponse(token: String): String {
    val httpClient: HttpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build()
        )
        .build()
    val uriBuilder = URIBuilder(this)
    val httpGet = HttpGet(uriBuilder.build())
    httpGet.setHeader("Authorization", String.format("Bearer %s", token))
    val response = httpClient.execute(httpGet)
    val entity = response.entity
    return EntityUtils.toString(entity, "UTF-8")
}
