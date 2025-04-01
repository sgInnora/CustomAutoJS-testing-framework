#!/bin/bash
#
# Test Runner Script for CustomAutoJS Monitor Module
# 
# This script automates the running of unit tests and integration tests
# for the CustomAutoJS Monitor module. It can be used in CI/CD pipelines
# or for local development testing.
#
# Usage: ./run_tests.sh [options]
#
# Options:
#   --unit-only          Run only unit tests
#   --integration-only   Run only integration tests
#   --kotlin-only        Run only Kotlin tests (no JavaScript tests)
#   --js-only            Run only JavaScript tests (requires connected device)
#   --device-id <id>     Specify Android device ID for JavaScript tests
#   --skip-benchmark     Skip benchmark tests (faster execution)
#   --verbose            Show detailed test output
#   --visual-test        Include visual recommendation test
#   --stress-test        Run only stress tests
#   --memory-test        Run only memory leak tests
#   --performance-test   Run only performance tests 
#   --quality-test       Run only code quality tests
#   --all-tests          Run all tests including stress, memory, and quality tests
#   --report <path>      Generate HTML test report at specified path
#   --ci-mode            Run in CI mode (non-interactive, fail on any error)
#   --help               Show this help message
#

# Default settings
RUN_UNIT_TESTS=true
RUN_INTEGRATION_TESTS=true
RUN_KOTLIN_TESTS=true
RUN_JS_TESTS=true
SKIP_BENCHMARK=false
VERBOSE=false
VISUAL_TEST=false
DEVICE_ID=""
TEST_TYPE="all"
GENERATE_REPORT=false
REPORT_PATH=""
CI_MODE=false
PROJECT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --unit-only)
            RUN_INTEGRATION_TESTS=false
            TEST_TYPE="unit"
            shift
            ;;
        --integration-only)
            RUN_UNIT_TESTS=false
            TEST_TYPE="integration"
            shift
            ;;
        --kotlin-only)
            RUN_JS_TESTS=false
            shift
            ;;
        --js-only)
            RUN_KOTLIN_TESTS=false
            shift
            ;;
        --device-id)
            DEVICE_ID="$2"
            shift 2
            ;;
        --skip-benchmark)
            SKIP_BENCHMARK=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --visual-test)
            VISUAL_TEST=true
            shift
            ;;
        --stress-test)
            TEST_TYPE="stress"
            shift
            ;;
        --memory-test)
            TEST_TYPE="memory"
            shift
            ;;
        --performance-test)
            TEST_TYPE="performance"
            shift
            ;;
        --quality-test)
            TEST_TYPE="quality"
            shift
            ;;
        --all-tests)
            TEST_TYPE="all"
            shift
            ;;
        --report)
            GENERATE_REPORT=true
            REPORT_PATH="$2"
            shift 2
            ;;
        --ci-mode)
            CI_MODE=true
            shift
            ;;
        --help)
            echo "Test Runner Script for CustomAutoJS Monitor Module"
            echo ""
            echo "Usage: ./run_tests.sh [options]"
            echo ""
            echo "Options:"
            echo "  --unit-only          Run only unit tests"
            echo "  --integration-only   Run only integration tests"
            echo "  --kotlin-only        Run only Kotlin tests (no JavaScript tests)"
            echo "  --js-only            Run only JavaScript tests (requires connected device)"
            echo "  --device-id <id>     Specify Android device ID for JavaScript tests"
            echo "  --skip-benchmark     Skip benchmark tests (faster execution)"
            echo "  --visual-test        Include visual recommendation test"
            echo "  --stress-test        Run only stress tests"
            echo "  --memory-test        Run only memory leak tests"
            echo "  --performance-test   Run only performance tests"
            echo "  --quality-test       Run only code quality tests"
            echo "  --all-tests          Run all tests including stress, memory, and quality tests"
            echo "  --report <path>      Generate HTML test report at specified path"
            echo "  --ci-mode            Run in CI mode (non-interactive, fail on any error)"
            echo "  --verbose            Show detailed test output"
            echo "  --help               Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help to see available options"
            exit 1
            ;;
    esac
