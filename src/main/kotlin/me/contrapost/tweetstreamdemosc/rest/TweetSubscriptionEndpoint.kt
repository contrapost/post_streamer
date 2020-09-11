package me.contrapost.tweetstreamdemosc.rest

import akka.actor.ActorSystem
import akka.pattern.Patterns
import me.contrapost.tweetstreamdemosc.actors.GetData
import me.contrapost.tweetstreamdemosc.actors.TweetStreamerActor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.CompletableFuture
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.math.abs

@RestController
class TweetSubscriptionEndpoint(private val actorSystem: ActorSystem) {

    @GetMapping(value = ["/subscribe"], produces = [MediaType.APPLICATION_JSON])
    fun createSubscription(
        @QueryParam(value = "userName") userName: String,
        @QueryParam(value = "subscriberId") subscriberId: String
    ): Response {
        val actorName = "tweet-streamer-actor-${abs((userName + subscriberId).hashCode())}"

        val x = actorSystem.actorSelection("user/$actorName").resolveOne(java.time.Duration.ofMillis(5_000))

        val actorRef = x.toCompletableFuture().thenApply {
            Response.ok().entity("EXISTS").build()
        }.exceptionally {
            val subscriptionActor = actorSystem.actorOf(TweetStreamerActor.props(userName), actorName)

            val future = CompletableFuture<String>()
            Patterns.ask(subscriptionActor, GetData, 1_000).onComplete(complete(future), actorSystem.dispatcher)
            Response.ok().entity(future.get()).build()
        }

        return actorRef.get()
    }

    fun complete(futureResult: CompletableFuture<String>) = { result: Any ->
        when (result) {
            is Success<*> -> {
                futureResult.complete(result.get().toString())
            }
            is Failure<*> -> {
            }
            else -> {
            }
        }
    }
}
