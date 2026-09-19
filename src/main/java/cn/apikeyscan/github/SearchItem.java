package cn.apikeyscan.github;

public record SearchItem(
        long repositoryId,
        String repository,
        boolean fork,
        String path,
        String blobSha,
        String apiUrl,
        String htmlUrl
) {
    public String objectKey() {
        return repositoryId + "|" + path + "|" + blobSha;
    }
}

