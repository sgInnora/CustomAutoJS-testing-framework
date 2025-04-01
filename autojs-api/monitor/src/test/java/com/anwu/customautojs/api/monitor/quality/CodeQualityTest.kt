package com.anwu.customautojs.api.monitor.quality

import com.anwu.customautojs.api.monitor.device.BenchmarkRecommendationEngine
import com.anwu.customautojs.api.monitor.device.DeviceBenchmarkManager
import com.anwu.customautojs.api.monitor.device.DeviceCapabilityDetector
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import java.io.File

/**
 * Tests to verify code quality standards in the benchmark recommendation system
 * 
 * These tests check for:
 * 1. Proper documentation (KDoc)
 * 2. Consistent naming conventions
 * 3. Method length and complexity
 * 4. Null safety
 * 5. Error handling
 */
class CodeQualityTest {

    /**
     * Test that classes have proper KDoc documentation
     */
    @Test
    fun `verify classes have proper documentation`() {
        val classesToCheck = listOf(
            BenchmarkRecommendationEngine::class,
            DeviceBenchmarkManager::class,
            DeviceCapabilityDetector::class
        )
        
        val undocumentedClasses = classesToCheck.filter { cls ->
            val sourceFile = findSourceFile(cls)
            if (sourceFile != null) {
                val content = sourceFile.readText()
                !content.contains("/**") || !content.contains("*/")
            } else {
                true // If we can't find the source file, count it as undocumented
            }
        }
        
        assert(undocumentedClasses.isEmpty()) {
            "The following classes lack proper KDoc documentation: ${undocumentedClasses.joinToString { it.simpleName ?: "Unknown" }}"
        }
    }
    
    /**
     * Test that all public methods in the recommendation system have KDoc documentation
     */
    @Test
    fun `verify methods have proper documentation`() {
        val classesToCheck = listOf(
            BenchmarkRecommendationEngine::class
        )
        
        val undocumentedMethods = mutableListOf<String>()
        
        classesToCheck.forEach { cls ->
            val sourceFile = findSourceFile(cls)
            if (sourceFile != null) {
                val content = sourceFile.readText()
                val contentLines = content.lines()
                
                // Check each public method
                cls.declaredFunctions.filter { !it.name.startsWith("_") && !it.isPrivate() }.forEach { function ->
                    val methodName = function.name
                    val methodLine = contentLines.indexOfFirst { it.contains("fun $methodName") }
                    
                    if (methodLine > 0) {
                        // Check if there's KDoc before the method
                        var foundKDoc = false
                        for (i in (methodLine - 1) downTo (methodLine - 10).coerceAtLeast(0)) {
                            if (contentLines[i].trim().startsWith("/**")) {
                                foundKDoc = true
                                break
                            }
                            if (!contentLines[i].trim().startsWith("*") && contentLines[i].trim().isNotEmpty()) {
                                break
                            }
                        }
                        
                        if (!foundKDoc) {
                            undocumentedMethods.add("${cls.simpleName}.${methodName}")
                        }
                    }
                }
            }
        }
        
        assert(undocumentedMethods.isEmpty()) {
            "The following methods lack proper KDoc documentation: $undocumentedMethods"
        }
    }
    
    /**
     * Test that methods don't exceed a reasonable length
     */
    @Test
    fun `verify methods are not too long`() {
        val classesToCheck = listOf(
            BenchmarkRecommendationEngine::class
        )
        
        val maxLines = 100 // Maximum allowed method length
        val methodsTooLong = mutableListOf<String>()
        
        classesToCheck.forEach { cls ->
            val sourceFile = findSourceFile(cls)
            if (sourceFile != null) {
                val content = sourceFile.readText()
                val contentLines = content.lines()
                
                cls.declaredFunctions.filter { !it.isPrivate() }.forEach { function ->
                    val methodName = function.name
                    val methodLine = contentLines.indexOfFirst { it.contains("fun $methodName") }
                    
                    if (methodLine > 0) {
                        var braceCount = 0
                        var methodEndLine = methodLine
                        for (i in methodLine until contentLines.size) {
                            val line = contentLines[i]
                            braceCount += line.count { it == '{' }
                            braceCount -= line.count { it == '}' }
                            
                            if (braceCount == 0 && i > methodLine) {
                                methodEndLine = i
                                break
                            }
                        }
                        
                        val methodLength = methodEndLine - methodLine + 1
                        if (methodLength > maxLines) {
                            methodsTooLong.add("${cls.simpleName}.${methodName} (${methodLength} lines)")
                        }
                    }
                }
            }
        }
        
        assert(methodsTooLong.isEmpty()) {
            "The following methods exceed the maximum length of $maxLines lines: $methodsTooLong"
        }
    }
    