done

# Function to print colored output
print_color() {
    local color=$1
    local message=$2
    
    case "$color" in
        "red")
            echo -e "\033[0;31m$message\033[0m"
            ;;
        "green")
            echo -e "\033[0;32m$message\033[0m"
            ;;
        "yellow")
            echo -e "\033[0;33m$message\033[0m"
            ;;
        "blue")
            echo -e "\033[0;34m$message\033[0m"
            ;;
        *)
            echo "$message"
            ;;
    esac
}

# Function to print section header
print_section() {
    local message=$1
    echo ""
    print_color "blue" "========================================"
    print_color "blue" "  $message"
    print_color "blue" "========================================"
    echo ""
}

# Function to check if a command exists
command_exists() {
    command -v "$1" &> /dev/null
}

# Function to check if Android device is connected
check_device() {
    if ! command_exists adb; then
        print_color "red" "Error: ADB is not installed or not in PATH"
        print_color "yellow" "Please install Android SDK platform tools"
        return 1
    fi
    
    if [ -n "$DEVICE_ID" ]; then
        adb -s "$DEVICE_ID" get-state &> /dev/null
        if [ $? -ne 0 ]; then
            print_color "red" "Error: Device with ID $DEVICE_ID not found or not connected"
            return 1
        fi
        print_color "green" "Using device: $DEVICE_ID"
        return 0
    else
        # Check if any device is connected
        local devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
        if [ "$devices" -eq 0 ]; then
            print_color "red" "Error: No Android devices connected"
            print_color "yellow" "Please connect a device or specify device ID with --device-id"
            return 1
        elif [ "$devices" -gt 1 ]; then
            print_color "yellow" "Warning: Multiple devices connected, using first available device"
            print_color "yellow" "Consider specifying a device ID with --device-id"
            DEVICE_ID=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | cut -f 1)
            print_color "green" "Using device: $DEVICE_ID"
            return 0
        else
            DEVICE_ID=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | cut -f 1)
            print_color "green" "Using device: $DEVICE_ID"
            return 0
        fi
    fi
}

