package me.contrapost.poststreamerdemosc.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.event.Logging
import akka.japi.pf.ReceiveBuilder
import me.contrapost.poststreamerdemosc.demomessagingservice.DemoMessagingService
import me.contrapost.poststreamerdemosc.models.Post
import java.util.*

class PostPublisherDemoActor(private val userName: String, private val subscriberId: String) : AbstractActor() {

    private val queueId = UUID.randomUUID().toString()
    private val logger = Logging.getLogger(context.system, this)

    companion object {

        fun props(userName: String, subscriberId: String): Props = Props.create(PostPublisherDemoActor::class.java) {
            PostPublisherDemoActor(userName, subscriberId)
        }
    }

    override fun preStart() {
        logger.info("Starting post publisher actor for subscriber with id '$subscriberId', user name: '$userName'")
        super.preStart()
    }

    override fun postStop() {
        super.postStop()
        logger.info("Stopped post publisher actor for subscriber with id '$subscriberId', user name: '$userName'")
    }

    override fun createReceive(): Receive = ReceiveBuilder()
        .match(CreateQueue::class.java, ::createQueue)
        .match(AddToQueue::class.java, ::addToQueue)
        .build()

    private fun createQueue(createQueueMsg: CreateQueue) {
        DemoMessagingService.service[queueId] = mutableListOf()
        logger.info("Created queue with id '$queueId' for subscriber with id '$subscriberId', user name: '$userName'")
        sender.tell(QueueCreationConfirmation(queueId), self)
    }

    private fun destroyQueue(destroyMessage: DestroyQueue) {
        DemoMessagingService.service.remove(queueId)
    }

    private fun addToQueue(addToQueueMsg: AddToQueue) {
        DemoMessagingService.service[queueId]!!.addAll(addToQueueMsg.posts)
        logger.info("Added new posts to queue with id '$queueId': Queue content: ${DemoMessagingService.service[queueId]}")
    }
}

data class AddToQueue(
    val posts: List<Post>
)

object CreateQueue

object DestroyQueue

data class QueueCreationConfirmation(
    val queueId: String
)
