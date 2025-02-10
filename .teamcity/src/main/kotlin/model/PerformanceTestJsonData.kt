package model

import com.fasterxml.jackson.annotation.JsonProperty

data class JsonPerformanceTestDuration(
    val scenario: String,
    val durations: List<JsonTestProjectDuration>
)

data class JsonTestProjectDuration(
    val testProject: String,
    val linux: Int? = null,
    val windows: Int? = null,
    @JsonProperty("macOs")
    val macOs: Int? = null
)

data class JsonPerformanceTestConfiguration(
    val testId: String,
    val groups: List<JsonTestProjectGroup>
)

data class JsonTestProjectGroup(
    val testProject: String,
    val comment: String? = null,
    val coverage: JsonCoverage
)

data class JsonCoverage(
    @JsonProperty("per_commit")
    val perCommit: List<String>? = null,
    @JsonProperty("per_day")
    val perDay: List<String>? = null,
    @JsonProperty("per_week") 
    val perWeek: List<String>? = null
) 