package nl.vintik.mocknest.application.runtime.extensions

interface WebhookHttpClientInterface {
    fun send(request: WebhookRequest): WebhookResult
}