# Function to run Kotlin tests
run_kotlin_tests() {
    print_section "Running Kotlin Tests"
    
    # Set up test flags
    local test_flags=""
    local test_description=""
    
    # Handle different test types
    case "$TEST_TYPE" in
        "unit")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.*\" --exclude-tests \"com.anwu.customautojs.api.monitor.integration.*\""
            test_description="Running unit tests only"
            ;;
        "integration")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.integration.*\""
            test_description="Running integration tests only"
            ;;
        "stress")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.stress.*\""
            test_description="Running stress tests only"
            ;;
        "memory")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.memory.*\""
            test_description="Running memory leak tests only"
            ;;
        "performance")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.performance.*\""
            test_description="Running performance tests only"
            ;;
        "quality")
            test_flags="--tests \"com.anwu.customautojs.api.monitor.quality.*\""
            test_description="Running code quality tests only"
            ;;
        "all")
            if [ "$RUN_UNIT_TESTS" = true ] && [ "$RUN_INTEGRATION_TESTS" = true ]; then
                # Run all tests including specialized tests
                test_flags=""
                test_description="Running all tests"
            elif [ "$RUN_UNIT_TESTS" = true ]; then
                # Run only unit tests (exclude integration tests)
                test_flags="--tests \"com.anwu.customautojs.api.monitor.*\" --exclude-tests \"com.anwu.customautojs.api.monitor.integration.*\""
                test_description="Running unit tests only"
            elif [ "$RUN_INTEGRATION_TESTS" = true ]; then
                # Run only integration tests
                test_flags="--tests \"com.anwu.customautojs.api.monitor.integration.*\""
                test_description="Running integration tests only"
            fi
            ;;
        *)
            print_color "red" "Unknown test type: $TEST_TYPE"
            return 1
            ;;
    esac
    
    print_color "yellow" "$test_description"
    
    # Set up output flags
    local output_flags=""
    if [ "$VERBOSE" = true ]; then
        output_flags="--info"
    else
        output_flags="--quiet"
    fi
    
    # Set up report flags if report generation is enabled
    local report_flags=""
    if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
        # Create report directory if it doesn't exist
        mkdir -p "$REPORT_PATH"
        report_flags="--tests-xml-enabled=true --tests-xml-output-dir=\"$REPORT_PATH\""
        print_color "blue" "Test reports will be generated in: $REPORT_PATH"
    fi
    
    # Configure environment variables for tests
    local env_vars=""
    if [ "$SKIP_BENCHMARK" = true ]; then
        env_vars="SKIP_BENCHMARK=true"
    fi
    
    # Add any CI-mode specific configurations
    if [ "$CI_MODE" = true ]; then
        env_vars="$env_vars CI_MODE=true"
        # In CI mode, we want to fail fast and be more strict
        output_flags="$output_flags --fail-fast"
    fi
    
    # Execute Gradle test command
    echo "Running Gradle test command..."
    local test_command="cd \"$PROJECT_DIR\" && $env_vars ./gradlew :autojs-api:monitor:test $test_flags $report_flags $output_flags"
    
    if [ "$VERBOSE" = true ]; then
        print_color "yellow" "Executing: $test_command"
    fi
    
    # Execute the command
    eval "$test_command"
    
    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        print_color "green" "Kotlin tests completed successfully"
        
        # Generate HTML report if requested
        if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
            print_color "blue" "Generating HTML test report..."
            cd "$PROJECT_DIR" && ./gradlew jacocoTestReport
            if [ $? -eq 0 ]; then
                print_color "green" "HTML test report generated successfully"
                print_color "blue" "Report location: $PROJECT_DIR/build/reports/tests/test/index.html"
            else
                print_color "yellow" "Warning: Failed to generate HTML test report"
            fi
        fi
        
        return 0
    else
        print_color "red" "Kotlin tests failed with exit code $exit_code"
        
        # In CI mode, provide more detailed error output
        if [ "$CI_MODE" = true ]; then
            print_color "yellow" "Displaying test failure details (CI mode):"
            cd "$PROJECT_DIR" && ./gradlew :autojs-api:monitor:test --tests "$TEST_TYPE" --info
        fi
        
        return 1
    fi
}

# Function to run JavaScript tests on device
run_js_tests() {
    print_section "Running JavaScript Tests on Device"
    
    # Check device connection
    check_device
    if [ $? -ne 0 ]; then
        print_color "red" "Skipping JavaScript tests due to device connection issues"
        return 1
    fi
    
    # Prepare test files
    echo "Preparing JavaScript test files..."
    local test_dir="$PROJECT_DIR/src/test/resources"
    local js_test_files=(
        "$test_dir/test_recommendation_system.js"
        "$test_dir/test_runner.js"
    )
    
    # Create temporary directory on device
    echo "Creating temporary directory on device..."
    adb -s "$DEVICE_ID" shell "mkdir -p /sdcard/CustomAutoJS_Tests"
    
    # Push test files to device
    echo "Pushing test files to device..."
    for file in "${js_test_files[@]}"; do
        if [ -f "$file" ]; then
            local filename=$(basename "$file")
            echo "Pushing $filename..."
            adb -s "$DEVICE_ID" push "$file" "/sdcard/CustomAutoJS_Tests/$filename"
        else
            print_color "yellow" "Warning: Test file not found: $file"
        fi
    done
    
    # Configure test parameters based on test type
    local test_params=""
    local test_description=""
    
    case "$TEST_TYPE" in
        "unit" | "integration")
            test_params="runBenchmarkTests: false, runLongTests: false, runStressTests: false, testType: '$TEST_TYPE'"
            test_description="Running basic $TEST_TYPE JavaScript tests"
            ;;
        "stress")
            test_params="runBenchmarkTests: true, runLongTests: true, runStressTests: true, testType: 'stress'"
            test_description="Running JavaScript stress tests"
            ;;
        "memory")
            test_params="runBenchmarkTests: false, runLongTests: true, runMemoryTests: true, testType: 'memory'"
            test_description="Running JavaScript memory tests"
            ;;
        "performance")
            test_params="runBenchmarkTests: true, runLongTests: true, runPerformanceTests: true, testType: 'performance'"
            test_description="Running JavaScript performance tests"
            ;;
        "all")
            if [ "$SKIP_BENCHMARK" = true ]; then
                test_params="runBenchmarkTests: false, runLongTests: false, testType: 'all'"
            else
                test_params="runBenchmarkTests: true, runLongTests: false, testType: 'all'"
            fi
            test_description="Running all applicable JavaScript tests"
            ;;
        *)
            test_params="runBenchmarkTests: false, testType: 'basic'"
            test_description="Running basic JavaScript tests"
            ;;
    esac
    
    print_color "yellow" "$test_description"
    
    # Add script for visual test if requested
    if [ "${VISUAL_TEST:-false}" = true ]; then
        echo "Including visual recommendation test..."
        adb -s "$DEVICE_ID" push "$PROJECT_DIR/src/test/resources/visual_recommendation_test.js" "/sdcard/CustomAutoJS_Tests/visual_recommendation_test.js"
        test_params="$test_params, runVisualTests: true"
    fi
    
    # Add CI mode configuration if applicable
    if [ "$CI_MODE" = true ]; then
        test_params="$test_params, ciMode: true, skipPrompts: true"
    fi
    
    # Create test configuration file
    echo "Creating test configuration..."
    cat > "$PROJECT_DIR/src/test/resources/test_config.js" << EOL
