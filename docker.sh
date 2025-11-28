#!/bin/bash

# Set image name and base tag
IMAGE_NAME="v2-leo-api-gateway-server"
BASE_TAG="r1v"
PLATFORM="linux/amd64"
START_VERSION=1
PORT=8080


# Create the named volume if it doesn't exist
docker volume inspect leo-server-nfs > /dev/null 2>&1 || docker volume create leo-server-nfs

# Function to check if image with tag exists locally
check_image_exists() {
    local image="$1"
    local tag="$2"
    docker image inspect "${image}:${tag}" > /dev/null 2>&1
    return $?
}

# Initialize version
version=$START_VERSION
TAG="${BASE_TAG}${version}"

# Check if tag is occupied and increment if necessary
while check_image_exists "$IMAGE_NAME" "$TAG"; do
    ((version++))
    TAG="${BASE_TAG}${version}"
done

# Build the Docker image with the available tag
echo "Building Docker image: ${IMAGE_NAME}:${TAG}"
docker build -t "${IMAGE_NAME}:${TAG}" --platform "$PLATFORM" .

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Successfully built ${IMAGE_NAME}:${TAG}"
else
    echo "Failed to build ${IMAGE_NAME}:${TAG}"
    exit 1
fi

# Run the Docker container with port 8080 exposed
echo "Running Docker container: ${IMAGE_NAME}:${TAG}"
docker run -d -p ${PORT}:${PORT} --name "${IMAGE_NAME}-${TAG}" "${IMAGE_NAME}:${TAG}"

# Check if container started successfully
if [ $? -eq 0 ]; then
    echo "Successfully started container ${IMAGE_NAME}-${TAG} on port ${PORT}"
else
    echo "Failed to start container ${IMAGE_NAME}-${TAG}"
    exit 1
fi