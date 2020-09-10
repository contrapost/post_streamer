package me.contrapost.tweetstreamdemosc.actors

import akka.actor.ActorSystem
import org.springframework.stereotype.Component

@Component
class TempActor(actorSystem: ActorSystem) {

    val temp = actorSystem.actorOf(TweetStreamerActor.props("as_contrapost"), "tweet-streamer-actor")
}
