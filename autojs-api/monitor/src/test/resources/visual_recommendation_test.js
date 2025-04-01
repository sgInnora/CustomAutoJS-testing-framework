/**
 * Visual Verification Test for Recommendation UI
 * 
 * This script provides a visual interface for manually testing and verifying
 * the recommendation system UI rendering and interactions.
 * 
 * Features:
 * - Displays recommendations in a custom UI
 * - Allows filtering by type and importance
 * - Tests action buttons and interactions
 * - Provides visual comparison with design specs
 */

// Import test configuration if available
let config = {
    showAdvancedOptions: true,
    fullScreenMode: false,
    showMockControls: true,
    animationsEnabled: true,
    themeColor: "#2196F3"
};

try {
    const testConfig = require('./test_config.js');
    if (testConfig && testConfig.visualTest) {
        Object.assign(config, testConfig.visualTest);
    }
} catch (e) {
    console.log("No test configuration found, using defaults");
}

// Theme colors
const COLORS = {
    primary: config.themeColor,
    text: "#212121",
    textLight: "#757575",
    background: "#FFFFFF",
    backgroundDark: "#F5F5F5",
    divider: "#EEEEEE",
    
    // Importance colors
    high: "#F44336",
    medium: "#FF9800",
    low: "#4CAF50",
    
    // Type colors
    general: "#2196F3",
    performance: "#9C27B0",
    battery: "#FFC107",
    memory: "#3F51B5",
    storage: "#009688"
};

// Icons for recommendation types
const TYPE_ICONS = {
    "general": "⚙️",
    "performance": "🚀",
    "battery": "🔋",
    "memory": "🧠",
    "storage": "💾"
};

// Mock recommendations for UI testing
const MOCK_RECOMMENDATIONS = [
    {
        id: "mock_battery_critical",
        type: "battery",
        title: "Critical Battery Level",
        description: "Your battery is critically low. Connect to a charger and avoid running benchmarks or intensive tasks.",
        importance: "high",
        action: "android.settings.BATTERY_SAVER_SETTINGS"
    },
    {
        id: "mock_memory_low",
        type: "memory",
        title: "Low Memory Condition",
        description: "Your device is currently low on memory. Close unused apps to improve performance and prevent app crashes.",
        importance: "high",
        action: "android.settings.APPLICATION_SETTINGS"
    },
    {
        id: "mock_storage_low",
        type: "storage",
        title: "Low Storage Space",
        description: "Your device is running low on storage space. Consider clearing cache and removing unused apps to free up space.",
        importance: "medium",
        action: "android.settings.INTERNAL_STORAGE_SETTINGS"
    },
    {
        id: "mock_performance_mode",
        type: "performance",
        title: "Performance Mode",
        description: "Your device has good performance capabilities. Consider enabling performance mode for optimal experience during intensive tasks.",
        importance: "medium"
    },
    {
        id: "mock_general_update",
        type: "general",
        title: "Check for System Updates",
        description: "System updates can improve device performance and fix bugs. Check if any updates are available for your device.",
        importance: "medium",
        action: "android.settings.SYSTEM_UPDATE_SETTINGS"
    },
    {
        id: "mock_benchmark_rerun",
        type: "general",
        title: "Run Performance Benchmark",
        description: "Run a benchmark to get more detailed and accurate performance recommendations for your device.",
        importance: "low"
    },
    {
        id: "mock_memory_moderate",
        type: "memory",
        title: "Moderate Memory Usage",
        description: "Your device is using a significant amount of memory. Consider closing background apps for better performance.",
        importance: "low"
    }
];

// State variables
let currentRecommendations = [];
let filterType = "all";
let filterImportance = "all";
let activeTheme = "light";
let mockMode = false;

