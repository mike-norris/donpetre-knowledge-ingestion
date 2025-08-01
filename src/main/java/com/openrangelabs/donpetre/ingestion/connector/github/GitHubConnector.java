package com.openrangelabs.donpetre.ingestion.connector.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.openrangelabs.donpetre.ingestion.connector.AbstractDataConnector;
import com.openrangelabs.donpetre.ingestion.model.RateLimitStatus;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.*;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * GitHub connector implementation
 * Handles repositories, commits, issues, pull requests, and wiki content
 */
@Slf4j
@Component
public class GitHubConnector extends AbstractDataConnector {

    private static final String CONNECTOR_TYPE = "github";

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public Mono<Void> validateConfiguration(ConnectorConfig config) {
        return Mono.fromCallable(() -> {
            JsonNode configNode = config.getConfiguration();

            // Validate required fields
            if (!configNode.has("base_url")) {
                throw new IllegalArgumentException("GitHub base_url is required");
            }

            // Validate organization or repositories are specified
            if (!configNode.has("organization") && !configNode.has("repositories")) {
                throw new IllegalArgumentException("Either organization or repositories must be specified");
            }

            return null;
        });
    }

    @Override
    public Flux<KnowledgeItem> fetchData(ConnectorConfig config, SyncContext context) {
        return credentialService.getDecryptedCredential(config.getId(), "api_token")
                .flatMapMany(token -> {
                    try {
                        GitHub github = createGitHubClient(config, token);
                        return fetchFromGitHub(github, config, context);
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Failed to create GitHub client", e));
                    }
                });
    }

    @Override
    protected Mono<SyncResult> doFullSync(ConnectorConfig config) {
        LocalDateTime startTime = LocalDateTime.now();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        return fetchData(config, SyncContext.forFullSync())
                .doOnNext(item -> processedCount.incrementAndGet())
                .doOnError(error -> failedCount.incrementAndGet())
                .onErrorContinue((error, item) -> {
                    log.warn("Failed to process GitHub item: {}", error.getMessage());
                })
                .then(Mono.fromCallable(() ->
                        SyncResult.builder(CONNECTOR_TYPE, SyncType.FULL)
                                .startTime(startTime)
                                .processedCount(processedCount.get())
                                .failedCount(failedCount.get())
                                .build()
                ));
    }

