package me.contrapost.poststreamerdemosc.demomessagingservice

import me.contrapost.poststreamerdemosc.models.Post

object DemoMessagingService {
    val service: MutableMap<String, MutableList<Post>> = mutableMapOf()

    fun getLastPosts(queueId: String): List<Post> {
        val posts = service[queueId]
        return when {
            posts != null -> {
                service[queueId] = mutableListOf()
                posts
            }
            else -> emptyList()
        }
    }
}
