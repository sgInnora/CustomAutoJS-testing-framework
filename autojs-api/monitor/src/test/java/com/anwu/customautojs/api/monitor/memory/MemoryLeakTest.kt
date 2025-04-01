package com.anwu.customautojs.api.monitor.memory

import android.content.Context
import android.content.SharedPreferences
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
import org.mozilla.javascript.Scriptable
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Tests to detect memory leaks in the recommendation system
 * 
 * These tests check for memory leaks by running long-running operations
 * and monitoring memory usage, or by checking for leaked references.
 */
@RunWith(MockitoJUnitRunner::class)
class MemoryLeakTest {

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
    
    // Test configuration
    private val iterationCount = TestConfig.getNestedValue("memoryTests", "leakTests", "iterations", 100)
    private val gcCycles = TestConfig.getNestedValue("memoryTests", "leakTests", "gcCycles", 3)
    private val memoryThresholdPercent = TestConfig.getNestedValue("memoryTests", "leakTests", "memoryThresholdPercent", 10)
    private val pauseForVisualGcMs = TestConfig.getNestedValue("memoryTests", "leakTests", "pauseForVisualGcMs", 100L)
    
    @Before
    fun setup() {
        // Setup SharedPreferences mocks
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        
        // Create mock for populateRecommendations
        doNothing().`when`(mockRhinoContext).sealObject(any(), anyBoolean())
    }
    
    /**
     * Test for memory leaks by repeatedly creating and using recommendation engines
     */
    @Test
    fun `test for memory leaks in recommendation engine`() {
        // Get initial memory state
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        System.gc() // Encourage garbage collection before starting
        Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        
        val initialMemory = memoryMXBean.heapMemoryUsage.used
        println("Initial memory usage: ${initialMemory / (1024 * 1024)} MB")
        
        // List to hold strong references temporarily
        val temporaryHolders = mutableListOf<Any>()
        
        // Create weak references to monitor objects that should be GC'd
        val weakRefs = mutableListOf<WeakReference<BenchmarkRecommendationEngine>>()
        
        // Create and use multiple recommendation engines
        for (i in 1..iterationCount) {
            // Create device state objects
            val detector = DeviceCapabilityDetector(mockContext)
            val recommendationEngine = BenchmarkRecommendationEngine(mockContext)
            
            // Create benchmark results
            val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
                cpuScore = 5000 + (i % 1000),
                memoryScore = 4500 + (i % 1500),
                combinedScore = 4800 + (i % 1200),
                performanceClass = PerformanceClass.MID,
                timestamp = System.currentTimeMillis() - (i * 1000)
            )
            
            // Use the recommendation engine
            val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
            
            // Add a weak reference to track garbage collection
            weakRefs.add(WeakReference(recommendationEngine))
            
            // For every 10th iteration, store a strong reference to simulate a memory leak
            if (i % 10 == 0 && temporaryHolders.size < 5) {
                temporaryHolders.add(recommendationEngine)
            }
            
            // Occasionally clear the temporary holders to verify objects can be collected
            if (i % 20 == 0) {
                temporaryHolders.clear()
                System.gc()
                Thread.sleep(pauseForVisualGcMs) // Give GC time to work
            }
            
            // Print progress
            if (i % 25 == 0) {
                val memoryNow = memoryMXBean.heapMemoryUsage.used
                val memoryDelta = memoryNow - initialMemory
                println("Iteration $i - Memory: ${memoryNow / (1024 * 1024)} MB, Delta: ${memoryDelta / (1024 * 1024)} MB")
            }
        }
        
        // Clear temporary holders to allow GC of all objects
        temporaryHolders.clear()
        
        // Force garbage collection multiple times
        for (i in 1..gcCycles) {
            System.gc()
            Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        }
        
        // Check how many weak references were cleared (should be most of them)
        val referencesCleared = weakRefs.count { it.get() == null }
        println("Weak references cleared: $referencesCleared of ${weakRefs.size}")
        
        // Calculate reference collection ratio
        val collectionRatio = referencesCleared.toDouble() / weakRefs.size.toDouble()
        println("Collection ratio: ${collectionRatio * 100}%")
        
        // Get final memory state
        val finalMemory = memoryMXBean.heapMemoryUsage.used
        val memoryDelta = finalMemory - initialMemory
        println("Final memory: ${finalMemory / (1024 * 1024)} MB")
        println("Memory delta: ${memoryDelta / (1024 * 1024)} MB")
        
