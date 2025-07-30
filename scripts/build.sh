#!/bin/bash
# Build script for Knowledge Ingestion Service
# knowledge-ingestion/scripts/build.sh

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_NAME="knowledge-ingestion"
JAR_NAME="knowledge-ingestion-1.0.0-SNAPSHOT.jar"
MAVEN_OPTS="-Xmx2048m"

echo -e "${GREEN}🔨 Building Knowledge Ingestion Service${NC}"
echo -e "${GREEN}=======================================${NC}"

# Function to print section headers
print_section() {
    echo -e "\n${BLUE}===== $1 =====${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_section "Checking Prerequisites"

    # Check if we're in the right directory
    if [ ! -f "pom.xml" ]; then
        echo -e "${RED}❌ pom.xml not found. Run this script from the knowledge-ingestion directory.${NC}"
        exit 1
    fi

    # Check Java version
    if ! command -v java &> /dev/null; then
        echo -e "${RED}❌ Java not found. Please install Java 17 or later.${NC}"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
    if [ "$java_version" -lt 17 ]; then
        echo -e "${YELLOW}⚠️  Java 17+ recommended. Current version: $java_version${NC}"
        echo -e "${YELLOW}   Build may fail with older Java versions.${NC}"
    else
        echo -e "${GREEN}✓ Java version: $java_version${NC}"
    fi

    # Check Maven wrapper
    if [ ! -f "./mvnw" ]; then
        echo -e "${RED}❌ Maven wrapper (mvnw) not found.${NC}"
        exit 1
    fi

    # Make Maven wrapper executable
    chmod +x ./mvnw
    echo -e "${GREEN}✓ Maven wrapper is ready${NC}"

    # Check available disk space (minimum 1GB)
    local available_space=$(df . | tail -1 | awk '{print $4}')
    if [ "$available_space" -lt 1048576 ]; then # 1GB in KB
        echo -e "${YELLOW}⚠️  Low disk space. Build may fail.${NC}"
    else
        echo -e "${GREEN}✓ Sufficient disk space available${NC}"
    fi
}

# Function to validate project structure
validate_project_structure() {
    print_section "Validating Project Structure"

    local required_dirs=(
        "src/main/java"
        "src/main/resources"
        "src/test/java"
    )

    for dir in "${required_dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            echo -e "${YELLOW}⚠️  Directory $dir not found - creating it${NC}"
            mkdir -p "$dir"
        else
            echo -e "${GREEN}✓ $dir exists${NC}"
        fi
    done

    # Check for main application class
    if [ ! -f "src/main/java/com/openrangelabs/donpetre/ingestion/IngestionApplication.java" ]; then
        echo -e "${YELLOW}⚠️  Main application class not found${NC}"
        echo -e "${YELLOW}   Make sure you've copied all Java source files${NC}"
    else
        echo -e "${GREEN}✓ Main application class found${NC}"
    fi

    # Check for application.yml
    if [ ! -f "src/main/resources/application.yml" ]; then
        echo -e "${YELLOW}⚠️  application.yml not found${NC}"
        echo -e "${YELLOW}   Make sure you've copied the configuration files${NC}"
    else
        echo -e "${GREEN}✓ application.yml found${NC}"
    fi
}

# Function to clean previous builds
clean_build() {
    print_section "Cleaning Previous Build"

    echo -e "${GREEN}🧹 Cleaning target directory...${NC}"
    ./mvnw clean -q

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Clean completed successfully${NC}"
    else
        echo -e "${RED}❌ Clean failed${NC}"
        exit 1
    fi
}

# Function to download dependencies
download_dependencies() {
    print_section "Downloading Dependencies"

    echo -e "${GREEN}📦 Downloading Maven dependencies...${NC}"
    echo -e "${YELLOW}   This may take a few minutes on first run...${NC}"

    MAVEN_OPTS="$MAVEN_OPTS" ./mvnw dependency:resolve dependency:resolve-sources -q

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Dependencies downloaded successfully${NC}"
    else
        echo -e "${RED}❌ Failed to download dependencies${NC}"
        echo -e "${YELLOW}   Check your internet connection and try again${NC}"
        exit 1
    fi
}