// Test configuration generated by test runner
const testConfig = {
    ${test_params},
    testDelay: 1000,
    testTimeout: 30000,
    showUiTests: ${CI_MODE:-false} ? false : true,
    visualTest: {
        showAdvancedOptions: true,
        fullScreenMode: false,
        showMockControls: true,
        animationsEnabled: true,
        themeColor: "#2196F3"
    },
    performanceTest: {
        iterations: 100,
        warmupIterations: 10,
        recommendationCount: 10
    },
    stressTest: {
        iterationsPerThread: 50,
        randomSeed: 42,
        maxRecommendations: 200,
        stressTimeSeconds: 10
    },
    memoryTest: {
        iterations: 50,
        gcCycles: 3,
        memoryThresholdPercent: 10
    },
    reportPath: ${GENERATE_REPORT:-false} ? "${REPORT_PATH:-'/sdcard/CustomAutoJS_Tests/reports'}" : null
};
module.exports = testConfig;
EOL
    
    # Push configuration to device
    adb -s "$DEVICE_ID" push "$PROJECT_DIR/src/test/resources/test_config.js" "/sdcard/CustomAutoJS_Tests/test_config.js"
    
    # In CI mode, we need to automate the test run
    if [ "$CI_MODE" = true ]; then
        print_color "blue" "Running in CI mode - automating JavaScript test execution"
        
        # Create launcher script
        cat > "$PROJECT_DIR/src/test/resources/ci_launcher.js" << EOL
// CI Mode JavaScript test launcher
console.show();
console.log("Starting automated test run in CI mode");
console.log("Loading test runner...");

// Load the test configuration
const config = require('./test_config.js');
const runner = require('./test_runner.js');

// Run the tests
console.log("Executing tests...");
let result = runner.runTests();

// Write results to file for the test script to read
let resultSummary = "TEST_RESULT=" + (result.success ? "SUCCESS" : "FAILURE") + "\n";
resultSummary += "TESTS_RUN=" + result.testsRun + "\n";
resultSummary += "TESTS_PASSED=" + result.testsPassed + "\n";
resultSummary += "TESTS_FAILED=" + result.testsFailed + "\n";

files.write("/sdcard/CustomAutoJS_Tests/results.txt", resultSummary);
console.log("Test execution complete. Results saved.");

