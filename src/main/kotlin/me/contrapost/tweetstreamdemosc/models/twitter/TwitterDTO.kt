package me.contrapost.tweetstreamdemosc.models.twitter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TweetDto(
    val id: String? = null,
    val text: String? = null
)
