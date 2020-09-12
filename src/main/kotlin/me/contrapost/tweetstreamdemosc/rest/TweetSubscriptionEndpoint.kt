package me.contrapost.tweetstreamdemosc.rest

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.Patterns
import me.contrapost.tweetstreamdemosc.actors.GetData
import me.contrapost.tweetstreamdemosc.actors.Shutdown
import me.contrapost.tweetstreamdemosc.actors.TweetSubscriptionActor
import org.springframework.web.bind.annotation.*
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.CompletableFuture
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@RestController
class TweetSubscriptionEndpoint(private val actorSystem: ActorSystem) {

    @PostMapping(value = ["/subscription"], produces = [MediaType.APPLICATION_JSON])
    fun createSubscription(
        @RequestBody @Valid subscriptionRequest: SubscriptionRequest
    ): CompletableFuture<Response> {
        val twitterUserName = subscriptionRequest.twitterUserName
        val subscriberId = subscriptionRequest.subscriberId

        val actorName = "tweet-streamer-actor-$twitterUserName-$subscriberId"

        val resolveCompletionStage = actorSystem.actorSelection("user/$actorName").resolveOne(java.time.Duration.ofMillis(1_000))

        return resolveCompletionStage.toCompletableFuture().thenApply {
            Response.status(Response.Status.CONFLICT).entity("Already exists").build()
        }.exceptionally {
            val subscriptionActor = actorSystem.actorOf(TweetSubscriptionActor.props(twitterUserName!!), actorName)

            val future = CompletableFuture<String>()
            Patterns.ask(subscriptionActor, GetData, 1_000).onComplete(complete(future), actorSystem.dispatcher)
            Response.ok().entity(future.get()).build()
        }
    }

    @DeleteMapping(value = ["/subscription/{subscriberId}"])
    fun deleteAllSubscriptions(
        @PathVariable subscriberId: String
    ) {
        actorSystem.actorSelection("user/*$subscriberId").tell(Shutdown, ActorRef.noSender())
    }

    @DeleteMapping(value = ["/subscription/{subscriberId}/{twitterUserName}"])
    fun deleteSubscription(
        @PathVariable subscriberId: String,
        @PathVariable twitterUserName: String
    ) {
        actorSystem.actorSelection("user/tweet-streamer-actor-$twitterUserName-$subscriberId").tell(Shutdown, ActorRef.noSender())
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


data class SubscriptionRequest(
    @field:NotEmpty(message = "Twitter user name is mandatory")
    val twitterUserName: String? = null,
    @field:NotEmpty(message = "Subscriber id is mandatory")
    val subscriberId: String? = null,
    val updateInterval: String? = null
)
