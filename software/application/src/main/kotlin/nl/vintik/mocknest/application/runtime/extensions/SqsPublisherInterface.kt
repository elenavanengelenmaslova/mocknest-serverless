package nl.vintik.mocknest.application.runtime.extensions

interface SqsPublisherInterface {
    suspend fun publish(queueUrl: String, messageBody: String)
}
