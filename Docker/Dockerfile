# Use Ubuntu 22.04 LTS as the base image.
FROM ubuntu:22.04

RUN apt-get update
RUN \
  DEBIAN_FRONTEND=noninteractive apt-get install -y software-properties-common && \
# Install Git
  apt-get install -y git && \
# Install python
  apt-get update && \
  rm -rf /var/lib/apt/lists/* && \
# Install misc
  apt-get update && \
  apt-get install -y sudo && \
  apt-get install -y vim && \
  apt-get install -y emacs && \  
  apt-get install -y wget && \
  apt-get install -y bc && \
  apt-get install -y cloc && \
  apt-get install -y zip unzip && \
  apt-get install -y locales locales-all && \
  apt-get install -y parallel && \
# Install OpenJDK 8
  apt-get install -y openjdk-8-jdk && \
  mv /usr/lib/jvm/java-8-openjdk* /usr/lib/jvm/java-8-openjdk

WORKDIR /root

# Use OpenJDK 8 when building the docker image
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk

# Install Maven 3.9.10 locally for user
RUN \
  wget https://dlcdn.apache.org/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.tar.gz && \
  tar -xzf apache-maven-3.9.10-bin.tar.gz && mv apache-maven-3.9.10/ apache-maven/ && \
  rm apache-maven-3.9.10-bin.tar.gz && \
  cd ~/ && \
# Set up the user's configurations
  wget https://github.com/eclipse-aspectj/aspectj/releases/download/V1_9_7/aspectj-1.9.7.jar && \
  mkdir aspectj-1.9.7 && /usr/lib/jvm/java-8-openjdk/bin/java -jar aspectj-1.9.7.jar -to aspectj-1.9.7 && rm aspectj-1.9.7.jar && \
  sed -i 's/64M/10240M/g' aspectj-1.9.7/bin/ajc && \
  echo 'JAVAHOME=/usr/lib/jvm/java-8-openjdk' >> ~/.bashrc && \
  echo 'export JAVA_HOME=${JAVAHOME}' >> ~/.bashrc && \
  echo 'export M2_HOME=${HOME}/apache-maven' >> ~/.bashrc && \
  echo 'export MAVEN_HOME=${HOME}/apache-maven' >> ~/.bashrc && \
  echo 'ASPECTJ_DIR=${HOME}/aspectj-1.9.7' >> ~/.bashrc && \
  echo 'export PATH=${M2_HOME}/bin:${JAVAHOME}/bin:${ASPECTJ_DIR}/bin:${ASPECTJ_DIR}/lib/aspectjweaver.jar:${PATH}' >> ~/.bashrc && \
  echo 'export CLASSPATH=${ASPECTJ_DIR}/lib/aspectjtools.jar:${ASPECTJ_DIR}/lib/aspectjrt.jar:${ASPECTJ_DIR}/lib/aspectjweaver.jar:${CLASSPATH}' >> ~/.bashrc

ENV CLASSPATH=/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar
ENV PATH=/root/apache-maven/bin:/usr/lib/jvm/java-8-openjdk/bin:/root/aspectj-1.9.7/bin:/root/aspectj-1.9.7/lib/aspectjtools.jar:/root/aspectj-1.9.7/lib/aspectjrt.jar:/root/aspectj-1.9.7/lib/aspectjweaver.jar:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin


ENV LC_ALL=en_US.UTF-8
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US.UTF-8

RUN git clone "https://github.com/baggage-shiver/mop" finemop

RUN bash finemop/scripts/setup.sh /root/env
