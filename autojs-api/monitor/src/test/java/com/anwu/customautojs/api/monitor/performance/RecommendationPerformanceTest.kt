package com.anwu.customautojs.api.monitor.performance

import android.content.Context
import android.content.SharedPreferences
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
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Performance tests for the recommendation system
 * 
 * This test suite measures the performance characteristics of the recommendation
 * system under various conditions and workloads.
 */
@RunWith(MockitoJUnitRunner::class)
class RecommendationPerformanceTest {

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
    
    private lateinit var monitorModule: MonitorModule
    private lateinit var capabilityDetector: DeviceCapabilityDetector
    private lateinit var recommendationEngine: BenchmarkRecommendationEngine
    
    // Configuration
    private val iterationCount = TestConfig.getValue("performanceTest", "iterations", 100)
    private val warmupCount = TestConfig.getValue("performanceTest", "warmupIterations", 10)
    private val recommendationCount = TestConfig.getValue("performanceTest", "recommendationCount", 10)
    
    @Before
    fun setup() {
        // Setup SharedPreferences mocks
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        
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
        `when`(mockRhinoContext.newArray(eq(mockScriptable), anyInt())).thenReturn(mock(Scriptable::class.java))
        
        // Mock populateRecommendations to avoid complex JS object creation
        doNothing().`when`(monitorModule).populateRecommendations(any(), any(), any(), any())
    }
    
    /**
     * Test the performance of generating basic recommendations
     */
    @Test
    fun `measure basic recommendation generation performance`() {
        // Prepare test data
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 4L * 1024 * 1024 * 1024, // 4GB
            availableRam = 1L * 1024 * 1024 * 1024, // 1GB
            isLowMemory = false,
            percentAvailable = 25
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 30,
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 35f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024, // 64GB
            availableInternalStorage = 10L * 1024 * 1024 * 1024, // 10GB
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
        
        // Create a real recommendation engine with mocked detector
        val detectorField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        detectorField.isAccessible = true
        detectorField.set(recommendationEngine, capabilityDetector)
        
        // Warm-up runs
        repeat(warmupCount) {
            recommendationEngine.getBasicRecommendations()
        }
        
        // Measure total execution time
        val durations = mutableListOf<Long>()
        repeat(iterationCount) {
            val startTime = SystemClock.elapsedRealtimeNanos()
            val recommendations = recommendationEngine.getBasicRecommendations()
            val endTime = SystemClock.elapsedRealtimeNanos()
            
            durations.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))
            