// Exit after 3 seconds
setTimeout(function() {
    console.hide();
    exit();
}, 3000);
EOL
        
        # Push launcher to device
        adb -s "$DEVICE_ID" push "$PROJECT_DIR/src/test/resources/ci_launcher.js" "/sdcard/CustomAutoJS_Tests/ci_launcher.js"
        
        # Launch script using CustomAutoJS if possible (device-specific, may need adjustments)
        print_color "blue" "Launching automated test script on device..."
        adb -s "$DEVICE_ID" shell "am start -n com.anwu.customautojs/.app.ui.LauncherActivity -a android.intent.action.MAIN -e 'scriptFile' '/sdcard/CustomAutoJS_Tests/ci_launcher.js' --activity-brought-to-front"
        
        # Wait for the test to complete
        print_color "blue" "Waiting for tests to complete..."
        
        # Poll for results file for up to 5 minutes
        local max_wait_seconds=300
        local waited=0
        local result_file_exists=0
        
        while [ $waited -lt $max_wait_seconds ]; do
            # Check if results file exists
            if adb -s "$DEVICE_ID" shell "[ -f /sdcard/CustomAutoJS_Tests/results.txt ] && echo exists" | grep -q "exists"; then
                result_file_exists=1
                break
            fi
            
            # Wait 5 seconds before checking again
            sleep 5
            waited=$((waited + 5))
            
            # Show progress dots
            printf "."
        done
        
        echo ""
        
        if [ $result_file_exists -eq 1 ]; then
            print_color "green" "Tests completed in $waited seconds"
            
            # Pull results file
            adb -s "$DEVICE_ID" pull "/sdcard/CustomAutoJS_Tests/results.txt" "$PROJECT_DIR/test-results.txt"
            
            # Display results
            if [ -f "$PROJECT_DIR/test-results.txt" ]; then
                print_color "blue" "Test Results:"
                cat "$PROJECT_DIR/test-results.txt"
                
                # Check if test was successful
                if grep -q "TEST_RESULT=SUCCESS" "$PROJECT_DIR/test-results.txt"; then
                    print_color "green" "JavaScript tests completed successfully"
                    # Clean up
                    rm -f "$PROJECT_DIR/test-results.txt"
                    echo "Cleaning up test files..."
                    adb -s "$DEVICE_ID" shell "rm -rf /sdcard/CustomAutoJS_Tests"
                    rm -f "$PROJECT_DIR/src/test/resources/test_config.js"
                    rm -f "$PROJECT_DIR/src/test/resources/ci_launcher.js"
                    return 0
                else
                    print_color "red" "JavaScript tests failed"
                    # Pull any report files if available
                    if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
                        mkdir -p "$REPORT_PATH/js"
                        adb -s "$DEVICE_ID" pull "/sdcard/CustomAutoJS_Tests/reports" "$REPORT_PATH/js" || true
                    fi
                    # Clean up
                    rm -f "$PROJECT_DIR/test-results.txt"
                    echo "Cleaning up test files..."
                    adb -s "$DEVICE_ID" shell "rm -rf /sdcard/CustomAutoJS_Tests"
                    rm -f "$PROJECT_DIR/src/test/resources/test_config.js"
                    rm -f "$PROJECT_DIR/src/test/resources/ci_launcher.js"
                    return 1
                fi
            else
                print_color "red" "Failed to retrieve test results file"
                return 1
            fi
        else
            print_color "red" "Test execution timed out after $max_wait_seconds seconds"
            return 1
        fi
    else
        # Interactive mode - ask user to run the tests manually
        print_color "yellow" "Please manually run the test_runner.js script on your device"
        print_color "yellow" "Test files are located in /sdcard/CustomAutoJS_Tests/"
        
        if [ "${VISUAL_TEST:-false}" = true ]; then
            print_color "yellow" "Also run visual_recommendation_test.js to test the UI components"
        fi
        
        print_color "yellow" "After completing the tests, press Enter to continue..."
        read -p ""
        
        # Prompt user for test results
        print_color "yellow" "Did all tests complete successfully? [y/n]"
        read -p "" test_success
        
        # Cleanup
        echo "Cleaning up test files..."
        adb -s "$DEVICE_ID" shell "rm -rf /sdcard/CustomAutoJS_Tests"
        rm -f "$PROJECT_DIR/src/test/resources/test_config.js"
        
        if [[ "$test_success" =~ ^[Yy]$ ]]; then
            print_color "green" "JavaScript tests completed successfully"
            return 0
        else
            print_color "red" "JavaScript tests reported as failed"
            return 1
        fi
    fi
}