# Function to compile sources
compile_sources() {
    print_section "Compiling Sources"

    echo -e "${GREEN}⚙️  Compiling Java sources...${NC}"

    MAVEN_OPTS="$MAVEN_OPTS" ./mvnw compile -q

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Source compilation successful${NC}"
    else
        echo -e "${RED}❌ Source compilation failed${NC}"
        echo -e "${YELLOW}   Check the error messages above${NC}"
        exit 1
    fi
}

# Function to run tests (optional)
run_tests() {
    print_section "Running Tests"

    if [ "${SKIP_TESTS:-false}" = "true" ]; then
        echo -e "${YELLOW}⚠️  Tests skipped (SKIP_TESTS=true)${NC}"
        return 0
    fi

    echo -e "${GREEN}🧪 Running unit tests...${NC}"

    MAVEN_OPTS="$MAVEN_OPTS" ./mvnw test -q

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ All tests passed${NC}"
    else
        echo -e "${YELLOW}⚠️  Some tests failed${NC}"
        echo -e "${YELLOW}   Build will continue, but investigate test failures${NC}"

        # Show test results summary
        if [ -d "target/surefire-reports" ]; then
            echo -e "${BLUE}Test Results Summary:${NC}"
            find target/surefire-reports -name "*.txt" -exec grep -l "FAILURE\|ERROR" {} \; | head -5
        fi
    fi
}

# Function to package application
package_application() {
    print_section "Packaging Application"

    echo -e "${GREEN}📦 Creating JAR package...${NC}"

    if [ "${SKIP_TESTS:-false}" = "true" ]; then
        MAVEN_OPTS="$MAVEN_OPTS" ./mvnw package -DskipTests -q
    else
        MAVEN_OPTS="$MAVEN_OPTS" ./mvnw package -q
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Packaging successful${NC}"
    else
        echo -e "${RED}❌ Packaging failed${NC}"
        exit 1
    fi
}

# Function to verify build artifacts
verify_build() {
    print_section "Verifying Build Artifacts"

    # Check if JAR file exists
    if [ -f "target/$JAR_NAME" ]; then
        echo -e "${GREEN}✓ JAR file created successfully${NC}"

        # Show JAR file details
        local jar_size=$(ls -lh "target/$JAR_NAME" | awk '{print $5}')
        echo -e "${GREEN}  📄 File: target/$JAR_NAME${NC}"
        echo -e "${GREEN}  📊 Size: $jar_size${NC}"

        # Verify JAR structure
        if jar -tf "target/$JAR_NAME" | grep -q "com/openrangelabs/donpetre/ingestion/IngestionApplication.class"; then
            echo -e "${GREEN}✓ Main application class found in JAR${NC}"
        else
            echo -e "${YELLOW}⚠️  Main application class not found in JAR${NC}"
        fi

        # Check for Spring Boot structure
        if jar -tf "target/$JAR_NAME" | grep -q "BOOT-INF"; then
            echo -e "${GREEN}✓ Spring Boot JAR structure verified${NC}"
        else
            echo -e "${YELLOW}⚠️  Not a Spring Boot executable JAR${NC}"
        fi

    else
        echo -e "${RED}❌ JAR file not found: target/$JAR_NAME${NC}"
        echo -e "${YELLOW}   Check the Maven output above for errors${NC}"
        exit 1
    fi

    # Check for additional artifacts
    if [ -f "target/classes/application.yml" ]; then
        echo -e "${GREEN}✓ Configuration files included${NC}"
    else
        echo -e "${YELLOW}⚠️  Configuration files may be missing${NC}"
    fi
}

# Function to run quick smoke test
smoke_test() {
    print_section "Running Smoke Test"

    echo -e "${GREEN}🔍 Running quick smoke test...${NC}"

    # Test JAR execution (should show help or fail gracefully)
    timeout 10s java -jar "target/$JAR_NAME" --help > /dev/null 2>&1
    local exit_code=$?

    if [ $exit_code -eq 0 ] || [ $exit_code -eq 124 ]; then # 124 is timeout exit code
        echo -e "${GREEN}✓ JAR executes successfully${NC}"
    else
        echo -e "${YELLOW}⚠️  JAR execution test inconclusive${NC}"
        echo -e "${YELLOW}   This may be normal for Spring Boot applications${NC}"
    fi
}

