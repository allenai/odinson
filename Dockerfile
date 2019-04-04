# This build uses SBT to create jars.
# This specific JDK version is important.  Both 8u212 and 11 cause compilation errors.
FROM openjdk:8u212-jdk-stretch as builder

# Install sbt
WORKDIR /sbt
RUN wget https://piccolo.link/sbt-1.2.8.tgz && tar xzvf sbt-1.2.8.tgz
ENV PATH="/sbt/sbt/bin:${PATH}"

WORKDIR /local
COPY . /local/

# Run update before stage and cache the output.
# The update takes a long time, and stage may fail.
RUN sbt update
RUN sbt backend/stage

# This build copies over the jars and sets up the path to run the application.
FROM openjdk:8u212-jdk-stretch

WORKDIR /local
COPY --from=builder /local/backend/target/universal/stage/ /local
ENTRYPOINT ["/local/bin/odinson"]
