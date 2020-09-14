package me.contrapost.poststreamdemosc.pollers

import me.contrapost.poststreamdemosc.models.Post

interface PostPoller {
    fun getLastPost(userName: String): Post?
    fun getNewPosts(userName: String, lastPostId: String?): List<Post>
}
