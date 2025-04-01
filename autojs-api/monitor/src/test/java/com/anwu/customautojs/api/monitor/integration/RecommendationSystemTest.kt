package com.anwu.customautojs.api.monitor.integration

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import com.anwu.customautojs.api.monitor.MonitorModule
import com.anwu.customautojs.api.monitor.device.BenchmarkRecommendationEngine
import com.anwu.customautojs.api.monitor.device.DeviceBenchmarkManager
import com.anwu.customautojs.api.monitor.device.DeviceCapabilityDetector
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
import org.junit.Assert.*

/**
 * Comprehensive test for the recommendation system.
 * 
 * Tests the integration between MonitorModule, DeviceCapabilityDetector,
 * and BenchmarkRecommendationEngine to ensure recommendations are correctly
 * generated and exposed to JavaScript.
 */
@RunWith(MockitoJUnitRunner::class)
class RecommendationSystemTest {

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
        val looperField = Looper::class.java.getDeclaredField("sMainLooper")
        looperField.isAccessible = true
        looperField.set(null, mockMainLooper)
        
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
    }

    @Test
    fun `getBenchmarkRecommendations returns recommendations from benchmark results when available`() {
        // Setup - Create benchmark results
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 5000,
            memoryScore = 4500,
            combinedScore = 4800,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID,
            timestamp = System.currentTimeMillis()
        )
        
        // Mock benchmark-based recommendations
        val recommendations = listOf(
            BenchmarkRecommendationEngine.Recommendation(
                id = "test_battery",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY,
                title = "Test Battery Recommendation",
                description = "Test description for battery recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_HIGH,
                action = "android.settings.BATTERY_SAVER_SETTINGS"
            ),
            BenchmarkRecommendationEngine.Recommendation(
                id = "test_performance",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_PERFORMANCE,
                title = "Test Performance Recommendation",
                description = "Test description for performance recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
            )
        )
        
        // Setup mocks
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        doReturn(recommendations).`when`(recommendationEngine).getRecommendationsFromBenchmark(benchmarkResults)
        
        // Execute
        val result = monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
        
        // Verify
        verify(capabilityDetector).getBenchmarkResults()
        verify(recommendationEngine).getRecommendationsFromBenchmark(benchmarkResults)
        verify(mockRhinoContext).newArray(mockScriptable, 0)
        
        // Check that recommendations were correctly processed
        verify(monitorModule).populateRecommendations(
            eq(mockRhinoContext),
            eq(mockScriptable),
            any(),
            eq(recommendations)
        )
    }

    @Test
    fun `getBenchmarkRecommendations returns basic recommendations when no benchmark results are available`() {
        // Setup - No benchmark results
        doReturn(null).`when`(capabilityDetector).getBenchmarkResults()
        
        // Mock basic recommendations
        val basicRecommendations = listOf(
            BenchmarkRecommendationEngine.Recommendation(
                id = "basic_storage",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_STORAGE,
                title = "Basic Storage Recommendation",
                description = "Test description for storage recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM,
                action = "android.settings.INTERNAL_STORAGE_SETTINGS"
            ),
            BenchmarkRecommendationEngine.Recommendation(
                id = "basic_general",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL,
                title = "Basic General Recommendation",
                description = "Test description for general recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_LOW
            )
        )
        
        doReturn(basicRecommendations).`when`(recommendationEngine).getBasicRecommendations()
        
        // Execute
        val result = monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
        
        // Verify
        verify(capabilityDetector).getBenchmarkResults()
        verify(recommendationEngine).getBasicRecommendations()
        verify(mockRhinoContext).newArray(mockScriptable, 0)
        
        // Check that basic recommendations were correctly processed
        verify(monitorModule).populateRecommendations(
            eq(mockRhinoContext),
            eq(mockScriptable),
            any(),
            eq(basicRecommendations)
        )
    }

    @Test
    fun `getBasicRecommendations returns basic recommendations directly`() {
        // Mock basic recommendations
        val basicRecommendations = listOf(
            BenchmarkRecommendationEngine.Recommendation(
                id = "test_memory",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY,
                title = "Test Memory Recommendation",
                description = "Test description for memory recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_HIGH
            ),
            BenchmarkRecommendationEngine.Recommendation(
                id = "test_general",
                type = BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL,
                title = "Test General Recommendation",
                description = "Test description for general recommendation",
                importance = BenchmarkRecommendationEngine.IMPORTANCE_MEDIUM
            )
        )
        
        doReturn(basicRecommendations).`when`(recommendationEngine).getBasicRecommendations()
        
        // Execute
        val result = monitorModule.getBasicRecommendations(mockRhinoContext, mockScriptable)
        
        // Verify
        verify(recommendationEngine).getBasicRecommendations()
        verify(mockRhinoContext).newArray(mockScriptable, 0)
        
        // Check that basic recommendations were correctly processed
        verify(monitorModule).populateRecommendations(
            eq(mockRhinoContext),
            eq(mockScriptable),
            any(),
            eq(basicRecommendations)
        )
    }

    @Test
    fun `BenchmarkRecommendationEngine generates appropriate recommendations based on device state`() {
        // Setup device state for testing
        val cpuCapabilities = DeviceCapabilityDetector.CpuCapabilities(
            cores = 4,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID,
            architecture = "ARM64",
            isArmV8 = true,
            supportsSIMD = true
        )
        
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 3L * 1024 * 1024 * 1024, // 3GB RAM
            availableRam = 1L * 1024 * 1024 * 1024, // 1GB available
            isLowMemory = false,
            percentAvailable = 33
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 25,
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 38f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024, // 64GB
            availableInternalStorage = 10L * 1024 * 1024 * 1024, // 10GB available
            externalStorageAvailable = true
        )
        
        // Setup mocks
        doReturn(cpuCapabilities).`when`(capabilityDetector).getCpuCapabilities()
        doReturn(memoryCapabilities).`when`(capabilityDetector).getMemoryCapabilities()
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        doReturn(storageCapabilities).`when`(capabilityDetector).getStorageCapabilities()
        
        // Create a real recommendation engine with our spied detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Test basic recommendations
        val basicRecommendations = recommendationEngine.getBasicRecommendations()
        
        // Verify
        assertTrue("Should include battery recommendations due to low battery level", 
            basicRecommendations.any { it.type == BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY })
        
        assertTrue("Should include general recommendations", 
            basicRecommendations.any { it.type == BenchmarkRecommendationEngine.RECOMMENDATION_GENERAL })
        
        assertTrue("Should include memory recommendations due to moderate memory availability", 
            basicRecommendations.any { it.type == BenchmarkRecommendationEngine.RECOMMENDATION_MEMORY })
        
        assertTrue("Should include temperature recommendation due to elevated temperature", 
            basicRecommendations.any { it.id.contains("temperature") || it.title.contains("Temperature") })
        
        assertTrue("Should include recommendation to run benchmark", 
            basicRecommendations.any { it.id.contains("benchmark") })
    }

    @Test
    fun `BenchmarkRecommendationEngine generates appropriate recommendations from benchmark results`() {
        // Setup device state for testing (same as previous test)
        val cpuCapabilities = DeviceCapabilityDetector.CpuCapabilities(
            cores = 4,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID,
            architecture = "ARM64",
            isArmV8 = true,
            supportsSIMD = true
        )
        
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 3L * 1024 * 1024 * 1024, // 3GB RAM
            availableRam = 1L * 1024 * 1024 * 1024, // 1GB available
            isLowMemory = false,
            percentAvailable = 33
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 25,
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 38f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024, // 64GB
            availableInternalStorage = 10L * 1024 * 1024 * 1024, // 10GB available
            externalStorageAvailable = true
        )
        
        // Setup mocks
        doReturn(cpuCapabilities).`when`(capabilityDetector).getCpuCapabilities()
        doReturn(memoryCapabilities).`when`(capabilityDetector).getMemoryCapabilities()
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        doReturn(storageCapabilities).`when`(capabilityDetector).getStorageCapabilities()
        
        // Create a real recommendation engine with our spied detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Create benchmark results with CPU significantly slower than memory
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 2000,
            memoryScore = 4000,
            combinedScore = 3000,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID,
            timestamp = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L) // 10 days old
        )
        
        // Test benchmark-based recommendations
        val benchmarkRecommendations = recommendationEngine.getRecommendationsFromBenchmark(benchmarkResults)
        
        // Verify
        assertTrue("Should include CPU bottleneck recommendation", 
            benchmarkRecommendations.any { it.id == "cpu_bottleneck" })
        
        assertTrue("Should include battery recommendations due to low battery level", 
            benchmarkRecommendations.any { it.type == BenchmarkRecommendationEngine.RECOMMENDATION_BATTERY })
        
        assertTrue("Should include recommendations based on MID performance class", 
            benchmarkRecommendations.any { it.id == "mid_perf_1" || it.id == "mid_perf_2" })
        
        assertTrue("Should include temperature recommendation due to elevated temperature", 
            benchmarkRecommendations.any { it.id.contains("temperature") || it.title.contains("Temperature") })
        
        assertTrue("Should include CPU-specific recommendations due to low CPU score", 
            benchmarkRecommendations.any { it.id == "cpu_low_1" || it.id == "cpu_mid_1" })
        
        assertFalse("Should NOT include recommendation to rerun benchmark (not old enough)", 
            benchmarkRecommendations.any { it.id == "rerun_benchmark" })
    }

    @Test
    fun `BenchmarkRecommendationEngine includes recommendation to rerun benchmark when benchmark is old`() {
        // Setup - Use an old benchmark
        val oldBenchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 5000,
            memoryScore = 5000,
            combinedScore = 5000,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID,
            timestamp = System.currentTimeMillis() - (35 * 24 * 60 * 60 * 1000L) // 35 days old
        )
        
        // Setup minimal device mocks
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 80,
            isCharging = true,
            isBatterySaver = false,
            batteryTemperature = 30f
        )
        
        val storageCapabilities = DeviceCapabilityDetector.StorageCapabilities(
            totalInternalStorage = 64L * 1024 * 1024 * 1024, // 64GB
            availableInternalStorage = 40L * 1024 * 1024 * 1024, // 40GB available
            externalStorageAvailable = true
        )
        
        val memoryCapabilities = DeviceCapabilityDetector.MemoryCapabilities(
            totalRam = 4L * 1024 * 1024 * 1024, // 4GB RAM
            availableRam = 2L * 1024 * 1024 * 1024, // 2GB available
            isLowMemory = false,
            percentAvailable = 50
        )
        
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        doReturn(storageCapabilities).`when`(capabilityDetector).getStorageCapabilities()
        doReturn(memoryCapabilities).`when`(capabilityDetector).getMemoryCapabilities()
        
        // Create a real recommendation engine with our spied detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Test benchmark-based recommendations
        val benchmarkRecommendations = recommendationEngine.getRecommendationsFromBenchmark(oldBenchmarkResults)
        
        // Verify
        assertTrue("Should include recommendation to rerun benchmark", 
            benchmarkRecommendations.any { it.id == "rerun_benchmark" })
    }

    @Test
    fun `End-to-end test with MonitorModule through JavaScript API`() {
        // This test verifies the full flow from MonitorModule through to JavaScript
        
        // Setup device state
        val benchmarkResults = DeviceBenchmarkManager.BenchmarkResults(
            cpuScore = 7000,
            memoryScore = 3000, // Memory bottleneck
            combinedScore = 5000,
            performanceClass = DeviceCapabilityDetector.PerformanceClass.MID_HIGH,
            timestamp = System.currentTimeMillis()
        )
        
        val batteryStatus = DeviceCapabilityDetector.BatteryStatus(
            batteryLevel = 15, // Critical battery
            isCharging = false,
            isBatterySaver = false,
            batteryTemperature = 41f // High temperature
        )
        
        // Setup mocks with our test data
        doReturn(benchmarkResults).`when`(capabilityDetector).getBenchmarkResults()
        doReturn(batteryStatus).`when`(capabilityDetector).getBatteryStatus()
        
        // Call through to real implementation for these methods
        doCallRealMethod().`when`(recommendationEngine).getRecommendationsFromBenchmark(any())
        
        // Create a real recommendation engine with our spied detector
        val engineField = BenchmarkRecommendationEngine::class.java.getDeclaredField("detector")
        engineField.isAccessible = true
        engineField.set(recommendationEngine, capabilityDetector)
        
        // Setup JS array for results
        val mockNativeArray = mock(NativeArray::class.java)
        `when`(mockRhinoContext.newArray(eq(mockScriptable), anyInt())).thenReturn(mockNativeArray)
        
        // Mock the populateRecommendations method to capture the recommendations
        var capturedRecommendations: List<BenchmarkRecommendationEngine.Recommendation>? = null
        doAnswer { invocation ->
            capturedRecommendations = invocation.getArgument<List<BenchmarkRecommendationEngine.Recommendation>>(3)
            null
        }.`when`(monitorModule).populateRecommendations(any(), any(), any(), any())
        
        // Execute - call MonitorModule's JavaScript API method
        monitorModule.getBenchmarkRecommendations(mockRhinoContext, mockScriptable)
        
        // Verify
        assertNotNull("Recommendations should have been captured", capturedRecommendations)
        
        // Check for expected recommendation types based on our test data
        assertTrue("Should include memory bottleneck recommendation due to memory being significantly slower than CPU",
            capturedRecommendations!!.any { it.id == "memory_bottleneck" })
            
        assertTrue("Should include critical battery recommendation",
            capturedRecommendations!!.any { it.id == "battery_critical" })
            
        assertTrue("Should include high temperature recommendation",
            capturedRecommendations!!.any { it.id == "high_temperature_benchmark" })
    }
}