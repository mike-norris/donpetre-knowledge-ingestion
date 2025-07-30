package com.openrangelabs.donpetre.ingestion.connector.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.openrangelabs.donpetre.ingestion.connector.AbstractDataConnector;
import com.openrangelabs.donpetre.ingestion.model.RateLimitStatus;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.*;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GitHub connector implementation
 * Handles repositories, commits, issues, pull requests, and wiki content
 */
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
                    logger.warn("Failed to process GitHub item: {}", error.getMessage());
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
                logger.warn("Invalid sync cursor, performing full sync: {}", lastSyncCursor);
            }
        }

        SyncContext context = SyncContext.forIncrementalSync(lastSyncCursor,
                since != null ? since.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null);

        return fetchData(config, context)
                .doOnNext(item -> processedCount.incrementAndGet())
                .doOnError(error -> failedCount.incrementAndGet())
                .onErrorContinue((error, item) -> {
                    logger.warn("Failed to process GitHub item: {}", error.getMessage());
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
                        logger.error("GitHub connection test failed", e);
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
                        logger.error("Failed to get GitHub rate limit status", e);
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
                    try {
                        return org.listRepositories().iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list repositories", e);
                    }
                })
                .filter(repo -> shouldIncludeRepository(repo, config))
                .flatMap(repo -> fetchFromRepository(repo, config, context))
                .onErrorContinue((error, repo) -> {
                    logger.warn("Failed to process repository {}: {}", repo, error.getMessage());
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
                        logger.warn("Failed to get repository {}: {}", repoName, e.getMessage());
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
                    try {
                        PagedIterable<GHCommit> commits = repo.listCommits();
                        if (context.getLastSyncTime() != null) {
                            Date since = Date.from(context.getLastSyncTime().atZone(ZoneId.systemDefault()).toInstant());
                            commits = repo.listCommits().since(since);
                        }
                        return commits.iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list commits", e);
                    }
                })
                .map(commit -> createKnowledgeItemFromCommit(repo, commit))
                .onErrorContinue((error, commit) -> {
                    logger.warn("Failed to process commit {}: {}", commit, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchIssues(GHRepository repo, SyncContext context) {
        return Flux.fromIterable(() -> {
                    try {
                        PagedIterable<GHIssue> issues = repo.getIssues(GHIssueState.ALL);
                        return issues.iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list issues", e);
                    }
                })
                .filter(issue -> {
                    if (context.getLastSyncTime() == null) return true;
                    return issue.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            .isAfter(context.getLastSyncTime());
                })
                .map(issue -> createKnowledgeItemFromIssue(repo, issue))
                .onErrorContinue((error, issue) -> {
                    logger.warn("Failed to process issue {}: {}", issue, error.getMessage());
                });
    }

    private Flux<KnowledgeItem> fetchPullRequests(GHRepository repo, SyncContext context) {
        return Flux.fromIterable(() -> {
                    try {
                        PagedIterable<GHPullRequest> pullRequests = repo.getPullRequests(GHIssueState.ALL);
                        return pullRequests.iterator();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to list pull requests", e);
                    }
                })
                .filter(pr -> {
                    if (context.getLastSyncTime() == null) return true;
                    return pr.getUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            .isAfter(context.getLastSyncTime());
                })
                .map(pr -> createKnowledgeItemFromPullRequest(repo, pr))
                .onErrorContinue((error, pr) -> {
                    logger.warn("Failed to process pull request {}: {}", pr, error.getMessage());
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
                        logger.debug("Wiki fetching not implemented for repository: {}", repo.getFullName());
                        return Flux.<KnowledgeItem>empty();
                    } catch (IOException e) {
                        logger.warn("Failed to check wiki for repository {}: {}", repo.getFullName(), e.getMessage());
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
                        logger.debug("No README found for repository: {}", repo.getFullName());
                        return null;
                    }
                })
                .filter(item -> item != null)
                .flux();
    }

    private KnowledgeItem createKnowledgeItemFromCommit(GHRepository repo, GHCommit commit) {
        try {
            GHCommit.ShortInfo info = commit.getCommitShortInfo();

            return KnowledgeItem.builder()
                    .title("Commit: " + commit.getSHA1().substring(0, 8) + " - " + info.getMessage())
                    .content(info.getMessage())
                    .sourceType("github_commit")
                    .sourceReference(repo.getFullName() + "/commit/" + commit.getSHA1())
                    .author(info.getAuthor() != null ? info.getAuthor().getName() : "Unknown")
                    .createdAt(info.getCommitDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("sha", commit.getSHA1())
                    .addMetadata("url", commit.getHtmlUrl().toString())
                    .addMetadata("files_changed", commit.getFiles().size())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from commit", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromIssue(GHRepository repo, GHIssue issue) {
        try {
            StringBuilder content = new StringBuilder();
            content.append(issue.getBody() != null ? issue.getBody() : "");

            // Add comments
            for (GHIssueComment comment : issue.getComments()) {
                content.append("\n\n--- Comment by ").append(comment.getUser().getLogin())
                        .append(" ---\n").append(comment.getBody());
            }

            return KnowledgeItem.builder()
                    .title("Issue #" + issue.getNumber() + ": " + issue.getTitle())
                    .content(content.toString())
                    .sourceType("github_issue")
                    .sourceReference(repo.getFullName() + "/issues/" + issue.getNumber())
                    .author(issue.getUser().getLogin())
                    .createdAt(issue.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("issue_number", issue.getNumber())
                    .addMetadata("state", issue.getState().toString())
                    .addMetadata("url", issue.getHtmlUrl().toString())
                    .addMetadata("labels", issue.getLabels().stream().map(GHLabel::getName).toList())
                    .addMetadata("comments_count", issue.getCommentsCount())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from issue", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromPullRequest(GHRepository repo, GHPullRequest pr) {
        try {
            StringBuilder content = new StringBuilder();
            content.append(pr.getBody() != null ? pr.getBody() : "");

            // Add review comments
            for (GHPullRequestReviewComment comment : pr.listReviewComments()) {
                content.append("\n\n--- Review Comment by ").append(comment.getUser().getLogin())
                        .append(" ---\n").append(comment.getBody());
            }

            return KnowledgeItem.builder()
                    .title("PR #" + pr.getNumber() + ": " + pr.getTitle())
                    .content(content.toString())
                    .sourceType("github_pull_request")
                    .sourceReference(repo.getFullName() + "/pull/" + pr.getNumber())
                    .author(pr.getUser().getLogin())
                    .createdAt(pr.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("pr_number", pr.getNumber())
                    .addMetadata("state", pr.getState().toString())
                    .addMetadata("url", pr.getHtmlUrl().toString())
                    .addMetadata("mergeable", pr.getMergeable())
                    .addMetadata("additions", pr.getAdditions())
                    .addMetadata("deletions", pr.getDeletions())
                    .addMetadata("changed_files", pr.getChangedFiles())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from pull request", e);
        }
    }

    private KnowledgeItem createKnowledgeItemFromReadme(GHRepository repo, GHContent readme) {
        try {
            String content = readme.getContent();

            return KnowledgeItem.builder()
                    .title("README - " + repo.getFullName())
                    .content(content)
                    .sourceType("github_readme")
                    .sourceReference(repo.getFullName() + "/blob/" + repo.getDefaultBranch() + "/" + readme.getName())
                    .author("Repository")
                    .createdAt(LocalDateTime.now()) // README doesn't have creation date in API
                    .addMetadata("repository", repo.getFullName())
                    .addMetadata("file_name", readme.getName())
                    .addMetadata("url", readme.getHtmlUrl())
                    .addMetadata("size", readme.getSize())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create knowledge item from README", e);
        }
    }

    private boolean shouldIncludeRepository(GHRepository repo, JsonNode config) {
        try {
            // Check if forks should be included
            if (repo.isFork() && config.has("include_forks") && !config.get("include_forks").asBoolean()) {
                return false;
            }

            // Check if archived repositories should be included
            if (repo.isArchived() && config.has("include_archived") && !config.get("include_archived").asBoolean()) {
                return false;
            }

            return true;
        } catch (IOException e) {
            logger.warn("Failed to check repository properties for {}: {}", repo, e.getMessage());
            return false;
        }
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