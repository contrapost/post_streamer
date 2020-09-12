package me.contrapost.tweetstreamdemosc.rest

import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import javax.ws.rs.core.Response

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun processValidationError(ex: MethodArgumentNotValidException): Response {
        val message = ex.bindingResult.fieldErrors.map { it.defaultMessage }.joinToString(", ")
        val validatedMessage = when {
            message.isEmpty() -> ex.message
            else -> message
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(validatedMessage).build()
    }
}
