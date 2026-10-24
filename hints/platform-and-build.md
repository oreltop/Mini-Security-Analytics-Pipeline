# Platform & build decisions (personal hint)

## Java 21 LTS (not 17)
- Both are LTS; 21 brings **virtual threads (Project Loom)**, directly relevant to a
  high-throughput ingestion service ("millions of DLRs") — `spring.threads.virtual.enabled=true`.
- 25 is the newest LTS but too fresh; Spring Boot 3.3.5 supports Java up to 22, so 21 sits
  comfortably inside the window.
- *Alt rejected:* Java 17 (older baseline, no virtual threads). 22/23/24 are non-LTS feature releases.

## Maven (not Gradle)
- Most reviewer-readable; the Spring Boot default; declarative `pom.xml`.
- *Alt rejected:* Gradle (terser, but less universally readable for a take-home).

## Maven wrapper (mvnw)
- Committed so the project is **self-contained**: a reviewer needs only a JDK, not a Maven install.
- The Docker build uses the wrapper too, so the image builds with the same pinned Maven 3.9.9.

## Spring Boot 3.3.5
- Current, actively supported; pulls in Jackson 2.17, Tomcat 10.1, Hibernate Validator.