            // Verify we got recommendations
            assert(recommendations.isNotEmpty()) { "Expected non-empty recommendations list" }
        }
        
        // Calculate and print statistics
        val averageTimeMs = durations.average() / 1000.0
        val minTimeMs = durations.minOrNull()!! / 1000.0
        val maxTimeMs = durations.maxOrNull()!! / 1000.0
        val p95TimeMs = durations.sorted()[((iterationCount * 0.95).toInt() - 1).coerceAtLeast(0)] / 1000.0
        
        println("Basic recommendation generation performance ($iterationCount iterations):")
        println("  Average: ${"%.2f".format(averageTimeMs)} ms")
        println("  Min: ${"%.2f".format(minTimeMs)} ms")
        println("  Max: ${"%.2f".format(maxTimeMs)} ms")
        println("  P95: ${"%.2f".format(p95TimeMs)} ms")
    }
    
    /**
     * Test the performance of generating benchmark-based recommendations
     */
    @Test
    fun `measure benchmark-based recommendation generation performance`() {
        // Prepare test data
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 6000,
            memoryScore = 5500,
            combinedScore = 5800,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Same device state as previous test
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 4L * 1024 * 1024 * 1024,
            availableRam = 1L * 1024 * 1024 * 1024,
            isLowMemory = false,
            percentAvailable = 25
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 30,
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 35f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024,
            availableInternalStorage = 10L * 1024 * 1024 * 1024,
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
        
        // Create a real recommendation engine with mocked detector
        val detectorField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        detectorField.isAccessible = true
        detectorField.set(recommendationEngine, capabilityDetector)
        
        // Warm-up runs
        repeat(warmupCount) {
            recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
        }
        
        // Measure execution time
        val durations = mutableListOf<Long>()
        repeat(iterationCount) {
            val startTime = SystemClock.elapsedRealtimeNanos()
            val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
            val endTime = SystemClock.elapsedRealtimeNanos()
            
            durations.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))
            
            // Verify we got recommendations
            assert(recommendations.isNotEmpty()) { "Expected non-empty recommendations list" }
        }
        
        // Calculate and print statistics
        val averageTimeMs = durations.average() / 1000.0
        val minTimeMs = durations.minOrNull()!! / 1000.0
        val maxTimeMs = durations.maxOrNull()!! / 1000.0
        val p95TimeMs = durations.sorted()[((iterationCount * 0.95).toInt() - 1).coerceAtLeast(0)] / 1000.0
        
        println("Benchmark-based recommendation generation performance ($iterationCount iterations):")
        println("  Average: ${"%.2f".format(averageTimeMs)} ms")
        println("  Min: ${"%.2f".format(minTimeMs)} ms")
        println("  Max: ${"%.2f".format(maxTimeMs)} ms")
        println("  P95: ${"%.2f".format(p95TimeMs)} ms")
    }
    
    /**
     * Test the performance of the JavaScript API bridge (getBenchmarkRecommendations)
     */
    @Test
    fun `measure JavaScript API bridge performance`() {
        // Prepare test data
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 6000,
            memoryScore = 5500,
            combinedScore = 5800,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Configure mocks
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        
        // Generate a list of realistic recommendations
        val recommendations = mutableListOf<BenchmarkRecommendationEngine.Recommendation>()
        for (i in 1..recommendationCount) {
            val type = when (i % 5) {
                0 -> BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL
                1 -> BenchmarkRecommendationEngine.RECOMMENDATION_PERFORMANCE
                2 -> BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY
                3 -> BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY
                else -> BenchmarkRecommendationEngine.RECOMMENDATION_STORAGE
            }
            
            val importance = when (i % 3) {
                0 -> BenchmarkRecommendationEngine.IMPORTANCE_HIGH
                1 -> BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
                else -> BenchmarkRecommendationEngine.IMPORTANCE_LOW
            }
            
            val hasAction = i % 2 == 0
            
            recommendations.add(
                BenchmarkRecommendationEngine.Recommendation(
                    id = "test_rec_$i",
                    type = type,
                    title = "Test Recommendation $i",
                    description = "This is a test recommendation for performance testing",
                    importance = importance,
                    action = if (hasAction) "android.settings.DEVICE_INFO_SETTINGS" else null
                )
            )
        }
        
        // Mock the recommendation engine
        doReturn(recommendations).`when`(recommendationEngine).getRecommendationsFromBenchmark(benchmarkResults)
        
        // Warm-up runs
        repeat(warmupCount) {
            monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
        }
        
        // Measure execution time
        val durations = mutableListOf<Long>()
        repeat(iterationCount) {
            val duration = measureTimeMillis {
                monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
            }
            durations.add(duration)
        }
        
        // Calculate and print statistics
        val averageTimeMs = durations.average()
        val minTimeMs = durations.minOrNull()!!
        val maxTimeMs = durations.maxOrNull()!!
        val p95TimeMs = durations.sorted()[((iterationCount * 0.95).toInt() - 1).coerceAtLeast(0)]
        
        println("JavaScript API bridge performance ($iterationCount iterations):")
        println("  Average: ${"%.2f".format(averageTimeMs)} ms")
        println("  Min: $minTimeMs ms")
        println("  Max: $maxTimeMs ms")
        println("  P95: $p95TimeMs ms")
    }
    
    /**
     * Test the performance impact of increasing number of recommendations
     */
    @Test
    fun `measure performance with increasing number of recommendations`() {
        // Configure the test parameters
        val recommendationCounts = listOf(5, 10, 20, 50, 100)
        
        // Configure mocks
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 6000,
            memoryScore = 5500,
            combinedScore = 5800,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        
        println("Performance impact of increasing number of recommendations:")
        println("| Count | Generation Time (ms) | JS Bridge Time (ms) |")
        println("|-------|----------------------|---------------------|")
        
        for (count in recommendationCounts) {
            // Generate a list of test recommendations
            val recommendations = mutableListOf<BenchmarkRecommendationEngine.Recommendation>()
            for (i in 1..count) {
                recommendations.add(
                    BenchmarkRecommendationEngine.Recommendation(
                        id = "test_rec_$i",
                        type = BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL,
                        title = "Test Recommendation $i",
                        description = "This is a test recommendation for performance testing",
                        importance = BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
                    )
                )
            }
            
            // Mock the recommendation engine
            doReturn(recommendations).`when`(recommendationEngine).getRecommendationsFromBenchmark(benchmarkResults)
            
            // Measure generation time (direct engine call)
            val generationTimes = mutableListOf<Long>()
            repeat(10) {
                val startTime = SystemClock.elapsedRealtimeNanos()
                recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
                val endTime = SystemClock.elapsedRealtimeNanos()
                generationTimes.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))
            }
            val avgGenerationTimeMs = generationTimes.average() / 1000.0
            
            // Measure JavaScript bridge time
            val bridgeTimes = mutableListOf<Long>()
            repeat(10) {
                val duration = measureTimeMillis {
                    monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
                }
                bridgeTimes.add(duration)
            }
            val avgBridgeTimeMs = bridgeTimes.average()
            
            // Print results
            println("| $count | ${"%.2f".format(avgGenerationTimeMs)} | ${"%.2f".format(avgBridgeTimeMs)} |")
        }
    }
    
    /**
     * Test the performance of filtering recommendations by type
     */
    @Test
    fun `measure recommendation filtering performance`() {
        // Generate a large set of test recommendations (100)
        val allRecommendations = mutableListOf<BenchmarkRecommendationEngine.Recommendation>()
        for (i in 1..100) {
            val type = when (i % 5) {
                0 -> BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL
                1 -> BenchmarkRecommendationEngine.RECOMMENDATION_PERFORMANCE
                2 -> BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY
                3 -> BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY
                else -> BenchmarkRecommendationEngine.RECOMMENDATION_STORAGE
            }
            
            val importance = when (i % 3) {
                0 -> BenchmarkRecommendationEngine.IMPORTANCE_HIGH
                1 -> BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
                else -> BenchmarkRecommendationEngine.IMPORTANCE_LOW
            }
            
            allRecommendations.add(
                BenchmarkRecommendationEngine.Recommendation(
                    id = "test_rec_$i",
                    type = type,
                    title = "Test Recommendation $i",
                    description = "This is a test recommendation for performance testing",
                    importance = importance
                )
            )
        }
        
        // Test filtering by different types
        val recommendationTypes = listOf(
            BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL,
            BenchmarkRecommendationEngine.RECOMMENDATION_PERFORMANCE,
            BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY,
            BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY,
            BenchmarkRecommendationEngine.RECOMMENDATION_STORAGE
        )
        
        println("Recommendation filtering performance:")
        println("| Type | Filter Time (μs) | Count | Sort Time (μs) |")
        println("|------|-----------------|-------|----------------|")
        
        for (type in recommendationTypes) {
            // Measure filtering time
            val filterTimes = mutableListOf<Long>()
            var filteredCount = 0
            
            repeat(100) {
                val startTime = SystemClock.elapsedRealtimeNanos()
                val filtered = allRecommendations.filter { it.type == type }
                val endTime = SystemClock.elapsedRealtimeNanos()
                
                filterTimes.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))
                filteredCount = filtered.size
            }
            
            // Measure sorting time (by importance)
            val sortTimes = mutableListOf<Long>()
            
            repeat(100) {
                val filtered = allRecommendations.filter { it.type == type }
                
                val startTime = SystemClock.elapsedRealtimeNanos()
                val sorted = filtered.sortedWith(compareBy { 
                    when (it.importance) {
                        BenchmarkRecommendationEngine.IMPORTANCE_HIGH -> 0
                        BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM -> 1
                        BenchmarkRecommendationEngine.IMPORTANCE_LOW -> 2
                        else -> 3
                    }
                })
                val endTime = SystemClock.elapsedRealtimeNanos()
                
                sortTimes.add(TimeUnit.NANOSECONDS.toMicros(endTime - startTime))
            }
            
            val avgFilterTime = filterTimes.average()
            val avgSortTime = sortTimes.average()
            
            println("| $type | ${"%.2f".format(avgFilterTime)} | $filteredCount | ${"%.2f".format(avgSortTime)} |")
        }
    }
}