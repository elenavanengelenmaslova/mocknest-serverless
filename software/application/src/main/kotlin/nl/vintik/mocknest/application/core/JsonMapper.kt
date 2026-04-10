package nl.vintik.mocknest.application.core

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.HttpMethod

val mapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .registerModule(Jdk8Module())
    .registerModule(SimpleModule().apply {
        addSerializer(HttpMethod::class.java, object : JsonSerializer<HttpMethod>() {
            override fun serialize(value: HttpMethod, gen: JsonGenerator, serializers: SerializerProvider) {
                gen.writeString(value.name())
            }
        })
        addDeserializer(HttpMethod::class.java, object : JsonDeserializer<HttpMethod>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HttpMethod {
                return HttpMethod.valueOf(p.text.uppercase())
            }
        })
    })
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
