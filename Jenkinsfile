pipeline {
    agent { label 'spring' }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 40, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    parameters {
        booleanParam(name: 'DEPLOY_PRODUCTION', defaultValue: true,
            description: 'Deploy prod after tests and image publication succeed')
    }

    triggers { pollSCM('H/5 * * * *') }

    environment {
        IMAGE_REPO = 'ghcr.io/thanhthbm/snapvocab-backend'
        DEPLOY_DIR = '/opt/snapvocab'
        AGENT_WORKSPACE_VOLUME = 'jenkins_agent_home'
        CI_NETWORK = 'jenkins_default'
    }

    stages {
        stage('Checkout prod') {
            steps {
                git branch: 'prod', credentialsId: 'github-snapvocab',
                    url: 'https://github.com/thanhthbm/snap-vocab-backend.git'
                script {
                    def sha = sh(script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true).trim()
                    env.IMAGE_TAG = "build-${env.BUILD_NUMBER}-${sha}"
                    env.CI_NAME = "snapvocab-ci-${env.BUILD_NUMBER}"
                }
            }
        }

        stage('Preflight') {
            steps {
                sh '''
                    set -eu
                    docker version --format '{{.Server.Version}}'
                    docker compose version
                    test -r "$DEPLOY_DIR/.env.prod"
                    docker volume inspect "$AGENT_WORKSPACE_VOLUME" >/dev/null
                    docker network inspect "$CI_NETWORK" >/dev/null
                    docker compose -p snapvocab -f "$DEPLOY_DIR/docker-compose.yml" config --quiet
                    docker pull mysql:8.0
                    docker pull maven:3.9-eclipse-temurin-17
                    docker pull curlimages/curl:latest
                '''
            }
        }

        stage('Test and package (Java 17)') {
            steps {
                sh '''
                    set -eu
                    docker run -d --name "$CI_NAME-db" \
                      --network "$CI_NETWORK" \
                      -e MYSQL_ROOT_PASSWORD=ci-test-only \
                      -e MYSQL_DATABASE=snapvocab_it mysql:8.0

                    ready=false
                    for i in $(seq 1 90); do
                      if docker exec "$CI_NAME-db" mysql \
                        -uroot -pci-test-only -e 'SELECT 1' >/dev/null 2>&1; then
                        ready=true
                        break
                      fi
                      sleep 2
                    done
                    if [ "$ready" != true ]; then
                      docker logs "$CI_NAME-db"
                      exit 1
                    fi

                    docker run --rm --name "$CI_NAME-build" \
                      --network "container:$CI_NAME-db" \
                      --user "$(id -u):$(id -g)" \
                      --mount "type=volume,src=$AGENT_WORKSPACE_VOLUME,dst=/home/jenkins/agent" \
                      -w "$WORKSPACE" -e MAVEN_CONFIG=/tmp/.m2 \
                      -e SNAPVOCAB_TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/snapvocab_it?createDatabaseIfNotExist=true&serverTimezone=UTC' \
                      -e SNAPVOCAB_TEST_DB_USERNAME=root \
                      -e SNAPVOCAB_TEST_DB_PASSWORD=ci-test-only \
                      maven:3.9-eclipse-temurin-17 \
                      sh -c 'export SPRING_DATASOURCE_URL="$SNAPVOCAB_TEST_DB_URL"; export SPRING_DATASOURCE_USERNAME="$SNAPVOCAB_TEST_DB_USERNAME"; export SPRING_DATASOURCE_PASSWORD="$SNAPVOCAB_TEST_DB_PASSWORD"; mvn -B -Dmaven.repo.local=/tmp/.m2/repository -Dspring.profiles.active=test clean verify'
                '''
            }
        }

        stage('Build and publish image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-snapvocab',
                    usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
                    sh '''
                        set -eu
                        set +x
                        export DOCKER_CONFIG="$(mktemp -d)"
                        context="$(mktemp -d)"
                        trap 'rm -rf "$DOCKER_CONFIG" "$context"' EXIT
                        printf '%s' "$GHCR_TOKEN" | docker login ghcr.io \
                          --username "$GHCR_USER" --password-stdin
                        mkdir "$context/target"
                        cp Dockerfile "$context/Dockerfile"
                        cp target/*.jar "$context/target/"
                        docker build -t "$IMAGE_REPO:$IMAGE_TAG" "$context"
                        docker push "$IMAGE_REPO:$IMAGE_TAG"
                    '''
                }
            }
        }

        stage('Deploy and smoke test') {
            when { expression { params.DEPLOY_PRODUCTION } }
            steps {
                sh '''
                    set -eu
                    cd "$DEPLOY_DIR"
                    previous=''
                    if docker inspect snapvocab-backend >/dev/null 2>&1; then
                      previous=$(docker inspect --format '{{.Config.Image}}' snapvocab-backend)
                    fi
                    if [ -n "$previous" ]; then
                      printf '%s\n' "$previous" > .previous-image
                    fi

                    if docker compose -p snapvocab up -d --no-deps backend && \
                      docker run --rm --name "$CI_NAME-probe" --network host \
                        curlimages/curl:latest \
                        --fail --silent --show-error --retry 30 \
                        --retry-delay 5 --retry-all-errors --retry-max-time 180 \
                        --max-time 5 --output /dev/null \
                        http://127.0.0.1:8080/api/collections/system; then
                      printf '%s\n' "$IMAGE_TAG" > .deployed-tag
                      printf '%s\n' "$IMAGE_REPO:$IMAGE_TAG" > .deployed-image
                    else
                      docker logs --tail=100 snapvocab-backend 2>&1 || true
                      if [ -n "$previous" ]; then
                        export BACKEND_IMAGE="$previous"
                        docker compose -p snapvocab up -d --no-deps backend
                        echo 'Restored previous image; database changes are not reversed.'
                      fi
                      exit 1
                    fi
                '''
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            sh '''
                if [ -n "${CI_NAME:-}" ]; then
                  docker rm -fv "$CI_NAME-build" "$CI_NAME-probe" "$CI_NAME-db" \
                    >/dev/null 2>&1 || true
                fi
            '''
        }
    }
}
