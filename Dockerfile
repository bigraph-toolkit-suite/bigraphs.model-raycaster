FROM nvidia/opengl:1.0-glvnd-devel-ubuntu22.04

# Set environment variables for non-interactive installation
ENV DEBIAN_FRONTEND=noninteractive

# Update and install dependencies
RUN apt update && apt install -y \
    wget \
    curl \
    unzip \
    xauth  \
    xorg \
    xvfb \
    git \
    openjdk-21-jdk \
    maven \
    && rm -rf /var/lib/apt/lists/*


USER root
SHELL ["/bin/bash", "-c"]


# Copy the JAR into the container (assumes it exists relative to the Docker build context)
COPY target/bigrid-raycaster.jar /workspace/bigrid-raycaster.jar
COPY assets/data /workspace/data

# Default working directory
WORKDIR /workspace
CMD ["/bin/bash", "-c", "source /root/.bashrc && java -jar bigrid-raycaster.jar"]