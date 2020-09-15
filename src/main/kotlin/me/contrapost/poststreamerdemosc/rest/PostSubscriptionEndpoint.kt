package me.contrapost.poststreamerdemosc.rest

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.Patterns.ask
import me.contrapost.poststreamerdemosc.actors.*
import me.contrapost.poststreamerdemosc.actors.ActorNamePrefixes.TWITTER_SUBSCRIPTION_ACTOR_NAME_PREFIX
import me.contrapost.poststreamerdemosc.actors.PostSubscriptionActor.Companion.validateSetup
import me.contrapost.poststreamerdemosc.demomessagingservice.DemoMessagingService
import me.contrapost.poststreamerdemosc.pollers.twitter.TwitterPostPoller
import org.springframework.web.bind.annotation.*
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.Pattern
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@RestController
class TweetSubscriptionEndpoint(private val actorSystem: ActorSystem) {

    @PostMapping(value = ["/twitter"], produces = [MediaType.APPLICATION_JSON])
    fun createSubscription(
        @RequestBody @Valid subscriptionRequest: SubscriptionRequest
    ): Response {
        val twitterUserName = subscriptionRequest.twitterUserName
        val subscriberId = subscriptionRequest.subscriberId

        val actorName = "$TWITTER_SUBSCRIPTION_ACTOR_NAME_PREFIX-$twitterUserName-$subscriberId"

        val resolveCompletionStage =
            actorSystem.actorSelection("user/$actorName").resolveOne(java.time.Duration.ofMillis(3_000))

        return resolveCompletionStage.toCompletableFuture()
            .thenApply {
                Response.status(Response.Status.CONFLICT).entity("Already exists").build()
            }.exceptionally {

                val pollInterval = subscriptionRequest.updateInterval?.toDuration()
                val subscriptionRegistrationRequest = RegisterSubscription(pollInterval)
                val setUpValidation = validateSetup(subscriptionRegistrationRequest)

                when {
                    setUpValidation.valid -> {
                        try {
                            val subscriptionActor =
                                actorSystem.actorOf(
                                    PostSubscriptionActor.props(
                                        twitterUserName!!,
                                        subscriberId!!,
                                        TwitterPostPoller()
                                    ), actorName
                                )

                            val future = CompletableFuture<SubscriptionRegistrationResult>()
                            ask(subscriptionActor, RegisterSubscription(pollInterval), 3_000)
                                .onComplete(complete(future), actorSystem.dispatcher)
                            val result = future.get()
                            when {
                                result.created -> Response.ok().entity(result).build()
                                else -> Response.status(Response.Status.BAD_REQUEST).entity(result).build()
                            }
                        } catch (thr: Throwable) {
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(thr.message ?: "internal error message")
                                .build()
                        }
                    }
                    else -> Response.status(Response.Status.BAD_REQUEST).entity(setUpValidation).build()
                }
            }.get()
    }

    @DeleteMapping(value = ["/twitter/{subscriberId}"])
    fun deleteAllSubscriptions(
        @PathVariable subscriberId: String
    ): Response {
        actorSystem.actorSelection("user/$TWITTER_SUBSCRIPTION_ACTOR_NAME_PREFIX*$subscriberId")
            .tell(Shutdown, ActorRef.noSender())
        return Response.status(Response.Status.ACCEPTED)
            .entity("Cancellation of subscriptions for subscriber with id '$subscriberId' accepted")
            .build()
    }

    @DeleteMapping(value = ["/twitter/{subscriberId}/{twitterUserName}"])
    fun deleteSubscription(
        @PathVariable subscriberId: String,
        @PathVariable twitterUserName: String
    ): Response {
        actorSystem.actorSelection("user/$TWITTER_SUBSCRIPTION_ACTOR_NAME_PREFIX-$twitterUserName-$subscriberId")
            .tell(Shutdown, ActorRef.noSender())
        return Response.status(Response.Status.ACCEPTED)
            .entity("Cancellation of subscription '$twitterUserName' for subscriber with id '$subscriberId' accepted")
            .build()
    }

    @PostMapping(value = ["twitter/demo-result/queue/{queueId}"])
    fun getLastTweets(
        @PathVariable queueId: String
    ): Response = Response.ok().entity(DemoMessagingService.getLastPosts(queueId)).build()

    fun complete(futureResult: CompletableFuture<SubscriptionRegistrationResult>) = { result: Any ->
        try {
            when (result) {
                is Success<*> -> futureResult.complete(result.get() as SubscriptionRegistrationResult)
                is Failure<*> -> futureResult.completeExceptionally(result.exception())
                else -> futureResult.completeExceptionally(IllegalArgumentException("Unknown response type: $result"))
            }
        } catch (throwable: Throwable) {
            futureResult.completeExceptionally(throwable)
        }
    }
}

private fun String.toDuration(): PollInterval = when {
    this.contains("S", true) -> PollInterval(this.replace("S", "", true).toLong(), TimeUnit.SECONDS)
    this.contains("M", true) -> PollInterval(this.replace("M", "", true).toLong(), TimeUnit.MINUTES)
    else -> PollInterval(this.replace("H", "", true).toLong(), TimeUnit.HOURS)
}


data class SubscriptionRequest(
    @field:NotEmpty(message = "Twitter user name is mandatory")
    val twitterUserName: String? = null,
    @field:NotEmpty(message = "Subscriber id is mandatory")
    val subscriberId: String? = null,
    @field:Pattern(regexp = "\\d+[SMHsmh]", message = "Update interval should follow pattern 'd+[SMHsmh]'")
    val updateInterval: String? = null
)
