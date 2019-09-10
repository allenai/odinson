# This build uses SBT to create jars.
# This specific JDK version is important.  Both 8u212 and 11 cause compilation errors.
FROM openjdk:8u212-jdk-stretch as builder

# Install sbt
WORKDIR /sbt
RUN wget https://piccolo.link/sbt-1.2.8.tgz && tar xzvf sbt-1.2.8.tgz
ENV PATH="/sbt/sbt/bin:${PATH}"

WORKDIR /local
COPY build.sbt /local/build.sbt
COPY project /local/project
COPY backend /local/backend
COPY extra /local/extra
COPY core/build.sbt /local/core/build.sbt
COPY core/src/main/scala /local/core/src/main/scala

# Run update before stage and cache the output.
# The update takes a long time, and stage may fail.
RUN sbt backend/update
RUN sbt backend/compile

COPY core/src/main/resources /local/core/src/main/resources
RUN sbt backend/stage

# This build copies over the jars and sets up the path to run the application.
FROM openjdk:8u212-jdk-stretch

WORKDIR /local
RUN mkdir /local/data

# by default we build an image with tacred. To build with KBP pass --build-arg user=kbp-odinson-index-ordered-<date>
# to docker build.
ARG dataset_name
ENV dataset_name ${dataset_name:-tacred-train-odinson-index-ordered-09092019}
RUN curl https://storage.googleapis.com/ai2i/SPIKE/${dataset_name}.tar.gz | tar -C /local/data -xzv
COPY --from=builder /local/backend/target/universal/stage/ /local

ENTRYPOINT ["/local/bin/odinson-rest-api"]