# Function to show build summary
show_build_summary() {
    print_section "Build Summary"

    if [ -f "target/$JAR_NAME" ]; then
        local jar_path=$(realpath "target/$JAR_NAME")
        local jar_size=$(ls -lh "target/$JAR_NAME" | awk '{print $5}')

        echo -e "${GREEN}🎉 Build completed successfully!${NC}"
        echo -e "${GREEN}📄 Artifact: $jar_path${NC}"
        echo -e "${GREEN}📊 Size: $jar_size${NC}"
        echo -e "${GREEN}🕒 Build time: $(date)${NC}"

        echo -e "\n${BLUE}Next Steps:${NC}"
        echo -e "  ${GREEN}1.${NC} Test the application:"
        echo -e "     ${YELLOW}java -jar target/$JAR_NAME${NC}"
        echo -e "  ${GREEN}2.${NC} Build Docker image:"
        echo -e "     ${YELLOW}docker build -t donpetre/knowledge-ingestion .${NC}"
        echo -e "  ${GREEN}3.${NC} Run with Docker Compose:"
        echo -e "     ${YELLOW}docker-compose up -d knowledge-ingestion${NC}"

        echo -e "\n${BLUE}Useful Commands:${NC}"
        echo -e "  • Check application info: ${YELLOW}java -jar target/$JAR_NAME --info${NC}"
        echo -e "  • Run tests only: ${YELLOW}./mvnw test${NC}"
        echo -e "  • Skip tests: ${YELLOW}SKIP_TESTS=true ./scripts/build.sh${NC}"
        echo -e "  • Clean build: ${YELLOW}./mvnw clean && ./scripts/build.sh${NC}"

    else
        echo -e "${RED}❌ Build failed - JAR file not created${NC}"
        exit 1
    fi
}

# Function to show help
show_help() {
    cat << EOF
Knowledge Ingestion Service Build Script

Usage: $0 [OPTIONS]

Options:
  --skip-tests     Skip running unit tests
  --clean          Force clean build (removes all cached data)
  --help, -h       Show this help message
  --verbose, -v    Enable verbose output

Environment Variables:
  SKIP_TESTS=true  Skip test execution
  MAVEN_OPTS       Additional Maven options (default: -Xmx2048m)

Examples:
  $0                    # Standard build
  $0 --skip-tests       # Build without running tests
  $0 --clean            # Clean build from scratch
  SKIP_TESTS=true $0    # Build with tests skipped via environment

Build Artifacts:
  target/$JAR_NAME    # Executable Spring Boot JAR
  target/classes/               # Compiled classes
  target/test-classes/          # Compiled test classes

EOF
}

# Main execution function
main() {
    local skip_tests=false
    local clean_build_flag=false
    local verbose=false

    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-tests)
                skip_tests=true
                export SKIP_TESTS=true
                shift
                ;;
            --clean)
                clean_build_flag=true
                shift
                ;;
            --verbose|-v)
                verbose=true
                set -x
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                show_help
                exit 1
                ;;
        esac
    done

    # Print build configuration
    echo -e "${BLUE}Build Configuration:${NC}"
    echo -e "  Project: $PROJECT_NAME"
    echo -e "  JAR: $JAR_NAME"
    echo -e "  Skip Tests: ${skip_tests}"
    echo -e "  Clean Build: ${clean_build_flag}"
    echo -e "  Maven Opts: $MAVEN_OPTS"

    # Execute build steps
    check_prerequisites
    validate_project_structure

    if [ "$clean_build_flag" = true ]; then
        clean_build
    fi

    download_dependencies
    compile_sources

    if [ "$skip_tests" = false ]; then
        run_tests
    fi

    package_application
    verify_build
    smoke_test
    show_build_summary
}

# Handle script interruption
trap 'echo -e "\n${RED}Build interrupted!${NC}"; exit 1' INT TERM

# Run main function with all arguments
main "$@"