        // Check for memory leaks
        if (memoryDelta > 0) {
            // Calculate memory leak severity
            val memoryIncrease = memoryDelta.toDouble() / initialMemory.toDouble() * 100.0
            println("Memory increase: ${"%.2f".format(memoryIncrease)}%")
            
            // Assert memory didn't increase beyond threshold
            assert(memoryIncrease < memoryThresholdPercent) {
                "Possible memory leak detected: ${memoryIncrease.toInt()}% memory increase after $iterationCount iterations"
            }
        }
        
        // Assert that most references were garbage collected
        assert(collectionRatio > 0.75) {
            "Possible memory leak: Only ${(collectionRatio * 100).toInt()}% of objects were garbage collected"
        }
    }
    
    /**
     * Test for memory leaks in the JavaScript bridge by repeatedly creating 
     * and using MonitorModule instances
     */
    @Test
    fun `test for memory leaks in JavaScript bridge`() {
        // Skip this test if JavaScript bridge testing is disabled
        val jsBridgeTestingEnabled = TestConfig.getNestedValue(
            "memoryTests", "leakTests", "testJavaScriptBridge", true
        )
        if (!jsBridgeTestingEnabled) {
            println("JavaScript bridge memory leak testing is disabled")
            return
        }
        
        // Get initial memory state
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        System.gc() // Encourage garbage collection before starting
        Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        
        val initialMemory = memoryMXBean.heapMemoryUsage.used
        println("Initial memory usage: ${initialMemory / (1024 * 1024)} MB")
        
        // Create mock for benchmark recommendations
        val mockRecommendations = mutableListOf<BenchmarkRecommendationEngine.Recommendation>()
        for (i in 1..10) {
            mockRecommendations.add(
                BenchmarkRecommendationEngine.Recommendation(
                    id = "test_rec_$i",
                    type = BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL,
                    title = "Test Recommendation $i",
                    description = "This is a test recommendation for memory leak testing",
                    importance = BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
                )
            )
        }
        
        // List to track weak references
        val weakRefs = mutableListOf<WeakReference<MonitorModule>>()
        
        // Repeatedly create and use MonitorModule instances
        for (i in 1..iterationCount) {
            // Create new monitor module
            val monitorModule = spy(MonitorModule(mockContext))
            
            // Mock the necessary dependencies
            val mockDetector = mock(DeviceCapabilityDetector::class.java)
            val mockEngine = mock(BenchmarkRecommendationEngine::class.java)
            
            // Configure mocks
            val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
                cpuScore = 5000,
                memoryScore = 4500,
                combinedScore = 4800,
                performanceClass = PerformanceClass.MID,
                timestamp = System.currentTimeMillis()
            )
            
            doReturn(benchmarkResults).`when`(mockDetector).getBenchmarkResults()
            doReturn(mockRecommendations).`when`(mockEngine).getRecommendationsFromBenchmark(any())
            
            // Inject mocks
            val detectorField = MonitorModule::class.java.getDeclaredField("detector")
            detectorField.isAccessible = true
            detectorField.set(monitorModule, mockDetector)
            
            val engineField = MonitorModule::class.java.getDeclaredField("recommendationEngine")
            engineField.isAccessible = true
            engineField.set(monitorModule, mockEngine)
            
            // Mock populateRecommendations to avoid complex JS object creation
            doNothing().`when`(monitorModule).populateRecommendations(any(), any(), any(), any())
            
            // Use the monitor module
            monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
            
            // Add weak reference to track garbage collection
            weakRefs.add(WeakReference(monitorModule))
            
            // Print progress
            if (i % 25 == 0) {
                val memoryNow = memoryMXBean.heapMemoryUsage.used
                val memoryDelta = memoryNow - initialMemory
                println("Iteration $i - Memory: ${memoryNow / (1024 * 1024)} MB, Delta: ${memoryDelta / (1024 * 1024)} MB")
            }
        }
        
        // Force garbage collection
        for (i in 1..gcCycles) {
            System.gc()
            Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        }
        
        // Check how many weak references were cleared
        val referencesCleared = weakRefs.count { it.get() == null }
        println("Weak references cleared: $referencesCleared of ${weakRefs.size}")
        
        // Calculate collection ratio
        val collectionRatio = referencesCleared.toDouble() / weakRefs.size.toDouble()
        println("Collection ratio: ${collectionRatio * 100}%")
        
        // Get final memory state
        val finalMemory = memoryMXBean.heapMemoryUsage.used
        val memoryDelta = finalMemory - initialMemory
        println("Final memory: ${finalMemory / (1024 * 1024)} MB")
        println("Memory delta: ${memoryDelta / (1024 * 1024)} MB")
        
        // Check for memory leaks
        if (memoryDelta > 0) {
            // Calculate memory leak severity
            val memoryIncrease = memoryDelta.toDouble() / initialMemory.toDouble() * 100.0
            println("Memory increase: ${"%.2f".format(memoryIncrease)}%")
            
            // Assert memory didn't increase beyond threshold
            assert(memoryIncrease < memoryThresholdPercent) {
                "Possible memory leak detected in JavaScript bridge: ${memoryIncrease.toInt()}% memory increase"
            }
        }
        
        // Assert that most references were garbage collected
        assert(collectionRatio > 0.75) {
            "Possible memory leak in JavaScript bridge: Only ${(collectionRatio * 100).toInt()}% of objects were garbage collected"
        }
    }
    
    /**
     * Test for memory leaks with long-running operations
     */
    @Test
    fun `test long-running recommendation generation for memory leaks`() {
        // Create device state objects
        val detector = spy(DeviceCapabilityDetector(mockContext))
        val recommendationEngine = spy(BenchmarkRecommendationEngine(mockContext))
        
        // Inject detector into recommendation engine
        val detectorField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        detectorField.isAccessible = true
        detectorField.set(recommendationEngine, detector)
        
        // Mock device state
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
        doReturn(memoryCapabilities).`when`(detector).getMemoryCapabilities()
        doReturn(batteryStatus).`when`(detector).getBatteryStatus()
        doReturn(storageCapabilities).`when`(detector).getStorageCapabilities()
        doReturn(cpuCapabilities).`when`(detector).getCpuCapabilities()
        
        // Create benchmark results
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 6000,
            combinedScore = 6600,
            performanceClass = PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        // Get initial memory state
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        System.gc() // Encourage garbage collection before starting
        Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        
        val initialMemory = memoryMXBean.heapMemoryUsage.used
        println("Initial memory usage: ${initialMemory / (1024 * 1024)} MB")
        
        // Track memory readings
        val memoryReadings = mutableListOf<Long>()
        memoryReadings.add(initialMemory)
        
        // Run many iterations
        val iterations = iterationCount * 2
        for (i in 1..iterations) {
            // Generate recommendations
            val recommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
            
            // Verify we got recommendations
            assert(recommendations.isNotEmpty()) { 
                "Empty recommendations list in iteration $i" 
            }
            
            // Track memory every 10 iterations
            if (i % 10 == 0) {
                // Force GC occasionally to see if memory is being properly released
                if (i % 20 == 0) {
                    System.gc()
                    Thread.sleep(pauseForVisualGcMs) // Give GC time to work
                }
                
                val currentMemory = memoryMXBean.heapMemoryUsage.used
                memoryReadings.add(currentMemory)
                
                val memoryDelta = currentMemory - initialMemory
                println("Iteration $i - Memory: ${currentMemory / (1024 * 1024)} MB, Delta: ${memoryDelta / (1024 * 1024)} MB")
            }
        }
        
        // Force final GC
        System.gc()
        Thread.sleep(pauseForVisualGcMs) // Give GC time to work
        
        // Add final memory reading
        val finalMemory = memoryMXBean.heapMemoryUsage.used
        memoryReadings.add(finalMemory)
        
        // Analyze memory trend
        val memoryIncreases = mutableListOf<Long>()
        for (i in 1 until memoryReadings.size) {
            val increase = memoryReadings[i] - memoryReadings[i-1]
            if (increase > 0) {
                memoryIncreases.add(increase)
            }
        }
        
        // Calculate average memory increase (if any)
        val avgIncrease = if (memoryIncreases.isNotEmpty()) memoryIncreases.average() else 0.0
        println("Average memory increase per measurement: ${avgIncrease / (1024 * 1024)} MB")
        
        // Calculate total memory change
        val totalDelta = finalMemory - initialMemory
        println("Final memory: ${finalMemory / (1024 * 1024)} MB")
        println("Total memory delta: ${totalDelta / (1024 * 1024)} MB")
        
        // Check for consistent memory growth indicating a leak
        if (avgIncrease > 0 && memoryIncreases.size > memoryReadings.size / 2) {
            // Calculate total increase as percentage
            val increasePercent = totalDelta.toDouble() / initialMemory.toDouble() * 100.0
            
            // Only fail if memory increase is significant
            if (increasePercent > memoryThresholdPercent) {
                assert(false) { 
                    "Possible memory leak detected: consistent memory growth of ${avgIncrease / (1024 * 1024)} MB per measurement, ${increasePercent.toInt()}% total increase" 
                }
            } else {
                println("Warning: Some memory growth detected (${increasePercent.toInt()}%) but below threshold")
            }
        } else {
            println("No significant memory growth pattern detected")
        }
    }
}