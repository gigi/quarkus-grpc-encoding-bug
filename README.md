# code-with-quarkus

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## gRPC Compression Bug Testing

This repository demonstrates a bug in Quarkus gRPC server compression. See test results below.

### Quick Test

```bash
# Start the server
./mvnw quarkus:dev

# In another terminal, run the compression test
./test-compression.sh
```

### Bug Summary

**Issue**: Server with `quarkus.grpc.server.compression=gzip` does NOT compress responses even when client accepts gzip.

**Evidence**: When client sends `grpc-accept-encoding: gzip`, server responds WITHOUT `grpc-encoding: gzip` header.

### Documentation

- **[QUICK_START.md](QUICK_START.md)** - One command to reproduce the bug
- **[FINAL_SUMMARY.md](FINAL_SUMMARY.md)** - Complete bug analysis
- **[BUG_CONFIRMATION.md](BUG_CONFIRMATION.md)** - Detailed test results
- **[TESTING_TOOLS.md](TESTING_TOOLS.md)** - Comparison of testing approaches
- **[K6_TEST_README.md](K6_TEST_README.md)** - k6 load testing instructions

### Test Files

- `test-compression.sh` - Shell script using grpcurl (recommended)
- `k6-grpc-test.js` - k6 load test script
- JUnit tests in `src/test/java/org/acme/`

---

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides


## Provided Code

### gRPC

Create your first gRPC service

[Related guide section...](https://quarkus.io/guides/grpc-getting-started)
