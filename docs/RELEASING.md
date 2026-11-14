# Versions and releases

[README](../README.md)

## Commit versions

By default, Gradle derives the library version from the checkout's commit:

```text
dev-8f2c5e8a339a
```

The suffix is the first 12 hexadecimal characters of the full commit SHA. Different commits produce different versions without editing a version file. This applies to the JAR filename, sources JAR, and Maven publication metadata.

Both JAR manifests include `Implementation-Version` and the full `Weft-Commit`. Archive entries use reproducible ordering and omit filesystem timestamps.

These versions identify committed source. Uncommitted changes do not alter the version or add a dirty suffix; use a clean checkout for distributable artifacts. The build does not automatically upload a package or create a release for each commit.

When Git metadata is unavailable, supply the full SHA with `-PcommitSha=<full SHA>`. That override records the supplied provenance; it does not check out or verify the corresponding source tree. The release workflow passes the SHA of its checked-out commit explicitly.

## Stable release versions

`-PreleaseVersion=1.0.0` selects a stable version instead of the commit version. Accepted release versions use `MAJOR.MINOR.PATCH`, without a `v` prefix or leading zeroes. Prerelease identifiers are not currently accepted.

For example, release `1.0.0` produces:

```text
Weft-1.0.0.jar
Weft-1.0.0-sources.jar
Weft-1.0.0.pom
SHA256SUMS
```

The POM retains the existing Maven group, `com.github.arl1s.weft`, and artifact ID, `Weft`. The Java package is `com.github.ar1ls.weft`; the existing group spelling is intentionally preserved to avoid changing publication coordinates as part of versioning.

## Publish manually on GitHub

The [Release workflow](../.github/workflows/release.yml) has only a `workflow_dispatch` trigger. Pushing a commit or tag does not start it.

1. Commit and push the release changes. The workflow must exist on the default branch before GitHub offers its manual run control.
2. Open **Actions → Release → Run workflow** in the repository.
3. Select the branch or tag containing the source you intend to release.
4. Enter a version such as `1.0.0`, then start the workflow.

The workflow packages the selected commit with Java 21 on Windows, using `D:\.gradle` as `GRADLE_USER_HOME`. It assembles the main and sources JARs, generates the dependency POM, and computes SHA-256 checksums. Packaging uses `assemble`, not `build` or `test`; it does not run the test suite. Complete any testing you require before starting a release.

After successful packaging, a separate job creates a lightweight tag such as `v1.0.0` at that exact commit and publishes a GitHub Release with generated release notes and all four downloads. The packaging job has read-only repository permissions; only the publication job receives `contents: write`.

No personal access token is required: the jobs use the repository's `GITHUB_TOKEN`. Repository rules must permit that token to create the release tag. Release runs are serialized, and an existing version tag is rejected before packaging. Published versions are not overwritten automatically.

A publication or asset-upload failure can leave a tag or partial release on GitHub. Inspect that state before retrying; the workflow does not automatically delete or replace it. Packaged artifacts are also retained in the workflow run for seven days.

## Dependency distribution

GitHub Release downloads are regular library artifacts, not a Maven repository. The main JAR does not bundle its dependencies. The attached POM records them, but downloading it beside the JAR does not make Gradle resolve them automatically.

For a local Maven repository, the existing `publishToMavenLocal` task uses the selected commit or release version. A remote Maven repository such as GitHub Packages or Maven Central needs separate repository and credential configuration; this workflow does not publish to one.