    /**
     * Test that error handling is present in key methods
     */
    @Test
    fun `verify error handling in key methods`() {
        val classesToCheck = listOf(
            BenchmarkRecommendationEngine::class
        )
        
        val methodsWithoutErrorHandling = mutableListOf<String>()
        
        // Key methods that should have error handling
        val keyMethods = listOf(
            "getRecommendationsFromBenchmark",
            "getBasicRecommendations"
        )
        
        classesToCheck.forEach { cls ->
            val sourceFile = findSourceFile(cls)
            if (sourceFile != null) {
                val content = sourceFile.readText()
                val contentLines = content.lines()
                
                keyMethods.forEach { methodName ->
                    // Find the method in the source code
                    val methodLine = contentLines.indexOfFirst { it.contains("fun $methodName") }
                    
                    if (methodLine > 0) {
                        // Find the method boundaries
                        var braceCount = 0
                        var methodEndLine = methodLine
                        for (i in methodLine until contentLines.size) {
                            val line = contentLines[i]
                            braceCount += line.count { it == '{' }
                            braceCount -= line.count { it == '}' }
                            
                            if (braceCount == 0 && i > methodLine) {
                                methodEndLine = i
                                break
                            }
                        }
                        
                        // Check for try-catch blocks in the method
                        val methodContent = contentLines.subList(methodLine, methodEndLine + 1).joinToString("\n")
                        if (!methodContent.contains("try") || !methodContent.contains("catch")) {
                            methodsWithoutErrorHandling.add("${cls.simpleName}.${methodName}")
                        }
                    }
                }
            }
        }
        
        assert(methodsWithoutErrorHandling.isEmpty()) {
            "The following key methods lack proper error handling (try-catch blocks): $methodsWithoutErrorHandling"
        }
    }
    
    /**
     * Test that mandatory constants are defined in the recommendation engine
     */
    @Test
    fun `verify required constants are defined`() {
        val requiredConstants = listOf(
            "RECOMMENDATION_GENERAL",
            "RECOMMENDATION_PERFORMANCE",
            "RECOMMENDATION_BATTERY",
            "RECOMMENDATION_MEMORY",
            "RECOMMENDATION_STORAGE",
            "IMPORTANCE_HIGH",
            "IMPORTANCE_MEDIUM",
            "IMPORTANCE_LOW"
        )
        
        val missingConstants = requiredConstants.filter { constant ->
            try {
                val field = BenchmarkRecommendationEngine::class.java.getDeclaredField(constant)
                false // Constant exists
            } catch (e: NoSuchFieldException) {
                true // Constant does not exist
            }
        }
        
        assert(missingConstants.isEmpty()) {
            "The following required constants are missing from BenchmarkRecommendationEngine: $missingConstants"
        }
    }
    
    /**
     * Test that the recommendation class has the required fields
     */
    @Test
    fun `verify recommendation class has required fields`() {
        val recommendationClass = BenchmarkRecommendationEngine::class.java.declaredClasses.find { 
            it.simpleName == "Recommendation" 
        }
        
        assert(recommendationClass != null) {
            "Recommendation data class not found in BenchmarkRecommendationEngine"
        }
        
        // Check required fields
        val requiredFields = listOf("id", "type", "title", "description", "importance", "action")
        
        val fields = recommendationClass?.declaredFields?.map { it.name } ?: emptyList()
        val missingFields = requiredFields.filter { !fields.contains(it) }
        
        assert(missingFields.isEmpty()) {
            "The following required fields are missing from the Recommendation class: $missingFields"
        }
    }
    
    // Helper functions
    
    /**
     * Find the source file for a given class
     */
    private fun findSourceFile(cls: KClass<*>): File? {
        val className = cls.simpleName ?: return null
        val packageName = cls.java.`package`.name
        val packagePath = packageName.replace('.', '/')
        
        // Look for the source file in common source directories
        val sourceRoots = listOf(
            "src/main/java",
            "src/main/kotlin",
            "../src/main/java",
            "../src/main/kotlin",
            "../../src/main/java",
            "../../src/main/kotlin"
        )
        
        for (root in sourceRoots) {
            val sourceFile = File("$root/$packagePath/$className.kt")
            if (sourceFile.exists()) {
                return sourceFile
            }
        }
        
        // Try to find source from the project root
        val projectRoot = File("").absoluteFile
        val sourceFiles = projectRoot.walkTopDown()
            .filter { it.isFile && it.name == "$className.kt" && it.path.contains(packagePath) }
            .toList()
        
        return sourceFiles.firstOrNull()
    }
    
    /**
     * Check if a function is private
     */
    private fun KFunction<*>.isPrivate(): Boolean {
        // Simple heuristic: if the name starts with an underscore or contains "private"
        return this.name.startsWith("_") || 
               this.toString().contains("private") ||
               this.toString().contains("PRIVATE")
    }
}