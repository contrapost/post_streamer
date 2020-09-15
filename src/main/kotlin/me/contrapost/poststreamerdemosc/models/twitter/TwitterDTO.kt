package me.contrapost.poststreamerdemosc.models.twitter

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import me.contrapost.poststreamerdemosc.models.Post

@JsonIgnoreProperties(ignoreUnknown = true)
data class TweetDto(
    override val id: String,
    @get: JsonProperty("created_at")
    val createdAt: String?,
    val text: String? = null,
    @get: JsonProperty("reply_count")
    val replyCount: Int? = null,
    val lang: String? = null
) : Post
