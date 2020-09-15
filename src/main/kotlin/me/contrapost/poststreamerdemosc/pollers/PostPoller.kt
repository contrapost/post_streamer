package me.contrapost.poststreamerdemosc.pollers

import me.contrapost.poststreamerdemosc.models.Post

interface PostPoller {
    fun getLastPost(userName: String): Post?
    fun getNewPosts(userName: String, lastPostId: String?): List<Post>
}
