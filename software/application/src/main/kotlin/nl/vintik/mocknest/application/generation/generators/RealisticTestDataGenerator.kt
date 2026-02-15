package nl.vintik.mocknest.application.generation.generators

import nl.vintik.mocknest.application.generation.interfaces.TestDataGeneratorInterface
import nl.vintik.mocknest.domain.generation.JsonSchema
import nl.vintik.mocknest.domain.generation.JsonSchemaType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

/**
 * Generates realistic test data based on JSON schemas.
 * Creates believable sample data that follows schema constraints.
 */
class RealisticTestDataGenerator : TestDataGeneratorInterface {
    
    private val random = Random.Default
    
    // Sample data pools for realistic generation
    private val firstNames = listOf("John", "Jane", "Michael", "Sarah", "David", "Emily", "Robert", "Lisa", "James", "Maria")
    private val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez")
    private val companies = listOf("Acme Corp", "TechStart Inc", "Global Solutions", "Innovation Labs", "Digital Dynamics", "Future Systems")
    private val domains = listOf("example.com", "test.org", "sample.net", "demo.io", "mock.dev")
    private val cities = listOf("New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio", "San Diego", "Dallas", "San Jose")
    private val countries = listOf("United States", "Canada", "United Kingdom", "Germany", "France", "Australia", "Japan", "Brazil", "India", "Mexico")
    
    override suspend fun generateForSchema(schema: JsonSchema): Any {
        // Use example if available
        schema.example?.let { return it }
        
        return when (schema.type) {
            JsonSchemaType.STRING -> generateString(schema)
            JsonSchemaType.NUMBER -> generateNumber(schema)
            JsonSchemaType.INTEGER -> generateInteger(schema)
            JsonSchemaType.BOOLEAN -> generateBoolean()
            JsonSchemaType.ARRAY -> generateArray(schema.items ?: JsonSchema(JsonSchemaType.STRING))
            JsonSchemaType.OBJECT -> generateObject(schema.properties, schema.required)
            JsonSchemaType.NULL -> "null"
        }
    }
    
    override suspend fun generatePrimitive(type: JsonSchemaType, format: String?): Any {
        return when (type) {
            JsonSchemaType.STRING -> generateStringByFormat(format)
            JsonSchemaType.NUMBER -> generateNumber(null)
            JsonSchemaType.INTEGER -> generateInteger(null)
            JsonSchemaType.BOOLEAN -> generateBoolean()
            JsonSchemaType.NULL -> "null"
            else -> generateStringByFormat(format)
        }
    }
    
    override suspend fun generateObject(properties: Map<String, JsonSchema>, required: List<String>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Generate required properties
        required.forEach { fieldName ->
            properties[fieldName]?.let { schema ->
                result[fieldName] = generateForSchema(schema)
            }
        }
        
        // Generate some optional properties (50% chance each)
        properties.keys.filter { it !in required }.forEach { fieldName ->
            if (random.nextBoolean()) {
                properties[fieldName]?.let { schema ->
                    result[fieldName] = generateForSchema(schema)
                }
            }
        }
        
        // If no properties defined, generate some common fields
        if (properties.isEmpty()) {
            result["id"] = random.nextInt(1, 10000)
            result["name"] = "${firstNames.random()} ${lastNames.random()}"
            result["created_at"] = Instant.now().toString()
        }
        
        return result
    }
    
    override suspend fun generateArray(itemSchema: JsonSchema, minItems: Int, maxItems: Int): List<Any> {
        val size = random.nextInt(minItems, maxItems + 1)
        return (1..size).map { generateForSchema(itemSchema) }
    }
    
    private fun generateString(schema: JsonSchema): String {
        // Check for enum values first
        if (schema.enum.isNotEmpty()) {
            return schema.enum.random().toString()
        }
        
        // Generate based on format
        val generated = generateStringByFormat(schema.format)
        
        // Apply length constraints
        return applyStringConstraints(generated, schema)
    }
    
    private fun generateStringByFormat(format: String?): String {
        return when (format?.lowercase()) {
            "email" -> "${firstNames.random().lowercase()}.${lastNames.random().lowercase()}@${domains.random()}"
            "uri", "url" -> "https://${domains.random()}/api/v1/resource/${random.nextInt(1, 1000)}"
            "uuid" -> UUID.randomUUID().toString()
            "date" -> LocalDate.now().minusDays(random.nextLong(0, 365)).format(DateTimeFormatter.ISO_LOCAL_DATE)
            "date-time" -> Instant.now().minusSeconds(random.nextLong(0, 86400 * 30)).toString()
            "time" -> "${random.nextInt(0, 24).toString().padStart(2, '0')}:${random.nextInt(0, 60).toString().padStart(2, '0')}:${random.nextInt(0, 60).toString().padStart(2, '0')}"
            "password" -> "SecurePass123!"
            "byte" -> Base64.getEncoder().encodeToString("sample data".toByteArray())
            "binary" -> Base64.getEncoder().encodeToString(ByteArray(16) { random.nextInt(256).toByte() })
            "phone" -> "+1-${random.nextInt(100, 999)}-${random.nextInt(100, 999)}-${random.nextInt(1000, 9999)}"
            "ipv4" -> "${random.nextInt(1, 255)}.${random.nextInt(0, 255)}.${random.nextInt(0, 255)}.${random.nextInt(1, 255)}"
            "hostname" -> "server-${random.nextInt(1, 100)}.${domains.random()}"
            else -> generateRealisticString()
        }
    }
    
    private fun generateRealisticString(): String {
        val stringTypes = listOf(
            { "${firstNames.random()} ${lastNames.random()}" }, // Name
            { companies.random() }, // Company
            { cities.random() }, // City
            { countries.random() }, // Country
            { "Sample description for testing purposes" }, // Description
            { "ACTIVE" }, // Status
            { "REF-${random.nextInt(10000, 99999)}" }, // Reference
            { "v${random.nextInt(1, 10)}.${random.nextInt(0, 10)}.${random.nextInt(0, 10)}" } // Version
        )
        
        return stringTypes.random().invoke()
    }
    
    private fun applyStringConstraints(value: String, schema: JsonSchema): String {
        var result = value
        
        // Apply pattern if specified (simplified)
        schema.pattern?.let { pattern ->
            if (pattern.contains("\\d+")) {
                result = "ID-${random.nextInt(1000, 9999)}"
            }
        }
        
        // Apply length constraints
        schema.minLength?.let { minLength ->
            if (result.length < minLength) {
                result = result.padEnd(minLength, 'X')
            }
        }
        
        schema.maxLength?.let { maxLength ->
            if (result.length > maxLength) {
                result = result.take(maxLength)
            }
        }
        
        return result
    }
    
    private fun generateNumber(schema: JsonSchema?): Double {
        val min = schema?.minimum?.toDouble() ?: 0.0
        val max = schema?.maximum?.toDouble() ?: 1000.0
        
        return min + (max - min) * random.nextDouble()
    }
    
    private fun generateInteger(schema: JsonSchema?): Int {
        val min = schema?.minimum?.toInt() ?: 1
        val max = schema?.maximum?.toInt() ?: 10000
        
        return random.nextInt(min, max + 1)
    }
    
    private fun generateBoolean(): Boolean {
        return random.nextBoolean()
    }
}