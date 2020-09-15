package me.contrapost.poststreamerdemosc.config

import akka.actor.ActorSystem
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Configuration
class ActorSystemConfig {
    private val actorSystemName = "PostStreamSystem"
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var actorSystem: ActorSystem

    @PostConstruct
    fun init() {
        logger.info("Starting actor system")
        actorSystem = ActorSystem.create(actorSystemName)
        logger.info("Actor system started")
    }

    @PreDestroy
    fun stop() {
        try {
            logger.info("Waiting for actor system to terminate...")
            // CoordinatedShutdown.get(actorSystem).runAll(SpringContextShutdownReason)
            actorSystem.terminate()
            logger.info("Actor system terminated")
        } catch (e: Exception) {
            logger.error("Actor system shutdown failed: $e", e)
        }
    }

    @Bean
    fun actorSystem(): ActorSystem = actorSystem
}
