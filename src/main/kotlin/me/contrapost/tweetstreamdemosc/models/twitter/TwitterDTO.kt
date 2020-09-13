package me.contrapost.tweetstreamdemosc.models.twitter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TweetDto(
    val id: String,
    val text: String? = null,
    @get: JsonProperty("reply_count")
    val replyCount: Int? = null,
    val lang: String? = null
)
