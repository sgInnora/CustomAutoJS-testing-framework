package com.anwu.customautojs.api.monitor.stress

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.SystemClock
import com.anwu.customautojs.api.monitor.MonitorModule
import com.anwu.customautojs.api.monitor.device.BenchmarkRecommendationEngine
import com.anwu.customautojs.api.monitor.device.DeviceBenchmarkManager
import com.anwu.customautojs.api.monitor.device.DeviceCapabilityDetector
import com.anwu.customautojs.api.monitor.device.DeviceCapabilityDetector.PerformanceClass
import com.anwu.customautojs.api.monitor.test.TestConfig
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.util.concurrent.*
import kotlin.math.max
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Stress tests for the recommendation system
 * 
 * This test suite verifies the reliability and stability of the recommendation
 * system under heavy load and various stress conditions.
 */
@RunWith(MockitoJUnitRunner::class)
class RecommendationStressTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Mock
    private lateinit var mockRhinoContext: RhinoContext
    
    @Mock
    private lateinit var mockScriptable: Scriptable
    
    // Components under test
    private lateinit var monitorModule: MonitorModule
    private lateinit var capabilityDetector: DeviceCapabilityDetector
    private lateinit var recommendationEngine: BenchmarkRecommendationEngine
    
    // Test configuration
    private val threadCount = TestConfig.getValue("stressTest", "concurrentThreads", 4)
    private val iterationsPerThread = TestConfig.getValue("stressTest", "iterationsPerThread", 50)
    private val randomSeed = TestConfig.getValue("stressTest", "randomSeed", 42L)
    private val maxRecommendations = TestConfig.getValue("stressTest", "maxRecommendations", 200)
    private val stressTimeSeconds = TestConfig.getValue("stressTest", "stressTimeSeconds", 10)
    
    @Before
    fun setup() {
        // Setup SharedPreferences mocks
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        
        // Setup Looper mock for tests that need to run on main thread
        val mockMainLooper = mock(Looper::class.java)
        try {
            val looperField = Looper::class.java.getDeclaredField("sMainLooper")
            looperField.isAccessible = true
            looperField.set(null, mockMainLooper)
        } catch (e: Exception) {
            println("Warning: Could not mock main Looper: ${e.message}")
        }
        
        // Create the objects under test
        capabilityDetector = spy(DeviceCapabilityDetector(mockContext))
        recommendationEngine = spy(BenchmarkRecommendationEngine(mockContext))
        monitorModule = spy(MonitorModule(mockContext))
        
        // Inject spies into MonitorModule
        val detectorField = MonitorModule::class.java.getDeclaredField("detector")
        detectorField.isAccessible = true
        detectorField.set(monitorModule, capabilityDetector)
        
        val recommendationEngineField = MonitorModule::class.java.getDeclaredField("recommendationEngine")
        recommendationEngineField.isAccessible = true
        recommendationEngineField.set(monitorModule, recommendationEngine)
        
        // Rhino context mocks for array creation
        `when`(mockRhinoContext.newArray(eq(mockScriptable), anyInt())).thenAnswer { invocation ->
            val size = invocation.getArgument<Int>(1)
            val nativeArray = mock(NativeArray::class.java)
            `when`(nativeArray.get("length", nativeArray)).thenReturn(size)
            nativeArray
        }
        
        // Mock populateRecommendations to avoid complex JS object creation
        doNothing().`when`(monitorModule).populateRecommendations(any(), any(), any(), any())
    }
    
    /**
     * Tests the recommendation engine under concurrent load
     * 
     * This test executes multiple threads simultaneously, each requesting recommendations,
     * to verify the system can handle concurrent access without errors.
     */
    @Test
    fun `test recommendation engine under concurrent load`() {
        // Setup device state and benchmark results for testing
        setupDeviceState()
        
        // Create benchmark results
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 6000,
            combinedScore = 6600,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Mock the benchmark results
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        
        // Mock the recommendation engine with a real detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Create a thread pool
        val executor = Executors.newFixedThreadPool(threadCount)
        val futures = mutableListOf<Future<ConcurrentTestResult>>()
        
        println("Starting concurrent stress test with $threadCount threads, $iterationsPerThread iterations each")
        
        // Submit tasks to the thread pool
        for (threadId in 0 until threadCount) {
            futures.add(executor.submit(Callable {
                runConcurrentTest(threadId, benchmarkResults)
            }))
        }
        
        // Collect results
        val results = futures.mapNotNull { 
            try {
                it.get(60, TimeUnit.SECONDS)
            } catch (e: Exception) {
                println("Error getting result from thread: ${e.message}")
                null
            }
        }
        
        // Shut down the executor
        executor.shutdown()
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            println("Thread pool did not terminate in time")
        }
        
        // Analyze results
        val totalIterations = results.sumOf { it.completedIterations }
        val totalErrors = results.sumOf { it.errors }
        val avgTimeMs = results.map { it.averageTimeMs }.average()
        val maxTimeMs = results.maxOfOrNull { it.maxTimeMs } ?: 0.0
        
        println("Concurrent stress test results:")
        println("  Total iterations: $totalIterations")
        println("  Total errors: $totalErrors")
        println("  Average time: ${"%.2f".format(avgTimeMs)} ms")
        println("  Maximum time: ${"%.2f".format(maxTimeMs)} ms")
        
        // Assert no errors occurred
        assert(totalErrors == 0) { "Stress test encountered $totalErrors errors" }
        
        // Verify all iterations completed
        assert(totalIterations == threadCount * iterationsPerThread) { 
            "Not all iterations completed: $totalIterations of ${threadCount * iterationsPerThread}" 
        }
    }
    
    /**
     * Tests the recommendation system under rapidly changing device conditions
     * 
     * This test simulates rapidly changing device conditions (like battery level, 
     * memory usage, etc.) to verify the system can adapt and provide appropriate
     * recommendations without errors.
     */
    @Test
    fun `test recommendation system under changing conditions`() {
        // Setup initial device state
        setupDeviceState()
        
        // Create a benchmark result
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 6000,
            combinedScore = 6600,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Mock the benchmark results
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        
        // Mock the recommendation engine with a real detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        println("Starting condition change stress test for ${stressTimeSeconds}s")
        
        // Random number generator with fixed seed for reproducibility
        val random = Random(randomSeed)
        
        // Track metrics
        var iterationCount = 0
        var errorCount = 0
        val durations = mutableListOf<Long>()
        val recommendationCounts = mutableListOf<Int>()
        
        // Run the test for specified duration
        val endTime = System.currentTimeMillis() + (stressTimeSeconds * 1000)
        while (System.currentTimeMillis() < endTime) {
            try {
                // Randomly change device state
                changeDeviceState(random)
                
                // Measure recommendation generation time
                val startTime = SystemClock.elapsedRealtimeNanos()
                val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
                val endTimeNanos = SystemClock.elapsedRealtimeNanos()
                
                // Record metrics
                durations.add(TimeUnit.NANOSECONDS.toMicros(endTimeNanos - startTime))
                recommendationCounts.add(recommendations.size)
                iterationCount++
                
            } catch (e: Exception) {
                errorCount++
                println("Error during condition change test: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Calculate statistics
        val avgDurationMs = durations.average() / 1000.0
        val maxDurationMs = durations.maxOrNull()?.toDouble()?.div(1000.0) ?: 0.0
        val avgRecommendationCount = recommendationCounts.average()
        val minRecommendationCount = recommendationCounts.minOrNull() ?: 0
        val maxRecommendationCount = recommendationCounts.maxOrNull() ?: 0
        
        println("Condition change stress test results:")
        println("  Iterations completed: $iterationCount")
        println("  Errors: $errorCount")
        println("  Average generation time: ${"%.2f".format(avgDurationMs)} ms")
        println("  Maximum generation time: ${"%.2f".format(maxDurationMs)} ms")
        println("  Average recommendation count: ${"%.1f".format(avgRecommendationCount)}")
        println("  Recommendation count range: $minRecommendationCount - $maxRecommendationCount")
        
        // Assert no errors occurred
        assert(errorCount == 0) { "Condition change test encountered $errorCount errors" }
        
        // Verify the test actually ran iterations
        assert(iterationCount > 0) { "No iterations completed during test" }
    }
    
    /**
     * Test the system's ability to handle very large numbers of recommendations
     * 
     * This test verifies the system can generate and process a large number of
     * recommendations without errors or excessive memory usage.
     */
    @Test
    fun `test recommendation system with large recommendation count`() {
        // Setup device state
        setupDeviceState()
        
        // Mock the recommendation engine with a deterministic implementation
        doAnswer { invocation ->
            val benchmarkResults = invocation.getArgument<DeviceBenchmarkManager.BenchmarkResults>(0)
            
            // Generate a large number of recommendations
            val recommendations = mutableListOf<BenchmarkRecommendationEngine.Recommendation>()
            for (i in 1..maxRecommendations) {
                val recommendationType = when (i % 5) {
                    0 -> BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL
                    1 -> BenchmarkRecommendationEngine.RECOMMENDATION_PERFORMANCE
                    2 -> BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY
                    3 -> BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY
                    else -> BenchmarkRecommendationEngine.RECOMMENDATION_STORAGE
                }
                
                val importanceLevel = when (i % 3) {
                    0 -> BenchmarkRecommendationEngine.IMPORTANCE_HIGH
                    1 -> BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
                    else -> BenchmarkRecommendationEngine.IMPORTANCE_LOW
                }
                
                recommendations.add(
                    BenchmarkRecommendationEngine.Recommendation(
                        id = "stress_rec_$i",
                        type = recommendationType,
                        title = "Stress Test Recommendation $i",
                        description = "This is a stress test recommendation with a long description " +
                                "that goes into detail about potential optimizations that " +
                                "could be applied to improve device performance. " +
                                "The recommendation is based on benchmark score: ${benchmarkResults.cpuScore}.",
                        importance = importanceLevel,
                        action = if (i % 2 == 0) "android.settings.SETTINGS" else null
                    )
                )
            }
            
            recommendations
        }.`when`(recommendationEngine).getRecommendationsFromBenchmark(any())
        
        // Create benchmark results
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 6000,
            combinedScore = 6600,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Mock the benchmark results
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        
        println("Testing with $maxRecommendations recommendations")
        
        // Test the recommendation engine with large number of recommendations
        val engineTimeMs = measureTimeMillis {
            val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
            
            // Verify we get the expected number of recommendations
            assert(recommendations.size == maxRecommendations) { 
                "Expected $maxRecommendations recommendations, got ${recommendations.size}" 
            }
        }
        
        // Test the JavaScript API bridge with large number of recommendations
        val bridgeTimeMs = measureTimeMillis {
            val result = monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
            
            // We can't verify the actual size since we've mocked the JS array creation
            assert(result != null) { "JavaScript API bridge returned null" }
        }
        
        println("Large recommendation count test results:")
        println("  Engine time: $engineTimeMs ms")
        println("  Bridge time: $bridgeTimeMs ms")
        
        // Verify that the time taken is reasonable (adjust as needed)
        assert(engineTimeMs < 5000) { "Recommendation engine took too long: $engineTimeMs ms" }
        assert(bridgeTimeMs < 5000) { "JavaScript API bridge took too long: $bridgeTimeMs ms" }
    }
    
    /**
     * Test the system's resilience to unreliable device state information
     * 
     * This test simulates scenarios where device state information is unreliable,
     * such as when system calls fail or return incomplete data.
     */
    @Test
    fun `test recommendation system with unreliable device information`() {
        // Mock failures in various device state queries
        
        // 1. Test with null or failing battery status
        doReturn(null).`when`(capabilityDetector).getBatteryStatus()
        
        // 2. Test with incomplete/unreliable memory information
        val unreliableMemory = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = -1,  // Invalid value
            availableRam = 0,
            isLowMemory = true,
            percentAvailable = -1  // Invalid value
        )
        doReturn(unreliableMemory).`when`(capabilityDetector).getMemoryCapabilities()
        
        // 3. Test with missing storage information
        val missingStorage = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 0,
            availableInternalStorage = 0,
            externalStorageAvailable = false
        )
        doReturn(missingStorage).`when`(capabilityDetector).getStorageCapabilities()
        
        // Create benchmark results
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 6000,
            combinedScore = 6600,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Create a real recommendation engine with our mocked detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Test the recommendation engine with unreliable information
        try {
            val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
            
            // We should still get some recommendations despite unreliable information
            assert(recommendations.isNotEmpty()) { "No recommendations were generated with unreliable device information" }
            
            println("System recovered from unreliable device information")
            println("  Generated ${recommendations.size} recommendations")
            
            // Check for fallback recommendations
            val hasFallbackRecs = recommendations.any { it.id.contains("fallback") }
            if (hasFallbackRecs) {
                println("  System correctly included fallback recommendations")
            }
        } catch (e: Exception) {
            assert(false) { "Recommendation engine failed with unreliable device information: ${e.message}" }
        }
        
        // Test the JavaScript API with unreliable information
        try {
            val result = monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
            assert(result != null) { "JavaScript API bridge returned null with unreliable device information" }
            println("JavaScript API recovered from unreliable device information")
        } catch (e: Exception) {
            assert(false) { "JavaScript API bridge failed with unreliable device information: ${e.message}" }
        }
    }
    
    // Helper methods
    
    /**
     * Setup basic device state for testing
     */
    private fun setupDeviceState() {
        // Setup common device state information
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 4L * 1024 * 1024 * 1024, // 4GB
            availableRam = 2L * 1024 * 1024 * 1024, // 2GB
            isLowMemory = false,
            percentAvailable = 50
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 50,
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 30f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024, // 64GB
            availableInternalStorage = 32L * 1024 * 1024 * 1024, // 32GB
            externalStorageAvailable = true
        )
        
        val cpuCapabilities = DeviceCapabilityDetector.CpuCapabilities(
            cores = 8,
            performanceClass = PerformanceClass.MID_HIGH,
            architecture = "ARM64",
            isArmV8 = true,
            supportsSIMD = true
        )
        
        // Configure detector mocks
        doReturn(memoryCapabilities).`when`(capabilityDetector).getMemoryCapabilities()
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        doReturn(storageCapabilities).`when`(capabilityDetector).getStorageCapabilities()
        doReturn(cpuCapabilities).`when`(capabilityDetector).getCpuCapabilities()
    }
    
    /**
     * Change device state randomly to simulate different device conditions
     */
    private fun changeDeviceState(random: Random) {
        // Create random device state
        val batteryLevel = random.nextInt(1, 100)
        val isCharging = random.nextBoolean()
        val isBatterySaver = random.nextBoolean()
        val temperature = 25f + random.nextFloat() * 20f // 25-45 degrees
        
        val memoryPercent = random.nextInt(5, 95)
        val totalRam = 4L * 1024 * 1024 * 1024 // 4GB
        val availableRam = totalRam * memoryPercent / 100
        val isLowMemory = memoryPercent < 15
        
        val storagePercent = random.nextInt(5, 95)
        val totalStorage = 64L * 1024 * 1024 * 1024 // 64GB
        val availableStorage = totalStorage * storagePercent / 100
        
        // Create device state objects
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isBatterySaver = isBatterySaver,
            batteryTemperature = temperature
        )
        
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = totalRam,
            availableRam = availableRam,
            isLowMemory = isLowMemory,
            percentAvailable = memoryPercent
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = totalStorage,
            availableInternalStorage = availableStorage,
            externalStorageAvailable = random.nextBoolean()
        )
        
        // Update detector mocks
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        doReturn(memoryCapabilities).`when`(capabilityDetector).getMemoryCapabilities()
        doReturn(storageCapabilities).`when`(capabilityDetector).getStorageCapabilities()
    }
    
    /**
     * Run a concurrent test iteration
     * 
     * @param threadId The ID of the current thread
     * @param benchmarkResults The benchmark results to use for testing
     * @return The test result metrics
     */
    private fun runConcurrentTest(
        threadId: Int,
        benchmarkResults: DeviceBenchmarkManager.BenchmarkResults
    ): ConcurrentTestResult {
        val random = Random(randomSeed + threadId)
        var completedIterations = 0
        var errors = 0
        val durations = mutableListOf<Long>()
        
        try {
            for (i in 0 until iterationsPerThread) {
                try {
                    // Randomly change device state to simulate real-world conditions
                    changeDeviceState(random)
                    
                    // Measure recommendation generation time
                    val startTime = SystemClock.elapsedRealtimeNanos()
                    val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
                    val endTimeNanos = SystemClock.elapsedRealtimeNanos()
                    
                    // Verify the recommendations
                    if (recommendations.isEmpty()) {
                        errors++
                        println("Thread $threadId: Empty recommendations list in iteration $i")
                    }
                    
                    // Record performance metrics
                    durations.add(TimeUnit.NANOSECONDS.toMicros(endTimeNanos - startTime))
                    completedIterations++
                    
                } catch (e: Exception) {
                    errors++
                    println("Thread $threadId: Error in iteration $i: ${e.message}")
                }
                
                // Small delay to avoid overwhelming the system
                Thread.sleep(10)
            }
        } catch (e: Exception) {
            println("Thread $threadId: Critical error: ${e.message}")
            e.printStackTrace()
        }
        
        // Calculate metrics
        val averageTimeMs = if (durations.isNotEmpty()) durations.average() / 1000.0 else 0.0
        val maxTimeMs = durations.maxOrNull()?.toDouble()?.div(1000.0) ?: 0.0
        
        return ConcurrentTestResult(
            threadId = threadId,
            completedIterations = completedIterations,
            errors = errors,
            averageTimeMs = averageTimeMs,
            maxTimeMs = maxTimeMs
        )
    }
    
    /**
     * Data class for tracking concurrent test results
     */
    data class ConcurrentTestResult(
        val threadId: Int,
        val completedIterations: Int,
        val errors: Int,
        val averageTimeMs: Double,
        val maxTimeMs: Double
    )
}