# Main execution flow
print_section "CustomAutoJS Monitor Module Test Runner"
echo "Test configuration:"
echo "- Test type: $TEST_TYPE"
echo "- Run unit tests: $RUN_UNIT_TESTS"
echo "- Run integration tests: $RUN_INTEGRATION_TESTS"
echo "- Run Kotlin tests: $RUN_KOTLIN_TESTS"
echo "- Run JavaScript tests: $RUN_JS_TESTS"
echo "- Skip benchmark tests: $SKIP_BENCHMARK"
echo "- Visual tests: $VISUAL_TEST"
echo "- Generate report: $GENERATE_REPORT"
if [ "$GENERATE_REPORT" = true ]; then
    echo "- Report path: $REPORT_PATH"
fi
echo "- CI mode: $CI_MODE"
echo "- Verbose output: $VERBOSE"
echo "- Project directory: $PROJECT_DIR"

# Track overall success and test statistics
overall_success=true
kotlin_tests_run=0
kotlin_tests_passed=0
js_tests_run=0
js_tests_passed=0
start_time=$(date +%s)

# Create report directory if needed
if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
    mkdir -p "$REPORT_PATH"
    # Create an initial report overview file
    cat > "$REPORT_PATH/report_summary.md" << EOL
# CustomAutoJS Monitor Module Test Report

Test configuration:
- Test type: $TEST_TYPE
- Unit tests: $RUN_UNIT_TESTS
- Integration tests: $RUN_INTEGRATION_TESTS
- Kotlin tests: $RUN_KOTLIN_TESTS
- JavaScript tests: $RUN_JS_TESTS
- Skip benchmark tests: $SKIP_BENCHMARK
- Visual tests: $VISUAL_TEST
- CI mode: $CI_MODE

## Test Results

_Test execution in progress..._

EOL
fi

# Function to update report summary
update_report_summary() {
    if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        # Format duration as minutes:seconds
        local minutes=$((duration / 60))
        local seconds=$((duration % 60))
        local duration_formatted="${minutes}m ${seconds}s"
        
        # Calculate total statistics
        local total_tests_run=$((kotlin_tests_run + js_tests_run))
        local total_tests_passed=$((kotlin_tests_passed + js_tests_passed))
        local success_percentage=0
        if [ $total_tests_run -gt 0 ]; then
            success_percentage=$((total_tests_passed * 100 / total_tests_run))
        fi
        
        # Update the summary file
        cat > "$REPORT_PATH/report_summary.md" << EOL
# CustomAutoJS Monitor Module Test Report

Test configuration:
- Test type: $TEST_TYPE
- Unit tests: $RUN_UNIT_TESTS
- Integration tests: $RUN_INTEGRATION_TESTS
- Kotlin tests: $RUN_KOTLIN_TESTS
- JavaScript tests: $RUN_JS_TESTS
- Skip benchmark tests: $SKIP_BENCHMARK
- Visual tests: $VISUAL_TEST
- CI mode: $CI_MODE

## Test Results Summary

- Total tests run: $total_tests_run
- Tests passed: $total_tests_passed
- Success rate: ${success_percentage}%
- Test duration: $duration_formatted

### Kotlin Tests
- Tests run: $kotlin_tests_run
- Tests passed: $kotlin_tests_passed

### JavaScript Tests
- Tests run: $js_tests_run
- Tests passed: $js_tests_passed

## Detailed Results

EOL
        
        # Add overall result
        if [ "$overall_success" = true ]; then
            cat >> "$REPORT_PATH/report_summary.md" << EOL
✅ **ALL TESTS PASSED**
EOL
        else
            cat >> "$REPORT_PATH/report_summary.md" << EOL
❌ **SOME TESTS FAILED**

Please check the detailed logs for more information.
EOL
        fi
        
        # Add timestamp
        local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
        cat >> "$REPORT_PATH/report_summary.md" << EOL

---
Report generated on: $timestamp
EOL
    fi
}

