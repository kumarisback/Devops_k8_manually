pipeline {
    agent any

    options {
        ansiColor('xterm')
        timestamps()
    }

    parameters {
        choice(name: 'SERVICE', choices: ['frontend', 'user-service', 'order-service', 'notification-service'], description: 'Select the service to build')
        booleanParam(name: 'PUSH_TO_ECR', defaultValue: false, description: 'Push Docker image to ECR after build')
        string(name: 'BACKEND_URL', defaultValue: '', description: 'Frontend API base URL for the frontend build')
    }

    environment {
        AWS_REGION = 'us-east-1'
        ECR_REGISTRY = '602367507570.dkr.ecr.us-east-1.amazonaws.com'
        IMAGE_TAG = "${env.GIT_COMMIT}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    // The image tag is the commit SHA, the tag gitops pins
                    // (ECR tags are immutable, so it can't be overwritten).
                    if (!(env.IMAGE_TAG ==~ /[0-9a-f]{40}/)) {
                        error "IMAGE_TAG '${env.IMAGE_TAG}' is not a commit SHA"
                    }
                }
            }
        }

        stage('Build & Test') {
            steps {
                script {
                    if (params.SERVICE == 'frontend') {
                        dir('frontend') {
                            sh 'npm install'
                            sh 'npm run lint'
                            sh 'npm run build'
                        }
                    } else if (params.SERVICE in ['user-service', 'order-service', 'notification-service']) {
                        dir(params.SERVICE) {
                            sh 'chmod +x gradlew'
                            sh './gradlew clean build -x test'
                        }
                    } else {
                        error "Unsupported service: ${params.SERVICE}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    if (params.SERVICE == 'frontend') {
                        dir('frontend') {
                            sh "docker build --build-arg VITE_API_BASE_URL=${params.BACKEND_URL} -t ${params.SERVICE}:${env.IMAGE_TAG} ."
                        }
                    } else {
                        dir(params.SERVICE) {
                            sh "docker build -t ${params.SERVICE}:${env.IMAGE_TAG} ."
                        }
                    }
                }
            }
        }

        stage('Push to ECR') {
            when {
                expression {
                    params.PUSH_TO_ECR
                }
            }
            steps {
                script {
                    def repository = params.SERVICE  // ECR repositories are named after the services (tf shared-services)
                    dir(params.SERVICE) {
                        sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${env.ECR_REGISTRY}"
                        sh "docker tag ${params.SERVICE}:${env.IMAGE_TAG} ${env.ECR_REGISTRY}/${repository}:${env.IMAGE_TAG}"
                        sh "docker push ${env.ECR_REGISTRY}/${repository}:${env.IMAGE_TAG}"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                def repository = params.SERVICE  // ECR repositories are named after the services (tf shared-services)
                dir(params.SERVICE) {
                    sh "docker rmi ${params.SERVICE}:${env.IMAGE_TAG} || true"
                    sh "docker rmi ${env.ECR_REGISTRY}/${repository}:${env.IMAGE_TAG} || true"
                }
            }
        }
    }
}