// Main UI creation
function createUI() {
    const screenWidth = device.width;
    const screenHeight = device.height;
    
    const ui = floaty.window(
        <frame id="rootContainer" w="*" h="*" gravity="center" padding="0">
            <vertical w="*" h="*" bg={COLORS.background}>
                <frame id="headerContainer" w="*" h="auto" bg={COLORS.primary}>
                    <vertical padding="16 8">
                        <text id="headerTitle" text="Recommendation Visual Test" textSize="18sp" textColor="#FFFFFF" textStyle="bold"/>
                        <text id="headerSubtitle" text="CustomAutoJS Monitoring Module" textSize="14sp" textColor="#FFFFFF"/>
                    </vertical>
                    <button id="themeToggle" text="🌙" w="48dp" h="48dp" layout_gravity="right|center_vertical" margin="0 0 8 0" style="Widget.AppCompat.Button.Borderless"/>
                </frame>
                
                <horizontal w="*" h="auto" padding="16 8" bg={COLORS.backgroundDark}>
                    <text text="Filter: " textSize="14sp" textColor={COLORS.text} layout_gravity="center_vertical"/>
                    <spinner id="typeFilter" w="auto" h="40dp" entries="All|General|Performance|Battery|Memory|Storage" textColor={COLORS.text} layout_gravity="center_vertical"/>
                    <text text="  Importance: " textSize="14sp" textColor={COLORS.text} layout_gravity="center_vertical"/>
                    <spinner id="importanceFilter" w="auto" h="40dp" entries="All|High|Medium|Low" textColor={COLORS.text} layout_gravity="center_vertical"/>
                </horizontal>
                
                <frame w="*" h="*" padding="0">
                    <scroll w="*" h="*">
                        <vertical id="recommendationContainer" w="*" h="auto" padding="8">
                            <text text="Loading recommendations..." textSize="16sp" textColor={COLORS.textLight} margin="16"/>
                        </vertical>
                    </scroll>
                    
                    <vertical id="mockControls" w="*" h="auto" layout_gravity="bottom" bg={COLORS.backgroundDark} visibility={config.showMockControls ? "visible" : "gone"}>
                        <horizontal w="*" h="auto" padding="16 8">
                            <button id="toggleMockBtn" text="Use Mock Data" w="*" h="auto" layout_weight="1"/>
                            <button id="refreshBtn" text="Refresh" w="*" h="auto" layout_weight="1"/>
                        </horizontal>
                    </vertical>
                </frame>
            </vertical>
        </frame>
    );
    
    // Adjust window size
    if (config.fullScreenMode) {
        ui.setSize(screenWidth, screenHeight);
        ui.setPosition(0, 0);
    } else {
        ui.setSize(screenWidth * 0.9, screenHeight * 0.8);
        ui.setPosition(screenWidth * 0.05, screenHeight * 0.1);
    }
    
    // Setup event handlers
    setupEventHandlers(ui);
    
    // Initial data load
    loadRecommendations(ui);
    
    return ui;
}

// Setup event handlers for UI
function setupEventHandlers(ui) {
    // Theme toggle
    ui.themeToggle.on("click", function() {
        toggleTheme(ui);
    });
    
    // Type filter change
    ui.typeFilter.on("item_selected", function(position) {
        const filterOptions = ["all", "general", "performance", "battery", "memory", "storage"];
        filterType = filterOptions[position];
        applyFilters(ui);
    });
    
    // Importance filter change
    ui.importanceFilter.on("item_selected", function(position) {
        const filterOptions = ["all", "high", "medium", "low"];
        filterImportance = filterOptions[position];
        applyFilters(ui);
    });
    
    // Toggle mock data
    if (ui.toggleMockBtn) {
        ui.toggleMockBtn.on("click", function() {
            mockMode = !mockMode;
            ui.toggleMockBtn.setText(mockMode ? "Use Real Data" : "Use Mock Data");
            loadRecommendations(ui);
        });
    }
    
    // Refresh button
    if (ui.refreshBtn) {
        ui.refreshBtn.on("click", function() {
            loadRecommendations(ui);
        });
    }
}

// Toggle between light and dark theme
function toggleTheme(ui) {
    activeTheme = activeTheme === "light" ? "dark" : "light";
    
    if (activeTheme === "dark") {
        ui.rootContainer.setBackgroundColor(android.graphics.Color.parseColor("#121212"));
        ui.recommendationContainer.setBackgroundColor(android.graphics.Color.parseColor("#121212"));
        ui.headerContainer.setBackgroundColor(android.graphics.Color.parseColor("#1F1F1F"));
        ui.themeToggle.setText("☀️");
        
        // Update all recommendation cards
        refreshCardTheme(ui);
    } else {
        ui.rootContainer.setBackgroundColor(android.graphics.Color.parseColor(COLORS.background));
        ui.recommendationContainer.setBackgroundColor(android.graphics.Color.parseColor(COLORS.background));
        ui.headerContainer.setBackgroundColor(android.graphics.Color.parseColor(COLORS.primary));
        ui.themeToggle.setText("🌙");
        
        // Update all recommendation cards
        refreshCardTheme(ui);
    }
}

// Refresh all card themes
function refreshCardTheme(ui) {
    for (let i = 0; i < currentRecommendations.length; i++) {
        const rec = currentRecommendations[i];
        const cardView = ui.findView(`card_${rec.id}`);
        if (cardView) {
            cardView.setBackgroundColor(android.graphics.Color.parseColor(
                activeTheme === "dark" ? "#1F1F1F" : "#FFFFFF"
            ));
            
            // Update text colors
            const titleView = ui.findView(`title_${rec.id}`);
            const descView = ui.findView(`desc_${rec.id}`);
            if (titleView) {
                titleView.setTextColor(android.graphics.Color.parseColor(
                    activeTheme === "dark" ? "#FFFFFF" : COLORS.text
                ));
            }
            if (descView) {
                descView.setTextColor(android.graphics.Color.parseColor(
                    activeTheme === "dark" ? "#BBBBBB" : COLORS.textLight
                ));
            }
        }
    }
}

