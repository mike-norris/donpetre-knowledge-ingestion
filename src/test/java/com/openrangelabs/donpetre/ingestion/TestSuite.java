package com.openrangelabs.donpetre.ingestion;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test suite for running all knowledge ingestion tests
 * 
 * Usage:
 * - Run all tests: ./mvnw test
 * - Run specific suite: ./mvnw test -Dtest=TestSuite
 * - Run with coverage: ./mvnw test jacoco:report
 */
@Suite
@SuiteDisplayName("Knowledge Ingestion Service Test Suite")
@SelectPackages({
    "com.openrangelabs.donpetre.ingestion.service",
    "com.openrangelabs.donpetre.ingestion.controller", 
    "com.openrangelabs.donpetre.ingestion.connector",
    "com.openrangelabs.donpetre.ingestion.scheduler",
    "com.openrangelabs.donpetre.ingestion.integration"
})
public class TestSuite {
    // Test suite aggregator class
    // JUnit 5 will automatically discover and run all tests in the specified packages
}