# Run Kotlin tests if enabled
if [ "$RUN_KOTLIN_TESTS" = true ]; then
    kotlin_test_start_time=$(date +%s)
    run_kotlin_tests
    kotlin_test_result=$?
    kotlin_test_end_time=$(date +%s)
    kotlin_test_duration=$((kotlin_test_end_time - kotlin_test_start_time))
    
    # Update test statistics based on result files if available
    if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
        # Try to find JUnit XML result files
        if [ -d "$REPORT_PATH" ]; then
            xml_files=$(find "$REPORT_PATH" -name "TEST-*.xml" | wc -l)
            if [ $xml_files -gt 0 ]; then
                # Count passed and failed tests from XML files
                kotlin_tests_run=$(grep -l "<testsuite" "$REPORT_PATH"/TEST-*.xml | xargs grep "tests=" | awk -F'tests=' '{print $2}' | awk -F'"' '{sum += $2} END {print sum}')
                kotlin_tests_failed=$(grep -l "<testsuite" "$REPORT_PATH"/TEST-*.xml | xargs grep "failures=" | awk -F'failures=' '{print $2}' | awk -F'"' '{sum += $2} END {print sum}')
                kotlin_tests_passed=$((kotlin_tests_run - kotlin_tests_failed))
            else
                # Estimate based on exit code
                if [ $kotlin_test_result -eq 0 ]; then
                    kotlin_tests_run=1
                    kotlin_tests_passed=1
                else
                    kotlin_tests_run=1
                    kotlin_tests_passed=0
                fi
            fi
        fi
    else
        # Estimate based on exit code
        if [ $kotlin_test_result -eq 0 ]; then
            kotlin_tests_run=1
            kotlin_tests_passed=1
        else
            kotlin_tests_run=1
            kotlin_tests_passed=0
        fi
    fi
    
    if [ $kotlin_test_result -ne 0 ]; then
        overall_success=false
    fi
fi

# Run JavaScript tests if enabled
if [ "$RUN_JS_TESTS" = true ]; then
    js_test_start_time=$(date +%s)
    run_js_tests
    js_test_result=$?
    js_test_end_time=$(date +%s)
    js_test_duration=$((js_test_end_time - js_test_start_time))
    
    # Update test statistics based on results file
    if [ -f "$PROJECT_DIR/test-results.txt" ]; then
        js_tests_run=$(grep "TESTS_RUN=" "$PROJECT_DIR/test-results.txt" | cut -d"=" -f2)
        js_tests_passed=$(grep "TESTS_PASSED=" "$PROJECT_DIR/test-results.txt" | cut -d"=" -f2)
    else
        # Estimate based on exit code
        if [ $js_test_result -eq 0 ]; then
            js_tests_run=1
            js_tests_passed=1
        else
            js_tests_run=1
            js_tests_passed=0
        fi
    fi
    
    if [ $js_test_result -ne 0 ]; then
        overall_success=false
    fi
fi

# Update the report summary
update_report_summary

# Print final summary
print_section "Test Summary"
echo "Test statistics:"
echo "- Kotlin tests: $kotlin_tests_passed passed of $kotlin_tests_run run"
echo "- JavaScript tests: $js_tests_passed passed of $js_tests_run run"

# Calculate total run time
end_time=$(date +%s)
duration=$((end_time - start_time))
minutes=$((duration / 60))
seconds=$((duration % 60))

echo "- Total test duration: ${minutes}m ${seconds}s"

if [ "$GENERATE_REPORT" = true ] && [ -n "$REPORT_PATH" ]; then
    echo "- Test report generated at: $REPORT_PATH/report_summary.md"
fi

if [ "$overall_success" = true ]; then
    print_color "green" "All tests completed successfully"
    exit 0
else
    print_color "red" "Some tests failed, please check the logs for details"
    exit 1
fi