// Load recommendations from API or mock data
function loadRecommendations(ui) {
    ui.recommendationContainer.removeAllViews();
    
    ui.recommendationContainer.addView(
        <text text="Loading recommendations..." textSize="16sp" textColor={COLORS.textLight} margin="16"/>
    );
    
    // Small delay to show loading state
    setTimeout(function() {
        let recommendations = [];
        
        if (mockMode) {
            // Use mock data
            recommendations = MOCK_RECOMMENDATIONS;
            console.log("Using mock recommendations");
        } else {
            // Use real API data if available
            try {
                if (typeof monitor !== 'undefined' && typeof monitor.getBenchmarkRecommendations === 'function') {
                    recommendations = monitor.getBenchmarkRecommendations();
                    console.log(`Loaded ${recommendations.length} real recommendations`);
                } else {
                    console.log("Monitor API not available, falling back to mock data");
                    recommendations = MOCK_RECOMMENDATIONS;
                }
            } catch (e) {
                console.error("Error loading recommendations:", e);
                recommendations = MOCK_RECOMMENDATIONS;
            }
        }
        
        // Store current recommendations
        currentRecommendations = recommendations;
        
        // Display recommendations
        displayRecommendations(ui, recommendations);
    }, 500);
}

// Display recommendations in the UI
function displayRecommendations(ui, recommendations) {
    ui.recommendationContainer.removeAllViews();
    
    if (!recommendations || recommendations.length === 0) {
        ui.recommendationContainer.addView(
            <text text="No recommendations available" textSize="16sp" textColor={COLORS.textLight} margin="16"/>
        );
        return;
    }
    
    // Add summary view
    addSummaryView(ui, recommendations);
    
    // Apply current filters
    const filteredRecs = filterRecommendations(recommendations);
    
    if (filteredRecs.length === 0) {
        ui.recommendationContainer.addView(
            <text text={`No recommendations match the current filters (${filterType}/${filterImportance})`} 
                  textSize="14sp" textColor={COLORS.textLight} margin="16"/>
        );
        return;
    }
    
    // Sort recommendations by importance
    const sortedRecs = [...filteredRecs].sort((a, b) => {
        const importanceOrder = { "high": 0, "medium": 1, "low": 2 };
        return importanceOrder[a.importance] - importanceOrder[b.importance];
    });
    
    // Display each recommendation
    sortedRecs.forEach(rec => addRecommendationCard(ui, rec));
}

// Add summary view of recommendations
function addSummaryView(ui, recommendations) {
    // Count recommendations by type and importance
    const typeCounts = {};
    const importanceCounts = { "high": 0, "medium": 0, "low": 0 };
    
    recommendations.forEach(rec => {
        typeCounts[rec.type] = (typeCounts[rec.type] || 0) + 1;
        importanceCounts[rec.importance]++;
    });
    
    // Create summary card
    const cardView = ui.recommendationContainer.addView(
        <frame w="*" h="auto" margin="8 8 8 16">
            <vertical padding="16" bg={COLORS.backgroundDark} cornerRadius="8dp">
                <text text={`${recommendations.length} Recommendations`} 
                      textSize="16sp" textColor={COLORS.text} textStyle="bold" marginBottom="8"/>
                
                <horizontal w="*" h="auto" marginTop="8">
                    <vertical layout_weight="1">
                        <text text="By Type" textSize="14sp" textColor={COLORS.textLight} textStyle="bold" marginBottom="4"/>
                        <vertical id="typeCountContainer" w="*" h="auto"></vertical>
                    </vertical>
                    
                    <vertical layout_weight="1" marginStart="16">
                        <text text="By Importance" textSize="14sp" textColor={COLORS.textLight} textStyle="bold" marginBottom="4"/>
                        <vertical id="importanceCountContainer" w="*" h="auto"></vertical>
                    </vertical>
                </horizontal>
            </vertical>
        </frame>
    );
    
    // Add type counts
    const typeContainer = cardView.findView("typeCountContainer");
    Object.entries(typeCounts).forEach(([type, count]) => {
        const typeIcon = TYPE_ICONS[type] || "📌";
        const typeColor = COLORS[type] || COLORS.primary;
        
        typeContainer.addView(
            <text text={`${typeIcon} ${type}: ${count}`} 
                  textSize="14sp" textColor={typeColor} marginBottom="2"/>
        );
    });
    
    // Add importance counts
    const importanceContainer = cardView.findView("importanceCountContainer");
    Object.entries(importanceCounts).forEach(([importance, count]) => {
        if (count > 0) {
            importanceContainer.addView(
                <text text={`${importance}: ${count}`} 
                      textSize="14sp" textColor={COLORS[importance]} marginBottom="2"/>
            );
        }
    });
}