    @Override
    protected Mono<SyncResult> doIncrementalSync(ConnectorConfig config, String lastSyncCursor) {
        LocalDateTime startTime = LocalDateTime.now();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        Date since = null;
        if (lastSyncCursor != null) {
            try {
                since = Date.from(LocalDateTime.parse(lastSyncCursor)
                        .atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception e) {
                log.warn("Invalid sync cursor, performing full sync: {}", lastSyncCursor);
            }
        }

        SyncContext context = SyncContext.forIncrementalSync(lastSyncCursor,
                since != null ? since.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);

        return fetchData(config, context)
                .doOnNext(item -> processedCount.incrementAndGet())
                .doOnError(error -> failedCount.incrementAndGet())
                .onErrorContinue((error, item) -> {
                    log.warn("Failed to process GitHub item: {}", error.getMessage());
                })
                .then(Mono.fromCallable(() ->
                        SyncResult.builder(CONNECTOR_TYPE, SyncType.INCREMENTAL)
                                .startTime(startTime)
                                .processedCount(processedCount.get())
                                .failedCount(failedCount.get())
                                .nextCursor(LocalDateTime.now().toString())
                                .build()
                ));
    }

    @Override
    public Mono<Boolean> testConnection(ConnectorConfig config) {
        return credentialService.getDecryptedCredential(config.getId(), "api_token")
                .flatMap(token -> Mono.fromCallable(() -> {
                    try {
                        GitHub github = createGitHubClient(config, token);
                        github.checkApiUrlValidity();
                        return true;
                    } catch (Exception e) {
                        log.error("GitHub connection test failed", e);
                        return false;
                    }
                }));
    }

    @Override
    public Mono<RateLimitStatus> getRateLimitStatus(ConnectorConfig config) {
        return credentialService.getDecryptedCredential(config.getId(), "api_token")
                .flatMap(token -> Mono.fromCallable(() -> {
                    try {
                        GitHub github = createGitHubClient(config, token);
                        GHRateLimit rateLimit = github.getRateLimit();

                        return new RateLimitStatus(
                                rateLimit.getLimit(),
                                rateLimit.getRemaining(),
                                rateLimit.getResetDate().toInstant()
                                        .atZone(ZoneId.systemDefault()).toLocalDateTime(),
                                "core"
                        );
                    } catch (Exception e) {
                        log.error("Failed to get GitHub rate limit status", e);
                        return new RateLimitStatus(0, 0, LocalDateTime.now(), "unknown");
                    }
                }));
    }

    private GitHub createGitHubClient(ConnectorConfig config, String token) throws IOException {
        JsonNode configNode = config.getConfiguration();
        String baseUrl = configNode.get("base_url").asText();

        if ("https://api.github.com".equals(baseUrl)) {
            return new GitHubBuilder().withOAuthToken(token).build();
        } else {
            // GitHub Enterprise
            return new GitHubBuilder()
                    .withEndpoint(baseUrl)
                    .withOAuthToken(token)
                    .build();
        }
    }

    private Flux<KnowledgeItem> fetchFromGitHub(GitHub github, ConnectorConfig config, SyncContext context) {
        JsonNode configNode = config.getConfiguration();

        try {
            if (configNode.has("organization")) {
                String orgName = configNode.get("organization").asText();
                GHOrganization org = github.getOrganization(orgName);
                return fetchFromOrganization(org, configNode, context);
            } else if (configNode.has("repositories")) {
                return fetchFromRepositoryList(github, configNode, context);
            } else {
                return Flux.error(new IllegalArgumentException("No organization or repositories specified"));
            }
        } catch (Exception e) {
            return Flux.error(new RuntimeException("Failed to fetch from GitHub", e));
        }
    }

    private Flux<KnowledgeItem> fetchFromOrganization(GHOrganization org, JsonNode config, SyncContext context) {
        return Flux.fromIterable(() -> {
                    // No IOException thrown here - just creates an iterator
                    return org.listRepositories().iterator();
                })
                .filter(repo -> shouldIncludeRepository(repo, config))
                .flatMap(repo -> fetchFromRepository(repo, config, context))
                .onErrorContinue((error, repo) -> {
                    log.warn("Failed to process repository {}: {}", repo, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchFromRepositoryList(GitHub github, JsonNode config, SyncContext context) {
        JsonNode repositories = config.get("repositories");
        return Flux.fromIterable(repositories)
                .map(JsonNode::asText)
                .flatMap(repoName -> {
                    try {
                        GHRepository repo = github.getRepository(repoName);
                        return fetchFromRepository(repo, config, context);
                    } catch (IOException e) {
                        log.warn("Failed to get repository {}: {}", repoName, e.getMessage());
                        return Flux.empty();
                    }
                });
    }

    private Flux<KnowledgeItem> fetchFromRepository(GHRepository repo, JsonNode config, SyncContext context) {
        JsonNode dataTypes = config.get("data_types");

        return Flux.concat(
                shouldFetchDataType(dataTypes, "commits") ? fetchCommits(repo, context) : Flux.empty(),
                shouldFetchDataType(dataTypes, "issues") ? fetchIssues(repo, context) : Flux.empty(),
                shouldFetchDataType(dataTypes, "pull_requests") ? fetchPullRequests(repo, context) : Flux.empty(),
                shouldFetchDataType(dataTypes, "wiki") ? fetchWiki(repo, context) : Flux.empty(),
                shouldFetchDataType(dataTypes, "readme") ? fetchReadme(repo, context) : Flux.empty()
        );
    }

    private Flux<KnowledgeItem> fetchCommits(GHRepository repo, SyncContext context) {
        return Flux.fromIterable(() -> {
                    // No IOException thrown here - just creates an iterator
                    PagedIterable<GHCommit> commits;
                    if (context.getLastSyncTime() != null) {
                        Date since = Date.from(context.getLastSyncTime().atZone(ZoneId.systemDefault()).toInstant());
                        // Fixed: Use queryCommits() instead of listCommits().since()
                        commits = repo.queryCommits().since(since).list();
                    } else {
                        commits = repo.listCommits();
                    }
                    return commits.iterator();
                })
                .map(commit -> createKnowledgeItemFromCommit(repo, commit))
                .onErrorContinue((error, commit) -> {
                    log.warn("Failed to process commit {}: {}", commit, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchIssues(GHRepository repo, SyncContext context) {
        return Flux.fromIterable(() -> {
                    try {
                        // This DOES throw IOException according to the method signature
                        List<GHIssue> issues = repo.getIssues(GHIssueState.ALL);
                        return issues.iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list issues", e);
                    }
                })
                .filter(issue -> {
                    if (context.getLastSyncTime() == null) return true;
                    try {
                        // issue.getUpdatedAt() can also throw IOException
                        return issue.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                .isAfter(context.getLastSyncTime());
                    } catch (IOException e) {
                        log.warn("Failed to get updated date for issue {}: {}", issue, e.getMessage());
                        return true; // Include the issue if we can't determine the date
                    }
                })
                .map(issue -> createKnowledgeItemFromIssue(repo, issue))
                .onErrorContinue((error, issue) -> {
                    log.warn("Failed to process issue {}: {}", issue, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchPullRequests(GHRepository repo, SyncContext context) {
        return Flux.fromIterable(() -> {
                    try {
                        // This likely also throws IOException - need to check method signature
                        List<GHPullRequest> pullRequests = repo.getPullRequests(GHIssueState.ALL);
                        return pullRequests.iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list pull requests", e);
                    }
                })
                .filter(pr -> {
                    if (context.getLastSyncTime() == null) return true;
                    try {
                        // pr.getUpdatedAt() can also throw IOException
                        return pr.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                .isAfter(context.getLastSyncTime());
                    } catch (IOException e) {
                        log.warn("Failed to get updated date for PR {}: {}", pr, e.getMessage());
                        return true; // Include the PR if we can't determine the date
                    }
                })
                .map(pr -> createKnowledgeItemFromPullRequest(repo, pr))
                .onErrorContinue((error, pr) -> {
                    log.warn("Failed to process pull request {}: {}", pr, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchWiki(GHRepository repo, SyncContext context) {
        return Mono.fromCallable(() -> {
                    try {
                        if (!repo.hasWiki()) {
                            return Flux.<KnowledgeItem>empty();
                        }

                        // Note: GitHub API doesn't provide direct wiki access
                        // This would need to be implemented using Git API or separate wiki repository
                        log.debug("Wiki fetching not implemented for repository: {}", repo.getFullName());
                        return Flux.<KnowledgeItem>empty();
                    } catch (Exception e) {
                        log.warn("Failed to check wiki for repository {}: {}", repo.getFullName(), e.getMessage());
                        return Flux.<KnowledgeItem>empty();
                    }
                })
                .flatMapMany(flux -> flux);
    }

    private Flux<KnowledgeItem> fetchReadme(GHRepository repo, SyncContext context) {
        return Mono.fromCallable(() -> {
                    try {
                        GHContent readme = repo.getReadme();
                        return createKnowledgeItemFromReadme(repo, readme);
                    } catch (IOException e) {
                        log.debug("No README found for repository: {}", repo.getFullName());
                        return null;
                    }
                })
                .filter(item -> item != null)
                .flux();
    }

    private KnowledgeItem createKnowledgeItemFromCommit(GHRepository repo, GHCommit commit) {
        try {
            // Network calls that throw IOException - get them all first
            GHCommit.ShortInfo info = commit.getCommitShortInfo();
            int filesChanged = commit.getFiles().size();
            String htmlUrl = commit.getHtmlUrl().toString();

            // Safe property access
            return KnowledgeItem.builder()
                    .title("Commit: " + commit.getSHA1().substring(0, 8) + " - " + info.getMessage())
                    .content(info.getMessage())
                    .sourceType("github_commit")
                    .sourceReference(repo.getFullName() + "/commit/" + commit.getSHA1())
                    .author(info.getAuthor() != null ? info.getAuthor().getName() : "Unknown")
                    .createdAt(info.getCommitDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("sha", commit.getSHA1())
                    .addMetadata("url", htmlUrl)
                    .addMetadata("files_changed", filesChanged)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from commit", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromIssue(GHRepository repo, GHIssue issue) {
        try {
            // Network calls that throw IOException - get them all first
            List<GHIssueComment> comments = issue.getComments();
            Date createdAt = issue.getCreatedAt();
            GHUser user = issue.getUser();
            String htmlUrl = issue.getHtmlUrl().toString();
            Collection<GHLabel> labels = issue.getLabels();
            int commentsCount = issue.getCommentsCount();

            // Safe property access
            StringBuilder content = new StringBuilder();
            content.append(issue.getBody() != null ? issue.getBody() : "");

            // Add comments with comprehensive null safety
            if (comments != null) {
                for (GHIssueComment comment : comments) {
                    try {
                        GHUser commentUser = comment.getUser();
                        String commentBody = comment.getBody();
                        if (commentUser != null && commentBody != null) {
                            content.append("\n\n--- Comment by ").append(commentUser.getLogin())
                                    .append(" ---\n").append(commentBody);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to get comment details: {}", e.getMessage());
                    }
                }
            }

            // Process labels safely - getName() doesn't throw IOException
            List<String> labelNames = labels.stream()
                    .map(GHLabel::getName)
                    .collect(Collectors.toList());

            return KnowledgeItem.builder()
                    .title("Issue #" + issue.getNumber() + ": " + issue.getTitle())
                    .content(content.toString())
                    .sourceType("github_issue")
                    .sourceReference(repo.getFullName() + "/issues/" + issue.getNumber())
                    .author(user != null ? user.getLogin() : "Unknown")
                    .createdAt(createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("issue_number", issue.getNumber())
                    .addMetadata("state", issue.getState().toString())
                    .addMetadata("url", htmlUrl)
                    .addMetadata("labels", labelNames)
                    .addMetadata("comments_count", commentsCount)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from issue", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromPullRequest(GHRepository repo, GHPullRequest pr) {
        try {
            // Network calls that throw IOException - get them all first
            PagedIterable<GHPullRequestReviewComment> reviewComments = pr.listReviewComments();
            Boolean mergeable = pr.getMergeable();
            int additions = pr.getAdditions();
            int deletions = pr.getDeletions();
            int changedFiles = pr.getChangedFiles();
            Date createdAt = pr.getCreatedAt();
            GHUser user = pr.getUser();
            String htmlUrl = pr.getHtmlUrl().toString();

            // Safe property access
            StringBuilder content = new StringBuilder();
            content.append(pr.getBody() != null ? pr.getBody() : "");

            // Add review comments with comprehensive null safety
            if (reviewComments != null) {
                for (GHPullRequestReviewComment comment : reviewComments) {
                    try {
                        GHUser commentUser = comment.getUser();
                        String commentBody = comment.getBody();
                        if (commentUser != null && commentBody != null) {
                            content.append("\n\n--- Review Comment by ").append(commentUser.getLogin())
                                    .append(" ---\n").append(commentBody);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to get review comment details: {}", e.getMessage());
                    }
                }
            }

            return KnowledgeItem.builder()
                    .title("PR #" + pr.getNumber() + ": " + pr.getTitle())
                    .content(content.toString())
                    .sourceType("github_pull_request")
                    .sourceReference(repo.getFullName() + "/pull/" + pr.getNumber())
                    .author(user != null ? user.getLogin() : "Unknown")
                    .createdAt(createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("pr_number", pr.getNumber())
                    .addMetadata("state", pr.getState().toString())
                    .addMetadata("url", htmlUrl)
                    .addMetadata("mergeable", mergeable)
                    .addMetadata("additions", additions)
                    .addMetadata("deletions", deletions)
                    .addMetadata("changed_files", changedFiles)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from pull request", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromReadme(GHRepository repo, GHContent readme) {
        try {
            // Network calls that throw IOException - get them all first
            String content = readme.getContent();
            String defaultBranch = repo.getDefaultBranch();
            String htmlUrl = readme.getHtmlUrl();

            // Safe property access
            return KnowledgeItem.builder()
                    .title("README - " + repo.getFullName())
                    .content(content)
                    .sourceType("github_readme")
                    .sourceReference(repo.getFullName() + "/blob/" + defaultBranch + "/" + readme.getName())
                    .author("Repository")
                    .createdAt(LocalDateTime.now()) // README doesn't have creation date in API
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("file_name", readme.getName())
                    .addMetadata("url", htmlUrl)
                    .addMetadata("size", readme.getSize())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from README", e);
        }
    }

    // Fixed: Removed unnecessary IOException catch - these are cached properties
    private boolean shouldIncludeRepository(GHRepository repo, JsonNode config) {
        // Check if forks should be included
        if (repo.isFork() && config.has("include_forks") && !config.get("include_forks").asBoolean()) {
            return false;
        }

        // Check if archived repositories should be included
        if (repo.isArchived() && config.has("include_archived") && !config.get("include_archived").asBoolean()) {
            return false;
        }

        return true;
    }

    private boolean shouldFetchDataType(JsonNode dataTypes, String dataType) {
        if (dataTypes == null || !dataTypes.isArray()) {
            return true; // Default to including all data types
        }

        for (JsonNode type : dataTypes) {
            if (dataType.equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}