// Add a recommendation card to the UI
function addRecommendationCard(ui, rec) {
    // Card ID for reference
    const cardId = `card_${rec.id}`;
    const titleId = `title_${rec.id}`;
    const descId = `desc_${rec.id}`;
    
    // Get appropriate colors and icons
    const importanceColor = COLORS[rec.importance] || COLORS.primary;
    const typeIcon = TYPE_ICONS[rec.type] || "📌";
    const bgColor = activeTheme === "dark" ? "#1F1F1F" : "#FFFFFF";
    const textColor = activeTheme === "dark" ? "#FFFFFF" : COLORS.text;
    const textLightColor = activeTheme === "dark" ? "#BBBBBB" : COLORS.textLight;
    
    // Create the card
    const cardView = ui.recommendationContainer.addView(
        <frame id={cardId} w="*" h="auto" margin="8">
            <vertical padding="16" bg={bgColor} cornerRadius="8dp">
                <horizontal w="*" h="auto">
                    <text text={typeIcon + " " + rec.type} 
                          textSize="14sp" textColor={importanceColor}/>
                    
                    <frame layout_width="0dp" layout_weight="1"></frame>
                    
                    <text text={rec.importance.toUpperCase()} 
                          textSize="12sp" textColor={importanceColor} textStyle="bold"/>
                </horizontal>
                
                <text id={titleId} text={rec.title} 
                      textSize="16sp" textColor={textColor} textStyle="bold"
                      marginTop="8" marginBottom="4"/>
                      
                <text id={descId} text={rec.description} 
                      textSize="14sp" textColor={textLightColor}
                      marginBottom="8"/>
                      
                {rec.action ? 
                    <button id={`action_${rec.id}`} text="Take Action" 
                           textSize="14sp" style="Widget.AppCompat.Button.Colored"
                           w="auto" h="auto" layout_gravity="right"/> : 
                    <frame w="0" h="0"></frame>
                }
            </vertical>
        </frame>
    );
    
    // Add shadow effect using canvas (if animations enabled)
    if (config.animationsEnabled) {
        // Add subtle elevation shadow
        cardView.setElevation(4);
        
        // Add ripple effect on click
        const rippleDrawable = new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33000000")),
            cardView.getBackground(),
            null
        );
        cardView.setBackground(rippleDrawable);
    }
    
    // Add action button handler
    if (rec.action) {
        const actionButton = cardView.findView(`action_${rec.id}`);
        if (actionButton) {
            actionButton.on("click", function() {
                // Show toast message for action
                toast(`Would open: ${rec.action}`);
                
                // Visual feedback
                actionButton.setText("✓ Action Taken");
                actionButton.setEnabled(false);
                
                // Try to start the activity if possible
                try {
                    if (typeof app !== 'undefined' && typeof app.startActivity === 'function') {
                        app.startActivity({
                            action: rec.action,
                            flags: ["activity_new_task"]
                        });
                    }
                } catch (e) {
                    console.error("Error starting activity:", e);
                }
            });
        }
    }
    
    // Add click effect to the entire card
    cardView.on("click", function() {
        // Toggle expanded view or show details
        toast(`Clicked: ${rec.title}`);
    });
}

// Filter recommendations based on current filter settings
function filterRecommendations(recommendations) {
    return recommendations.filter(rec => {
        const typeMatch = filterType === "all" || rec.type === filterType;
        const importanceMatch = filterImportance === "all" || rec.importance === filterImportance;
        return typeMatch && importanceMatch;
    });
}

// Apply filters to the current recommendations
function applyFilters(ui) {
    displayRecommendations(ui, currentRecommendations);
}

// Main function to run the test
function runVisualTest() {
    console.log("Starting Visual Recommendation Test");
    
    // Create and display the UI
    const ui = createUI();
    
    // Handle script exit
    events.on("exit", function() {
        if (ui) ui.close();
    });
}

// Run the test if executed directly
if (engines.myEngine().toString() === engines.all()[0].toString()) {
    runVisualTest();
}

// Export the test function for external use
module.exports = {
    runVisualTest: runVisualTest,
    mockRecommendations: MOCK_RECOMMENDATIONS
};